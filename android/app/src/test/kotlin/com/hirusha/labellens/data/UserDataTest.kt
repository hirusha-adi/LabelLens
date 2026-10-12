package com.hirusha.labellens.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserDataTest {
    private val data = UserData(
        onboarded = true,
        theme = "dark",
        preferences = Preferences(
            allergies = listOf(Allergy("Milk", "warn"), Allergy("Kiwi", "avoid", custom = true)),
            diet = "vegan",
            groups = mapOf("artificial_colours" to "avoid"),
            additives = listOf("E951"),
            sensitivities = listOf("children"),
        ),
        list = listOf("E330", "E102"),
        saved = listOf("E621"),
        scans = listOf(Scan("s1", at = 1_760_000_000_000, verdict = "avoid", codes = listOf("E102"))),
    )

    @Test
    fun roundTripsThroughText() {
        assertEquals(data, AppJson.decodeFromString<UserData>(AppJson.encodeToString(data)))
    }

    @Test
    fun readsADocumentWrittenByALaterVersionWithExtraFields() {
        val text = AppJson.encodeToString(data).replaceFirst("{", "{\"somethingNew\":[1,2,3],")
        assertEquals(data, AppJson.decodeFromString<UserData>(text))
    }

    @Test
    fun missingFieldsFallBackToDefaults() {
        val read = AppJson.decodeFromString<UserData>("{\"list\":[\"E330\"]}")
        assertEquals(listOf("E330"), read.list)
        assertEquals("system", read.theme)
        assertEquals(Preferences(), read.preferences)
    }
}
