package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class YesBankParserTest {

    private val parser = YesBankParser()

    @Test
    fun `debit UPI alert strips VPA label from merchant`() {
        val result = parser.parse(
            sms(
                "AD-YESBNK-S",
                "Rs.500.00 debited from A/c XX1234 on 05-08-26 to VPA rahul@ybl. " +
                    "UPI Ref 302345678901",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.amount).isEqualTo(Money.ofMajor(500))
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(txn.bankCode).isEqualTo("YESB")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        assertThat(txn.referenceNumber).isEqualTo("302345678901")
        // The generic pattern would otherwise leave "VPA rahul@ybl" as the merchant.
        assertThat(txn.rawMerchant).isEqualTo("rahul@ybl")
        assertThat(txn.confidence.value).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun `credit alert with bank signature gets a confidence bonus`() {
        val result = parser.parse(
            sms(
                "VM-YESBK-T",
                "YES BANK: INR 15,000.00 credited to your A/c XX5678 on 05-08-26. " +
                    "UPI Ref 302345678999",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.amount).isEqualTo(Money.ofMajor(15_000))
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.maskedAccount).isEqualTo("XX5678")
        assertThat(txn.referenceNumber).isEqualTo("302345678999")
        // Base 0.8 (amount + account, non-default type excluded) plus the 0.1 "YES BANK"
        // signature bonus, clamped — high enough to land in the CERTAIN band.
        assertThat(txn.confidence.band).isEqualTo(Confidence.Band.CERTAIN)
    }

    @Test
    fun `salary credit is typed as salary`() {
        val result = parser.parse(
            sms(
                "AD-YESBNK-S",
                "YES BANK: INR 85,000.00 credited to your A/c XX1234 towards SALARY " +
                    "on 05-08-26. Avl Bal INR 1,25,000.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.SALARY)
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(85_000))
        assertThat(txn.balanceAfter).isEqualTo(Money.ofMajor(1_25_000))
    }

    @Test
    fun `EMI debit is typed as EMI`() {
        val result = parser.parse(
            sms(
                "AD-YESBNK-S",
                "Rs.5,499.00 debited from A/c XX1234 towards EMI on 05-08-26. " +
                    "UPI Ref 302345671111 -YES BANK",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(5_499))
    }

    @Test
    fun `ATM withdrawal is typed and railed correctly`() {
        val result = parser.parse(
            sms(
                "AD-YESBNK-S",
                "Rs.10,000.00 withdrawn from A/c XX1234 at YES BANK ATM Koramangala " +
                    "on 05-08-26. Avl Bal Rs.40,000.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.ATM_WITHDRAWAL)
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(10_000))
        assertThat(txn.balanceAfter).isEqualTo(Money.ofMajor(40_000))
    }

    @Test
    fun `refund is typed as refund credit`() {
        val result = parser.parse(
            sms(
                "AD-YESBNK-S",
                "Rs.799.00 credited to your A/c XX1234 as refund for order #12345 " +
                    "on 05-08-26 -YES BANK",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(799))
    }

    @Test
    fun `promotional message is ignored, not parsed as a transaction`() {
        val result = parser.parse(
            sms(
                "VM-YESBK-T",
                "Yes Bank: Get 20% cashback on your next UPI transaction! Limited " +
                    "period offer, download the app now. T&C apply.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message is ignored, not parsed as a transaction`() {
        val result = parser.parse(
            sms(
                "VM-YESBK-T",
                "Do not share your OTP 482913 with anyone. Yes Bank will never call " +
                    "and ask for your OTP. Valid for 5 mins.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `message with no amount is not applicable`() {
        val result = parser.parse(
            sms("AD-YESBNK-S", "Yes Bank: Your account statement is ready for download."),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `malformed amount with no digits is not applicable`() {
        val result = parser.parse(
            sms("YESBNK", "YESBNK: Rs debited from your account"),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `canHandle matches both known Yes Bank sender codes`() {
        assertThat(parser.canHandle(sms("AD-YESBNK-S", "Rs.1 debited"))).isTrue()
        assertThat(parser.canHandle(sms("VM-YESBK-T", "Rs.1 debited"))).isTrue()
        assertThat(parser.canHandle(sms("AD-HDFCBK-S", "Rs.1 debited"))).isFalse()
    }
}
