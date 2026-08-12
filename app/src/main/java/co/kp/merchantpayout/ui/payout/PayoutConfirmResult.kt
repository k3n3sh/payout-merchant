package co.kp.merchantpayout.ui.payout

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kp.merchantpayout.domain.AmountParse
import co.kp.merchantpayout.domain.AmountParser
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.DomainError
import co.kp.merchantpayout.domain.Money
import co.kp.merchantpayout.domain.Payout
import co.kp.merchantpayout.domain.PayoutDraft
import co.kp.merchantpayout.domain.PayoutRules
import co.kp.merchantpayout.domain.PayoutStatus
import co.kp.merchantpayout.security.BiometricAvailability
import co.kp.merchantpayout.security.BiometricGate
import co.kp.merchantpayout.security.BiometricResult
import co.kp.merchantpayout.security.PayoutSecurityBanner
import co.kp.merchantpayout.security.ScreenSecurityEffect
import co.kp.merchantpayout.security.rememberBiometricGate
import co.kp.merchantpayout.security.rememberBiometricPromptLauncher
import co.kp.merchantpayout.ui.theme.CheckoutTheme
import kotlinx.coroutines.launch

// ─── Flow container ───────────────────────────────────────────────────

@Composable
fun PayoutFlow(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PayoutViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()

    if (step == PayoutStep.FORM) {
        PayoutFormScreen(
            viewModel = viewModel,
            onCancel = { viewModel.reset(); onExit() },
            onConfirm = { viewModel.goToConfirm() },
            modifier = modifier,
        )
    }
    if (step == PayoutStep.CONFIRM) {
        PayoutConfirmScreen(
            viewModel = viewModel,
            onBack = { viewModel.goBackToForm() },
            onSubmit = { viewModel.submit() },
            modifier = modifier,
        )
    }
    if (step == PayoutStep.RESULT) {
        PayoutResultScreen(
            viewModel = viewModel,
            onDone = { viewModel.reset(); onExit() },
            onRetry = { viewModel.retryFromResult() },
            modifier = modifier,
        )
    }
}

// ─── Confirm screen (stateful wrapper) ────────────────────────────────

@Composable
fun PayoutConfirmScreen(
    viewModel: PayoutViewModel,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val submission by viewModel.submission.collectAsStateWithLifecycle()
    val isSubmitting = submission is SubmissionState.Submitting

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberBiometricGate()
    val runBiometric = rememberBiometricPromptLauncher(
        title = "Confirm payout",
        subtitle = "Verify your identity to send this payment.",
    )
    var alertText by remember { mutableStateOf<String?>(null) }
    var showEnrolmentDialog by remember { mutableStateOf(false) }

    val amountText: String
    val parsed = AmountParser.parse(form.amountText)
    if (parsed is AmountParse.Ok)
        amountText = Money(parsed.minorUnits, form.currency).format()
     else
        amountText = "-"

    val ibanClean = form.ibanText.replace(Regex("\\s"), "").uppercase()

    ScreenSecurityEffect()

    fun handleSend() {
        if (!isSubmitting) {
            scope.launch {
                val decision = handleSendTap(form, gate, runBiometric)
                when (decision) {
                    SendDecision.Proceed -> onSubmit()
                    SendDecision.Silent -> { /* no-op */ }
                    SendDecision.NeedsEnrolment -> showEnrolmentDialog = true
                    is SendDecision.ShowMessage -> alertText = decision.text
                }
            }
        }
    }

    PayoutConfirmContent(
        amountText = amountText,
        ibanText = ibanClean,
        isSubmitting = isSubmitting,
        actions = ConfirmActions(onBack = onBack, onSend = ::handleSend),
        modifier = modifier,
    )

    if (showEnrolmentDialog) {
        AlertDialog(
            onDismissRequest = { showEnrolmentDialog = false },
            title = { Text(text = "Set up biometrics") },
            text = { Text(text = "Payouts over £1,000.00 require biometric verification. Add a fingerprint or face unlock in Settings, then try again.") },
            confirmButton = {
                TextButton(onClick = {
                    showEnrolmentDialog = false
                    val intent: Intent
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                            putExtra(
                                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                BiometricManager.Authenticators.BIOMETRIC_STRONG,
                            )
                        }
                    } else {
                        intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    }
                    ContextCompat.startActivity(context, intent, null)
                }) { Text(text = "Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showEnrolmentDialog = false }) {
                    Text(text = "Not now")
                }
            },
        )
    }
    val current = alertText
    if (current != null) {
        AlertDialog(
            onDismissRequest = { alertText = null },
            title = { Text(text = "Biometrics unavailable") },
            text = { Text(text = current) },
            confirmButton = {
                TextButton(onClick = { alertText = null }) { Text(text = "OK") }
            },
        )
    }
}

// ─── Confirm content (stateless) ──────────────────────────────────────

// group the two callbacks so the composable signature stay short.
data class ConfirmActions(
    val onBack: () -> Unit,
    val onSend: () -> Unit,
)

@Composable
private fun PayoutConfirmContent(
    amountText: String,
    ibanText: String,
    isSubmitting: Boolean,
    actions: ConfirmActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CheckoutTheme.colors.canvas),
    ) {
        PayoutSecurityBanner()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Confirm payout",
                style = CheckoutTheme.typography.title,
                color = CheckoutTheme.colors.textPrimary,
            )
            Text(
                text = "Double-check the details below. Once you send, the transfer is irreversible.",
                style = CheckoutTheme.typography.body,
                color = CheckoutTheme.colors.textSecondary,
            )
            SummaryCard(amount = amountText, iban = ibanText)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = actions.onBack, enabled = !isSubmitting) {
                    Text(text = "Back", color = CheckoutTheme.colors.textSecondary)
                }
                Button(
                    onClick = actions.onSend,
                    enabled = !isSubmitting,
                    shape = CheckoutTheme.shapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CheckoutTheme.colors.brand,
                        contentColor = CheckoutTheme.colors.onBrand,
                    ),
                ) {
                    Text(text = "Send payout", style = CheckoutTheme.typography.subtitle)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(amount: String, iban: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = CheckoutTheme.colors.surface,
            contentColor = CheckoutTheme.colors.textPrimary,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SummaryRow(label = "Amount", value = amount, big = true)
            HorizontalDivider(color = CheckoutTheme.colors.outline)
            SummaryRow(label = "Destination IBAN", value = iban, big = false)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, big: Boolean) {
    val valueStyle = if (big) CheckoutTheme.typography.amount else CheckoutTheme.typography.subtitle
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = CheckoutTheme.typography.label, color = CheckoutTheme.colors.textSecondary)
        Text(text = value, style = valueStyle, color = CheckoutTheme.colors.textPrimary)
    }
}

// ─── Result screen  ─────────────────────────────────

@Composable
fun PayoutResultScreen(
    viewModel: PayoutViewModel,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val submission by viewModel.submission.collectAsStateWithLifecycle()
    ScreenSecurityEffect()
    PayoutResultContent(submission, ResultActions(onDone, onRetry, onDone), modifier)
}

// ─── Result Content ───────────────────────────────────────

// group the three callbacks so the composable signature stay short.
data class ResultActions(
    val onDone: () -> Unit,
    val onRetry: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
private fun PayoutResultContent(
    submission: SubmissionState,
    actions: ResultActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CheckoutTheme.colors.canvas),
    ) {
        PayoutSecurityBanner()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (submission is SubmissionState.Submitting) {
                CircularProgressIndicator(color = CheckoutTheme.colors.brand)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sending payout...",
                    style = CheckoutTheme.typography.body,
                    color = CheckoutTheme.colors.textPrimary,
                )
            }
            if (submission is SubmissionState.Success) {
                SuccessBlock(payout = submission.payout, onDone = actions.onDone)
            }
            if (submission is SubmissionState.Failure) {
                FailureBlock(error = submission.error, onRetry = actions.onRetry, onCancel = actions.onCancel)
            }
        }
    }
}

@Composable
private fun SuccessBlock(payout: Payout, onDone: () -> Unit) {

    val statusLabel = when (payout.status) {
        PayoutStatus.PROCESSING -> "Processing…"
        PayoutStatus.COMPLETED  -> "Completed"
        PayoutStatus.FAILED     -> "Failed"
        else                    -> payout.status.name
    }
    val badgeColor = when (payout.status) {
        PayoutStatus.COMPLETED -> CheckoutTheme.colors.success
        PayoutStatus.FAILED    -> CheckoutTheme.colors.danger
        else                   -> CheckoutTheme.colors.brand
    }
    val badgeGlyph = when (payout.status) {
        PayoutStatus.COMPLETED -> "✓"
        PayoutStatus.FAILED    -> "!"
        else                   -> "…"
    }



    StatusBadge(color = badgeColor, glyph = badgeGlyph)
    Spacer(modifier = Modifier.height(20.dp))

    Text(text = statusLabel, style = CheckoutTheme.typography.title, color = CheckoutTheme.colors.textPrimary)

    Spacer(modifier = Modifier.height(8.dp))

    if (payout.status != PayoutStatus.COMPLETED &&
        payout.status != PayoutStatus.FAILED) {
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(
            color = CheckoutTheme.colors.brand,
            modifier = Modifier.size(48.dp)      // ← add this
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = payout.amount.format() + " has been sent.",
        style = CheckoutTheme.typography.body,
        color = CheckoutTheme.colors.textSecondary,
    )

    if (payout.status == PayoutStatus.PROCESSING) {
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(color = CheckoutTheme.colors.brand)
    }

    Spacer(modifier = Modifier.height(28.dp))
    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = CheckoutTheme.colors.brand,
            contentColor = CheckoutTheme.colors.onBrand,
        ),
    ) {
        Text(text = "Back to home", style = CheckoutTheme.typography.subtitle)
    }
}

@Composable
private fun FailureBlock(error: DomainError, onRetry: () -> Unit, onCancel: () -> Unit) {
    StatusBadge(color = CheckoutTheme.colors.danger, glyph = "!")
    Spacer(modifier = Modifier.height(20.dp))
    Text(text = errorTitle(error), style = CheckoutTheme.typography.title, color = CheckoutTheme.colors.textPrimary)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = errorBody(error), style = CheckoutTheme.typography.body, color = CheckoutTheme.colors.textSecondary)
    Spacer(modifier = Modifier.height(28.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = CheckoutTheme.colors.brand,
            contentColor = CheckoutTheme.colors.onBrand,
        ),
    ) {
        Text(text = "Try again", style = CheckoutTheme.typography.subtitle)
    }
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onCancel) {
        Text(text = "Cancel", color = CheckoutTheme.colors.textSecondary)
    }
}

@Composable
private fun StatusBadge(color: Color, glyph: String) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = CheckoutTheme.typography.title, color = color)
    }
}

// ─── Send decision ────────────────────────────────────────────────────

// what the confirm screen should do after the user taps Send.
sealed class SendDecision {
    object Proceed : SendDecision()
    object Silent : SendDecision()
    object NeedsEnrolment : SendDecision()
    data class ShowMessage(val text: String) : SendDecision()
}

// considered adding an Idempotency-Key header before hitting the api but the mock server
// doesn't respect it and I didn't want to pretend it works. would add for a real backend
// so a retry on the same payout doesn't fire twice.
private suspend fun handleSendTap(
    form: PayoutFormState,
    gate: BiometricGate,
    runBiometric: suspend () -> BiometricResult,
): SendDecision {
    val parsed = AmountParser.parse(form.amountText)

    if (parsed !is AmountParse.Ok)
        return SendDecision.Silent

    val draft = PayoutDraft(
        amount = Money(parsed.minorUnits, form.currency),
        iban = form.ibanText,
        ref = form.ref,
    )

    if (!PayoutRules.requiresStepUp(draft))
        return SendDecision.Proceed


    val availability = gate.availability()
    if (availability is BiometricAvailability.Ready) {
        val result = runBiometric()
        return when (result) {
            is BiometricResult.Success -> SendDecision.Proceed
            is BiometricResult.UserCanceled -> SendDecision.Silent
            is BiometricResult.LockedOut ->
                SendDecision.ShowMessage("Too many attempts. Please try again later.")
            is BiometricResult.Error ->
                SendDecision.ShowMessage(
                    result.message.ifBlank { "Biometric authentication failed." }
                )
        }
    }


    if (availability is BiometricAvailability.NotEnrolled)
        return SendDecision.NeedsEnrolment

    val message = when (availability) {
        is BiometricAvailability.NoHardware -> "This device doesn't have a biometric sensor."
        is BiometricAvailability.TemporarilyUnavailable -> "The biometric sensor is unavailable right now."
        is BiometricAvailability.SecurityUpdateRequired -> "A system update is required before biometrics can be used."
        else -> "Biometrics can't be used right now."
    }
    return SendDecision.ShowMessage(message)
}

private fun errorTitle(error: DomainError): String {
    if (error is DomainError.Network) return "No connection"
    if (error is DomainError.ServiceUnavailable) return "Service unavailable"
    if (error is DomainError.InsufficientFunds) return "Insufficient funds"
    if (error is DomainError.Http) return "Payout failed"
    return "Something went wrong"
}

private fun errorBody(error: DomainError): String {
    if (error is DomainError.Network) return "Check your internet and try again."
    if (error is DomainError.ServiceUnavailable) return "The service is temporarily unavailable."
    if (error is DomainError.InsufficientFunds) return "Your available balance doesn't cover this payout."
    if (error is DomainError.Http) return "The server rejected the request (code " + error.statusCode + ")."
    return "An unexpected error occurred."
}
