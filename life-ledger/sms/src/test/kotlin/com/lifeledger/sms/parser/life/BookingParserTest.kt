package com.lifeledger.sms.parser.life

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class BookingParserTest {

    private val parser = BookingParser()

    @Test
    fun `flight booking extracts provider pnr and route`() {
        val result = parser.parse(
            sms(
                "VM-INDIGO",
                "Your IndiGo flight 6E-2134 from DEL to BOM on 15-Aug-26 is confirmed. PNR: X7K9L2. " +
                    "Web check-in opens 48 hrs prior.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.BOOKING)
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("IndiGo")
        assertThat(success.transaction.extractedFields["pnr"]).isEqualTo("X7K9L2")
        assertThat(success.transaction.extractedFields["route"]).isEqualTo("DEL to BOM")
        assertThat(success.transaction.extractedFields["travel_date"]).isEqualTo("2026-08-15")
    }

    @Test
    fun `train booking via IRCTC extracts provider and pnr`() {
        val result = parser.parse(
            sms(
                "VM-IRCTC",
                "IRCTC: Your train booking PNR 2345678901 is confirmed. Train No 12951, Coach B4, Berth 23. " +
                    "Boarding station: NDLS.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("IRCTC")
        assertThat(success.transaction.extractedFields["pnr"]).isEqualTo("2345678901")
    }

    @Test
    fun `bus ticket via RedBus extracts provider and pnr`() {
        val result = parser.parse(
            sms(
                "VM-REDBUS",
                "Your RedBus ticket is booked. PNR: RB998877, Seat No 14, Boarding Point: Koyambedu, " +
                    "Departure 22:30 on 10-Aug-26.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("RedBus")
        assertThat(success.transaction.extractedFields["pnr"]).isEqualTo("RB998877")
    }

    @Test
    fun `hotel booking via OYO extracts provider without a pnr`() {
        val result = parser.parse(
            sms(
                "VM-OYO",
                "Your OYO booking is confirmed. Booking ID OYO12345678, Check-in 12-Aug-26, " +
                    "Check-out 14-Aug-26 at OYO Townhouse Bangalore.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("OYO")
        assertThat(success.transaction.extractedFields).doesNotContainKey("pnr")
    }

    @Test
    fun `movie ticket via BookMyShow is claimed`() {
        val result = parser.parse(
            sms(
                "VM-BMS",
                "Your BookMyShow tickets for Pathaan (10-Aug-26, 7:00 PM, PVR Forum Mall) are confirmed. " +
                    "Booking ID BMS8877665.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("BookMyShow")
    }

    @Test
    fun `cab booking via Ola is claimed`() {
        val result = parser.parse(
            sms(
                "VM-OLA",
                "Your Ola cab is confirmed. Driver Ramesh (KA-01-AB-1234) will arrive in 5 mins. Trip ID OLA9988776.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("Ola")
    }

    @Test
    fun `cab booking via Uber is claimed`() {
        val result = parser.parse(
            sms(
                "VM-UBER",
                "Your Uber trip is confirmed. Driver Suresh will pick you up at 10:15 AM. Trip ID UBR1234567.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("Uber")
    }

    @Test
    fun `promotional airline sale is ignored not treated as a booking`() {
        val result = parser.parse(
            sms("VM-INDIGO", "Flat 50% off on IndiGo flights this monsoon! Book now and save big. T&C apply."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare boarding mention without pnr or provider is too weak to claim`() {
        val result = parser.parse(
            sms("VM-AIRPORT", "Please carry a valid ID proof and arrive at the boarding gate 45 minutes prior to departure."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `message with no booking vocabulary at all is not applicable`() {
        val result = parser.parse(
            sms("HDFCBK", "Your electricity bill of Rs.1200 is due on 15-Aug-26."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `a fully specified flight booking is high confidence and actionable`() {
        val result = parser.parse(
            sms(
                "VM-INDIGO",
                "Your IndiGo flight 6E-2134 from DEL to BOM on 15-Aug-26 is confirmed. PNR: X7K9L2.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.isActionable).isTrue()
        assertThat(success.transaction.confidence.value).isAtLeast(0.7f)
    }

    @Test
    fun `booking events never carry an amount`() {
        val result = parser.parse(
            sms("VM-IRCTC", "IRCTC: Your train booking PNR 2345678901 is confirmed. Coach B4, Berth 23."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.amount).isNull()
    }
}
