package com.lifeledger.sms.parser.life

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class DeliveryParserTest {

    private val parser = DeliveryParser()

    @Test
    fun `Amazon shipment shipped is claimed with courier and tracking id`() {
        val result = parser.parse(
            sms(
                "AMAZON",
                "Your Amazon order #402-1234567-8901234 has been shipped and will arrive by 07-Aug-26. " +
                    "AWB: AMZL1234567890",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.DELIVERY)
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("SHIPPED")
        assertThat(success.transaction.rawMerchant).isEqualTo("Amazon")
        assertThat(success.transaction.extractedFields["tracking_id"]).isEqualTo("AMZL1234567890")
    }

    @Test
    fun `Flipkart order out for delivery is claimed`() {
        val result = parser.parse(
            sms("VM-FKRT", "Your Flipkart order OD12345678901234 is out for delivery today. Tracking ID: FMPC0012345678"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("OUT_FOR_DELIVERY")
        assertThat(success.transaction.rawMerchant).isEqualTo("Flipkart")
        assertThat(success.transaction.extractedFields["tracking_id"]).isEqualTo("FMPC0012345678")
    }

    @Test
    fun `Delhivery shipment delivered is claimed`() {
        val result = parser.parse(
            sms("VM-DELHVY", "Your Delhivery shipment AWB 1234567890123 has been delivered. Thank you for shopping with us."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("DELIVERED")
        assertThat(success.transaction.rawMerchant).isEqualTo("Delhivery")
    }

    @Test
    fun `BlueDart out for delivery without a tracking label still claims via courier name`() {
        val result = parser.parse(
            sms("VM-BLUDRT", "BlueDart: Your shipment 987654321098 is out for delivery, expected by 6 PM today."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("OUT_FOR_DELIVERY")
        assertThat(success.transaction.rawMerchant).isEqualTo("BlueDart")
        assertThat(success.transaction.extractedFields).doesNotContainKey("tracking_id")
    }

    @Test
    fun `Ekart delivered with tracking id`() {
        val result = parser.parse(
            sms("VM-EKART", "Ekart Logistics: Your package with Tracking ID EKL998877665 has been delivered."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("DELIVERED")
        assertThat(success.transaction.rawMerchant).isEqualTo("Ekart")
        assertThat(success.transaction.extractedFields["tracking_id"]).isEqualTo("EKL998877665")
    }

    @Test
    fun `DTDC shipped with consignment number`() {
        val result = parser.parse(
            sms("VM-DTDC", "Your order has been shipped via DTDC. Consignment No: D123456789."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("SHIPPED")
        assertThat(success.transaction.rawMerchant).isEqualTo("DTDC")
        assertThat(success.transaction.extractedFields["tracking_id"]).isEqualTo("D123456789")
    }

    @Test
    fun `Swiggy order delivered is claimed as a delivery event`() {
        val result = parser.parse(
            sms("VM-SWIGGY", "Your Swiggy order from Meghana Foods has been delivered. Enjoy your meal!"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["stage"]).isEqualTo("DELIVERED")
        assertThat(success.transaction.rawMerchant).isEqualTo("Swiggy")
    }

    @Test
    fun `Zomato order delivered is claimed as a delivery event`() {
        val result = parser.parse(
            sms("VM-ZOMATO", "Your Zomato order #ZP123456 has been delivered. Bon appetit!"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("Zomato")
    }

    @Test
    fun `India Post parcel delivered is claimed`() {
        val result = parser.parse(
            sms("VM-INDPST", "Your India Post parcel EE123456789IN has been delivered to your address."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.rawMerchant).isEqualTo("India Post")
    }

    @Test
    fun `promotional free shipping offer is ignored`() {
        val result = parser.parse(
            sms("VM-SHOP", "Free shipping on all orders above Rs.499! Shop now and get delivered within 2 days. Click here."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare delivered mention without courier or tracking is too weak to claim`() {
        val result = parser.parse(
            sms("VM-FRIEND", "The pizza was delivered late again, so annoying."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `message with no delivery vocabulary at all is not applicable`() {
        val result = parser.parse(
            sms("HDFCBK", "Your account statement for August is ready to view."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
