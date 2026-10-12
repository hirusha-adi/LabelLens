package app.labellens.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import app.labellens.data.Screen
import app.labellens.data.Store
import app.labellens.ui.screens.CropScreen
import app.labellens.ui.screens.DetailScreen
import app.labellens.ui.screens.MainScreen
import app.labellens.ui.screens.OnboardingScreen
import app.labellens.ui.screens.PrefsReviewScreen
import app.labellens.ui.screens.PrefsStepScreen
import app.labellens.ui.screens.ResultsScreen
import app.labellens.ui.screens.ScanSourceSheet
import app.labellens.ui.screens.SearchScreen
import app.labellens.ui.screens.SettingsScreen
import app.labellens.ui.theme.LabelLensTheme
import app.labellens.ui.theme.hg
import java.io.File
import kotlinx.coroutines.delay

/** Placeholder destinations for links that leave the app. */
object Links {
    const val REPOSITORY = "https://github.com/hirusha-adi/LabelLens"
    const val HELP = "$REPOSITORY#readme"
    const val LICENCE = "$REPOSITORY/blob/main/LICENSE"
    const val LEGAL = "$REPOSITORY/blob/main/LEGAL.md"
    const val CONTRIBUTING = "$REPOSITORY/blob/main/CONTRIBUTING.md"
    const val TERMS = "$REPOSITORY/blob/main/TERMS.md"
    const val PRIVACY = "$REPOSITORY/blob/main/PRIVACY.md"
    const val SUPPORT_EMAIL = "support@labellens.app"
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser installed. Nothing useful to do.
    }
}

@Composable
fun AppRoot(store: Store) {
    val dark = when (store.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    LabelLensTheme(dark) {
        CompositionLocalProvider(LocalStore provides store) {
            val screen = store.nav.current ?: return@CompositionLocalProvider
            // The crop and reading screens are camera UI and stay dark in both themes.
            SystemBarIcons(darkIcons = !dark && screen !is Screen.Crop)
            BackHandler(enabled = store.nav.stack.size > 1) { store.nav.pop() }

            val holder = rememberSaveableStateHolder()
            Box(Modifier.fillMaxSize().background(cs.background)) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "screen",
                ) { target ->
                    holder.SaveableStateProvider(target.toString()) { ScreenContent(target) }
                }
                ScanEntry(store)
                SnackbarLayer(store, raised = screen is Screen.Main)
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen) {
    when (screen) {
        is Screen.Onboarding -> OnboardingScreen(screen.replay)
        is Screen.PrefsStep -> PrefsStepScreen(screen.step)
        Screen.PrefsReview -> PrefsReviewScreen()
        Screen.Main -> MainScreen()
        Screen.Search -> SearchScreen()
        is Screen.Crop -> CropScreen(screen.uri)
        Screen.Results -> ResultsScreen()
        is Screen.Detail -> DetailScreen(screen.code)
        Screen.Settings -> SettingsScreen()
    }
}

@Composable
private fun SystemBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}

/** Owns the camera and gallery launchers, so a result still lands after the sheet has closed. */
@Composable
private fun ScanEntry(store: Store) {
    val context = LocalContext.current
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = captureUri
        if (taken && uri != null) store.nav.push(Screen.Crop(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) store.nav.push(Screen.Crop(uri.toString()))
    }
    if (!store.showScanSheet) return
    ScanSourceSheet(
        onDismiss = { store.showScanSheet = false },
        onCamera = {
            store.showScanSheet = false
            val file = File(File(context.cacheDir, "captures").apply { mkdirs() }, "label.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            captureUri = uri.toString()
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                store.snack("No camera app found. Choose a photo from your gallery instead.")
            }
        },
        onGallery = {
            store.showScanSheet = false
            try {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: ActivityNotFoundException) {
                store.snack("No gallery app found.")
            }
        },
    )
}

@Composable
private fun SnackbarLayer(store: Store, raised: Boolean) {
    val message = store.snackbar ?: return
    LaunchedEffect(message.id) {
        delay(if (message.actionLabel != null) 6000 else 3500)
        if (store.snackbar?.id == message.id) store.snackbar = null
    }
    Box(Modifier.fillMaxSize().imePadding().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (raised) 104.dp else 96.dp).fillMaxWidth().heightIn(min = 48.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(cs.inverseSurface)
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(message.text, hg(500, 14, 20), Modifier.weight(1f).padding(vertical = 12.dp), color = cs.inverseOnSurface)
            if (message.actionLabel != null) {
                TextAction(message.actionLabel, {
                    store.snackbar = null
                    message.onAction?.invoke()
                }, color = cs.inversePrimary, height = 40.dp)
            }
        }
    }
}
