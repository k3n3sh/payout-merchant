package co.kp.merchantpayout.data

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton


interface DeviceIdProvider {
    suspend fun get(): String?
}

// server-provide device id via /api/devices. cache in memory so we dont hit api
// every time a payout is submit.
@Singleton
class ServerDeviceIdProvider @Inject constructor(private val api: MerchantApi,) : DeviceIdProvider {

    private val cached = AtomicReference<String?>(null)

    override suspend fun get(): String? {
        val existing = cached.get()
        if (existing != null)
            return existing

        try {
            val response = api.getDevice()
            val fresh = response.deviceId
            cached.compareAndSet(null, fresh)
            return fresh
        } catch (t: Throwable) {
            // device id is nice-to-have. if fetch fail we just send null in the payout body.
            return null
        }
    }
}