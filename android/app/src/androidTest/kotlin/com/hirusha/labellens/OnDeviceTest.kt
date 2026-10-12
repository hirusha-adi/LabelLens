package com.hirusha.labellens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hirusha.labellens.data.Matcher
import com.hirusha.labellens.data.Store
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that need a real device: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class OnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // These run with no server (api = null), so they exercise the copy of the data that
    // ships in the app: what a first launch with no connection gets.

    @Test
    fun readsALabelOnThePhoneAndFindsItsAdditives() {
        val lines = listOf(
            "INGREDIENTS: Sugar, glucose syrup, water,",
            "citric acid (E330), colour: tartrazine (E102),",
            "preservative: sodium benzoate (E 211),",
            "flavour enhancer: monosodium glutamate.",
        )
        val bitmap = Bitmap.createBitmap(1400, 420, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 54f }
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            lines.forEachIndexed { i, line -> drawText(line, 40f, 90f + i * 90f, paint) }
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 60, TimeUnit.SECONDS).text.replace("\n", " ")
        recognizer.close()
        assertTrue("Recognised: $text", text.contains("E330") && text.contains("E102"))

        val store = Store(context, api = null).also { it.start() }
        val found = Matcher.scan(text, store.catalogue).all.map { it.additive.code }
        for (code in listOf("E330", "E102", "E211", "E621")) assertTrue("$code missing from $found (text: $text)", code in found)
    }

    @Test
    fun theBundledCatalogueAndItsPagesLoad() = runBlocking {
        val store = Store(context, api = null).also { it.start() }
        assertTrue(store.catalogue.size > 500)
        assertTrue(store.options.allergens.isNotEmpty() && store.options.diets.isNotEmpty())
        for (additive in store.catalogue) assertNotNull("${additive.code} has no page", store.detail(additive.code))
        assertTrue(store.detail("E330")!!.sections.isNotEmpty())
    }

    @Test
    fun everythingSurvivesABackupAndRestore() {
        val store = Store(context, api = null).also { it.start() }
        store.addToList(listOf("E330", "E102"))
        store.toggleSaved("E621")
        store.chooseTheme("dark")
        val backup = store.exportData()

        store.clearList()
        store.toggleSaved("E621")
        store.chooseTheme("light")
        assertTrue(store.importData(backup))
        assertEquals(listOf("E330", "E102"), store.myList.takeLast(2))
        assertTrue("E621" in store.saved)
        assertEquals("dark", store.themeMode)

        // A second Store reads the same thing back from storage, as after an app restart.
        val reopened = Store(context, api = null).also { it.start() }
        assertEquals(store.myList, reopened.myList)
        assertEquals(store.saved, reopened.saved)

        assertFalse(store.importData("not a backup"))
        assertFalse(store.importData("{\"formatVersion\": 99}"))
    }
}
