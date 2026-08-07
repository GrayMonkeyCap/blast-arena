package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

/**
 * [GenericBankParser] cannot literally return [ParseResult.NotApplicable] from [GenericBankParser.parse]
 * once it has been entered — [com.lifeledger.sms.parser.BaseBankParser.parse] is `final` and
 * resolves only to `Success` or `Ignored` from that point on. The real refusal contract is
 * [GenericBankParser.canHandle]: a declined `canHandle` and a `NotApplicable` parse are the
 * same event to the pipeline (this parser did not claim the message), and it's the
 * documented, sanctioned way for a parser to say "not mine". These tests exercise both:
 * `canHandle` as the primary proof of refusal, and a direct `parse` call — which a real
 * engine would never make once `canHandle` says no — to show it degrades safely rather than
 * fabricating a transaction even if that gate were bypassed.
 */
class GenericBankParserTest {

    private val parser = GenericBankParser()

    @Test
    fun `well-formed debit with a masked account is accepted`() {
        val sms = sms(
            "AD-SIBLTD-S",
            "Rs.499.00 debited from A/c XX1234 on 05-08-26 towards Zomato. Ref " +
                "302345678901 -South Indian Bank",
        )
        assertThat(parser.canHandle(sms)).isTrue()

        val txn = parser.parse(sms, testContext).asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(499))
        assertThat(txn.bankCode).isEqualTo("UNKNOWN")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
    }

    @Test
    fun `well-formed debit with only a UPI id is accepted`() {
        val sms = sms(
            "AD-SIBLTD-S",
            "INR 250.00 paid to merchant@upi via UPI on 05-08-26. Txn ID TXNAB12345678",
        )
        assertThat(parser.canHandle(sms)).isTrue()

        val txn = parser.parse(sms, testContext).asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.upiId).isEqualTo("merchant@upi")
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(txn.referenceNumber).isEqualTo("TXNAB12345678")
    }

    @Test
    fun `well-formed credit with only a UTR reference is accepted`() {
        val sms = sms(
            "AD-SIBLTD-S",
            "Rs.5,000.00 credited to your account via NEFT on 05-08-26. UTR NEFTAB123456789",
        )
        assertThat(parser.canHandle(sms)).isTrue()

        val txn = parser.parse(sms, testContext).asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.NEFT)
        assertThat(txn.referenceNumber).isEqualTo("NEFTAB123456789")
    }

    @Test
    fun `well-formed EMI debit is typed correctly`() {
        val sms = sms(
            "AD-SIBLTD-S",
            "Rs.3,500.00 debited from A/c XX9988 towards EMI on 05-08-26. Ref " +
                "302345670001 -Jana Small Finance Bank",
        )
        assertThat(parser.canHandle(sms)).isTrue()

        val txn = parser.parse(sms, testContext).asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(3_500))
    }

    @Test
    fun `message with no amount at all is not applicable`() {
        val sms = sms("AD-SIBLTD-S", "Thank you for being a valued customer. Have a wonderful day!")
        assertThat(parser.canHandle(sms)).isFalse()
        // This is the one shape where the base class's own early-exit fires before refine
        // is ever reached, so a literal ParseResult.NotApplicable is actually observable.
        assertThat(parser.parse(sms, testContext)).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `ambiguous debit with no corroborating identifier is refused`() {
        val sms = sms(
            "AD-SIBLTD-S",
            "Rs.500.00 debited today for your purchase. Thank you for banking with us.",
        )
        assertThat(parser.canHandle(sms)).isFalse()

        val result = parser.parse(sms, testContext)
        // Never a false Success: this is the one property that actually matters here.
        assertThat(result).isNotInstanceOf(ParseResult.Success::class.java)
        assertThat(result).isEqualTo(
            ParseResult.Ignored(parser.info.id, "rejected by ${parser.info.id} refinement"),
        )
    }

    @Test
    fun `ambiguous credit with no corroborating identifier is refused`() {
        val sms = sms("AD-SIBLTD-S", "INR 1,200 credited towards cashback bonus. Enjoy!")
        assertThat(parser.canHandle(sms)).isFalse()

        val result = parser.parse(sms, testContext)
        assertThat(result).isNotInstanceOf(ParseResult.Success::class.java)
    }

    @Test
    fun `promotional pitch with no real verb is refused, and ignored if parsed anyway`() {
        val sms = sms(
            "VM-SIBLTD-T",
            "Get instant loan up to Rs.5,00,000 at 10.5% interest! Apply now, " +
                "limited period offer. Click here to know more.",
        )
        assertThat(parser.canHandle(sms)).isFalse()
        assertThat(parser.parse(sms, testContext)).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message is refused, and ignored if parsed anyway`() {
        val sms = sms(
            "VM-SIBLTD-T",
            "295817 is your OTP for transaction of Rs.500. Do not share this OTP with anyone.",
        )
        assertThat(parser.canHandle(sms)).isFalse()
        assertThat(parser.parse(sms, testContext)).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `canHandle requires amount, verb and an identifier together`() {
        // Amount and account, but no debit/credit verb: a balance enquiry, not a transaction.
        assertThat(
            parser.canHandle(
                sms("AD-SIBLTD-S", "Available balance in A/c XX1234 is Rs.5,000.00 as on 05-08-26."),
            ),
        ).isFalse()

        // Amount and verb, but nothing to corroborate it against.
        assertThat(
            parser.canHandle(sms("AD-SIBLTD-S", "Rs.500.00 debited today for your purchase.")),
        ).isFalse()

        // Nothing at all.
        assertThat(parser.canHandle(sms("AD-SIBLTD-S", "Thank you for choosing us."))).isFalse()

        // Amount, verb and a masked account: the real thing.
        assertThat(
            parser.canHandle(sms("AD-SIBLTD-S", "Rs.499.00 debited from A/c XX1234 towards Zomato.")),
        ).isTrue()
    }
}
