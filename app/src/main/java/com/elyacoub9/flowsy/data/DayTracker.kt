package com.elyacoub9.flowsy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Today's date as a flow. Refreshed on every app resume so screens follow date changes. */
class DayTracker(private val today: () -> LocalDate) {
    private val _day = MutableStateFlow(today())
    val day: StateFlow<LocalDate> = _day.asStateFlow()

    fun refresh() {
        _day.value = today()
    }
}
