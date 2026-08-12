package co.kp.merchantpayout.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale

// ─── Currency ───────────────────────────────────────────────────────────────

enum class Currency(val symbol: String) {
    GBP("£"),
    EUR("€"),
}

// ─── Money ──────────────────────────────────────────────────────────────────

// Store money as Long minor units (pence, paisa(indian currency)) + Currency. Never Double = fractions and BigDecimal = huge storage

data class Money(val minorUnits: Long, val currency: Currency) {

    val isNegative: Boolean get() = minorUnits < 0

// minor units = smallest unit of money (pence for GBP, cents for EUR, paisa for INR). we store as Long
// for exact math, Double would drift on decimal fractions (0.1 + 0.2 != 0.3).
// 100050L in GBP = £1,000.50.

    fun format(locale: Locale = Locale.getDefault()): String {
        val decimal = BigDecimal(minorUnits).movePointLeft(2).setScale(2, RoundingMode.HALF_EVEN)
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val sign = if (isNegative) "-" else ""
        return "$sign${currency.symbol}${nf.format(decimal.abs())}"
    }
}

// ─── Activity + Snapshot ────────────────────────────────────────────────────

enum class ActivityKind { PAYOUT, DEPOSIT, REFUND, FEE }
enum class ActivityStatus { COMPLETED, PENDING, PROCESSING, FAILED }

data class MerchantActivity(
    val id: String,
    val kind: ActivityKind,
    val amount: Money,
    val occurredAt: Instant,
    val description: String,
    val status: ActivityStatus,
)

data class MerchantSnapshot(
    val availableBalance: Money,
    val pendingBalance: Money,
    val recentActivity: List<MerchantActivity>,
)

// ─── Payout ─────────────────────────────────────────────────────────────────

enum class PayoutStatus { PENDING, PROCESSING, COMPLETED, FAILED }

data class Payout(
    val id: String,
    val status: PayoutStatus,
    val amount: Money,
    val iban: String,
    val ref: String,
    val createdAt: Instant,
)

// Draft form values the user is building. No id/status yet, it will comes back in Payout.
data class PayoutDraft(
    val amount: Money,
    val iban: String,
    val ref: String
)

// ─── Errors + Result wrapper ────────────────────────────────────────────────

// Sealed so the UI can do exhaustive `when` and pick a specific message per case.

sealed interface DomainError {
    data object Network : DomainError
    data object ServiceUnavailable : DomainError
    data object InsufficientFunds : DomainError
    data class Http(val statusCode: Int) : DomainError
    data class Unknown(val cause: Throwable) : DomainError
}

// Kotlin's built-in Result<T> throws away error type. Ours keeps DomainError typed so the
// UI's `when` on the error stays exhaustive.
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: DomainError) : Outcome<Nothing>
}

fun PayoutStatus.isTerminal(): Boolean =
    this == PayoutStatus.COMPLETED || this == PayoutStatus.FAILED