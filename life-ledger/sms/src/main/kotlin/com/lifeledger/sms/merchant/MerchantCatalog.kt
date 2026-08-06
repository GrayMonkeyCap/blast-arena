package com.lifeledger.sms.merchant

import com.lifeledger.core.model.TxnCategory

/**
 * One shipped merchant record: a canonical display name plus every raw string variant
 * known to mean it in an SMS or card-statement narration.
 *
 * [isPassThrough] marks payment processors and bill aggregators (Razorpay, PhonePe,
 * BillDesk, Bharat BillPay...) that show up in SMS as the apparent merchant but are never
 * the thing the user actually bought. [MerchantResolver] treats a pass-through hit as a
 * cue to keep looking at the rest of the string, not as a final answer — [category] and
 * [subcategory] on these entries describe the processor itself (`MISC` / "Payment
 * Gateway"), which is only ever surfaced when nothing more specific can be recovered.
 */
data class CatalogEntry(
    val canonicalName: String,
    val aliases: List<String>,
    val category: TxnCategory,
    val subcategory: String? = null,
    val isSubscription: Boolean = false,
    val isBill: Boolean = false,
    val isInvestment: Boolean = false,
    val isPassThrough: Boolean = false,
)

private fun entry(
    canonicalName: String,
    aliases: List<String>,
    category: TxnCategory,
    subcategory: String? = null,
    isSubscription: Boolean = false,
    isBill: Boolean = false,
    isInvestment: Boolean = false,
    isPassThrough: Boolean = false,
) = CatalogEntry(canonicalName, aliases, category, subcategory, isSubscription, isBill, isInvestment, isPassThrough)

/**
 * The merchant catalogue Life Ledger ships with.
 *
 * Aliases are transcribed the way they actually appear in Indian bank/card SMS and
 * statement narrations — `AMZN*MKTP`, city-suffixed forms, `*`-delimited processor
 * prefixes, corporate legal names banks sometimes settle to — rather than tidy marketing
 * names, because raw text like that is exactly what [MerchantResolver] has to match
 * against. Entries that are legally the same company but operate under a distinct
 * consumer brand (Zomato/Blinkit, Swiggy/Instamart) are folded into one entry so a single
 * canonical merchant, category and spend history results.
 */
object MerchantCatalog {

    val entries: List<CatalogEntry> = listOf(
        // ---------------------------------------------------------- food delivery / restaurants
        entry("Swiggy", listOf("SWIGGY", "SWIGGY LTD", "SWIGGY INSTAMART", "BUNDL TECHNOLOGIES", "SWIGGY BANGALORE"), TxnCategory.FOOD, "Food Delivery"),
        entry("Zomato", listOf("ZOMATO", "ZOMATO LTD", "ZOMATO ONLINE", "BLINKIT", "GROFERS", "BLINKIT INDIA"), TxnCategory.FOOD, "Food Delivery"),
        entry("Domino's Pizza", listOf("DOMINOS", "DOMINOS PIZZA", "JUBILANT FOODWORKS", "DOMINOS INDIA"), TxnCategory.FOOD, "Restaurant"),
        entry("McDonald's", listOf("MCDONALDS", "MCDONALD S", "HARDCASTLE RESTAURANTS", "WESTLIFE FOODWORLD"), TxnCategory.FOOD, "Restaurant"),
        entry("KFC", listOf("KFC", "KFC INDIA", "YUM RESTAURANTS"), TxnCategory.FOOD, "Restaurant"),
        entry("Starbucks", listOf("STARBUCKS", "STARBUCKS COFFEE", "TATA STARBUCKS"), TxnCategory.FOOD, "Cafe"),
        entry("Third Wave Coffee", listOf("THIRD WAVE COFFEE", "THIRDWAVE COFFEE ROASTERS"), TxnCategory.FOOD, "Cafe"),
        entry("Chaayos", listOf("CHAAYOS", "SUNSHINE TEAHOUSE"), TxnCategory.FOOD, "Cafe"),
        entry("Barbeque Nation", listOf("BARBEQUE NATION", "BBQ NATION", "BARBEQUE NATION HOSPITALITY"), TxnCategory.FOOD, "Restaurant"),
        entry("EatFit", listOf("EATFIT", "EAT FIT", "CUREFIT EATFIT"), TxnCategory.FOOD, "Food Delivery"),
        entry("Faasos", listOf("FAASOS", "REBEL FOODS FAASOS"), TxnCategory.FOOD, "Food Delivery"),
        entry("Behrouz Biryani", listOf("BEHROUZ", "BEHROUZ BIRYANI", "REBEL FOODS BEHROUZ"), TxnCategory.FOOD, "Food Delivery"),
        entry("Pizza Hut", listOf("PIZZA HUT", "PIZZA HUT INDIA", "DEVYANI INTERNATIONAL PIZZA HUT"), TxnCategory.FOOD, "Restaurant"),
        entry("Burger King", listOf("BURGER KING", "BURGER KING INDIA", "RESTAURANT BRANDS ASIA"), TxnCategory.FOOD, "Restaurant"),
        entry("Subway", listOf("SUBWAY", "SUBWAY SANDWICHES"), TxnCategory.FOOD, "Restaurant"),
        entry("Wow! Momo", listOf("WOW MOMO", "WOWMOMO FOODS"), TxnCategory.FOOD, "Restaurant"),
        entry("Haldiram's", listOf("HALDIRAM", "HALDIRAMS", "HALDIRAM BHUJIAWALA"), TxnCategory.FOOD, "Restaurant"),
        entry("Bikanervala", listOf("BIKANERVALA", "BIKANERVALA FOODS"), TxnCategory.FOOD, "Restaurant"),
        entry("Cafe Coffee Day", listOf("CAFE COFFEE DAY", "COFFEE DAY GLOBAL"), TxnCategory.FOOD, "Cafe"),
        entry("Costa Coffee", listOf("COSTA COFFEE", "COSTA COFFEE INDIA"), TxnCategory.FOOD, "Cafe"),
        entry("Blue Tokai", listOf("BLUE TOKAI", "BLUE TOKAI COFFEE"), TxnCategory.FOOD, "Cafe"),
        entry("Theobroma", listOf("THEOBROMA", "THEOBROMA FOODS"), TxnCategory.FOOD, "Bakery"),

        // ------------------------------------------------------------------------ groceries
        entry("BigBasket", listOf("BIGBASKET", "BBNOW", "BB DAILY", "INNOVATIVE RETAIL", "BIGBASKET COM", "SUPERMARKET GROCERY SUPPLIES"), TxnCategory.GROCERIES, "Quick Commerce"),
        entry("Zepto", listOf("ZEPTO", "ZEPTO MARKETPLACE", "KIRANAKART TECHNOLOGIES"), TxnCategory.GROCERIES, "Quick Commerce"),
        entry("Dunzo", listOf("DUNZO", "DUNZO DAILY", "DUNZO DIGITAL"), TxnCategory.GROCERIES, "Quick Commerce"),
        entry("JioMart", listOf("JIOMART", "JIO MART", "RELIANCE RETAIL JIOMART"), TxnCategory.GROCERIES, "Quick Commerce"),
        entry("DMart", listOf("DMART", "D MART", "AVENUE SUPERMARTS"), TxnCategory.GROCERIES, "Supermarket"),
        entry("Nature's Basket", listOf("NATURES BASKET", "NATURE S BASKET"), TxnCategory.GROCERIES, "Supermarket"),
        entry("Licious", listOf("LICIOUS", "DELIGHTFUL GOURMET"), TxnCategory.GROCERIES, "Fresh & Meat"),
        entry("FreshToHome", listOf("FRESHTOHOME", "FRESH TO HOME"), TxnCategory.GROCERIES, "Fresh & Meat"),
        entry("Country Delight", listOf("COUNTRY DELIGHT", "MOOFARM COUNTRY DELIGHT"), TxnCategory.GROCERIES, "Fresh & Meat"),
        entry("Milkbasket", listOf("MILKBASKET", "MILKBASKET GROCERY"), TxnCategory.GROCERIES, "Quick Commerce"),
        entry("Spencer's Retail", listOf("SPENCERS", "SPENCERS RETAIL"), TxnCategory.GROCERIES, "Supermarket"),
        entry("More Retail", listOf("MORE RETAIL", "MORE MEGASTORE"), TxnCategory.GROCERIES, "Supermarket"),
        entry("Reliance Fresh", listOf("RELIANCE FRESH", "RELIANCE FRESH SIGNATURE"), TxnCategory.GROCERIES, "Supermarket"),
        entry("Star Bazaar", listOf("STAR BAZAAR", "TRENT HYPERMARKET STAR BAZAAR"), TxnCategory.GROCERIES, "Supermarket"),

        // -------------------------------------------------------------------------- shopping
        entry("Amazon", listOf("AMZN", "AMAZON", "AMAZON PAY", "AMZN MKTP", "AMAZON SELLER", "CLICKTECH", "AMAZON SELLER SERVICES", "AMZN MKTP IN", "AMAZON.IN"), TxnCategory.SHOPPING, "E-commerce"),
        entry("Flipkart", listOf("FKRT", "FLIPKART", "FLIPKART INTERNET", "FKRT PRIVATE LIMITED"), TxnCategory.SHOPPING, "E-commerce"),
        entry("Myntra", listOf("MYNTRA", "MYNTRA DESIGNS", "MYNTRA JABONG"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),
        entry("Ajio", listOf("AJIO", "AJIO COM", "RELIANCE RETAIL AJIO"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),
        entry("Nykaa", listOf("NYKAA", "FSN E COMMERCE", "NYKAA FASHION"), TxnCategory.SHOPPING, "Beauty"),
        entry("Meesho", listOf("MEESHO", "FASHNEAR TECHNOLOGIES"), TxnCategory.SHOPPING, "E-commerce"),
        entry("Tata Cliq", listOf("TATA CLIQ", "TATACLIQ", "TATA UNISTORE"), TxnCategory.SHOPPING, "E-commerce"),
        entry("Croma", listOf("CROMA", "INFINITY RETAIL CROMA"), TxnCategory.SHOPPING, "Electronics"),
        entry("Reliance Digital", listOf("RELIANCE DIGITAL", "RELIANCE DIGITAL RETAIL"), TxnCategory.SHOPPING, "Electronics"),
        entry("Decathlon", listOf("DECATHLON", "DECATHLON SPORTS INDIA"), TxnCategory.SHOPPING, "Sports & Fitness"),
        entry("IKEA", listOf("IKEA", "IKEA INDIA", "IKEA HYDERABAD"), TxnCategory.SHOPPING, "Home & Furniture"),
        entry("Lenskart", listOf("LENSKART", "LENSKART SOLUTIONS"), TxnCategory.SHOPPING, "Eyewear"),
        entry("FirstCry", listOf("FIRSTCRY", "FIRSTCRY COM", "BROBOT TECHNOLOGIES"), TxnCategory.SHOPPING, "Kids & Baby"),
        entry("Pepperfry", listOf("PEPPERFRY", "TRENDSUTRA PLATFORM"), TxnCategory.SHOPPING, "Home & Furniture"),
        entry("Urban Company", listOf("URBAN COMPANY", "URBANCLAP", "UC HOME SOLUTIONS"), TxnCategory.HOME, "Home Services"),
        entry("Snapdeal", listOf("SNAPDEAL", "JASPER INFOTECH"), TxnCategory.SHOPPING, "E-commerce"),
        entry("ShopClues", listOf("SHOPCLUES", "CLUES NETWORK"), TxnCategory.SHOPPING, "E-commerce"),
        entry("Purplle", listOf("PURPLLE", "PURPLLE COM"), TxnCategory.SHOPPING, "Beauty"),
        entry("H&M", listOf("H AND M", "H&M HENNES"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),
        entry("Zara", listOf("ZARA", "INDITEX TRENT ZARA"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),
        entry("Westside", listOf("WESTSIDE", "TRENT LIMITED WESTSIDE"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),
        entry("Shoppers Stop", listOf("SHOPPERS STOP", "SHOPPERS STOP LTD"), TxnCategory.SHOPPING, "Fashion & Lifestyle"),

        // ---------------------------------------------------------------------------- travel
        entry("MakeMyTrip", listOf("MMT", "MAKEMYTRIP", "MAKE MY TRIP"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("Goibibo", listOf("GOIBIBO", "IBIBO GROUP"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("Cleartrip", listOf("CLEARTRIP", "CLEARTRIP PRIVATE"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("EaseMyTrip", listOf("EASEMYTRIP", "EASE MY TRIP", "EASY TRIP PLANNERS"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("IRCTC", listOf("IRCTC", "IRCTC RAIL", "INDIAN RAILWAY CATERING"), TxnCategory.TRAVEL, "Trains"),
        entry("IndiGo", listOf("INTERGLOBE", "INDIGO AIRLINES", "INTERGLOBE AVIATION"), TxnCategory.TRAVEL, "Flights"),
        entry("Air India", listOf("AIR INDIA", "AIR INDIA LIMITED"), TxnCategory.TRAVEL, "Flights"),
        entry("Vistara", listOf("VISTARA", "TATA SIA AIRLINES"), TxnCategory.TRAVEL, "Flights"),
        entry("SpiceJet", listOf("SPICEJET", "SPICEJET LIMITED"), TxnCategory.TRAVEL, "Flights"),
        entry("Akasa Air", listOf("AKASA AIR", "AKASA"), TxnCategory.TRAVEL, "Flights"),
        entry("RedBus", listOf("REDBUS", "IBIBO REDBUS"), TxnCategory.TRAVEL, "Buses"),
        entry("AbhiBus", listOf("ABHIBUS", "ABHIBUS SERVICES"), TxnCategory.TRAVEL, "Buses"),
        entry("Ola", listOf("OLA", "OLA CABS", "ANI TECHNOLOGIES"), TxnCategory.TRANSPORT, "Cabs"),
        entry("Uber", listOf("UBER", "UBER INDIA", "UBER TRIP"), TxnCategory.TRANSPORT, "Cabs"),
        entry("Rapido", listOf("RAPIDO", "ROPPEN TRANSPORTATION"), TxnCategory.TRANSPORT, "Bike Taxi"),
        entry("OYO", listOf("OYO", "OYO ROOMS", "OYO HOTELS"), TxnCategory.TRAVEL, "Hotels"),
        entry("Airbnb", listOf("AIRBNB", "AIRBNB PAYMENTS"), TxnCategory.TRAVEL, "Hotels"),
        entry("Booking.com", listOf("BOOKING COM", "BOOKING COM BV"), TxnCategory.TRAVEL, "Hotels"),
        entry("Treebo", listOf("TREEBO", "TREEBO HOTELS"), TxnCategory.TRAVEL, "Hotels"),
        entry("Yatra", listOf("YATRA", "YATRA ONLINE"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("ixigo", listOf("IXIGO", "LE TRAVENUES TECHNOLOGY"), TxnCategory.TRAVEL, "Booking Aggregator"),
        entry("Trainman", listOf("TRAINMAN", "SEAT SEER TECHNOLOGIES"), TxnCategory.TRAVEL, "Trains"),
        entry("Zoomcar", listOf("ZOOMCAR", "ZOOMCAR INDIA"), TxnCategory.TRANSPORT, "Self-Drive Rental"),
        entry("Revv", listOf("REVV", "WORK MOBILE INDIA REVV"), TxnCategory.TRANSPORT, "Self-Drive Rental"),

        // ----------------------------------------------------------------------------- fuel
        entry("Indian Oil", listOf("IOCL", "INDIAN OIL", "IOC PETROL PUMP"), TxnCategory.FUEL, "Petrol Pump"),
        entry("HP Petrol Pump", listOf("HPCL", "HP PETROL PUMP", "HINDUSTAN PETROLEUM"), TxnCategory.FUEL, "Petrol Pump"),
        entry("Bharat Petroleum", listOf("BPCL", "BHARAT PETROLEUM", "BP PETROL PUMP"), TxnCategory.FUEL, "Petrol Pump"),
        entry("Shell", listOf("SHELL", "SHELL INDIA", "SHELL PETROL"), TxnCategory.FUEL, "Petrol Pump"),
        entry("Nayara Energy", listOf("NAYARA", "NAYARA ENERGY"), TxnCategory.FUEL, "Petrol Pump"),
        entry("Jio-bp", listOf("JIO BP", "JIOBP", "RELIANCE BP MOBILITY"), TxnCategory.FUEL, "Petrol Pump"),

        // ------------------------------------------------------------------- utilities / telecom
        entry("Airtel", listOf("AIRTEL", "BHARTI AIRTEL", "AIRTEL PREPAID", "AIRTEL POSTPAID"), TxnCategory.UTILITIES, "Mobile", isBill = true),
        entry("Jio", listOf("JIO", "RELIANCE JIO", "JIO PREPAID", "MY JIO"), TxnCategory.UTILITIES, "Mobile", isBill = true),
        entry("Vi (Vodafone Idea)", listOf("VI PREPAID", "VODAFONE IDEA", "VODAFONE"), TxnCategory.UTILITIES, "Mobile", isBill = true),
        entry("BSNL", listOf("BSNL", "BHARAT SANCHAR NIGAM"), TxnCategory.UTILITIES, "Mobile", isBill = true),
        entry("ACT Fibernet", listOf("ACT FIBERNET", "ATRIA CONVERGENCE"), TxnCategory.UTILITIES, "Broadband", isBill = true),
        entry("Hathway", listOf("HATHWAY", "HATHWAY CABLE"), TxnCategory.UTILITIES, "Broadband", isBill = true),
        entry("Tata Play", listOf("TATA PLAY", "TATA SKY", "TATA PLAY LIMITED"), TxnCategory.UTILITIES, "DTH", isBill = true),
        entry("Dish TV", listOf("DISH TV", "DISH TV INDIA"), TxnCategory.UTILITIES, "DTH", isBill = true),
        entry("BESCOM", listOf("BESCOM", "BANGALORE ELECTRICITY"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("MSEDCL", listOf("MSEDCL", "MSEB", "MAHARASHTRA STATE ELECTRICITY"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("TNEB", listOf("TNEB", "TAMIL NADU ELECTRICITY"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("KSEB", listOf("KSEB", "KERALA STATE ELECTRICITY"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("Adani Electricity", listOf("ADANI ELECTRICITY", "ADANI ELECTRICITY MUMBAI"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("Tata Power", listOf("TATA POWER", "TATA POWER DELHI"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("Torrent Power", listOf("TORRENT POWER", "TORRENT POWER LTD"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("BSES", listOf("BSES", "BSES RAJDHANI", "BSES YAMUNA"), TxnCategory.UTILITIES, "Electricity", isBill = true),
        entry("Mahanagar Gas", listOf("MAHANAGAR GAS", "MGL PIPED GAS"), TxnCategory.UTILITIES, "Gas", isBill = true),
        entry("Indraprastha Gas", listOf("INDRAPRASTHA GAS", "IGL PIPED GAS"), TxnCategory.UTILITIES, "Gas", isBill = true),
        entry("Indane", listOf("INDANE", "INDANE GAS", "INDIAN OIL INDANE"), TxnCategory.UTILITIES, "Gas", isBill = true),
        entry("HP Gas", listOf("HP GAS", "HPCL GAS", "HINDUSTAN PETROLEUM GAS"), TxnCategory.UTILITIES, "Gas", isBill = true),
        entry("Airtel Xstream", listOf("AIRTEL XSTREAM", "AIRTEL XSTREAM FIBER"), TxnCategory.UTILITIES, "Broadband", isBill = true),
        entry("JioFiber", listOf("JIOFIBER", "JIO FIBER"), TxnCategory.UTILITIES, "Broadband", isBill = true),
        entry("Excitel", listOf("EXCITEL", "EXCITEL BROADBAND"), TxnCategory.UTILITIES, "Broadband", isBill = true),

        // ------------------------------------------------------------------------ subscriptions
        entry("Netflix", listOf("NETFLIX", "NETFLIX COM"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("Amazon Prime", listOf("AMAZON PRIME", "PRIME VIDEO", "AMAZON PRIME MEMBERSHIP"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("Spotify", listOf("SPOTIFY", "SPOTIFY INDIA"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("YouTube Premium", listOf("YOUTUBE PREMIUM", "GOOGLE YOUTUBE PREMIUM"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("Google One", listOf("GOOGLE ONE", "GOOGLE STORAGE"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("Google Play", listOf("GOOGLE PLAY", "GOOGLE PLAY APPS", "GOOGLE PLAY IN"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Apple", listOf("APPLE COM BILL", "ITUNES", "APPLE SERVICES"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Microsoft", listOf("MICROSOFT", "MICROSOFT 365", "MSFT"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Adobe", listOf("ADOBE", "ADOBE SYSTEMS", "ADOBE CREATIVE CLOUD"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Notion", listOf("NOTION", "NOTION LABS"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Figma", listOf("FIGMA", "FIGMA INC"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("GitHub", listOf("GITHUB", "GITHUB INC"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("OpenAI / ChatGPT", listOf("OPENAI", "CHATGPT", "OPENAI CHATGPT"), TxnCategory.SUBSCRIPTIONS, "AI Tools", isSubscription = true),
        entry("Anthropic / Claude", listOf("ANTHROPIC", "CLAUDE AI", "ANTHROPIC PBC"), TxnCategory.SUBSCRIPTIONS, "AI Tools", isSubscription = true),
        entry("Google Gemini", listOf("GOOGLE GEMINI", "GEMINI ADVANCED"), TxnCategory.SUBSCRIPTIONS, "AI Tools", isSubscription = true),
        entry("Canva", listOf("CANVA", "CANVA PTY"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),
        entry("Dropbox", listOf("DROPBOX", "DROPBOX INC"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("iCloud", listOf("ICLOUD", "APPLE ICLOUD"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("LinkedIn Premium", listOf("LINKEDIN", "LINKEDIN PREMIUM"), TxnCategory.SUBSCRIPTIONS, "Professional", isSubscription = true),
        entry("Coursera", listOf("COURSERA", "COURSERA INC"), TxnCategory.SUBSCRIPTIONS, "Online Learning", isSubscription = true),
        entry("Udemy", listOf("UDEMY", "UDEMY INC"), TxnCategory.SUBSCRIPTIONS, "Online Learning", isSubscription = true),
        entry("Hotstar / JioCinema", listOf("HOTSTAR", "DISNEY HOTSTAR", "JIOCINEMA", "JIO CINEMA"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("SonyLIV", listOf("SONYLIV", "SONY LIV"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("ZEE5", listOf("ZEE5", "ZEE5 INDIA"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("Audible", listOf("AUDIBLE", "AUDIBLE INC"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("Kindle", listOf("KINDLE", "AMAZON KINDLE"), TxnCategory.SUBSCRIPTIONS, "Streaming & Media", isSubscription = true),
        entry("AWS", listOf("AWS", "AMAZON WEB SERVICES"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("DigitalOcean", listOf("DIGITALOCEAN", "DIGITAL OCEAN"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("Cloudflare", listOf("CLOUDFLARE", "CLOUDFLARE INC"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("GoDaddy", listOf("GODADDY", "GODADDY COM"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("Namecheap", listOf("NAMECHEAP", "NAMECHEAP INC"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("Hostinger", listOf("HOSTINGER", "HOSTINGER INTERNATIONAL"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("Vercel", listOf("VERCEL", "VERCEL INC"), TxnCategory.SUBSCRIPTIONS, "Cloud & Hosting", isSubscription = true),
        entry("JetBrains", listOf("JETBRAINS", "JETBRAINS SRO"), TxnCategory.SUBSCRIPTIONS, "Software", isSubscription = true),

        // -------------------------------------------------------------------------- healthcare
        entry("Apollo", listOf("APOLLO PHARMACY", "APOLLO HOSPITALS", "APOLLO 24 7"), TxnCategory.HEALTHCARE, "Pharmacy & Hospital"),
        entry("PharmEasy", listOf("PHARMEASY", "API HOLDINGS PHARMEASY"), TxnCategory.HEALTHCARE, "Pharmacy"),
        entry("Tata 1mg", listOf("TATA 1MG", "1MG", "TATA1MG TECHNOLOGIES"), TxnCategory.HEALTHCARE, "Pharmacy"),
        entry("Netmeds", listOf("NETMEDS", "NETMEDS MARKETPLACE"), TxnCategory.HEALTHCARE, "Pharmacy"),
        entry("Practo", listOf("PRACTO", "PRACTO TECHNOLOGIES"), TxnCategory.HEALTHCARE, "Telehealth"),
        entry("Cult.fit", listOf("CULTFIT", "CULT FIT", "CUREFIT HEALTHCARE"), TxnCategory.HEALTHCARE, "Fitness"),
        entry("Dr Lal PathLabs", listOf("DR LAL PATHLABS", "LAL PATHLABS"), TxnCategory.HEALTHCARE, "Diagnostics"),
        entry("Thyrocare", listOf("THYROCARE", "THYROCARE TECHNOLOGIES"), TxnCategory.HEALTHCARE, "Diagnostics"),
        entry("Max Healthcare", listOf("MAX HEALTHCARE", "MAX SUPER SPECIALITY"), TxnCategory.HEALTHCARE, "Hospital"),
        entry("Fortis Healthcare", listOf("FORTIS HEALTHCARE", "FORTIS HOSPITAL"), TxnCategory.HEALTHCARE, "Hospital"),
        entry("Manipal Hospitals", listOf("MANIPAL HOSPITALS", "MANIPAL HEALTH"), TxnCategory.HEALTHCARE, "Hospital"),
        entry("Medplus", listOf("MEDPLUS", "MEDPLUS HEALTH SERVICES"), TxnCategory.HEALTHCARE, "Pharmacy"),
        entry("Portea", listOf("PORTEA", "PORTEA MEDICAL"), TxnCategory.HEALTHCARE, "Home Healthcare"),
        entry("HealthifyMe", listOf("HEALTHIFYME", "HEALTHIFY ME"), TxnCategory.HEALTHCARE, "Fitness"),

        // ------------------------------------------------------------------------ investments
        entry("Zerodha", listOf("ZERODHA", "ZERODHA BROKING"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("Groww", listOf("GROWW", "NEXTBILLION TECHNOLOGY GROWW"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("Upstox", listOf("UPSTOX", "RKSV SECURITIES UPSTOX"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("Angel One", listOf("ANGEL ONE", "ANGEL BROKING"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("ICICI Direct", listOf("ICICIDIRECT", "ICICI DIRECT", "ICICI SECURITIES"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("HDFC Securities", listOf("HDFC SECURITIES", "HDFC SEC"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("Kuvera", listOf("KUVERA", "KUVERA IN"), TxnCategory.INVESTMENTS, "Mutual Fund Platform", isInvestment = true),
        entry("Zerodha Coin", listOf("COIN ZERODHA", "ZERODHA COIN"), TxnCategory.INVESTMENTS, "Mutual Fund Platform", isInvestment = true),
        entry("INDmoney", listOf("INDMONEY", "IND MONEY"), TxnCategory.INVESTMENTS, "Mutual Fund Platform", isInvestment = true),
        entry("Paytm Money", listOf("PAYTM MONEY", "PAYTM MONEY LIMITED"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("CAMS", listOf("CAMS", "COMPUTER AGE MANAGEMENT"), TxnCategory.INVESTMENTS, "Registrar & Transfer Agent", isInvestment = true),
        entry("KFintech", listOf("KFINTECH", "KARVY FINTECH"), TxnCategory.INVESTMENTS, "Registrar & Transfer Agent", isInvestment = true),
        entry("NSDL", listOf("NSDL", "NATIONAL SECURITIES DEPOSITORY"), TxnCategory.INVESTMENTS, "Depository", isInvestment = true),
        entry("CDSL", listOf("CDSL", "CENTRAL DEPOSITORY SERVICES"), TxnCategory.INVESTMENTS, "Depository", isInvestment = true),
        entry("SBI Mutual Fund", listOf("SBI MUTUAL FUND", "SBI FUNDS MANAGEMENT"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("HDFC AMC", listOf("HDFC AMC", "HDFC ASSET MANAGEMENT"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("ICICI Prudential AMC", listOf("ICICI PRUDENTIAL AMC", "ICICI PRUDENTIAL MUTUAL FUND"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("Axis AMC", listOf("AXIS AMC", "AXIS MUTUAL FUND"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("Nippon India Mutual Fund", listOf("NIPPON INDIA", "NIPPON INDIA MUTUAL FUND"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("Mirae Asset", listOf("MIRAE ASSET", "MIRAE ASSET MUTUAL FUND"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("Parag Parikh Mutual Fund", listOf("PARAG PARIKH", "PPFAS MUTUAL FUND"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("Quant Mutual Fund", listOf("QUANT MUTUAL FUND", "QUANT MONEY MANAGERS"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("UTI Mutual Fund", listOf("UTI MUTUAL FUND", "UTI AMC"), TxnCategory.INVESTMENTS, "AMC", isInvestment = true),
        entry("NPS Trust", listOf("NPS TRUST", "NATIONAL PENSION SYSTEM TRUST"), TxnCategory.INVESTMENTS, "Pension", isInvestment = true),
        entry("Motilal Oswal", listOf("MOTILAL OSWAL", "MOTILAL OSWAL FINANCIAL"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("Sharekhan", listOf("SHAREKHAN", "SHAREKHAN BNP PARIBAS"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("5paisa", listOf("5PAISA", "5PAISA CAPITAL"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),
        entry("IIFL Securities", listOf("IIFL", "IIFL SECURITIES"), TxnCategory.INVESTMENTS, "Broking", isInvestment = true),

        // -------------------------------------------------------------------------- insurance
        entry("LIC", listOf("LIC", "LIFE INSURANCE CORPORATION"), TxnCategory.INSURANCE, "Life Insurance", isBill = true),
        entry("HDFC Life", listOf("HDFC LIFE", "HDFC LIFE INSURANCE"), TxnCategory.INSURANCE, "Life Insurance", isBill = true),
        entry("ICICI Lombard", listOf("ICICI LOMBARD", "ICICI LOMBARD GIC"), TxnCategory.INSURANCE, "General Insurance", isBill = true),
        entry("ICICI Prudential Life", listOf("ICICI PRUDENTIAL LIFE", "ICICI PRU LIFE"), TxnCategory.INSURANCE, "Life Insurance", isBill = true),
        entry("Bajaj Allianz", listOf("BAJAJ ALLIANZ", "BAJAJ ALLIANZ GENERAL"), TxnCategory.INSURANCE, "General Insurance", isBill = true),
        entry("SBI Life", listOf("SBI LIFE", "SBI LIFE INSURANCE"), TxnCategory.INSURANCE, "Life Insurance", isBill = true),
        entry("Star Health", listOf("STAR HEALTH", "STAR HEALTH INSURANCE"), TxnCategory.INSURANCE, "Health Insurance", isBill = true),
        entry("Niva Bupa", listOf("NIVA BUPA", "MAX BUPA"), TxnCategory.INSURANCE, "Health Insurance", isBill = true),
        entry("Acko", listOf("ACKO", "ACKO GENERAL INSURANCE"), TxnCategory.INSURANCE, "General Insurance", isBill = true),
        entry("Digit Insurance", listOf("GO DIGIT", "GO DIGIT GENERAL INSURANCE"), TxnCategory.INSURANCE, "General Insurance", isBill = true),
        entry("PolicyBazaar", listOf("POLICYBAZAAR", "PB FINTECH"), TxnCategory.INSURANCE, "Aggregator"),
        entry("Tata AIG", listOf("TATA AIG", "TATA AIG GENERAL INSURANCE"), TxnCategory.INSURANCE, "General Insurance", isBill = true),
        entry("Max Life", listOf("MAX LIFE", "MAX LIFE INSURANCE"), TxnCategory.INSURANCE, "Life Insurance", isBill = true),

        // ----------------------------------------------------------------------- entertainment
        entry("BookMyShow", listOf("BOOKMYSHOW", "BIGTREE ENTERTAINMENT"), TxnCategory.ENTERTAINMENT, "Movies"),
        entry("PVR INOX", listOf("PVR CINEMAS", "PVR LIMITED", "PVR INOX"), TxnCategory.ENTERTAINMENT, "Movies"),
        entry("INOX", listOf("INOX LEISURE", "INOX CINEMAS"), TxnCategory.ENTERTAINMENT, "Movies"),
        entry("Cinepolis", listOf("CINEPOLIS", "CINEPOLIS INDIA"), TxnCategory.ENTERTAINMENT, "Movies"),
        entry("Paytm Insider", listOf("INSIDER", "PAYTM INSIDER"), TxnCategory.ENTERTAINMENT, "Events"),

        // --------------------------------------------------------------------------- education
        entry("Byju's", listOf("BYJUS", "THINK AND LEARN BYJUS"), TxnCategory.EDUCATION, "Edtech"),
        entry("Unacademy", listOf("UNACADEMY", "SORTING HAT TECHNOLOGIES"), TxnCategory.EDUCATION, "Edtech"),
        entry("Vedantu", listOf("VEDANTU", "VEDANTU INNOVATIONS"), TxnCategory.EDUCATION, "Edtech"),
        entry("upGrad", listOf("UPGRAD", "UPGRAD EDUCATION"), TxnCategory.EDUCATION, "Edtech"),
        entry("Physics Wallah", listOf("PHYSICS WALLAH", "PW EDTECH"), TxnCategory.EDUCATION, "Edtech"),
        entry("Great Learning", listOf("GREAT LEARNING", "GREATLEARNING"), TxnCategory.EDUCATION, "Edtech"),
        entry("Scaler", listOf("SCALER", "INTERVIEWBIT SCALER"), TxnCategory.EDUCATION, "Edtech"),

        // ------------------------------------------------------------------- government / tax
        entry("Income Tax Department", listOf("INCOME TAX", "TIN NSDL", "CPC INCOME TAX", "ITD CPC"), TxnCategory.TAXES, "Income Tax"),
        entry("GSTN", listOf("GSTN", "GST PAYMENT", "GOODS AND SERVICES TAX"), TxnCategory.TAXES, "GST"),
        entry("EPFO", listOf("EPFO", "EMPLOYEES PROVIDENT FUND ORGANISATION"), TxnCategory.GOVERNMENT, "Provident Fund"),
        entry("Passport Seva", listOf("PASSPORT SEVA", "PASSPORT SEVA KENDRA"), TxnCategory.GOVERNMENT, "Passport"),
        entry(
            "Bharat BillPay",
            listOf("BHARAT BILLPAY", "BBPS", "NPCI BBPS"),
            TxnCategory.MISC,
            "Bill Aggregator",
            isPassThrough = true,
        ),
        entry("Municipal Corporation", listOf("MUNICIPAL CORPORATION", "MCGM", "BBMP PROPERTY TAX"), TxnCategory.GOVERNMENT, "Municipal Services"),

        // ---------------------------------------------------------- payment intermediaries (pass-through)
        // These are rails, not merchants: the resolver strips them and re-resolves the remainder
        // of the string against the rest of the catalogue (see MerchantResolver.resolve KDoc).
        entry("Razorpay", listOf("RAZORPAY", "RAZORPAY SOFTWARE"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("BillDesk", listOf("BILLDESK", "BILLDESK PAYMENT"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("CCAvenue", listOf("CCAVENUE", "CC AVENUE", "AVENUES INDIA"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("PayU", listOf("PAYU", "PAYU PAYMENTS", "PAYU INDIA"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("Cashfree", listOf("CASHFREE", "CASHFREE PAYMENTS"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("Instamojo", listOf("INSTAMOJO", "INSTAMOJO TECHNOLOGIES"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("PhonePe", listOf("PHONEPE", "PHONEPE PRIVATE LIMITED"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("Paytm", listOf("PAYTM", "ONE97 COMMUNICATIONS", "PAYTM PAYMENTS BANK"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("BharatPe", listOf("BHARATPE", "RESILIENT INNOVATIONS"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("Pine Labs", listOf("PINE LABS", "PINE LABS PRIVATE LIMITED"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
        entry("Juspay", listOf("JUSPAY", "JUSPAY TECHNOLOGIES"), TxnCategory.MISC, "Payment Gateway", isPassThrough = true),
    )
}
