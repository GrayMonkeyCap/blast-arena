package com.lifeledger.sms.parser.life

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class OtpParserTest {

    private val parser = OtpParser()

    @Test
    fun `OTP for a named service extracts the service as rawMerchant`() {
        val result = parser.parse(
            sms("AMAZON", "445566 is your OTP for Amazon login. Valid for 10 minutes. Do not share this OTP with anyone."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("Amazon")
    }

    @Test
    fun `OTP with a longer service phrase still resolves a non-null textual merchant`() {
        val result = parser.parse(
            sms("SBIYONO", "Your OTP for SBI YONO transaction is 778899. Valid till 10:45 AM. Do not share with anyone."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isNotNull()
        assertThat(success.transaction.rawMerchant).doesNotContain("778899")
    }

    @Test
    fun `trailing sender signature is used as a fallback service name`() {
        val result = parser.parse(
            sms("VD-HDFCBK", "778899 is your OTP. Do not share this with anyone. -HDFC Bank"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("HDFC Bank")
    }

    @Test
    fun `trailing brand signature with verification code wording is extracted`() {
        val result = parser.parse(
            sms("VM-FLPKRT", "Your verification code is 5566. It expires in 2 minutes. -Flipkart"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("Flipkart")
    }

    @Test
    fun `OTP for a specific action still resolves a textual service phrase`() {
        val result = parser.parse(
            sms("VM-ICICIB", "Your OTP for adding beneficiary is 998877. Valid for 10 mins. -ICICI Bank"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isNotNull()
        assertThat(success.transaction.rawMerchant).doesNotContain("998877")
    }

    @Test
    fun `when no service text is extractable the sender code is used as a fallback`() {
        val result = parser.parse(
            sms("VM-XYZBNK", "Use 334455 to verify your identity. This OTP is valid for 5 mins."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("XYZBNK")
    }

    @Test
    fun `OTP combined with a real debit is not claimed as OTP-only`() {
        val result = parser.parse(
            sms("HDFCBK", "OTP for txn of Rs.500 debited from A/c XX1234 is 223344. Do not share."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `promotional message that mentions OTP is ignored not treated as a code delivery`() {
        val result = parser.parse(
            sms("VM-PROMO", "Get your OTP faster! Download the app now. Click here to know more. T&C apply."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `unrelated bank message without an OTP marker is not applicable`() {
        val result = parser.parse(
            sms("HDFCBK", "Rs.500.00 debited from A/c XX1234 to Merchant XYZ on 05-Aug-26."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `type direction payment method and confidence are consistent for an OTP event`() {
        val result = parser.parse(
            sms("AMAZON", "112233 is your OTP for Amazon login. Do not share."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.OTP)
        assertThat(success.transaction.amount).isNull()
        assertThat(success.transaction.direction).isEqualTo(Direction.NEUTRAL)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UNKNOWN)
        assertThat(success.transaction.confidence).isEqualTo(Confidence.HIGH)
    }

    /**
     * The critical redaction guarantee: no matter how many times the code appears in the
     * body — after "OTP is", after "OTP", after "Ref:" — it must never surface in any field
     * of the produced [com.lifeledger.core.model.ParsedTransaction]. extractedFields carries
     * only the boolean `code_present` fact, and every other string-bearing field is either
     * null or built from non-digit text.
     */
    @Test
    fun `the OTP digits never appear in any field of the parsed transaction`() {
        val code = "913579"
        val result = parser.parse(
            sms(
                "AD-PAYZAP",
                "Your OTP is $code. Do not share OTP $code with anyone for security. Ref: $code -PayZapp",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        val txn = success.transaction

        assertThat(txn.rawMerchant.orEmpty()).doesNotContain(code)
        assertThat(txn.description).isNull()
        assertThat(txn.referenceNumber).isNull()
        assertThat(txn.transactionId).isNull()
        assertThat(txn.upiId).isNull()
        assertThat(txn.maskedAccount).isNull()
        assertThat(txn.bankCode).isNull()
        assertThat(txn.extractedFields).isEqualTo(mapOf("code_present" to "true"))
        assertThat(txn.extractedFields.values.none { it.contains(code) }).isTrue()
    }
}
