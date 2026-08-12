package co.kp.merchantpayout

import co.kp.merchantpayout.domain.IbanIssue
import co.kp.merchantpayout.domain.IbanValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IbanValidatorTest {

    @Test
    fun validGBIbanFromBriefPasses() {
        val issue = IbanValidator.validate("GB29NWBK60161331926819")
        assertNull(issue)
    }

    @Test
    fun invalidFRIbanFromBriefFailsWithWrongLength() {
        // brief supply this as the invalid example. length is 40, FR expects 27.
        val issue = IbanValidator.validate("FR1212345123451234567A12310131231231231")
        assertEquals(IbanIssue.WRONG_LENGTH, issue)
    }

    @Test
    fun wrongChecksumFailsWithChecksumIssue() {
        // valid country + length but check digits wrong. proves we run mod-97, not just regex.
        val issue = IbanValidator.validate("GB00NWBK60161331926819")
        assertEquals(IbanIssue.CHECKSUM_FAILED, issue)
    }
}