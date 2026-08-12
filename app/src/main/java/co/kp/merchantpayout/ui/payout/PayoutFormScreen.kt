package co.kp.merchantpayout.ui.payout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.kp.merchantpayout.domain.AmountIssue
import co.kp.merchantpayout.domain.Currency
import co.kp.merchantpayout.domain.IbanBreakdown
import co.kp.merchantpayout.domain.IbanIssue
import co.kp.merchantpayout.domain.IbanValidator
import co.kp.merchantpayout.security.PayoutSecurityBanner
import co.kp.merchantpayout.security.ScreenSecurityEffect
import co.kp.merchantpayout.ui.theme.CheckoutTheme

@Composable
fun PayoutFormScreen(
    viewModel: PayoutViewModel,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val breakdown by viewModel.ibanBreakdown.collectAsStateWithLifecycle()

    ScreenSecurityEffect()

    PayoutFormContent(
        form = form,
        breakdown = breakdown,
        onCancel = onCancel,
        onConfirm = onConfirm,
        onAmountChange = { input -> viewModel.onAmountChange(sanitizeAmountInput(input)) },
        onAmountBlur = {
            val padded = normalizeAmountOnBlur(form.amountText)
            if (padded != form.amountText)
                viewModel.onAmountChange(padded)

            viewModel.validateAmountOnBlur()
        },
        onCurrencyChange = { c -> viewModel.onCurrencyChange(c) },
        onIbanChange = { input ->
            val cleaned = StringBuilder()
            for (c in input)
                if (!c.isWhitespace())
                    cleaned.append(c.uppercaseChar())

            val cleanedText = cleaned.toString()
            val country = cleanedText.take(2)
            val expected = IbanValidator.expectedLengthFor(country)
            val cap: Int = if (expected > 0)
                expected
            else
                IbanValidator.ABSOLUTE_MAX_LENGTH

            viewModel.onIbanChange(cleanedText.take(cap))
        },
        onRefChange = { viewModel.onRefChange(it) },
        modifier = modifier,
    )
}

@Composable
private fun PayoutFormContent(
    form: PayoutFormState,
    breakdown: IbanBreakdown,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onAmountChange: (String) -> Unit,
    onAmountBlur: () -> Unit,
    onCurrencyChange: (Currency) -> Unit,
    onIbanChange: (String) -> Unit,
    onRefChange: (String) -> Unit,
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
                text = "Send a payout",
                style = CheckoutTheme.typography.title,
                color = CheckoutTheme.colors.textPrimary,
            )
            Text(
                text = "Enter the amount, choose a currency, and paste the destination IBAN.",
                style = CheckoutTheme.typography.body,
                color = CheckoutTheme.colors.textSecondary,
            )

            AmountField(
                text = form.amountText,
                issue = form.amountIssue,
                onChange = onAmountChange,
                onBlur = onAmountBlur,
            )

            CurrencyPicker(selected = form.currency, onSelected = onCurrencyChange)

            IbanField(text = form.ibanText, breakdown = breakdown, onChange = onIbanChange)

            RefField(text = form.ref, onChange = onRefChange)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(text = "Cancel", color = CheckoutTheme.colors.textSecondary)
                }
                Button(
                    onClick = onConfirm,
                    enabled = form.canConfirm,
                    shape = CheckoutTheme.shapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CheckoutTheme.colors.brand,
                        contentColor = CheckoutTheme.colors.onBrand,
                        disabledContainerColor = CheckoutTheme.colors.outline,
                        disabledContentColor = CheckoutTheme.colors.textSecondary,
                    ),
                ) {
                    Text(text = "Confirm", style = CheckoutTheme.typography.subtitle)
                }
            }
        }
    }
}

// ─── Sub composables ──────────────────────────────────────────────────

@Composable
private fun AmountField(
    text: String,
    issue: AmountIssue?,
    onChange: (String) -> Unit,
    onBlur: () -> Unit,
) {
    val supporting: @Composable (() -> Unit)?
    if (issue != null)
        supporting = { Text(text = amountIssueText(issue)) }
    else
        supporting = null

    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(text = "Amount") },
        placeholder = { Text(text = "0.00", color = CheckoutTheme.colors.textSecondary) },
        singleLine = true,
        isError = issue != null,
        supportingText = supporting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (!focus.isFocused && text.isNotBlank()) {
                    onBlur()
                }
            },
        shape = CheckoutTheme.shapes.input,
    )
}

@Composable
private fun CurrencyPicker(selected: Currency, onSelected: (Currency) -> Unit) {
    Text(
        text = "Currency",
        style = CheckoutTheme.typography.label,
        color = CheckoutTheme.colors.textSecondary,
    )
    val currencies = Currency.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        for (i in currencies.indices) {
            val currency = currencies[i]
            SegmentedButton(
                selected = selected == currency,
                onClick = { onSelected(currency) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = currencies.size),
            ) {
                Text(text = currency.name + " (" + currency.symbol + ")")
            }
        }
    }
}

@Composable
private fun IbanField(
    text: String,
    breakdown: IbanBreakdown,
    onChange: (String) -> Unit,
) {
    val issue = breakdown.issue
    val isError: Boolean = if (issue == null)
        false
    else if (issue == IbanIssue.EMPTY || issue == IbanIssue.TOO_SHORT)
        false
    else if (issue == IbanIssue.WRONG_LENGTH && breakdown.actualLength < breakdown.expectedLength)
        false
    else
        true

    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(text = "Destination IBAN") },
        placeholder = {
            Text(text = "GB29 NWBK 6016 1331 9268 19", color = CheckoutTheme.colors.textSecondary)
        },
        singleLine = true,
        isError = isError,
        visualTransformation = IbanVisualTransformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        supportingText = { IbanSupportingText(breakdown) },
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.input,
    )
}


@Composable
private fun RefField(
    text: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(text = "Payout Ref") },
        placeholder = {
            Text(text = "Enter ref e.g. Invoice XYZ", color = CheckoutTheme.colors.textSecondary)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = CheckoutTheme.shapes.input,
    )
}

@Composable
private fun IbanSupportingText(breakdown: IbanBreakdown) {
    val issue = breakdown.issue
    val text: String
    val color: Color
    if (breakdown.isBlank) {
        text = "Paste or type the destination IBAN."
        color = CheckoutTheme.colors.textSecondary
    } else if (issue == null) {
        val country: String = if (breakdown.countryName != null)
            breakdown.countryName
        else
            breakdown.countryCode

        text = "$country · IBAN valid"
        color = CheckoutTheme.colors.success
    } else if (issue == IbanIssue.TOO_SHORT ||
        (issue == IbanIssue.WRONG_LENGTH && breakdown.actualLength < breakdown.expectedLength)
    ) {
        val country: String
        if (breakdown.countryName != null)
            country = breakdown.countryName
        else if (breakdown.countryCode.isNotEmpty())
            country = breakdown.countryCode
        else
            country = "IBAN"

        text = country + " · " + breakdown.actualLength + " of " + breakdown.expectedLength + " characters"
        color = CheckoutTheme.colors.textSecondary
    } else {
        text = ibanIssueText(issue)
        color = CheckoutTheme.colors.danger
    }
    Text(text = text, color = color)
}

// ─── Visual transformation for IBAN (groups of 4) ─────────────────────

object IbanVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = StringBuilder()
        for (i in raw.indices) {
            builder.append(raw[i])
            val addSpace = (i + 1) % 4 == 0 && i != raw.length - 1
            if (addSpace)
                builder.append(' ')

        }
        val formatted = builder.toString()

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val maxSpaces: Int = if (raw.isEmpty())
                    0
                else
                    (raw.length - 1) / 4

                val spaces = offset / 4
                val actual: Int = if (spaces > maxSpaces)
                    maxSpaces
                else
                    spaces

                return offset + actual
            }

            override fun transformedToOriginal(offset: Int): Int {
                val spaces = offset / 5
                val result = offset - spaces
                if (result < 0)
                    return 0

                return result
            }
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

// ─── Amount helpers ───────────────────────────────────────────────────

fun sanitizeAmountInput(raw: String): String {
    val builder = StringBuilder()
    for (c in raw)
        if (c.isDigit() || c == '.')
            builder.append(c)

    val digitsAndDot = builder.toString()
    val firstDot = digitsAndDot.indexOf('.')
    if (firstDot == -1)
        return digitsAndDot

    val intPart = digitsAndDot.substring(0, firstDot)
    val fractionRaw = digitsAndDot.substring(firstDot + 1)
    val fraction = StringBuilder()
    for (c in fractionRaw) {
        if (c.isDigit())
            fraction.append(c)
        if (fraction.length == 2)
            break
    }
    return "$intPart.$fraction"
}

fun normalizeAmountOnBlur(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty())
        return trimmed

    val dot = trimmed.indexOf('.')
    if (dot == -1)
        return "$trimmed.00"

    if (dot == trimmed.length - 1)
        return trimmed + "00"

    if (trimmed.length - dot == 2)
        return trimmed + "0"

    return trimmed
}

// ─── Issue text ────────────────────────────────────────────────────

private fun amountIssueText(issue: AmountIssue): String {
    if (issue == AmountIssue.EMPTY) return "Enter an amount"
    if (issue == AmountIssue.NOT_A_NUMBER) return "Enter a valid number"
    if (issue == AmountIssue.NOT_POSITIVE) return "Amount must be greater than zero"
    if (issue == AmountIssue.TOO_MANY_DECIMALS) return "Only two decimal places are allowed"
    if (issue == AmountIssue.OVER_BALANCE) return "Amount is more than your available balance"
    return "Amount is too large"
}

private fun ibanIssueText(issue: IbanIssue): String {
    if (issue == IbanIssue.EMPTY) return "IBAN is required"
    if (issue == IbanIssue.TOO_SHORT) return "Keep typing to complete this IBAN"
    if (issue == IbanIssue.BAD_CHARACTER) return "Only letters and numbers are allowed"
    if (issue == IbanIssue.UNKNOWN_COUNTRY) return "Unrecognised country code"
    if (issue == IbanIssue.WRONG_LENGTH) return "Length doesn't match the country's IBAN format"
    return "Check digits don't match — please verify the IBAN"
}
