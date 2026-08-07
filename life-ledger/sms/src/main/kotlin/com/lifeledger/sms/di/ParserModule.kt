package com.lifeledger.sms.di

import com.lifeledger.sms.api.DefaultParserRegistry
import com.lifeledger.sms.api.ParserRegistry
import com.lifeledger.sms.api.SmsParser
import com.lifeledger.sms.parser.banks.AxisParser
import com.lifeledger.sms.parser.banks.HdfcParser
import com.lifeledger.sms.parser.banks.IciciParser
import com.lifeledger.sms.parser.banks.IdfcParser
import com.lifeledger.sms.parser.banks.KotakParser
import com.lifeledger.sms.parser.banks.SbiParser
import com.lifeledger.sms.parser.life.AppointmentParser
import com.lifeledger.sms.parser.life.BookingParser
import com.lifeledger.sms.parser.life.DeliveryParser
import com.lifeledger.sms.parser.life.OtpParser
import com.lifeledger.sms.parser.regional.AuBankParser
import com.lifeledger.sms.parser.regional.CanaraBankParser
import com.lifeledger.sms.parser.regional.FederalBankParser
import com.lifeledger.sms.parser.regional.GenericBankParser
import com.lifeledger.sms.parser.regional.IndianBankParser
import com.lifeledger.sms.parser.regional.YesBankParser
import com.lifeledger.sms.parser.wallets.AmazonPayParser
import com.lifeledger.sms.parser.wallets.CreditCardParser
import com.lifeledger.sms.parser.wallets.GooglePayParser
import com.lifeledger.sms.parser.wallets.PaytmParser
import com.lifeledger.sms.parser.wallets.PhonePeParser
import com.lifeledger.sms.parser.wallets.WalletParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Registers every parser the engine knows about.
 *
 * This module is the *only* place that has to change when a bank is added. A new parser is
 * one class plus one `@Binds @IntoSet` line here; the registry sorts by priority, the
 * pipeline picks it up, and nothing else in the app is aware anything changed. That is the
 * extensibility requirement made concrete rather than promised.
 *
 * Priorities are declared on each parser's `ParserInfo`, not here, so a parser's ordering
 * travels with it: bank-specific parsers at 10, payment apps at 15, card and wallet
 * content-matchers at 30–40, non-financial life events at 5 (they claim only messages no
 * financial parser wants), and the generic fallback at 900.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @Singleton
    abstract fun bindsParserRegistry(impl: DefaultParserRegistry): ParserRegistry

    // ---- Banks -------------------------------------------------------------

    @Binds
    @IntoSet
    abstract fun bindsHdfc(parser: HdfcParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsIcici(parser: IciciParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsSbi(parser: SbiParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsAxis(parser: AxisParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsKotak(parser: KotakParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsIdfc(parser: IdfcParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsYesBank(parser: YesBankParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsCanara(parser: CanaraBankParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsIndianBank(parser: IndianBankParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsFederal(parser: FederalBankParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsAuBank(parser: AuBankParser): SmsParser

    // ---- Payment apps, cards and wallets -----------------------------------

    @Binds
    @IntoSet
    abstract fun bindsPhonePe(parser: PhonePeParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsGooglePay(parser: GooglePayParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsPaytm(parser: PaytmParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsAmazonPay(parser: AmazonPayParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsCreditCard(parser: CreditCardParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsWallet(parser: WalletParser): SmsParser

    // ---- Non-financial life events -----------------------------------------

    @Binds
    @IntoSet
    abstract fun bindsOtp(parser: OtpParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsDelivery(parser: DeliveryParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsBooking(parser: BookingParser): SmsParser

    @Binds
    @IntoSet
    abstract fun bindsAppointment(parser: AppointmentParser): SmsParser

    // ---- Fallback ----------------------------------------------------------

    /**
     * Runs last and refuses anything ambiguous. A message it declines is stored as
     * UNMATCHED rather than guessed at, so it can be replayed once a real parser exists.
     */
    @Binds
    @IntoSet
    abstract fun bindsGeneric(parser: GenericBankParser): SmsParser
}
