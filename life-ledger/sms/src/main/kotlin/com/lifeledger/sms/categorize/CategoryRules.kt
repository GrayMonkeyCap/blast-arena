package com.lifeledger.sms.categorize

import com.lifeledger.core.model.TxnCategory

/** One keyword group and the category/subcategory it implies. */
data class CategoryRule(
    val category: TxnCategory,
    val subcategory: String?,
    val keywords: List<String>,
)

/**
 * Keyword vocabulary used to categorise a message when the merchant catalogue has no
 * opinion — an unrecognised merchant, or a transaction with no merchant at all (a plain
 * NEFT, an ATM withdrawal narration, a bill-payment SMS that never names the biller).
 *
 * Rules are tried in order and the first hit wins, so **ordering is the precedence rule**:
 * multi-word, unambiguous phrases ("mutual fund", "passport fee") are placed ahead of short,
 * generic words ("fee", "bill") that would otherwise steal matches that belong to a more
 * specific category. This mirrors the same principle [com.lifeledger.sms.lex.Lexicon] uses
 * for transaction-type keywords, for the same reason: the vocabulary is small and stable
 * enough that a curated list beats anything probabilistic, as long as specificity order is
 * respected.
 */
object CategoryRules {

    val rules: List<CategoryRule> = listOf(
        // ---- Investments (checked early: "sip", "fund", "folio" are unambiguous and must
        // not be shadowed by the generic "fees" or "bank" rules below).
        CategoryRule(
            TxnCategory.INVESTMENTS,
            "Mutual Fund",
            listOf("mutual fund", "sip installment", "sip instalment", "folio number", "nav of", "units allotted", "elss"),
        ),
        CategoryRule(TxnCategory.INVESTMENTS, "Broking", listOf("demat account", "stock purchase", "shares purchased", "trading account", "nse ", "bse ")),
        CategoryRule(TxnCategory.INVESTMENTS, "Retirement", listOf("nps contribution", "ppf deposit", "public provident fund")),
        CategoryRule(TxnCategory.INVESTMENTS, "Gold", listOf("sovereign gold bond", "digital gold purchase")),

        // ---- Insurance
        CategoryRule(TxnCategory.INSURANCE, "Premium", listOf("insurance premium", "policy renewal", "premium due", "term plan premium", "policy no")),

        // ---- Loans
        CategoryRule(TxnCategory.LOANS, "EMI", listOf("emi", "loan installment", "loan instalment", "loan repayment", "personal loan", "home loan")),

        // ---- Taxes
        CategoryRule(TxnCategory.TAXES, "Income Tax", listOf("income tax", "advance tax", "tds deducted", "self assessment tax")),
        CategoryRule(TxnCategory.TAXES, "GST", listOf("gst payment", "goods and services tax", "gst challan")),
        CategoryRule(TxnCategory.TAXES, "Property Tax", listOf("property tax")),

        // ---- Government
        CategoryRule(TxnCategory.GOVERNMENT, "Passport", listOf("passport fee", "passport seva")),
        CategoryRule(TxnCategory.GOVERNMENT, "RTO", listOf("rto fee", "vehicle registration", "challan payment")),
        CategoryRule(TxnCategory.GOVERNMENT, "Municipal", listOf("municipal corporation", "municipal tax")),

        // ---- Education
        CategoryRule(TxnCategory.EDUCATION, "Fees", listOf("tuition fee", "school fee", "college fee", "course fee", "exam fee", "coaching class", "admission fee")),

        // ---- Healthcare
        CategoryRule(TxnCategory.HEALTHCARE, "Diagnostics", listOf("lab test", "diagnostic centre", "diagnostic center", "pathology")),
        CategoryRule(TxnCategory.HEALTHCARE, "Pharmacy", listOf("pharmacy", "medicine purchase", "chemist")),
        CategoryRule(TxnCategory.HEALTHCARE, "Hospital", listOf("hospital bill", "clinic visit", "doctor fee", "consultation fee")),

        // ---- Travel
        CategoryRule(TxnCategory.TRAVEL, "Flights", listOf("flight booking", "airlines ticket", "air ticket")),
        CategoryRule(TxnCategory.TRAVEL, "Trains", listOf("railway ticket", "irctc booking", "pnr ")),
        CategoryRule(TxnCategory.TRAVEL, "Hotels", listOf("hotel booking", "holiday package", "travel agency")),

        // ---- Transport
        CategoryRule(TxnCategory.TRANSPORT, "Cabs", listOf("cab fare", "taxi fare", "auto fare")),
        CategoryRule(TxnCategory.TRANSPORT, "Public Transit", listOf("metro card", "metro recharge", "bus fare")),
        CategoryRule(TxnCategory.TRANSPORT, "Tolls & Parking", listOf("fastag", "toll plaza", "parking charges")),

        // ---- Fuel
        CategoryRule(TxnCategory.FUEL, "Petrol Pump", listOf("petrol pump", "diesel purchase", "fuel station", "cng station", "petrol filling")),

        // ---- Groceries (checked before Food/Shopping: "grocery"/"kirana" are unambiguous)
        CategoryRule(TxnCategory.GROCERIES, "Grocery Store", listOf("grocery store", "kirana store", "supermarket bill", "provision store", "vegetable market")),

        // ---- Food
        CategoryRule(TxnCategory.FOOD, "Food Delivery", listOf("food order", "food delivery")),
        CategoryRule(TxnCategory.FOOD, "Restaurant", listOf("restaurant bill", "dine in", "dine-in", "eatery", "biryani house", "dhaba")),

        // ---- Entertainment
        CategoryRule(TxnCategory.ENTERTAINMENT, "Movies", listOf("movie ticket", "cinema hall", "multiplex")),
        CategoryRule(TxnCategory.ENTERTAINMENT, "Events", listOf("concert ticket", "amusement park", "gaming zone", "event ticket")),

        // ---- Subscriptions (recurring digital services, generic phrasing)
        CategoryRule(TxnCategory.SUBSCRIPTIONS, "Recurring Charge", listOf("subscription renewed", "auto-renewal", "auto renewal", "monthly plan renewed", "streaming plan")),

        // ---- Utilities
        CategoryRule(TxnCategory.UTILITIES, "Electricity", listOf("electricity bill", "power bill")),
        CategoryRule(TxnCategory.UTILITIES, "Water", listOf("water bill", "water charges")),
        CategoryRule(TxnCategory.UTILITIES, "Gas", listOf("gas bill", "lpg cylinder", "piped gas")),
        CategoryRule(TxnCategory.UTILITIES, "Broadband", listOf("broadband bill", "internet bill", "wifi bill")),
        CategoryRule(TxnCategory.UTILITIES, "Mobile", listOf("mobile recharge", "prepaid recharge", "postpaid bill", "dth recharge")),

        // ---- Rent
        CategoryRule(TxnCategory.RENT, "House Rent", listOf("house rent", "rent payment", "rental agreement", "landlord")),

        // ---- Home
        CategoryRule(TxnCategory.HOME, "Repairs & Maintenance", listOf("plumber charges", "electrician service", "pest control", "home repair", "hardware store")),
        CategoryRule(TxnCategory.HOME, "Furniture", listOf("furniture purchase")),

        // ---- Personal care
        CategoryRule(TxnCategory.PERSONAL_CARE, "Salon & Spa", listOf("salon bill", "spa booking", "parlour bill", "grooming service", "haircut")),

        // ---- Pets
        CategoryRule(TxnCategory.PETS, "Pet Care", listOf("pet store", "veterinary clinic", "pet grooming", "pet food purchase")),

        // ---- Gifts
        CategoryRule(TxnCategory.GIFTS, "Gifts", listOf("gift voucher", "gift card purchase", "flower delivery", "greeting card")),

        // ---- Shopping (deliberately generic; checked after every more specific retail rule)
        CategoryRule(TxnCategory.SHOPPING, "Retail", listOf("shopping mall", "apparel purchase", "footwear purchase", "electronics store", "fashion store")),

        // ---- Charity
        CategoryRule(TxnCategory.CHARITY, "Donation", listOf("donation", "donated to", "ngo contribution", "relief fund")),

        // ---- Cash
        CategoryRule(TxnCategory.CASH, "ATM", listOf("cash withdrawal", "atm withdrawal", "cash deposit")),

        // ---- Fees (generic financial-institution charges; must stay below every domain-specific "fee" rule above)
        CategoryRule(TxnCategory.FEES, "Bank Charges", listOf("processing fee", "late fee", "annual fee", "service charge", "penalty charge", "convenience fee")),

        // ---- Transfers
        CategoryRule(TxnCategory.TRANSFERS, "Fund Transfer", listOf("fund transfer", "money transfer", "neft transfer", "imps transfer", "rtgs transfer")),

        // ---- Income (credits; kept last among "real" rules since income keywords are
        // rarely present in a debit-side merchant string but do appear in narrations).
        CategoryRule(TxnCategory.INCOME, "Salary", listOf("salary credited", "salary payment", "stipend credited")),
        CategoryRule(TxnCategory.INCOME, "Interest", listOf("interest credited", "interest earned")),
        CategoryRule(TxnCategory.INCOME, "Dividend", listOf("dividend credited", "dividend payout")),
        CategoryRule(TxnCategory.INCOME, "Freelance", listOf("consulting fee received", "freelance payment received")),

        // ---- Miscellaneous (weakest, most generic signal; last resort before UNCATEGORIZED)
        CategoryRule(TxnCategory.MISC, null, listOf("miscellaneous", "sundry expense", "other charges")),
    )

    /**
     * First rule whose keyword appears in [text], or `null` when nothing matches. Matching
     * is case-insensitive substring containment — deliberately simple, since every keyword
     * above is itself a specific enough phrase that accidental containment is not a real risk.
     */
    fun match(text: String): CategoryRule? {
        val lower = text.lowercase()
        return rules.firstOrNull { rule -> rule.keywords.any { lower.contains(it) } }
    }
}
