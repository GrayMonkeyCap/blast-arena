package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class FederalBankParserTest {

    private val parser = FederalBankParser()

    @Test
    fun `debit alert names the merchant via 'towards'`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.799.00 debited from your A/c XX1234 towards Swiggy on 05-08-26. " +
                    "Ref 302345678901 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(799))
        assertThat(txn.bankCode).isEqualTo("FDRL")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        assertThat(txn.referenceNumber).isEqualTo("302345678901")
        assertThat(txn.rawMerchant).isEqualTo("Swiggy")
    }

    @Test
    fun `credit alert`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.25,000.00 credited to your A/c XX5678 on 05-08-26. Ref " +
                    "302345679999 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(25_000))
        assertThat(txn.referenceNumber).isEqualTo("302345679999")
    }

    @Test
    fun `salary credit`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Salary Rs.72,000.00 credited to your A/c XX1234 on 05-08-26. Ref " +
                    "302345670000 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.SALARY)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(72_000))
    }

    @Test
    fun `EMI debit for a loan account keeps the user's own account masked, not the loan's`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.4,500.00 debited from your A/c XX1234 towards EMI for Loan A/c " +
                    "XX7788 on 05-08-26. Ref 302345671234 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        // The generic patterns cannot cross the "/" in "Loan A/c" and would leave this
        // null; the Federal-specific fix allows it through.
        assertThat(txn.rawMerchant).isEqualTo("EMI for Loan A/c XX7788")
    }

    @Test
    fun `ATM withdrawal`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.4,000.00 withdrawn from A/c XX1234 at Federal Bank ATM on " +
                    "05-08-26. Ref 302345678888 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.ATM_WITHDRAWAL)
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.ATM)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(4_000))
    }

    @Test
    fun `refund credit`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.499.00 credited to your A/c XX1234 towards refund on 05-08-26. " +
                    "Ref 302345677777 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(499))
    }

    @Test
    fun `bill payment debit sets a bill type`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Rs.1,500.00 debited from your A/c XX1234 towards Electricity Bill " +
                    "Payment on 05-08-26. Ref 302345676666 - Federal Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.BILL_PAYMENT)
        assertThat(txn.billType).isEqualTo(BillType.ELECTRICITY)
        assertThat(txn.rawMerchant).isEqualTo("Electricity Bill Payment")
    }

    @Test
    fun `promotional message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-FEDERAL-T",
                "Federal Bank: Enjoy zero processing fee on personal loans this " +
                    "month! Apply now, limited period offer.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-FEDERAL-T",
                "482910 is your OTP to login to Federal Bank FedMobile. Do not " +
                    "share your OTP with anyone.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `message with no amount is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Federal Bank: Your debit card has been dispatched and will arrive " +
                    "in 5-7 business days.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `balance statement with an amount but no verb is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-FEDBNK-S",
                "Federal Bank: Total available balance in A/c XX1234 is Rs.20,000.00 " +
                    "as on 05-08-26.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
