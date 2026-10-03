package com.elyacoub9.flowsy.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elyacoub9.flowsy.AppContainer
import com.elyacoub9.flowsy.FlowsyApp
import com.elyacoub9.flowsy.MainActivity
import com.elyacoub9.flowsy.R
import com.elyacoub9.flowsy.data.db.FlowsyDatabase
import com.elyacoub9.flowsy.data.db.SettingsEntity
import com.elyacoub9.flowsy.ui.common.formatMonth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Runs the real app (MainActivity, NavHost, ViewModels) against an in-memory database and a fixed date
 * (12 Oct 2026), never the phone's data. Records exist only in October and the app was installed in
 * October, as on the developer's phone.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FlowsyApp
    private lateinit var realContainer: AppContainer
    private lateinit var db: FlowsyDatabase
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var strings: (Int) -> String
    private lateinit var locale: Locale

    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    @Before
    fun setUp() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(app, FlowsyDatabase::class.java).addCallback(FlowsyDatabase.SeedOnCreate).build()
        db.settingsDao().upsert(SettingsEntity(currencyCode = "MAD", languageTag = "en", onboardingDone = true))
        realContainer = app.container
        app.container = AppContainer(
            context = app,
            databaseFactory = { db },
            clock = { Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.systemDefault()) },
            installDateOverride = LocalDate.of(2026, 10, 2),
        )
        app.container.incomeRepository.add("Salary", 500_000, LocalDate.of(2026, 10, 1), null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val resources = activity.resources
            strings = { id -> resources.getString(id) }
            locale = resources.configuration.locales[0]
        }
    }

    @After
    fun tearDown() {
        scenario.close()
        app.container = realContainer
        db.close()
    }

    private fun title(month: YearMonth) = formatMonth(month, locale)

    private fun waitFor(matcher: androidx.compose.ui.test.SemanticsMatcher) =
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun tapTab(label: Int) = compose.onNodeWithText(strings(label)).performClick()

    private fun assertOnYearScreen(year: String) {
        // The Year top bar is always on screen, unlike the (possibly scrolled) list.
        waitFor(hasContentDescription(strings(R.string.action_previous_year)))
        compose.onNodeWithText(year).assertExists()
    }

    private fun assertOnMonth(month: YearMonth) {
        waitFor(hasText(title(month)))
        compose.onNodeWithText(title(month)).assertExists()
    }

    private fun openCard(month: YearMonth) {
        val name = formatMonth(month, locale, withYear = false)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
    }

    @Test
    fun yearCard_opensThatMonth() {
        tapTab(R.string.nav_year)
        assertOnYearScreen("2026")
        openCard(september)
        assertOnMonth(september)
    }

    @Test
    fun reselectingYearTab_returnsToYearScreen_thenMonthTabShowsCurrentMonth() {
        tapTab(R.string.nav_year)
        assertOnYearScreen("2026")
        openCard(september)
        assertOnMonth(september)

        tapTab(R.string.nav_year)
        assertOnYearScreen("2026")
        compose.onNodeWithText(title(september)).assertDoesNotExist()

        tapTab(R.string.nav_month)
        assertOnMonth(october)
    }

    /** Opened month restored when coming back from another tab; reselecting Year still returns to the Year screen. */
    @Test
    fun restoredMonthFromYear_reselectingYearTab_returnsToYearScreen() {
        tapTab(R.string.nav_year)
        assertOnYearScreen("2026")
        openCard(september)
        assertOnMonth(september)

        tapTab(R.string.nav_month)
        assertOnMonth(october)
        tapTab(R.string.nav_year)
        assertOnMonth(september)

        tapTab(R.string.nav_year)
        assertOnYearScreen("2026")
        compose.onNodeWithText(title(september)).assertDoesNotExist()
    }

    @Test
    fun reselectingMonthTab_staysOnCurrentMonth() {
        assertOnMonth(october)
        tapTab(R.string.nav_month)
        assertOnMonth(october)
    }
}
