package co.kp.merchantpayout.data

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import co.kp.merchantpayout.domain.MerchantActivity
import co.kp.merchantpayout.domain.MerchantSnapshot
import co.kp.merchantpayout.domain.Outcome
import co.kp.merchantpayout.domain.Payout
import co.kp.merchantpayout.domain.PayoutDraft
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

// ─── MerchantRepository ────────────────────────────────────────────────────

interface MerchantRepository {
    suspend fun getSnapshot(): Outcome<MerchantSnapshot>
}

@Singleton
class MerchantRepositoryImpl @Inject constructor(
    private val api: MerchantApi,
) : MerchantRepository {

    override suspend fun getSnapshot(): Outcome<MerchantSnapshot> {
        return runApi {
            val dto = api.getMerchant()
            mapMerchant(dto)
        }
    }
}

// ─── ActivityRepository + paging source ────────────────────────────────────

interface ActivityRepository {
    fun pagedActivity(): Flow<PagingData<MerchantActivity>>
}

// server give us a "next_cursor" string with every page. we send it back to ask for the
// next page. this is better than using page number, because if a new activity row shows
// up while user is scrolling, page numbers would go wrong. cursor points to a specific
// row so it stay correct.
class ActivityPagingSource(private val api: MerchantApi) : PagingSource<String, MerchantActivity>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, MerchantActivity> {
        val cursor = params.key

        // use loadSize the paging library gives us, but keep it in a safe range so we dont
        // send too small or too big number to the server
        var limit = params.loadSize
        if (limit < 10)
            limit = 10

        if (limit > 50)
            limit = 50


        try {
            val response = api.getActivity(cursor = cursor, limit = limit)
            val items = mutableListOf<MerchantActivity>()
            for (dto in response.items)
                items.add(mapActivity(dto))

            val next = if (response.hasMore) response.nextCursor else null
            return LoadResult.Page(
                data = items,
                prevKey = null,   // no back paging, feed is newest first
                nextKey = next,
            )
        } catch (e: IOException) {
            return LoadResult.Error(e)
        } catch (e: HttpException) {
            return LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, MerchantActivity>): String? {
        return null
    }
}

@Singleton
class ActivityRepositoryImpl @Inject constructor(private val api: MerchantApi) : ActivityRepository {

    override fun pagedActivity(): Flow<PagingData<MerchantActivity>> {
        val config = PagingConfig(
            pageSize = 15,
            prefetchDistance = 5,
            enablePlaceholders = false,   // cursor paging dont know total count
        )
        val pager = Pager(config = config, pagingSourceFactory = { ActivityPagingSource(api) })
        return pager.flow
    }

    // thought i need this in transactions VM but no. keep for later maybe.
    @Suppress("unused")
    suspend fun firstPageOnly(limit: Int = 15): List<MerchantActivity> {
        val response = api.getActivity(cursor = null, limit = limit)
        val out = mutableListOf<MerchantActivity>()
        for (dto in response.items) out.add(mapActivity(dto))
        return out
    }
}

// ─── PayoutRepository ──────────────────────────────────────────────────────

interface PayoutRepository {
    suspend fun submit(draft: PayoutDraft, deviceId: String?): Outcome<Payout>
    suspend fun get(id: String): Outcome<Payout>
}

@Singleton
class PayoutRepositoryImpl @Inject constructor(
    private val api: MerchantApi,
    private val refresher: PayoutRefresher,
) : PayoutRepository {

    override suspend fun submit(draft: PayoutDraft, deviceId: String?): Outcome<Payout> {
        val outcome = runApi {
            val request = buildPayoutRequest(draft, deviceId)
            val dto = api.createPayout(request)
            mapPayout(dto)
        }
        Log.d("PAYOUT", "submit outcome: $outcome")

        if (outcome is Outcome.Ok)
            refresher.bump()

        return outcome
    }

    override suspend fun get(id: String): Outcome<Payout> {
        return runApi {
            val dto = api.getPayout(id)
            mapPayout(dto)
        }
    }
}


