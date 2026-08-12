package co.kp.merchantpayout.mock

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal object MockData {

    // *Mock change: this was a fixed number before (const val) that never change. now it's a
    // variable so we can subtract from it when payout happen. synchronized just make sure two
    // thread dont read/write at the same time. TOGGLE 2 below is what actually change the number.

    private var _availableBalance = 5000_0000
    private var _pendingBalance = 25_000

    val availableBalance: Int get() = synchronized(this) { _availableBalance }
    val pendingBalance: Int get() = synchronized(this) { _pendingBalance }

    val deviceId: String = "device_${UUID.randomUUID()}"

    data class ActivityRecord(
        val id: String, val type: String, val amount: Int,
        val date: String, val description: String, val status: String,
    )


    private val _activities: MutableList<ActivityRecord> = mutableListOf<ActivityRecord>().apply {
        val now = Instant.now()

        val seed = listOf(
            Triple("payout", -12500, "Invoice payment"),
            Triple("deposit", 75000, "Payment received"),
            Triple("fee", -1500, "Monthly service fee"),
            Triple("payout", -50000, "Supplier transfer"),
            Triple("deposit", 120000, "Client payment"),
            Triple("refund", 10000, "Refund from merchant"),
            Triple("payout", -8750, "Card transaction"),
            Triple("deposit", 35000, "Direct debit receipt"),
            Triple("fee", -2000, "Transaction fee"),
            Triple("payout", -22500, "Standing order"),
            Triple("deposit", 60000, "Online purchase refund"),
            Triple("payout", -5000, "Service charge"),
            Triple("deposit", 90000, "Wire transfer received"),
            Triple("fee", -3500, "Annual fee"),
            Triple("payout", -18000, "Contractor payment"),
            Triple("deposit", 45000, "Invoice settlement"),
            Triple("refund", 15000, "Overcharge refund"),
            Triple("payout", -30000, "Equipment purchase"),
            Triple("deposit", 80000, "Partnership payment"),
            Triple("fee", -1000, "Statement fee"),
            Triple("payout", -42000, "Vendor payment"),
            Triple("deposit", 110000, "Project milestone"),
            Triple("refund", 25000, "Product return"),
            Triple("payout", -16500, "Utility payment"),
            Triple("deposit", 55000, "Retainer payment"),
            Triple("payout", -70000, "Software licence"),
            Triple("deposit", 95000, "Consultancy fee"),
            Triple("payout", -9000, "Office supplies"),
            Triple("deposit", 40000, "Royalty payment"),
            Triple("payout", -34500, "Freelancer invoice"),

            // *Mock change: Seeding extra seed rows for larger pagination demo

            Triple("deposit", 72000, "Marketplace payout"),
            Triple("payout", -14000, "Subscription renewal"),
            Triple("deposit", 85000, "Recurring client"),
            Triple("payout", -26000, "Legal fees"),
            Triple("deposit", 130000, "Series A tranche"),
            Triple("payout", -11500, "Cloud hosting"),
            Triple("deposit", 50000, "Grant disbursement"),
            Triple("payout", -6500, "Domain renewal"),
            Triple("deposit", 67000, "Affiliate commission"),
            Triple("payout", -21000, "Insurance premium"),
            Triple("deposit", 92000, "Enterprise licence"),
            Triple("refund", 12000, "Cancelled service"),
            Triple("payout", -38000, "Office rent"),
            Triple("deposit", 47000, "Referral bonus"),
            Triple("payout", -55000, "Marketing campaign"),
            Triple("deposit", 105000, "Contract signing bonus"),
            Triple("payout", -13500, "Payroll top-up"),
            Triple("deposit", 62000, "Sponsorship deal"),
            Triple("payout", -78000, "Hardware refresh"),
            Triple("deposit", 99000, "Advisory retainer"),
        )
        seed.forEachIndexed { i, (type, amt, desc) ->
            add(
                ActivityRecord(
                    id = "act_${(i + 1).toString().padStart(3, '0')}",
                    type = type,
                    amount = amt,
                    date = now.minus(i / 3L, ChronoUnit.DAYS).toString(),
                    description = desc,
                    status = if (i / 3L > 1) "completed" else "processing",
                )
            )
        }
    }

    val activities: List<ActivityRecord>
        get() = synchronized(_activities) { _activities.toList() }

    data class Page(val items: List<ActivityRecord>, val nextCursor: String?, val hasMore: Boolean)

    fun getPage(cursor: String?, limit: Int): Page {
        val snap = activities
        val start = if (cursor == null) 0 else snap.indexOfFirst { it.id == cursor }.let {
            if (it < 0) 0 else it + 1
        }
        val page = snap.subList(start, minOf(start + limit, snap.size))
        val hasMore = start + limit < snap.size
        return Page(page, if (hasMore) page.last().id else null, hasMore)
    }

    data class PayoutRecord(
        val id: String, val amount: Int, val currency: String,
        val iban: String, val ref: String, val createdAt: Long, var status: String = "pending"
    )

    private val payoutCounter = AtomicInteger(1)
    private val payouts = mutableMapOf<String, PayoutRecord>()

    fun createPayout(amount: Int, currency: String, iban: String, ref: String): PayoutRecord {
        val id = "pay_${payoutCounter.getAndIncrement().toString().padStart(3, '0')}"
        val record = PayoutRecord(id, amount, currency, iban, ref, System.currentTimeMillis())
        synchronized(payouts) { payouts[id] = record }

         val masked = if (iban.length > 8) "${iban.take(4)}…${iban.takeLast(4)}" else iban
         synchronized(_activities) {
             _activities.add(
                 0,
                 ActivityRecord(
                     id = "act_pay_${id.removePrefix("pay_")}",
                     type = "payout",
                     amount = -amount,
                     date = Instant.now().toString(),
                     description = ref,
                     status = "processing",
                 )
             )
         }

         synchronized(this) {
             _availableBalance -= amount
             _pendingBalance += amount
         }

        return record
    }

    fun getPayoutById(id: String): PayoutRecord? {
        val record = synchronized(payouts) { payouts[id] } ?: return null
        val elapsedSeconds = (System.currentTimeMillis() - record.createdAt) / 1_000
        val newStatus: String = when {
            elapsedSeconds < 3 -> "pending"
            elapsedSeconds < 8 -> "processing"
            else -> "completed"
        }
        return record.copy(status = newStatus)
    }
}