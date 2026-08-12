package co.kp.merchantpayout.ui.payout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.kp.merchantpayout.data.DeviceIdProvider
import co.kp.merchantpayout.data.MerchantRepository
import co.kp.merchantpayout.data.PayoutRepository
import co.kp.merchantpayout.domain.AmountIssue
import co.kp.merchantpayout.domain.AmountParse
import co.kp.merchantpayout.domain.AmountParser
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.DomainError
import co.kp.merchantpayout.domain.IbanBreakdown
import co.kp.merchantpayout.domain.IbanIssue
import co.kp.merchantpayout.domain.IbanValidator
import co.kp.merchantpayout.domain.Money
import co.kp.merchantpayout.domain.Outcome
import co.kp.merchantpayout.domain.Payout
import co.kp.merchantpayout.domain.PayoutDraft
import co.kp.merchantpayout.domain.PayoutStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// ─── Which of the three payout screen are we showing ────────────────────

enum class PayoutStep { FORM, CONFIRM, RESULT }

// ─── Form state ─────────────────────────────────────────────────────────

data class PayoutFormState(
    val amountText: String = "",
    val currency: Currency = Currency.GBP,
    val ibanText: String = "",
    val ref: String = "",
    val amountIssue: AmountIssue? = null,
    val availableBalanceAmount: Money = Money(0L, Currency.GBP),
    val ibanIssue: IbanIssue? = null,
) {
    // strict validation used to enable/disable Confirm button. run the real parsers
    // so a mid typing "500." wont let button turn on even if error is suppressed for display.
    val canConfirm: Boolean
        get() {
            if (ref.isBlank())
                return false

            if (amountText.isBlank())
                return false

            if (ibanText.isBlank())
                return false

            val amountParsed = AmountParser.parse(amountText)
            if (amountParsed !is AmountParse.Ok)
                return false

            val ibanCheck = IbanValidator.validate(ibanText)

            return ibanCheck == null
        }
}

// ─── Submission state ───────────────────────────────────────────────────

sealed class SubmissionState {
    object Idle : SubmissionState()
    object Submitting : SubmissionState()
    data class Success(val payout: Payout) : SubmissionState()
    data class Failure(val error: DomainError) : SubmissionState()
}

// ─── ViewModel ──────────────────────────────────────────────────────────

// TODO: save draft in SavedStateHandle so it survive process death. do later.
@HiltViewModel
class PayoutViewModel @Inject constructor(
    private val payoutRepository: PayoutRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val merchantRepository: MerchantRepository,
) : ViewModel() {


    private val formFlow = MutableStateFlow(PayoutFormState())
    val form: StateFlow<PayoutFormState> = formFlow.asStateFlow()

    // derived — analyze iban text every keystroke, share result with the composable.
    val ibanBreakdown: StateFlow<IbanBreakdown> = formFlow
        .map { state -> IbanValidator.analyze(state.ibanText) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, IbanValidator.analyze(""))

    private val stepFlow = MutableStateFlow(PayoutStep.FORM)
    val step: StateFlow<PayoutStep> = stepFlow.asStateFlow()

    private val submissionFlow = MutableStateFlow<SubmissionState>(SubmissionState.Idle)
    val submission: StateFlow<SubmissionState> = submissionFlow.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val merchantResult = merchantRepository.getSnapshot()
            if (merchantResult is Outcome.Ok) {
                val snap = merchantResult.value
                formFlow.value = formFlow.value.copy(
                    availableBalanceAmount = snap.availableBalance
                )
            }
        }
    }

    fun onAmountChange(text: String) {
        val issue = computeSoftAmountIssue(text)
        val current = formFlow.value
        formFlow.value = current.copy(amountText = text, amountIssue = issue)
    }

    fun onCurrencyChange(currency: Currency) {
        val current = formFlow.value
        formFlow.value = current.copy(currency = currency)
    }

    fun onRefChange(text: String) {
        val current = formFlow.value
        formFlow.value = current.copy(ref = text)
    }

    fun onIbanChange(text: String) {
        val issue: IbanIssue?
        if (text.isEmpty()) {
            issue = null
        } else {
            issue = IbanValidator.validate(text)
        }
        val current = formFlow.value
        formFlow.value = current.copy(ibanText = text, ibanIssue = issue)
    }

    // strict pass on blur — no more "user is mid typing" suppression.
    fun validateAmountOnBlur() {
        val text = formFlow.value.amountText
        val issue: AmountIssue?
        if (text.isBlank()) {
            issue = null
        } else {
            val parsed = AmountParser.parse(text)
            if (parsed is AmountParse.Err) {
                issue = parsed.issue
            } else {
                issue = null
            }
        }
        val current = formFlow.value
        formFlow.value = current.copy(amountIssue = issue)
    }

    fun goToConfirm() {
        val draft = buildDraft()
        if (draft != null)
            stepFlow.value = PayoutStep.CONFIRM
    }

    fun goBackToForm() {
        stepFlow.value = PayoutStep.FORM
    }

    fun submit() {
        val draft = buildDraft() ?: return
        // stop double tap.
        if (submissionFlow.value is SubmissionState.Submitting)
            return

        submissionFlow.value = SubmissionState.Submitting
        stepFlow.value = PayoutStep.RESULT

        viewModelScope.launch {
            val deviceId = deviceIdProvider.get()
            val result = payoutRepository.submit(draft, deviceId)
            if (result is Outcome.Ok) {
                submissionFlow.value = SubmissionState.Success(result.value)
                startPolling(result.value.id)
            }
            if (result is Outcome.Err)
                submissionFlow.value = SubmissionState.Failure(result.error)

        }
    }

    fun startPolling(id: String) {
        pollJob?.cancel()

        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(2000.milliseconds)
                val outcome = payoutRepository.get(id)
                if (outcome is Outcome.Ok) {
                    val payout = outcome.value
                    if (payout.status == PayoutStatus.COMPLETED || payout.status == PayoutStatus.FAILED) {
                        submissionFlow.value = SubmissionState.Success(payout)
                        pollJob?.cancel()
                    }
                }

            }
        }
    }


    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    fun retryFromResult() {
        submissionFlow.value = SubmissionState.Idle
        stepFlow.value = PayoutStep.CONFIRM
    }

    fun reset() {
        formFlow.value = PayoutFormState()
        submissionFlow.value = SubmissionState.Idle
        stepFlow.value = PayoutStep.FORM
    }

    // first version return sealed error type but only one caller so nullable is fine.
    private fun buildDraft(): PayoutDraft? {
        val current = formFlow.value
        val amountResult = AmountParser.parse(current.amountText)
        if (amountResult !is AmountParse.Ok) return null

        val ibanIssue = IbanValidator.validate(current.ibanText)
        if (ibanIssue != null) return null

        val cleanedIban = current.ibanText.replace(Regex("\\s"), "").uppercase()
        return PayoutDraft(
            amount = Money(amountResult.minorUnits, current.currency),
            iban = cleanedIban,
            ref = current.ref
        )
    }

    // dont flag errors while user is still typing,  empty, "0", "." or trailing ".".
    private fun computeSoftAmountIssue(text: String): AmountIssue? {
        val trimmed = text.trim()
        if (trimmed.isEmpty())
            return null

        if (trimmed == "0" || trimmed == ".")
            return null

        if (trimmed.endsWith('.'))
            return null

        val parsed = AmountParser.parse(trimmed)
        if (parsed is AmountParse.Err)
            return parsed.issue

        if (parsed is AmountParse.Ok) {
            val formValue = formFlow.value
            if (formValue.availableBalanceAmount.minorUnits < parsed.minorUnits)
                return AmountIssue.OVER_BALANCE
        }

        return null
    }
}