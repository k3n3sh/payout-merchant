package co.kp.merchantpayout

import co.kp.merchantpayout.data.DeviceIdProvider
import co.kp.merchantpayout.data.MerchantRepository
import co.kp.merchantpayout.data.PayoutRepository
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.DomainError
import co.kp.merchantpayout.domain.MerchantSnapshot
import co.kp.merchantpayout.domain.Money
import co.kp.merchantpayout.domain.Outcome
import co.kp.merchantpayout.domain.Payout
import co.kp.merchantpayout.domain.PayoutDraft
import co.kp.merchantpayout.domain.PayoutStatus
import co.kp.merchantpayout.ui.payout.PayoutViewModel
import co.kp.merchantpayout.ui.payout.SubmissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PayoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // ViewModel launch on Main dispatcher. redirect Main to test dispatcher so we can
        // control when coroutine run.
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitSuccessEmitsSuccessState() = runTest {
        val payout = samplePayout()
        val repo = FakePayoutRepository(Outcome.Ok(payout))
        val vm = PayoutViewModel(repo, FakeDeviceIdProvider("device-1"),FakeMerchantRepository())

        setupValidForm(vm)
        vm.submit()
        advanceUntilIdle()

        val submission = vm.submission.value
        val expected = SubmissionState.Success(payout)
        assertEquals(expected, submission)
    }

    @Test
    fun submitInsufficientFundsEmitsFailureState() = runTest {

        val repo = FakePayoutRepository(Outcome.Err(DomainError.InsufficientFunds))
        val vm = PayoutViewModel(repo, FakeDeviceIdProvider(),FakeMerchantRepository())

        setupValidForm(vm)
        vm.submit()
        advanceUntilIdle()

        val submission = vm.submission.value
        val expected = SubmissionState.Failure(DomainError.InsufficientFunds)
        assertEquals(expected, submission)
    }

    @Test
    fun doubleTapOnlyFiresOneNetworkCall() = runTest {
        val repo = FakePayoutRepository(Outcome.Ok(samplePayout()))
        val fakeMerchantRepository = FakeMerchantRepository()
        val vm = PayoutViewModel(repo, FakeDeviceIdProvider(), fakeMerchantRepository)

        setupValidForm(vm)
        vm.submit()
        vm.submit()  // should be ignored while first one still in flight
        advanceUntilIdle()

        assertEquals(1, repo.submitCount)
    }

    // ─── helpers ────────────────────────────────────────────

    private fun setupValidForm(vm: PayoutViewModel) {
        vm.onAmountChange("50.00")
        vm.onCurrencyChange(Currency.GBP)
        vm.onIbanChange("GB29NWBK60161331926819")
        vm.goToConfirm()
    }

    private fun samplePayout(): Payout {
        return Payout(
            id = "pay_001",
            status = PayoutStatus.COMPLETED,
            amount = Money(5000L, Currency.GBP),
            iban = "GB29NWBK60161331926819",
            ref = "Invoice XYZ",
            createdAt = Instant.parse("2025-05-18T10:00:00Z"),
        )
    }

    // ─── fakes ──────────────────────────────────────────────

    private class FakePayoutRepository(
        private val result: Outcome<Payout>,
    ) : PayoutRepository {
        var submitCount = 0

        override suspend fun submit(draft: PayoutDraft, deviceId: String?): Outcome<Payout> {
            submitCount++
            return result
        }

        override suspend fun get(id: String): Outcome<Payout> {
            return result
        }
    }

    private class FakeDeviceIdProvider(
        private val value: String? = "device-1",
    ) : DeviceIdProvider {
        override suspend fun get(): String? {
            return value
        }
    }

    private class FakeMerchantRepository(
        private val snapshot: MerchantSnapshot = MerchantSnapshot(
            availableBalance = Money(500_000_00L, Currency.GBP),
            pendingBalance = Money(0L, Currency.GBP),
            recentActivity = emptyList(),
        ),
    ) : MerchantRepository {
        override suspend fun getSnapshot(): Outcome<MerchantSnapshot> = Outcome.Ok(snapshot)
    }
}