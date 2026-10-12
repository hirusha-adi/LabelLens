package app.labellens.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.labellens.data.Matcher
import app.labellens.data.Screen
import app.labellens.ui.FilledButton
import app.labellens.ui.LoadingShape
import app.labellens.ui.LocalStore
import app.labellens.ui.PlainIconButton
import app.labellens.ui.SegList
import app.labellens.ui.SegRow
import app.labellens.ui.Sym
import app.labellens.ui.T
import app.labellens.ui.TextAction
import app.labellens.ui.cs
import app.labellens.ui.statusColors
import app.labellens.ui.theme.gab
import app.labellens.ui.theme.hg
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
fun SheetHandle() {
    Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(32.dp, 4.dp).background(cs.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
}

/** C06: choose between the camera and the gallery. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSourceSheet(onDismiss: () -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = cs.surfaceContainerLow,
        contentColor = cs.onSurface,
        scrimColor = statusColors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            T("Scan a label", gab(800, 24, 30), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp))
            T(
                "Get the ingredients list in frame. Your photo stays on this phone.",
                hg(400, 15, 22), Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp), color = cs.onSurfaceVariant,
            )
            SegList {
                SegRow(0, 2, minHeight = 72.dp, gap = 16.dp, onClick = onCamera) {
                    Box(Modifier.size(48.dp).background(cs.primary, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Sym("photo_camera", size = 26.dp, tint = cs.onPrimary)
                    }
                    Column {
                        T("Take a photo", hg(700, 16, 22))
                        T("Best in good light, held flat", hg(400, 14, 20), color = cs.onSurfaceVariant)
                    }
                }
                SegRow(1, 2, minHeight = 72.dp, gap = 16.dp, onClick = onGallery) {
                    Box(Modifier.size(48.dp).background(cs.secondaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Sym("photo_library", size = 26.dp, tint = cs.onSecondaryContainer)
                    }
                    Column {
                        T("Choose from gallery", hg(700, 16, 22))
                        T("Use a photo you already took", hg(400, 14, 20), color = cs.onSurfaceVariant)
                    }
                }
            }
            Row(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("shield_lock", size = 18.dp, tint = cs.tertiary, fill = true)
                T("Read on-device. Nothing is uploaded.", hg(600, 13), color = cs.tertiary)
            }
        }
    }
}

// ---------- Crop and reading (camera UI, dark in both themes) ----------

private val CameraBackground = Color(0xFF0B0C09)
private val CameraText = Color(0xFFF1F2E5)
private val CameraMuted = Color(0xFFC5C8B8)
private val Lime = Color(0xFFBEF27A)

private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE, NONE }

private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > 2400) {
            val factor = 2400f / longest
            decoder.setTargetSize((info.size.width * factor).roundToInt(), (info.size.height * factor).roundToInt())
        }
    }
}

private suspend fun readText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { result ->
            recognizer.close()
            continuation.resume(result.textBlocks.joinToString(" ") { block -> block.lines.joinToString(" ") { it.text } })
        }
        .addOnFailureListener { error ->
            recognizer.close()
            continuation.resumeWithException(error)
        }
}

/** C07 and C08: crop the photo to the ingredients list, then read it on the phone. */
@Composable
fun CropScreen(uri: String) {
    val store = LocalStore.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    // Crop rectangle as fractions of the image.
    var crop by remember { mutableStateOf(Rect(0.06f, 0.2f, 0.94f, 0.8f)) }
    var reading by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(uri) {
        try {
            bitmap = loadBitmap(context, Uri.parse(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    fun read() {
        val source = bitmap ?: return
        reading = true
        job = scope.launch {
            try {
                val text = withContext(Dispatchers.Default) {
                    val left = (crop.left * source.width).roundToInt().coerceIn(0, source.width - 1)
                    val top = (crop.top * source.height).roundToInt().coerceIn(0, source.height - 1)
                    val width = (crop.width * source.width).roundToInt().coerceIn(1, source.width - left)
                    val height = (crop.height * source.height).roundToInt().coerceIn(1, source.height - top)
                    Bitmap.createBitmap(source, left, top, width, height)
                }.let { cropped ->
                    val started = System.currentTimeMillis()
                    val raw = readText(cropped)
                    // Keep the reading state on screen long enough to register.
                    delay((700 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                    raw.replace(Regex("\\s+"), " ").trim()
                }
                val result = withContext(Dispatchers.Default) { Matcher.scan(text, store.catalogue) }
                store.recordScan(result)
                store.nav.replace(Screen.Results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reading = false
                store.snack("Couldn't read that photo. Try again, or choose another one.")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(CameraBackground)) {
        val image = bitmap
        when {
            failed -> Column(
                Modifier.align(Alignment.Center).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                T("Couldn't open that photo", gab(800, 22, 28), color = CameraText, align = TextAlign.Center)
                T("It may have been moved or deleted. Go back and choose another.", hg(500, 14, 20), color = CameraMuted, align = TextAlign.Center)
            }
            image == null -> LoadingShape(48.dp, Lime, Modifier.align(Alignment.Center))
            else -> CropCanvas(image, crop, reading, onCropChange = { crop = it })
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            if (!reading) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    CameraIconButton("close", "Close") { store.nav.pop() }
                    Spacer(Modifier.weight(1f))
                    if (image != null) {
                        CameraIconButton("rotate_right", "Rotate") {
                            val matrix = Matrix().apply { postRotate(90f) }
                            bitmap = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
                            crop = Rect(0.06f, 0.2f, 0.94f, 0.8f)
                        }
                    }
                }
                if (image != null) {
                    Row(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).height(40.dp).clip(CircleShape)
                            .background(Color(0xFF2F3129)).padding(start = 12.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sym("crop", size = 20.dp, tint = CameraText)
                        T("Crop to the ingredients list", hg(600, 14), color = CameraText)
                    }
                }
            }
        }

        if (reading) {
            Column(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(64.dp).background(Color(0xFF2E5000), CircleShape), contentAlignment = Alignment.Center) {
                    LoadingShape(40.dp, Lime)
                }
                T("Reading label…", gab(800, 22, 28), color = CameraText)
                T("On this phone. Usually under 2 seconds.", hg(500, 14, 20), color = CameraMuted)
                Box(
                    Modifier.padding(top = 18.dp).fillMaxWidth().height(56.dp).clip(CircleShape).background(Color(0x1FE2E4D6))
                        .clickable(role = Role.Button) {
                            job?.cancel()
                            reading = false
                        },
                    contentAlignment = Alignment.Center,
                ) { T("Cancel", hg(700, 16), color = Color(0xCCE2E4D6)) }
            }
        } else if (image != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color(0xB3000000)))
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                T("Drag the corners. A tighter crop reads faster.", hg(500, 13, 18), Modifier.fillMaxWidth(), color = CameraMuted, align = TextAlign.Center)
                FilledButton(
                    "Read label", { read() }, Modifier.fillMaxWidth(), icon = "text_select_start",
                    container = Color(0xFFA2D663), content = Color(0xFF1F3700),
                )
            }
        }
    }
}

@Composable
private fun CameraIconButton(icon: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color(0x80000000)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Sym(icon, tint = CameraText) }
}

@Composable
private fun CropCanvas(bitmap: Bitmap, crop: Rect, reading: Boolean, onCropChange: (Rect) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "sweep",
    )
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val areaLeft = with(density) { 20.dp.toPx() }
        val areaTop = with(density) { 128.dp.toPx() }
        val areaWidth = with(density) { maxWidth.toPx() } - areaLeft * 2
        val areaHeight = with(density) { maxHeight.toPx() } - areaTop - with(density) { (if (reading) 250.dp else 150.dp).toPx() }
        val scale = minOf(areaWidth / bitmap.width, areaHeight / bitmap.height)
        val shown = Size(bitmap.width * scale, bitmap.height * scale)
        val origin = Offset(areaLeft + (areaWidth - shown.width) / 2, areaTop + (areaHeight - shown.height) / 2)
        val touch = with(density) { 36.dp.toPx() }
        val minSize = with(density) { 56.dp.toPx() }
        val current by androidx.compose.runtime.rememberUpdatedState(crop)

        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap, reading, shown, origin) {
                if (reading) return@pointerInput
                var handle = Handle.NONE
                detectDragGestures(
                    onDragStart = { position ->
                        val box = Rect(
                            origin.x + current.left * shown.width, origin.y + current.top * shown.height,
                            origin.x + current.right * shown.width, origin.y + current.bottom * shown.height,
                        )
                        fun near(corner: Offset) = (position - corner).getDistance() < touch
                        handle = when {
                            near(box.topLeft) -> Handle.TOP_LEFT
                            near(box.topRight) -> Handle.TOP_RIGHT
                            near(box.bottomLeft) -> Handle.BOTTOM_LEFT
                            near(box.bottomRight) -> Handle.BOTTOM_RIGHT
                            box.contains(position) -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        val dx = drag.x / shown.width
                        val dy = drag.y / shown.height
                        val minW = minSize / shown.width
                        val minH = minSize / shown.height
                        val c = current
                        val next = when (handle) {
                            Handle.TOP_LEFT -> Rect((c.left + dx).coerceIn(0f, c.right - minW), (c.top + dy).coerceIn(0f, c.bottom - minH), c.right, c.bottom)
                            Handle.TOP_RIGHT -> Rect(c.left, (c.top + dy).coerceIn(0f, c.bottom - minH), (c.right + dx).coerceIn(c.left + minW, 1f), c.bottom)
                            Handle.BOTTOM_LEFT -> Rect((c.left + dx).coerceIn(0f, c.right - minW), c.top, c.right, (c.bottom + dy).coerceIn(c.top + minH, 1f))
                            Handle.BOTTOM_RIGHT -> Rect(c.left, c.top, (c.right + dx).coerceIn(c.left + minW, 1f), (c.bottom + dy).coerceIn(c.top + minH, 1f))
                            Handle.MOVE -> {
                                val mx = dx.coerceIn(-c.left, 1f - c.right)
                                val my = dy.coerceIn(-c.top, 1f - c.bottom)
                                c.translate(mx, my)
                            }
                            Handle.NONE -> c
                        }
                        onCropChange(next)
                    },
                )
            },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                dstSize = IntSize(shown.width.roundToInt(), shown.height.roundToInt()),
            )
            val box = Rect(
                origin.x + crop.left * shown.width, origin.y + crop.top * shown.height,
                origin.x + crop.right * shown.width, origin.y + crop.bottom * shown.height,
            )
            val radius = if (reading) 12.dp.toPx() else 0f
            val window = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(box, CornerRadius(radius))) }
            clipPath(window, ClipOp.Difference) {
                drawRect(Color(0xFF050604).copy(alpha = if (reading) 0.78f else 0.62f), Offset(-size.width, -size.height), Size(size.width * 3, size.height * 3))
            }
            if (reading) {
                drawRoundRect(Lime, box.topLeft, box.size, CornerRadius(radius), style = Stroke(2.dp.toPx()))
                val y = box.top + box.height * sweep
                clipPath(window) {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Lime.copy(alpha = 0.18f)), box.top, y), box.topLeft, Size(box.width, y - box.top))
                    drawLine(Lime.copy(alpha = 0.35f), Offset(box.left, y), Offset(box.right, y), strokeWidth = 10.dp.toPx())
                    drawLine(Lime, Offset(box.left, y), Offset(box.right, y), strokeWidth = 2.dp.toPx())
                }
            } else {
                val line = Color.White.copy(alpha = 0.4f)
                for (i in 1..2) {
                    val x = box.left + box.width * i / 3
                    val gy = box.top + box.height * i / 3
                    drawLine(line, Offset(x, box.top), Offset(x, box.bottom), strokeWidth = 1.dp.toPx())
                    drawLine(line, Offset(box.left, gy), Offset(box.right, gy), strokeWidth = 1.dp.toPx())
                }
                val arm = 28.dp.toPx()
                val stroke = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val out = 2.dp.toPx()
                fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                    val px = x - sx * out
                    val py = y - sy * out
                    drawPath(Path().apply { moveTo(px + sx * arm, py); lineTo(px, py); lineTo(px, py + sy * arm) }, Lime, style = stroke)
                }
                corner(box.left, box.top, 1f, 1f)
                corner(box.right, box.top, -1f, 1f)
                corner(box.left, box.bottom, 1f, -1f)
                corner(box.right, box.bottom, -1f, -1f)
            }
        }
    }
}
