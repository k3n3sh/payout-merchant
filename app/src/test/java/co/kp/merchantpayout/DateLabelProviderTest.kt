package co.kp.merchantpayout

import co.kp.merchantpayout.ui.transactions.DateLabelProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DateLabelProviderTest {

    @Test
    fun payoutFromLastNightIsYesterday_inWesternZone() {
        // A payout committed at 22:00 UTC on 15 March 2026.
        val payoutInstant = Instant.parse("2026-03-15T22:00:00Z")

        // Now, from Los Angeles's perspective, it's mid-afternoon on the 16th.
        val la = ZoneId.of("America/Los_Angeles")
        val nowInLA = LocalDateTime.of(2026, 3, 16, 15, 0).atZone(la).toInstant()

        val labels = DateLabelProvider(Clock.fixed(nowInLA, la), la)

        // 22:00 UTC on the 15th is 15:00 local on the 15th in LA
        // → from "now = 16 Mar" the payout is "yesterday".
        assertEquals("Yesterday", labels.headerFor(payoutInstant))
    }
}
