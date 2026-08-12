package co.kp.merchantpayout.data


import co.kp.merchantpayout.domain.ActivityKind
import co.kp.merchantpayout.domain.ActivityStatus
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.MerchantActivity
import co.kp.merchantpayout.domain.MerchantSnapshot
import co.kp.merchantpayout.domain.Money
import co.kp.merchantpayout.domain.Payout
import co.kp.merchantpayout.domain.PayoutDraft
import co.kp.merchantpayout.domain.PayoutStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant


// Merge in one file as its small app, including some enum, mapper and dtos - less over engineering at this stage

// ─── Retrofit interface ─────────────────────────────────────────────────────

//Retrofit dispatches on its own IO pool. Don't need withContext(IO)
interface MerchantApi {

    @GET("api/merchant")
    suspend fun getMerchant(): MerchantDto

    @GET("api/merchant/activity")
    suspend fun getActivity(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 15,
    ): PaginatedActivityDto

    @POST("api/payouts")
    suspend fun createPayout(@Body body: CreatePayoutRequestDto): PayoutDto

    @GET("api/devices")
    suspend fun getDevice(): DeviceDto

    @GET("api/payouts/{id}")
    suspend fun getPayout(@Path("id") id: String): PayoutDto
}

// ─── Enums ──────────────────────────────────────────────────────────────────

// Server sends lowercase; we need in UPPERCASE. @SerialName bridges without
// pushing "payout" (the string) into UI code.

@Serializable
enum class CurrencyDto { GBP, EUR }

@Serializable
enum class ActivityKindDto {
    @SerialName("payout") PAYOUT,
    @SerialName("deposit") DEPOSIT,
    @SerialName("refund") REFUND,
    @SerialName("fee") FEE,
}

@Serializable
enum class ActivityStatusDto {
    @SerialName("completed") COMPLETED,
    @SerialName("pending") PENDING,
    @SerialName("processing") PROCESSING,
    @SerialName("failed") FAILED,
}

@Serializable
enum class PayoutStatusDto {
    @SerialName("pending") PENDING,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
}

// ─── DTOs ───────────────────────────────────────────────────────────────────

// keep names in camelCase here. AppModule tells the parser to change them to upper case
// when reading/writing JSON, so we dont need @SerialName on every field.

@Serializable
data class ActivityDto(
    val id: String,
    val type: ActivityKindDto,
    val amount: Int,        // pence/paisa; negative for outflows
    val currency: CurrencyDto,
    val date: String,       // ISO
    val description: String,
    val status: ActivityStatusDto,
)

@Serializable
data class MerchantDto(
    val availableBalance: Int,
    val pendingBalance: Int,
    val currency: CurrencyDto,
    val activity: List<ActivityDto>,
)

@Serializable
data class PaginatedActivityDto(
    val items: List<ActivityDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

@Serializable
data class CreatePayoutRequestDto(
    val amount: Int,
    val currency: CurrencyDto,
    val iban: String,
    val ref :String,
    // Null by default, Json { explicitNulls = false } remove the field from the payload
    // when we dont have a device id yet.
    val deviceId: String? = null,
)

@Serializable
data class PayoutDto(
    val id: String,
    val status: PayoutStatusDto,
    val amount: Int,
    val currency: CurrencyDto,
    val iban: String,
    val ref: String,
    val createdAt: String,
)

@Serializable
data class DeviceDto(val deviceId: String)






// ─── Mappers — plain functions, no extension receivers ──────────────────────

fun mapCurrency(dto: CurrencyDto): Currency = when (dto) {
    CurrencyDto.GBP -> Currency.GBP
    CurrencyDto.EUR -> Currency.EUR
}

fun mapCurrencyToDto(currency: Currency): CurrencyDto = when (currency) {
    Currency.GBP -> CurrencyDto.GBP
    Currency.EUR -> CurrencyDto.EUR
}

fun mapActivityKind(dto: ActivityKindDto): ActivityKind = when (dto) {
    ActivityKindDto.PAYOUT -> ActivityKind.PAYOUT
    ActivityKindDto.DEPOSIT -> ActivityKind.DEPOSIT
    ActivityKindDto.REFUND -> ActivityKind.REFUND
    ActivityKindDto.FEE -> ActivityKind.FEE
}

fun mapActivityStatus(dto: ActivityStatusDto): ActivityStatus = when (dto) {
    ActivityStatusDto.COMPLETED -> ActivityStatus.COMPLETED
    ActivityStatusDto.PENDING -> ActivityStatus.PENDING
    ActivityStatusDto.PROCESSING -> ActivityStatus.PROCESSING
    ActivityStatusDto.FAILED -> ActivityStatus.FAILED
}

fun mapPayoutStatus(dto: PayoutStatusDto): PayoutStatus = when (dto) {
    PayoutStatusDto.PENDING -> PayoutStatus.PENDING
    PayoutStatusDto.PROCESSING -> PayoutStatus.PROCESSING
    PayoutStatusDto.COMPLETED -> PayoutStatus.COMPLETED
    PayoutStatusDto.FAILED -> PayoutStatus.FAILED
}

fun mapActivity(dto: ActivityDto): MerchantActivity = MerchantActivity(
    id = dto.id,
    kind = mapActivityKind(dto.type),
    amount = Money(dto.amount.toLong(), mapCurrency(dto.currency)),
    occurredAt = Instant.parse(dto.date),
    description = dto.description,
    status = mapActivityStatus(dto.status),
)

fun mapMerchant(dto: MerchantDto): MerchantSnapshot {
    val cur = mapCurrency(dto.currency)
    return MerchantSnapshot(
        availableBalance = Money(dto.availableBalance.toLong(), cur),
        pendingBalance = Money(dto.pendingBalance.toLong(), cur),
        recentActivity = dto.activity.map { mapActivity(it) },
    )
}

fun mapPayout(dto: PayoutDto): Payout = Payout(
    id = dto.id,
    status = mapPayoutStatus(dto.status),
    amount = Money(dto.amount.toLong(), mapCurrency(dto.currency)),
    iban = dto.iban,
    ref = dto.ref,
    createdAt = Instant.parse(dto.createdAt),
)

fun buildPayoutRequest(draft: PayoutDraft, deviceId: String?): CreatePayoutRequestDto =
    CreatePayoutRequestDto(
        amount = draft.amount.minorUnits.toInt(),
        currency = mapCurrencyToDto(draft.amount.currency),
        iban = draft.iban,
        ref = draft.ref,
        deviceId = deviceId,
    )