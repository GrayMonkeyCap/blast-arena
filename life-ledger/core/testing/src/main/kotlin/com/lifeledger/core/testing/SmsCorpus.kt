package com.lifeledger.core.testing

import com.lifeledger.core.model.Direction

/**
 * One message in [SmsCorpus], with the ground truth a parser is expected to produce.
 *
 * [expectDirection] and [expectAmountMinor] are `null` for messages that carry no value
 * transfer at all (OTPs, deliveries, promotions) — asserting `null` is the point, since a
 * parser that invents an amount for a non-financial message is a bug.
 */
data class CorpusMessage(
    val sender: String,
    val body: String,
    val expectDirection: Direction?,
    val expectAmountMinor: Long?,
    val label: String,
)

/**
 * A shared corpus of realistic Indian bank/UPI/merchant SMS, used by both the parser test
 * suite (does the parser extract the right fields?) and repository/pipeline tests (does an
 * end-to-end run land the right row in the database?).
 *
 * Bodies are modelled closely on real bank templates but use fictitious names, numbers and
 * reference ids throughout — nothing here is a real account, phone number or transaction.
 */
object SmsCorpus {

    val messages: List<CorpusMessage> = listOf(
        // --- Debit card / UPI debits -----------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.499.00 debited from A/c XX4521 on 15-01-26 to VPA amazon@apl UPI Ref 123456789012. " +
                "Not you? Call 18002586161.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 49_900,
            label = "hdfc_upi_debit_amazon",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 1,240.00 on 03-Jan-26; SWIGGY BANGALORE credited. " +
                "UPI:987654321098. Call 18001080 for dispute.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 124_000,
            label = "icici_upi_debit_swiggy",
        ),
        CorpusMessage(
            sender = "AX-SBIUPI-S",
            body = "Dear UPI user A/C X9922 debited by 350.0 on date 12Jan26 trf to ZOMATO ONLINE ORDER " +
                "Refno 445566778899. If not done call 1800111109. -SBI",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 35_000,
            label = "sbi_upi_debit_zomato",
        ),
        CorpusMessage(
            sender = "VD-AXISBK-S",
            body = "INR 2,999.00 spent on Axis Bank Card XX3344 at FLIPKART INTERNET on 20-01-26. " +
                "Avl Lmt: INR 1,45,000.00.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 299_900,
            label = "axis_card_debit_flipkart",
        ),
        CorpusMessage(
            sender = "JD-KOTAKB-S",
            body = "Rs 850.00 debited from Kotak Bank AC X2211 on 18-01-2026 towards UPI to " +
                "bigbasket@ybl. UPI Ref 556677889900.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 85_000,
            label = "kotak_upi_debit_bigbasket",
        ),
        CorpusMessage(
            sender = "TM-PAYTMB-S",
            body = "Paid Rs.120 to Chai Point via Paytm UPI from A/c XX6543. Ref No. 778899001122.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 12_000,
            label = "paytm_upi_debit_chaipoint",
        ),
        CorpusMessage(
            sender = "IM-YESBNK-S",
            body = "Rs.75.00 debited from A/c XX1199 on 22-01-26 UPI/P2M/889900112233/AUTO RICKSHAW STAND. " +
                "Bal: Rs.9,340.20 -YES BANK",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 7_500,
            label = "yesbank_upi_debit_autostand",
        ),
        CorpusMessage(
            sender = "BX-PNBSMS-S",
            body = "Your A/c XX4433 debited INR 5,000.00 on 25-01-26 towards NEFT to LANDLORD RENT " +
                "MR SHARMA. Ref N012345678901.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 500_000,
            label = "pnb_neft_debit_rent",
        ),
        CorpusMessage(
            sender = "CP-IDFCFB-S",
            body = "IDFC FIRST Bank: Rs.199.00 debited from A/c XX8821 for RECHARGE-AIRTEL PREPAID " +
                "on 05-01-26 via UPI. Ref 001122334455.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 19_900,
            label = "idfc_upi_debit_airtel_recharge",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.15,000.00 withdrawn from A/c XX4521 at HDFC ATM MG ROAD BLR on 07-01-26. " +
                "Avl Bal Rs.42,110.55.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 1_500_000,
            label = "hdfc_atm_withdrawal",
        ),

        // --- Credits ------------------------------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.85,000.00 credited to A/c XX4521 on 01-01-26 by NEFT from ACME SOFTWARE PVT LTD, " +
                "SALARY JAN 2026. Ref N998877665544.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 8_500_000,
            label = "hdfc_neft_credit_salary",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 credited with Rs. 249.00 on 14-Jan-26 from AMAZON REFUNDS. " +
                "UPI:112233445566.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 24_900,
            label = "icici_credit_refund_amazon",
        ),
        CorpusMessage(
            sender = "AX-SBIUPI-S",
            body = "Dear Customer A/C X9922 credited by Rs.60.00 on 19Jan26 towards CASHBACK OFFER. " +
                "Bal Rs.12,540.10 -SBI",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 6_000,
            label = "sbi_credit_cashback",
        ),
        CorpusMessage(
            sender = "VD-AXISBK-S",
            body = "INR 3,20,000.00 credited to A/c XX3344 on 02-01-26 via IMPS from RAJESH KUMAR. " +
                "Ref P665544332211.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 32_000_000,
            label = "axis_imps_credit_transfer",
        ),
        CorpusMessage(
            sender = "JD-KOTAKB-S",
            body = "Rs 4,50,000.00 credited to Kotak A/c X2211 on 09-01-26 - HOME LOAN DISBURSAL from " +
                "KOTAK MAHINDRA HOUSING. Ref D001200340056.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 45_000_000,
            label = "kotak_credit_loan_disbursal",
        ),
        CorpusMessage(
            sender = "BX-PNBSMS-S",
            body = "Your A/c XX4433 credited INR 210.50 on 11-01-26 towards SAVINGS INTEREST for Q4. " +
                "-PNB",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 21_050,
            label = "pnb_credit_interest",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.1,299.00 reversed to A/c XX4521 on 06-01-26. Original txn on 04-01-26 to " +
                "MYNTRA DESIGNS. Ref R334455667788.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 129_900,
            label = "hdfc_credit_reversal",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 credited Rs. 2,500.00 dividend from RELIANCE INDUSTRIES LTD " +
                "on 16-01-26. UPI:NA.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 250_000,
            label = "icici_credit_dividend",
        ),

        // --- Credit card statements / bills -------------------------------------------------
        CorpusMessage(
            sender = "AX-SBICRD-S",
            body = "Your SBI Card XX9988 has been used for Rs.4,599.00 at DECATHLON SPORTS on 08-01-26. " +
                "Avl Limit: Rs.95,401.00.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 459_900,
            label = "sbicard_debit_decathlon",
        ),
        CorpusMessage(
            sender = "VK-HDFCBK-S",
            body = "Payment of Rs.12,340.00 received towards HDFC Credit Card XX5566 on 10-01-26. " +
                "Thank you.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 1_234_000,
            label = "hdfc_creditcard_payment",
        ),
        CorpusMessage(
            sender = "AX-AXISCC-S",
            body = "Your Axis Bank Credit Card XX7711 statement is generated. Total Due: Rs.18,760.00. " +
                "Min Due: Rs.940.00. Due Date: 28-01-26.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "axis_creditcard_statement_notice",
        ),

        // --- Bills, recharges, utilities -----------------------------------------------------
        CorpusMessage(
            sender = "VM-BESCOM-T",
            body = "Dear Customer, your BESCOM electricity bill of Rs.1,240.00 for A/c 123456789 is " +
                "due on 05-02-26. Pay via UPI to avoid disconnection.",
            expectDirection = null,
            expectAmountMinor = 124_000,
            label = "bescom_bill_due_reminder",
        ),
        CorpusMessage(
            sender = "IM-JIOBIL-T",
            body = "Your Jio Fiber bill of Rs.999.00 is due on 12-02-26. Pay now to avoid service " +
                "interruption. Ref: JF778899001.",
            expectDirection = null,
            expectAmountMinor = 99_900,
            label = "jio_fiber_bill_due",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.1,240.00 debited from A/c XX4521 on 05-02-26 towards BESCOM ELECTRICITY BILL " +
                "PAYMENT via BillDesk. Ref B009988776655.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 124_000,
            label = "hdfc_bill_payment_electricity",
        ),
        CorpusMessage(
            sender = "TM-PAYTMB-S",
            body = "Rs.399.00 paid for Airtel DTH Recharge via Paytm from A/c XX6543. Ref " +
                "223344556677.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 39_900,
            label = "paytm_dth_recharge",
        ),
        CorpusMessage(
            sender = "VK-ACTFIB-T",
            body = "Reminder: Your ACT Fibernet bill of Rs.1,099.00 is overdue since 15-01-26. " +
                "Please pay immediately.",
            expectDirection = null,
            expectAmountMinor = 109_900,
            label = "act_fibernet_overdue_reminder",
        ),

        // --- Subscriptions --------------------------------------------------------------------
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 649.00 on 15-01-26; NETFLIX.COM credited. " +
                "UPI:NA.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 64_900,
            label = "icici_debit_netflix_subscription",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.119.00 debited from A/c XX4521 on 20-01-26 towards SPOTIFY INDIA via AUTOPAY " +
                "mandate. Ref A556677889900.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 11_900,
            label = "hdfc_autopay_spotify",
        ),
        CorpusMessage(
            sender = "VD-AXISBK-S",
            body = "INR 199.00 spent on Axis Bank Card XX3344 at AMAZON PRIME MEMBERSHIP on 25-01-26.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 19_900,
            label = "axis_card_amazon_prime",
        ),

        // --- Investments / SIPs -----------------------------------------------------------------
        CorpusMessage(
            sender = "VK-AXISMF-T",
            body = "Your SIP of Rs.5,000.00 in Axis Bluechip Fund - Direct Growth has been processed " +
                "on 05-01-26. Folio: 1234567/89.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 500_000,
            label = "axismf_sip_processed",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.5,000.00 debited from A/c XX4521 on 05-01-26 towards SIP - AXIS MUTUAL FUND " +
                "via NACH. Ref S001122334455.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 500_000,
            label = "hdfc_nach_sip_debit",
        ),
        CorpusMessage(
            sender = "VK-ZERODH-T",
            body = "Order executed: BUY 10 shares of TATA MOTORS at Rs.789.50 on NSE. Total value " +
                "Rs.7,895.00. -Zerodha",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 789_500,
            label = "zerodha_stock_buy_order",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 1,50,000.00 on 30-01-26 towards FIXED DEPOSIT " +
                "booking, A/c FD9988776655.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 15_000_000,
            label = "icici_fd_booking_debit",
        ),
        CorpusMessage(
            sender = "VK-PPFIND-T",
            body = "Rs.12,500.00 credited to your PPF Account PPF001122334 as maturity interest for " +
                "FY 2025-26.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 1_250_000,
            label = "ppf_maturity_interest_credit",
        ),

        // --- EMI / loans / insurance -------------------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.18,450.00 debited from A/c XX4521 on 05-01-26 towards HOME LOAN EMI. " +
                "Loan A/c HL0011223344. Ref E778899001122.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 1_845_000,
            label = "hdfc_home_loan_emi",
        ),
        CorpusMessage(
            sender = "VD-BAJFIN-T",
            body = "Bajaj Finserv: EMI of Rs.3,200.00 for your Consumer Durable Loan is due on 10-02-26. " +
                "Pay on time to avoid late fee.",
            expectDirection = null,
            expectAmountMinor = 320_000,
            label = "bajfin_emi_due_reminder",
        ),
        CorpusMessage(
            sender = "VK-LICIND-T",
            body = "Rs.24,500.00 debited from A/c XX4521 on 15-01-26 towards LIC PREMIUM PAYMENT, " +
                "Policy No. 998877665544.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 2_450_000,
            label = "lic_premium_payment_debit",
        ),

        // --- Delivery / booking / appointment (non-financial) -------------------------------------
        CorpusMessage(
            sender = "VM-AMAZON-T",
            body = "Your Amazon package with AirPods Pro is out for delivery and will arrive today " +
                "by 9 PM. Track: amzn.in/xyz123",
            expectDirection = null,
            expectAmountMinor = null,
            label = "amazon_delivery_out_for_delivery",
        ),
        CorpusMessage(
            sender = "VM-SWIGGY-T",
            body = "Your Swiggy order #445566 from Truffles has been picked up and is on its way! " +
                "ETA 25 mins.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "swiggy_order_picked_up",
        ),
        CorpusMessage(
            sender = "VM-IRCTC-T",
            body = "Your PNR 2345678901 is confirmed. Train 12658 departs 22-01-26 20:15 from " +
                "SBC to MAS. Have a safe journey.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "irctc_booking_confirmed",
        ),
        CorpusMessage(
            sender = "VM-APOLLO-T",
            body = "Your appointment with Dr. Rao at Apollo Clinic Koramangala is confirmed for " +
                "24-01-26, 11:00 AM. Token No: 14.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "apollo_appointment_confirmed",
        ),
        CorpusMessage(
            sender = "VM-ZOMATO-T",
            body = "Your Zomato order has been delivered. Enjoy your meal! Rate your experience " +
                "in the app.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "zomato_order_delivered",
        ),
        CorpusMessage(
            sender = "VM-MAKEMY-T",
            body = "Booking confirmed! Hotel Taj Bangalore, 25-27 Jan 26, 1 Room. Booking ID " +
                "MMT99887766.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "makemytrip_hotel_booking",
        ),

        // --- OTPs (non-financial) -----------------------------------------------------------------
        CorpusMessage(
            sender = "VD-HDFCBK-S",
            body = "OTP for txn of Rs.499.00 at AMAZON is 445566. Valid for 10 mins. Do not share " +
                "this OTP with anyone. -HDFC Bank",
            expectDirection = null,
            expectAmountMinor = null,
            label = "hdfc_otp_transaction",
        ),
        CorpusMessage(
            sender = "VK-ICICIB-S",
            body = "112233 is your OTP for login to iMobile Pay. Valid for 5 minutes. Do not share " +
                "with anyone.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "icici_otp_login",
        ),
        CorpusMessage(
            sender = "AX-AMAZON-T",
            body = "778899 is your Amazon OTP. Do not share it with anyone.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "amazon_otp_signin",
        ),

        // --- Balance enquiry (non-financial, no amount transferred) ------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Your A/c XX4521 available balance as of 26-01-26 08:00 is Rs.42,110.55.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "hdfc_balance_enquiry",
        ),
        CorpusMessage(
            sender = "AX-SBIUPI-S",
            body = "Your A/C X9922 Avl Bal is Rs.12,540.10 as on 27Jan26. -SBI",
            expectDirection = null,
            expectAmountMinor = null,
            label = "sbi_balance_enquiry",
        ),

        // --- Promotional (non-financial) ----------------------------------------------------------
        CorpusMessage(
            sender = "VM-AXISBK-P",
            body = "Get flat 10% cashback on your next Axis Bank Credit Card transaction above " +
                "Rs.5,000 this weekend. T&C apply.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "axis_promo_cashback_offer",
        ),
        CorpusMessage(
            sender = "VM-FLPKRT-P",
            body = "Big Billion Days is here! Up to 80% off on Electronics. Shop now at Flipkart.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "flipkart_promo_sale",
        ),
        CorpusMessage(
            sender = "VM-HDFCBK-P",
            body = "Pre-approved personal loan of Rs.5,00,000 is available for you. Apply now on " +
                "HDFC Bank app.",
            expectDirection = null,
            expectAmountMinor = null,
            label = "hdfc_promo_preapproved_loan",
        ),

        // --- Edge-case amount formats ----------------------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.1,23,456.78 debited from A/c XX4521 on 28-01-26 towards NEFT to BUILDER " +
                "PAYMENT. Ref N334455667788.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 12_345_678,
            label = "hdfc_neft_debit_lakh_format",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 2.5L on 29-01-26 towards RTGS to VENDOR " +
                "PAYMENTS PVT LTD. UPI:NA.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 25_000_000,
            label = "icici_rtgs_debit_lakh_suffix",
        ),
        CorpusMessage(
            sender = "VD-AXISBK-S",
            body = "INR 1.2Cr credited to A/c XX3344 on 30-01-26 - PROPERTY SALE PROCEEDS via RTGS. " +
                "Ref P998877665544.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 1_200_000_000,
            label = "axis_rtgs_credit_crore_suffix",
        ),
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.10.00 debited from A/c XX4521 on 31-01-26 towards SMS ALERT CHARGES for Q4. " +
                "Ref F001100220033.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 1_000,
            label = "hdfc_debit_small_fee_charges",
        ),

        // --- Cash deposit / cheque -----------------------------------------------------------------
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 credited Rs. 20,000.00 on 02-01-26 - CASH DEPOSIT at " +
                "Koramangala Branch.",
            expectDirection = Direction.CREDIT,
            expectAmountMinor = 2_000_000,
            label = "icici_cash_deposit_credit",
        ),
        CorpusMessage(
            sender = "BX-PNBSMS-S",
            body = "Cheque No. 445566 for Rs.8,000.00 has been cleared from your A/c XX4433 on " +
                "06-01-26. -PNB",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 800_000,
            label = "pnb_cheque_cleared_debit",
        ),

        // --- Donations / charity ------------------------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.501.00 debited from A/c XX4521 on 15-01-26 towards UPI to donations@give.in " +
                "PM CARES FUND. Ref D556677889900.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 50_100,
            label = "hdfc_upi_donation_debit",
        ),

        // --- Tax payments -----------------------------------------------------------------------
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 45,000.00 on 15-03-26 towards ADVANCE TAX " +
                "PAYMENT via NetBanking. Challan No. 998877665544.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 4_500_000,
            label = "icici_advance_tax_debit",
        ),

        // --- Wallet top-up / spend -----------------------------------------------------------------
        CorpusMessage(
            sender = "TM-PAYTMB-S",
            body = "Rs.1,000.00 added to your Paytm Wallet from A/c XX6543. Ref W112233445566.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 100_000,
            label = "paytm_wallet_topup_debit",
        ),
        CorpusMessage(
            sender = "TM-PAYTMB-S",
            body = "Paid Rs.60.00 to METRO RAIL RECHARGE via Paytm Wallet. Ref W667788990011.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 6_000,
            label = "paytm_wallet_spend_metro",
        ),

        // --- Fuel ------------------------------------------------------------------------------
        CorpusMessage(
            sender = "VD-AXISBK-S",
            body = "INR 2,500.00 spent on Axis Bank Card XX3344 at INDIAN OIL PETROL PUMP on " +
                "17-01-26.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 250_000,
            label = "axis_card_fuel_debit",
        ),

        // --- Healthcare ---------------------------------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Rs.850.00 debited from A/c XX4521 on 24-01-26 towards UPI to apollopharmacy@icici " +
                "APOLLO PHARMACY. Ref H001122334455.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 85_000,
            label = "hdfc_upi_debit_pharmacy",
        ),

        // --- Failed / reversed transactions ------------------------------------------------------
        CorpusMessage(
            sender = "AX-SBIUPI-S",
            body = "Your UPI txn of Rs.2,000.00 to merchant@upi has FAILED due to insufficient " +
                "funds. No amount debited. -SBI",
            expectDirection = null,
            expectAmountMinor = null,
            label = "sbi_upi_failed_transaction",
        ),

        // --- Standing instruction / autopay setup -------------------------------------------------
        CorpusMessage(
            sender = "AD-HDFCBK-S",
            body = "Your AutoPay mandate for Rs.649.00 towards NETFLIX.COM has been registered on " +
                "A/c XX4521. UMN: umn445566778899@okhdfcbank.",
            expectDirection = null,
            expectAmountMinor = 64_900,
            label = "hdfc_autopay_mandate_registered",
        ),

        // --- Multi-account / masked variations -----------------------------------------------------
        CorpusMessage(
            sender = "JD-KOTAKB-S",
            body = "Rs 3,300.00 debited from Kotak Credit Card ending 9012 on 28-01-26 at " +
                "H&M INDIA STORE. Avl Limit Rs.62,700.00.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 330_000,
            label = "kotak_creditcard_debit_hm",
        ),
        CorpusMessage(
            sender = "VM-ICICIB-S",
            body = "ICICI Bank Acct XX7788 debited Rs. 999.00 on 29-01-26; MYPROTEIN INDIA credited. " +
                "UPI:334455667788.",
            expectDirection = Direction.DEBIT,
            expectAmountMinor = 99_900,
            label = "icici_upi_debit_myprotein",
        ),
    )

    /** Every non-financial message: OTPs, deliveries, bookings, promos, balance enquiries. */
    val nonFinancial: List<CorpusMessage> get() = messages.filter { it.expectDirection == null }

    /** Every message a parser should classify as either a debit or a credit. */
    val financial: List<CorpusMessage> get() = messages.filter { it.expectDirection != null }
}
