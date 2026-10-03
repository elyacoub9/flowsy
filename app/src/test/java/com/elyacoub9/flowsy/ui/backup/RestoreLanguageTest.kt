package com.elyacoub9.flowsy.ui.backup

import com.elyacoub9.flowsy.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreLanguageTest {

    @Test
    fun knownTag_appliesThatLanguage() {
        assertEquals(AppLanguage.FRENCH, RestoreController.languageFor("fr"))
        assertEquals(AppLanguage.ENGLISH, RestoreController.languageFor("en-US"))
    }

    @Test
    fun blankMissingOrUnknown_followsThePhone() {
        assertNull(RestoreController.languageFor(null))
        assertNull(RestoreController.languageFor(""))
        assertNull(RestoreController.languageFor("   "))
        assertNull(RestoreController.languageFor("de"))
    }
}
