package co.kp.merchantpayout

import co.kp.merchantpayout.domain.AmountIssue
import co.kp.merchantpayout.domain.AmountParse
import co.kp.merchantpayout.domain.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AmountParserTest {

    @Test
    fun enGBParses1000_50As100050Minor() {
        val result = AmountParser.parse("1000.50", Locale.UK)
        val expected = AmountParse.Ok(100_050L)
        assertEquals(expected, result)
    }

    @Test
    fun amountAboveIntLimitYieldsTooLarge() {
        // 999,999,999.99 in minor units is 99_999_999_999 which is bigger than Int.MAX_VALUE.
        val result = AmountParser.parse("999999999.99", Locale.UK)
        val expected = AmountParse.Err(AmountIssue.TOO_LARGE)
        assertEquals(expected, result)
    }
}