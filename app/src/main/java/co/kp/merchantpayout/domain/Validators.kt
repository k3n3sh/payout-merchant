package co.kp.merchantpayout.domain


import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

// ─── IBAN validation ────────────────────────────────────────────────────

enum class IbanIssue {
    EMPTY, TOO_SHORT, BAD_CHARACTER, UNKNOWN_COUNTRY, WRONG_LENGTH, CHECKSUM_FAILED
}

// carry the parsed info UI need — cleaned text, country info, and the current issue.
data class IbanBreakdown(
    val cleaned: String,
    val countryCode: String,
    val countryName: String?,
    val expectedLength: Int,
    val issue: IbanIssue?,
) {
    val isBlank: Boolean get() = cleaned.isBlank()
    val actualLength: Int get() = cleaned.length
}

object IbanValidator {

    // biggest iban length in swift.
    const val ABSOLUTE_MAX_LENGTH = 34

    fun expectedLengthFor(country: String): Int {
        val length: Int = COUNTRY_LENGTHS[country] ?: return 0
        return length
    }

    // return null if iban is valid, or the reason it fail.
    fun validate(input: String?): IbanIssue? {
        val cleaned = clean(input)
        if (cleaned.isEmpty())
            return IbanIssue.EMPTY

        if (cleaned.length < 5)
            return IbanIssue.TOO_SHORT

        for (c in cleaned)
            if (!c.isLetterOrDigit())
                return IbanIssue.BAD_CHARACTER


        val country = cleaned.substring(0, 2)
        val expected = COUNTRY_LENGTHS[country] ?: return IbanIssue.UNKNOWN_COUNTRY

        if (cleaned.length != expected)
            return IbanIssue.WRONG_LENGTH

        val checksum = mod97(cleaned)
        if (checksum != 1)
            return IbanIssue.CHECKSUM_FAILED

        return null
    }

    // give UI cleaned text + country + issue in one call so it can render live.
    fun analyze(input: String?): IbanBreakdown {
        val cleaned = clean(input)
        val country = cleaned.take(2)
        val expected = expectedLengthFor(country)
        val name: String? = if (expected > 0)
            COUNTRY_NAMES[country]
        else
            null

        val issue = validate(cleaned)
        return IbanBreakdown(
            cleaned = cleaned,
            countryCode = country,
            countryName = name,
            expectedLength = expected,
            issue = issue,
        )
    }

    // strip whitespace and uppercase everything. paste safe.
    private fun clean(input: String?): String {
        if (input == null)
            return ""

        val builder = StringBuilder()
        for (c in input)
            if (!c.isWhitespace())
                builder.append(c.uppercaseChar())

        return builder.toString()
    }

    // mod-97 check. rotate first 4 char to end, letter -> 2 digit (A=10..Z=35).
    // walk char by char keep remainder small. no BigInteger needed.
    private fun mod97(iban: String): Int {
        val rearranged = iban.substring(4) + iban.substring(0, 4)
        var remainder = 0
        for (c in rearranged) {

            val value: Int = if (c >= '0' && c <= '9')
                c - '0'
            else if (c >= 'A' && c <= 'Z')
                c - 'A' + 10
            else
                return -1

            remainder = if (value < 10)
                (remainder * 10 + value) % 97
            else
                (remainder * 100 + value) % 97

        }
        return remainder
    }

    // country -> total iban length. subset of swift registry.
    private val COUNTRY_LENGTHS = mapOf(
        "AD" to 24, "AE" to 23, "AT" to 20, "BE" to 16, "BG" to 22,
        "CH" to 21, "CY" to 28, "CZ" to 24,
        "DE" to 22, "DK" to 18,
        "EE" to 20, "ES" to 24,
        "FI" to 18, "FR" to 27,
        "GB" to 22, "GR" to 27,
        "HR" to 21, "HU" to 28,
        "IE" to 22, "IS" to 26, "IT" to 27,
        "LI" to 21, "LT" to 20, "LU" to 20, "LV" to 21,
        "MT" to 31, "NL" to 18, "NO" to 15,
        "PL" to 28, "PT" to 25,
        "RO" to 24, "SE" to 24, "SI" to 19, "SK" to 24,
    )

    // country -> friendly name for the preview.
    private val COUNTRY_NAMES = mapOf(
        "AT" to "Austria", "BE" to "Belgium", "CH" to "Switzerland", "CY" to "Cyprus",
        "CZ" to "Czechia", "DE" to "Germany", "DK" to "Denmark", "EE" to "Estonia",
        "ES" to "Spain", "FI" to "Finland", "FR" to "France", "GB" to "United Kingdom",
        "GR" to "Greece", "HR" to "Croatia", "HU" to "Hungary", "IE" to "Ireland",
        "IS" to "Iceland", "IT" to "Italy", "LI" to "Liechtenstein", "LT" to "Lithuania",
        "LU" to "Luxembourg", "LV" to "Latvia", "MT" to "Malta", "NL" to "Netherlands",
        "NO" to "Norway", "PL" to "Poland", "PT" to "Portugal", "RO" to "Romania",
        "SE" to "Sweden", "SI" to "Slovenia", "SK" to "Slovakia",
    )
}

// ─── Amount parsing ────────────────────────────────────────────────────

enum class AmountIssue {
    EMPTY, NOT_A_NUMBER, NOT_POSITIVE, TOO_MANY_DECIMALS, TOO_LARGE, OVER_BALANCE
}

sealed class AmountParse {
    data class Ok(val minorUnits: Long) : AmountParse()
    data class Err(val issue: AmountIssue) : AmountParse()
}

object AmountParser {

    // take user text like "1000.50" and turn it into 100050 minor units.
    // locale aware — en-GB use "1000.50", fr-FR use "1000,50".
    fun parse(input: String?, locale: Locale = Locale.getDefault()): AmountParse {
        if (input == null) {
            return AmountParse.Err(AmountIssue.EMPTY)
        }
        val trimmed = input.trim()
        if (trimmed.isEmpty())
            return AmountParse.Err(AmountIssue.EMPTY)


        val format = NumberFormat.getNumberInstance(locale)
        if (format !is DecimalFormat)
            return AmountParse.Err(AmountIssue.NOT_A_NUMBER)

        // this flag mean parse return BigDecimal, not Double.
        format.isParseBigDecimal = true

        val parsed: Number?
        try {
            parsed = format.parse(trimmed)
        } catch (t: Throwable) {
            return AmountParse.Err(AmountIssue.NOT_A_NUMBER)
        }
        if (parsed !is BigDecimal)
            return AmountParse.Err(AmountIssue.NOT_A_NUMBER)

        val decimal: BigDecimal = parsed

        if (decimal.signum() <= 0)
            return AmountParse.Err(AmountIssue.NOT_POSITIVE)

        // strip trailing zero so "1.50" and "1.5" both have scale 1.
        if (decimal.stripTrailingZeros().scale() > 2)
            return AmountParse.Err(AmountIssue.TOO_MANY_DECIMALS)


        val minor: Long
        try {
            minor = decimal.movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            return AmountParse.Err(AmountIssue.TOO_LARGE)
        }
        // wire is Int so cant be bigger than Int.MAX_VALUE.
        if (minor > Int.MAX_VALUE.toLong())
            return AmountParse.Err(AmountIssue.TOO_LARGE)

        return AmountParse.Ok(minor)
    }
}

// ─── Payout rules ──────────────────────────────────────────────────────

object PayoutRules {
    // brief say biometric required for payout of 1000 or more.
    // 1000.00 in minor units = 100_000.
    const val STEP_UP_THRESHOLD_MINOR: Long = 100_000L

    fun requiresStepUp(draft: PayoutDraft): Boolean {
        return draft.amount.minorUnits >= STEP_UP_THRESHOLD_MINOR
    }
}

// tried a small "did you mean" for iban typo but it kept suggesting wrong thing. drop for now.
// private fun suggestCorrection(bad: String): String? { return null }