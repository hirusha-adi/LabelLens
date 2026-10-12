package com.hirusha.labellens.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the matcher against the additive list that ships in the app, to catch names
 * that match ordinary label text.
 */
class RealCatalogueTest {
    private val catalogue: List<Additive> =
        AppJson.decodeFromString<Catalogue>(File("src/main/assets/catalogue.json").readText()).additives

    private fun codes(text: String) = Matcher.scan(text, catalogue).all.map { it.additive.code }.sorted()

    @Test
    fun plainFoodsMatchNothing() {
        val text = "INGREDIENTS: Whole grain oats, sugar, wheat flour, water, salt, palm oil, natural flavourings, skimmed milk powder, " +
            "cocoa butter, yeast, barley malt extract, sunflower oil, dried egg, vinegar, tomato puree, onion powder, garlic, spices, " +
            "raising agent, contains sodium and potassium. Packaged in a protective atmosphere. Store in a cool dry place. " +
            "Nutrition per 100 g: energy 1490 kJ, fat 12 g of which saturates 3 g, carbohydrate 60 g, protein 8 g, basic recipe, natural colour"
        assertEquals(emptyList<String>(), codes(text))
    }

    @Test
    fun aRealisticLabel() {
        val text = "Ingredients: Sugar, glucose syrup, water, gelatine, citric acid, acidity regulator: sodium citrate, " +
            "colours: tartrazine, sunset yelow FCF (E110), preservative: sodium benzoate (E 211), potassium sorbate, " +
            "flavour enhancer: monosodium glutamate, glazing agent: carnauba wax, emulsifier: soy lecithin, antioxidant: ascorbic acid, " +
            "thickener: xanthan gum, sweetener: aspartame, carbon dioxide, E160a, INS 330."
        val found = codes(text)
        println("MATCHED " + Matcher.scan(text, catalogue).all.joinToString { "${it.additive.code}=${it.readAs}(${it.percent ?: "code"})" })
        for (code in listOf("E102", "E110", "E211", "E202", "E621", "E903", "E300", "E415", "E951", "E290", "E160a", "E330", "E331", "E441")) {
            assertTrue("$code missing from $found", code in found)
        }
        // "sodium citrate" must not also be read as a misspelt "sodium nitrate".
        assertTrue("E251" !in found)
        assertTrue("unexpected extras in $found", found.size <= 16)
    }
}
