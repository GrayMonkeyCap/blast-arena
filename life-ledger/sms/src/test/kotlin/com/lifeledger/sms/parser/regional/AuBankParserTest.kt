package com.lifeledger.sms.parser.regional

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class AuBankParserTest {

    private val parser = AuBankParser()

    @Test
    fun `debit alert decodes the RRN and counterparty from the slash-delimited Info field`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 500.00 debited from A/c XX1234. Info: " +
                    "UPI/DR/302345678901/Swiggy/YESB on 05-08-26",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.DEBIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(500))
        assertThat(txn.bankCode).isEqualTo("AUBL")
        assertThat(txn.maskedAccount).isEqualTo("XX1234")
        // The generic patterns need a "ref"/"rrn" keyword next to the digits and find
        // none in the Info field, so without the bank-specific fix this would be null.
        assertThat(txn.referenceNumber).isEqualTo("302345678901")
        assertThat(txn.transactionId).isEqualTo("302345678901")
        assertThat(txn.rawMerchant).isEqualTo("Swiggy")
    }

    @Test
    fun `credit alert with a person-name counterparty`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 15,000.00 credited to A/c XX5678. Info: " +
                    "UPI/CR/302345679999/Rahul Sharma/HDFC on 05-08-26",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.direction).isEqualTo(Direction.CREDIT)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(15_000))
        assertThat(txn.referenceNumber).isEqualTo("302345679999")
        assertThat(txn.rawMerchant).isEqualTo("Rahul Sharma")
    }

    @Test
    fun `salary credit without an Info field`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 60,000.00 credited to A/c XX1234 towards SALARY on " +
                    "05-08-26. Avl Bal INR 95,000.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.SALARY)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(60_000))
        assertThat(txn.balanceAfter).isEqualTo(Money.ofMajor(95_000))
    }

    @Test
    fun `EMI debit without an Info field`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 8,500.00 debited from A/c XX1234 towards EMI on " +
                    "05-08-26. Avl Bal INR 86,500.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.EMI)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(8_500))
    }

    @Test
    fun `ATM withdrawal`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 3,000.00 withdrawn from A/c XX1234 at AU Bank ATM on " +
                    "05-08-26. Avl Bal INR 83,500.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.ATM_WITHDRAWAL)
        assertThat(txn.paymentMethod).isEqualTo(PaymentMethod.ATM)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(3_000))
    }

    @Test
    fun `refund credit decoded from the Info field`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 799.00 credited to A/c XX1234. Info: " +
                    "UPI/CR/302345671111/Refund-Flipkart on 05-08-26",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.REFUND)
        assertThat(txn.referenceNumber).isEqualTo("302345671111")
        assertThat(txn.rawMerchant).isEqualTo("Refund-Flipkart")
    }

    @Test
    fun `bill payment debit sets a bill type`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: INR 1,800.00 debited from A/c XX1234 towards Mobile Bill " +
                    "Payment on 05-08-26. Avl Bal INR 81,700.00",
            ),
            testContext,
        )

        val txn = result.asSuccess()
        assertThat(txn.type).isEqualTo(TransactionType.BILL_PAYMENT)
        assertThat(txn.billType).isEqualTo(BillType.MOBILE)
        assertThat(txn.amount).isEqualTo(Money.ofMajor(1_800))
    }

    @Test
    fun `promotional message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-AUSFBL-T",
                "AU Bank: Get instant personal loan up to Rs.10 lakh with minimal " +
                    "documentation. Apply now! Limited period offer.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP message is ignored`() {
        val result = parser.parse(
            sms(
                "VM-AUSFBL-T",
                "473829 is your OTP for AU Bank mobile banking login. Do not share " +
                    "your OTP with anyone. Valid 5 mins.",
            ),
            testContext,
        )

        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `message with no amount is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: Your monthly account statement is ready. Download from " +
                    "AU 0101 app.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `balance enquiry with an amount but no verb is not applicable`() {
        val result = parser.parse(
            sms(
                "AD-AUBANK-S",
                "AU Bank: Available balance in A/c XX1234 is INR 45,000.00 as on " +
                    "05-08-26.",
            ),
            testContext,
        )

        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
