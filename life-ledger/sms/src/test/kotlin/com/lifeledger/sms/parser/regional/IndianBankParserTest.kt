package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class IndianBankParserTest {

    private val parser = IndianBankParser()

    @Test
    fun `plain debit alert`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.500.00 debited from your a/c XX1234 on 05-08-2026. Bal " +
                    "Rs.15,000.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(500))
        assertThat(txn.balanceAfter).isEqualTo(Money.ofMajor(15_000))
        assertThat(txn.bankCode).isEqualTo("IDIB")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        // "-Indian Bank" appears in the body, so the signature bonus applies.
        assertThat(txn.confidence.band).isAnyOf(Confidence.Band.HIGH, Confidence.Band.CERTAIN)
    }

    @Test
    fun `NEFT credit alert`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.25,000.00 credited to your a/c XX5678 on 05-08-2026 by NEFT. Bal " +
                    "Rs.40,000.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.NEFT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(25_000))
        assertThat(txn.maskedAccount).isEqualTo("XX5678")
    }

    @Test
    fun `salary credit`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Salary of Rs.68,000.00 credited to your a/c XX1234 on 05-08-2026. " +
                    "Bal Rs.90,000.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.SALARY)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(68_000))
    }

    @Test
    fun `EMI debit`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.7,200.00 debited from your a/c XX1234 towards EMI on 05-08-2026. " +
                    "Bal Rs.32,800.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(7_200))
    }

    @Test
    fun `ATM withdrawal`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.2,000.00 withdrawn from your a/c XX1234 at Indian Bank ATM on " +
                    "05-08-2026. Bal Rs.30,800.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.ATM_WITHDRAWAL)
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.ATM)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(2_000))
    }

    @Test
    fun `refund credit`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.999.00 credited to your a/c XX1234 as refund on 05-08-2026. Bal " +
                    "Rs.31,799.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(999))
    }

    @Test
    fun `IMPS debit extracts reference number from the trailing Ref No field`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Rs.10,000.00 debited from your a/c XX1234 IMPS Ref No 302345678901 " +
                    "on 05-08-2026. Bal Rs.21,799.00 -Indian Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.IMPS)
        assertThat(txn.referenceNumber).isEqualTo("302345678901")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
    }

    @Test
    fun `allahabad bank legacy sender is routed here`() {
        assertThat(parser.canHandle(sms("AD-ALLBNK-S", "Rs.1 debited"))).isTrue()
    }

    @Test
    fun `promotional message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-INDIAN-T",
                "Indian Bank: Get pre-approved personal loan up to Rs.5 lakh at " +
                    "attractive rates. Apply now! T&C apply.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-INDIAN-T",
                "738291 is your OTP for Indian Bank net banking. Do not share this " +
                    "OTP with anyone. Valid 10 mins.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `message with no amount is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Indian Bank: Your account statement for July 2026 is now available " +
                    "for download.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `balance enquiry with an amount but no verb is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-INDBNK-S",
                "Indian Bank: Your a/c XX1234 balance is Rs.15,000.00 as of 05-08-2026.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
