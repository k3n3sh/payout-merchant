package co.kp.merchantpayout.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// small helper — when payout happen, bump this counter. Home and Transactions watch it
// and refetch when the number change. plain counter, no fancy signal or event bus.
//
// tried this first with MutableSharedFlow<Unit> but Home missed the emit if it wasn't
// subscribed yet at boot. counter with StateFlow works because new subscribers always
// get the current value.
// private val innerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
@Singleton
class PayoutRefresher @Inject constructor() {

    private val counter = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = counter.asStateFlow()

    fun bump() {
        counter.value += 1
    }
}
