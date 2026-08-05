package com.lifeledger.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.ui.graphics.vector.ImageVector
import com.lifeledger.core.model.TxnCategory

/**
 * Every icon Life Ledger shows, keyed by app concept rather than picked ad hoc per screen.
 * Feature modules never `import androidx.compose.material.icons.filled.*` directly — that
 * keeps one file to check when an icon needs to change everywhere at once, and stops five
 * near-identical shopping-bag icons creeping into five different screens.
 */
object LlIcons {

    // Navigation & chrome
    val Home: ImageVector = Icons.Filled.Home
    val Transactions: ImageVector = Icons.AutoMirrored.Filled.List
    val Analytics: ImageVector = Icons.Filled.Insights
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Money: ImageVector = Icons.Filled.AccountBalanceWallet
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector = Icons.Filled.Close
    val Add: ImageVector = Icons.Filled.Add
    val Edit: ImageVector = Icons.Filled.Edit
    val Delete: ImageVector = Icons.Filled.Delete
    val Filter: ImageVector = Icons.Filled.FilterList
    val Tune: ImageVector = Icons.Filled.Tune
    val Calendar: ImageVector = Icons.Filled.CalendarMonth
    val CalendarDay: ImageVector = Icons.Filled.CalendarViewDay
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Sync: ImageVector = Icons.Filled.Sync

    // Status & feedback
    val Success: ImageVector = Icons.Filled.CheckCircle
    val Warning: ImageVector = Icons.Filled.Warning
    val Error: ImageVector = Icons.Filled.ErrorOutline
    val Empty: ImageVector = Icons.Outlined.Inbox
    val Notification: ImageVector = Icons.Filled.NotificationsActive

    // Money direction & concepts
    val Income: ImageVector = Icons.AutoMirrored.Filled.TrendingUp
    val Expense: ImageVector = Icons.AutoMirrored.Filled.TrendingDown
    val Transfer: ImageVector = Icons.Filled.SwapHoriz
    val Chart: ImageVector = Icons.AutoMirrored.Filled.ShowChart
    val Donut: ImageVector = Icons.Filled.PieChart
    val Timeline: ImageVector = Icons.Filled.Timeline
    val Account: ImageVector = Icons.Filled.AccountBalance
    val Card: ImageVector = Icons.Filled.CreditCard
    val Investment: ImageVector = Icons.Filled.Savings
    val Subscription: ImageVector = Icons.Filled.Subscriptions
    val Bill: ImageVector = Icons.Filled.Receipt
    val Merchant: ImageVector = Icons.Filled.Sell
    val More: ImageVector = Icons.Filled.MoreHoriz

    /** One icon per [TxnCategory], each paired with a tint from [LifeLedgerColors.categorical]. */
    fun forCategory(category: TxnCategory): ImageVector = when (category) {
        TxnCategory.INCOME -> Icons.Filled.MonetizationOn
        TxnCategory.FOOD -> Icons.Filled.Fastfood
        TxnCategory.GROCERIES -> Icons.Filled.ShoppingCart
        TxnCategory.SHOPPING -> Icons.Filled.ShoppingBag
        TxnCategory.TRAVEL -> Icons.Filled.Flight
        TxnCategory.TRANSPORT -> Icons.Filled.LocalTaxi
        TxnCategory.FUEL -> Icons.Filled.LocalGasStation
        TxnCategory.HEALTHCARE -> Icons.Filled.LocalHospital
        TxnCategory.ENTERTAINMENT -> Icons.Filled.MovieFilter
        TxnCategory.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
        TxnCategory.UTILITIES -> Icons.Filled.Bolt
        TxnCategory.RENT -> Icons.Filled.Home
        TxnCategory.EDUCATION -> Icons.Filled.MenuBook
        TxnCategory.INVESTMENTS -> Icons.Filled.Savings
        TxnCategory.INSURANCE -> Icons.Filled.Security
        TxnCategory.LOANS -> Icons.Filled.Gavel
        TxnCategory.TAXES -> Icons.Filled.Receipt
        TxnCategory.GOVERNMENT -> Icons.Filled.AccountBalance
        TxnCategory.CHARITY -> Icons.Filled.VolunteerActivism
        TxnCategory.PERSONAL_CARE -> Icons.Filled.Spa
        TxnCategory.HOME -> Icons.Filled.HomeRepairService
        TxnCategory.PETS -> Icons.Filled.Pets
        TxnCategory.GIFTS -> Icons.Filled.CardGiftcard
        TxnCategory.CASH -> Icons.Filled.AccountBalanceWallet
        TxnCategory.FEES -> Icons.Filled.Discount
        TxnCategory.TRANSFERS -> Icons.Filled.SwapHoriz
        TxnCategory.MISC -> Icons.Filled.Landscape
        TxnCategory.UNCATEGORIZED -> Icons.Filled.MoreHoriz
    }

    // Non-financial life events, matched against TimelineEventType in :core:model.
    val Otp: ImageVector = Icons.Filled.Security
    val Delivery: ImageVector = Icons.Filled.LocalShipping
    val Booking: ImageVector = Icons.Filled.EventAvailable
    val Appointment: ImageVector = Icons.Filled.Cake
    val Call: ImageVector = Icons.Filled.Call
    val Internet: ImageVector = Icons.Filled.Wifi
    val Water: ImageVector = Icons.Filled.WaterDrop
    val Wardrobe: ImageVector = Icons.Filled.Checkroom
    val Login: ImageVector = Icons.Filled.Login
    val Car: ImageVector = Icons.Filled.DirectionsCar
}
