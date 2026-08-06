package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class CanaraBankParserTest {

    private val parser = CanaraBankParser()

    @Test
    fun `debit alert recovers merchant hidden before the parenthesised UPI ref`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Your A/c XX1234 is debited by Rs.500.00 on 05-08-26 and credited to " +
                    "Amazon Pay (UPI Ref no 302345678901)-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(500))
        assertThat(txn.bankCode).isEqualTo("CNRB")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        assertThat(txn.referenceNumber).isEqualTo("302345678901")
        // Without the bank-specific fix this would be null: the generic patterns never
        // consume "(" so they cannot cross into the UPI-ref parenthesis at all.
        assertThat(txn.rawMerchant).isEqualTo("Amazon Pay")
    }

    @Test
    fun `salary credit`() {
        val result = parser.parse(
            sms(
                "AD-CANARA-S",
                "Your A/c XX5678 is credited by Rs.85,000.00 on 05-08-26 towards " +
                    "SALARY-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.SALARY)
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(85_000))
        assertThat(txn.maskedAccount).isEqualTo("XX5678")
    }

    @Test
    fun `EMI debit on a loan account`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Your Loan A/c XX9999 is debited by Rs.12,345.00 towards EMI on " +
                    "05-08-26-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(12_345))
        assertThat(txn.maskedAccount).isEqualTo("XX9999")
    }

    @Test
    fun `ATM withdrawal`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Rs.5,000.00 withdrawn from A/c XX1234 using Canara Bank ATM on " +
                    "05-08-26. Avl Bal Rs.20,000.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.ATM_WITHDRAWAL)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(5_000))
        assertThat(txn.balanceAfter).isEqualTo(Money.ofMajor(20_000))
    }

    @Test
    fun `refund credit`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Rs.1,200.00 credited to your A/c XX1234 as refund on 05-08-26-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(1_200))
    }

    @Test
    fun `bill payment debit sets a bill type`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Your A/c XX1234 is debited by Rs.2,499.00 on 05-08-26 towards " +
                    "Electricity Bill Payment-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.BILL_PAYMENT)
        assertThat(txn.billType).isEqualTo(BillType.ELECTRICITY)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(2_499))
    }

    @Test
    fun `interest credited is typed as interest income`() {
        val result = parser.parse(
            sms(
                "AD-CANBNK-S",
                "Rs.150.00 interest credited to your A/c XX1234 on 05-08-26-Canara Bank",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.INTEREST)
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(150))
    }

    @Test
    fun `promotional message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-CANARA-T",
                "Canara Bank: Avail personal loan at low interest rates! Apply now, " +
                    "limited period offer. T&C apply.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-CANARA-T",
                "Your OTP for Canara Bank net banking login is 738291. Do not share " +
                    "this OTP with anyone.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `message with no amount is not applicable`() {
        val result = parser.parse(
            sms("AD-CANBNK-S", "Canara Bank: Your cheque book request has been processed."),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `malformed amount with no digits is not applicable`() {
        val result = parser.parse(
            sms("CANBNK", "CANBNK: A/c XX1234 debited Rs. Please check your statement."),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
