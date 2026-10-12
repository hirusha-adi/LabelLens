package com.hirusha.labellens.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {
    private val catalogue = listOf(
        Additive("E102", "Tartrazine", status = "avoid", groups = listOf("artificial_colours"), sensitivities = listOf("children")),
        Additive("E110", "Sunset yellow FCF", status = "avoid"),
        Additive("E211", "Sodium benzoate", status = "caution", sensitivities = listOf("children")),
        Additive("E330", "Citric acid"),
        Additive("E331", "Sodium citrates", aliases = listOf("Sodium citrate")),
        Additive("E441", "Gelatine", origin = "animal"),
        Additive("E621", "Monosodium glutamate", shortName = "MSG", status = "caution", aliases = listOf("MSG")),
        Additive("E160a", "Beta-carotene"),
    )

    private val label = "INGREDIENTS: Sugar, glucose syrup, wheat flour, water, palm oil, citric acid (E330), " +
        "colours: tartrazine (E102), sunset yelow FCF, preservative: sodium benzoate (E 211), " +
        "flavour enhancer: monosodium glutamate. May contain traces of milk and soy. " +
        "Nutrition per 100 g: energy 1490 kJ / 352 kcal"

    @Test
    fun findsCodesHoweverTheyAreSpaced() {
        val result = Matcher.scan(label, catalogue)
        assertEquals(listOf("E330", "E102", "E211"), result.codeMatches.map { it.additive.code })
        assertEquals("E 211", result.codeMatches.last().readAs)
    }

    @Test
    fun findsNamesIncludingMisreadOnes() {
        val byCode = Matcher.scan(label, catalogue).nameMatches.associateBy { it.additive.code }
        assertEquals(setOf("E110", "E621"), byCode.keys)
        assertEquals(100, byCode.getValue("E621").percent)
        assertEquals("sunset yelow FCF", byCode.getValue("E110").readAs)
        assertTrue(byCode.getValue("E110").percent!! in 85..99)
    }

    @Test
    fun reportsNumbersThatAreNotCodes() {
        assertEquals(listOf("100", "1490", "352"), Matcher.scan(label, catalogue).otherNumbers)
    }

    @Test
    fun codeWithLetterSuffixAndInsPrefix() {
        val result = Matcher.scan("colour (E160a), acid INS 330", catalogue)
        assertEquals(listOf("E160a", "E330"), result.codeMatches.map { it.additive.code })
    }

    @Test
    fun plainTextMatchesNothing() {
        assertTrue(Matcher.scan("Oats, water, salt.", catalogue).all.isEmpty())
    }

    @Test
    fun searchIgnoresSpacingAndUnderstandsIns() {
        assertEquals(listOf("E330"), Matcher.search("ins 330", catalogue).map { it.code })
        assertEquals(listOf("E330"), Matcher.search("e-330", catalogue).map { it.code })
        assertEquals(listOf("E621"), Matcher.search("msg", catalogue).map { it.code })
        assertTrue(Matcher.search("E9999", catalogue).isEmpty())
    }

    @Test
    fun personalChecksFollowTheProfile() {
        val options = PreferenceOptions(
            diets = listOf(DietOption("vegan", "Vegan", "eco", "animal")),
            groups = listOf(GroupOption("artificial_colours", "Artificial colours", "palette", "a colour you always avoid", "a colour you avoid")),
            sensitivities = listOf(SensitivityOption("children", "Children", icon = "child_care", reason = "stricter for children")),
        )
        val prefs = Preferences(
            allergies = listOf(Allergy("Milk", "warn"), Allergy("Peanuts")),
            diet = "vegan",
            groups = mapOf("artificial_colours" to "avoid"),
            sensitivities = listOf("children"),
        )
        fun check(code: String) = Verdicts.personal(catalogue.first { it.code == code }, prefs, options)
        assertEquals(Verdict.AVOID, check("E102").verdict)
        assertEquals("a colour you avoid", check("E102").reason)
        assertEquals(Verdict.AVOID, check("E211").verdict)
        assertEquals("stricter for children", check("E211").reason)
        assertEquals(Verdict.AVOID, check("E441").verdict)
        assertEquals(Verdict.SAFE, check("E330").verdict)
        assertEquals(null, check("E330").reason)
        assertEquals(listOf("Milk"), Verdicts.allergens(label, prefs).map { it.title })
    }
}
