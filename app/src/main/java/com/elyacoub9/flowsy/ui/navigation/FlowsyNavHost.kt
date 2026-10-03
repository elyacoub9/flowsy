package com.elyacoub9.flowsy.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.ui.about.AboutScreen
import com.elyacoub9.flowsy.ui.month.MonthRoute
import com.elyacoub9.flowsy.ui.month.MonthViewModel
import com.elyacoub9.flowsy.ui.savings.PotDetailRoute
import com.elyacoub9.flowsy.ui.savings.PotDetailViewModel
import com.elyacoub9.flowsy.ui.savings.SavingsRoute
import com.elyacoub9.flowsy.ui.settings.CategoriesRoute
import com.elyacoub9.flowsy.ui.settings.SettingsRoute
import com.elyacoub9.flowsy.ui.theme.FlowsyTheme
import com.elyacoub9.flowsy.ui.year.YearRoute

enum class TopLevelDestination(
    val route: String,
    /** Route as registered in the NavHost (with optional arguments, or the nested graph). */
    val pattern: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    MONTH("month", "month?${MonthViewModel.MONTH_ARG}={${MonthViewModel.MONTH_ARG}}", R.string.nav_month, R.drawable.ic_calendar),
    YEAR(YEAR_GRAPH, YEAR_GRAPH, R.string.nav_year, R.drawable.ic_stats),
    SAVINGS("savings", "savings", R.string.nav_savings, R.drawable.ic_savings),
}

private const val SETTINGS_ROUTE = "settings"
private const val CATEGORIES_ROUTE = "categories"
private const val ABOUT_ROUTE = "about"
private const val POT_ROUTE = "pot/{potId}"
private const val YEAR_GRAPH = "year_graph"
private const val YEAR_ROUTE = "year"

/** A month opened from a Year card: Month screen with a back arrow, inside the Year tab. */
private const val YEAR_MONTH_ROUTE = "year/month?${MonthViewModel.MONTH_ARG}={${MonthViewModel.MONTH_ARG}}"

private val monthArgument = navArgument(MonthViewModel.MONTH_ARG) {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

@Composable
fun FlowsyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in
        setOf(TopLevelDestination.MONTH.pattern, YEAR_ROUTE, YEAR_MONTH_ROUTE, TopLevelDestination.SAVINGS.pattern)

    Scaffold(
        // Each screen handles its own top inset; this Scaffold only reserves the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    isSelected = { destination -> currentDestination?.hierarchy?.any { it.route == destination.pattern } == true },
                    onSelect = { destination ->
                        val reselected = currentDestination?.hierarchy?.any { it.route == destination.pattern } == true
                        // Reselecting the current tab goes back to its root screen (e.g. Year from a month opened there).
                        if (reselected) navController.popBackStack(destination.rootRoute, inclusive = false) else navController.navigateTopLevel(destination)
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.MONTH.pattern,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(
                TopLevelDestination.MONTH.pattern,
                arguments = listOf(monthArgument),
            ) {
                MonthRoute(onOpenSettings = { navController.navigate(SETTINGS_ROUTE) })
            }
            navigation(startDestination = YEAR_ROUTE, route = YEAR_GRAPH) {
                composable(YEAR_ROUTE) {
                    YearRoute(
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenMonth = { navController.navigate("year/month?${MonthViewModel.MONTH_ARG}=$it") },
                    )
                }
                composable(YEAR_MONTH_ROUTE, arguments = listOf(monthArgument)) {
                    MonthRoute(
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(TopLevelDestination.SAVINGS.route) {
                SavingsRoute(onOpenPot = { navController.navigate("pot/$it") })
            }
            composable(
                POT_ROUTE,
                arguments = listOf(navArgument(PotDetailViewModel.POT_ID) { type = NavType.LongType }),
            ) {
                PotDetailRoute(onBack = { navController.popBackStack() })
            }
            composable(SETTINGS_ROUTE) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onOpenCategories = { navController.navigate(CATEGORIES_ROUTE) },
                    onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
                )
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(CATEGORIES_ROUTE) {
                CategoriesRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Root screen of each tab. */
private val TopLevelDestination.rootRoute: String
    get() = if (this == TopLevelDestination.YEAR) YEAR_ROUTE else pattern

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = isSelected(destination),
                onClick = { onSelect(destination) },
                // The label is always shown, so the icon needs no separate description.
                icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Preview
@Composable
private fun BottomBarPreview() {
    FlowsyTheme { BottomBar(isSelected = { it == TopLevelDestination.MONTH }, onSelect = {}) }
}
