package co.kp.merchantpayout

import android.app.Application
import android.util.Log
import co.kp.merchantpayout.mock.MockServerManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class MerchantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Boot the mock server on IO thread. any network work that StrictMode blocks on main.
        // runBlocking parks the main thread until the socket is open + baseUrl is cached.
        try {
            runBlocking(Dispatchers.IO) { MockServerManager.start() }
        } catch (t: Throwable) {
            Log.e("MerchantApp", "Mock server failed to boot", t)
            throw t
        }
    }


    // Commented out because it would break the app: Retrofit cache the old port as @Singleton,
    // so after restart it hit a dead port. also on real device the api is on the
    // internet so nothing to shut down.

    // ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    //     override fun onStop(owner: LifecycleOwner) {
    //         MockServer.shutdown()
    //     }
    // })
}