package com.nh.tilesmarbledesigner.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.tilesmarbledesigner.data.*
import com.nh.tilesmarbledesigner.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun CropScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    val src = vm.cropSource
    var quad by remember(src) { mutableStateOf(src?.let { CropEngine.defaultQuad(it) } ?: FloatArray(8)) }
    var zoom by remember(src) { mutableFloatStateOf(1f) }
    var pan by remember(src) { mutableStateOf(Offset.Zero) }
    var angle by remember(src) { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val b = withContext(Dispatchers.IO) { CropEngine.decode(ctx, uri) }
            if (b == null) vm.toast = "Could not open this image" else vm.cropSource = b
            busy = false
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ScreenFrame("Atomic Crop Editor", back) {
        if (src == null) {
            NHCard {
                SectionTitle("Import Surface Photo")
                Text(
                    "Import a photo of marble, ceramic tile, granite, or wood, then drag the four corners to isolate the surface. Perspective distortion is corrected automatically.",
                    fontSize = 13.sp,
                    color = pal.label
                )
                PrimaryButton(if (busy) "Opening…" else "Import Image from Gallery", enabled = !busy) { pick() }
            }
            return@ScreenFrame
        }
        val iw = src.width; val ih = src.height
        val cropShape = RoundedCornerShape(24.dp)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .softShadow(14.dp, cropShape, glow = true)
                .clip(cropShape)
                .background(Color(0xFF070B14))
                .border(1.5.dp, Brush.linearGradient(listOf(pal.vivid, pal.primary, pal.subAccent)), cropShape)
                .pointerInput(src) {
                    var drag = -1
                    detectDragGestures(
                        onDragStart = { o ->
                            val fit = min(size.width / iw.toFloat(), size.height / ih.toFloat()) * zoom
                            val ox = size.width / 2f + pan.x - iw * fit / 2f; val oy = size.height / 2f + pan.y - ih * fit / 2f
                            var best = -1; var bd = 70f
                            for (i in 0 until 4) {
                                val d = hypot(quad[i * 2] * fit + ox - o.x, quad[i * 2 + 1] * fit + oy - o.y)
                                if (d < bd) { bd = d; best = i }
                            }
                            drag = best
                        },
                        onDrag = { ch, amt ->
                            ch.consume()
                            val fit = min(size.width / iw.toFloat(), size.height / ih.toFloat()) * zoom
                            if (drag >= 0) {
                                val q = quad.copyOf()
                                q[drag * 2] = (q[drag * 2] + amt.x / fit).coerceIn(0f, iw.toFloat())
                                q[drag * 2 + 1] = (q[drag * 2 + 1] + amt.y / fit).coerceIn(0f, ih.toFloat())
                                quad = q
                            } else pan += amt
                        }
                    )
                }
        ) {
            val fit = min(size.width / iw, size.height / ih) * zoom
            val ox = size.width / 2f + pan.x - iw * fit / 2f; val oy = size.height / 2f + pan.y - ih * fit / 2f
            drawImage(src.asImageBitmap(), IntOffset.Zero, IntSize(iw, ih), IntOffset(ox.toInt(), oy.toInt()), IntSize((iw * fit).toInt(), (ih * fit).toInt()))
            val pts = (0 until 4).map { Offset(quad[it * 2] * fit + ox, quad[it * 2 + 1] * fit + oy) }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, Color(0x2800E5FF))
            drawPath(path, Color(0xFF00E5FF), style = Stroke(4.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))))
            pts.forEach {
                drawCircle(Color.White, 17f, it)
                drawCircle(Color(0xFF1F6FFF), 17f, it, style = Stroke(5.5f))
            }
        }
        Text(
            "Drag any corner handle to warp/select the tile. Drag inside to pan.",
            fontSize = 12.sp,
            color = pal.subAccent,
            fontWeight = FontWeight.SemiBold
        )
        NHCard {
            SectionTitle("Transform Controls")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Zoom", Modifier.width(56.dp), fontWeight = FontWeight.Bold, color = pal.accent)
                Slider(zoom, { zoom = it }, Modifier.weight(1f), valueRange = 0.5f..4f)
                Text("${fmt2(zoom.toDouble())}×", color = pal.metric, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rotate", Modifier.width(56.dp), fontWeight = FontWeight.Bold, color = pal.accent)
                Slider(angle, { v -> quad = CropEngine.rotateQuad(quad, v - angle, iw, ih); angle = v }, Modifier.weight(1f), valueRange = -45f..45f)
                Text("${angle.toInt()}°", color = pal.metric, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ quad = CropEngine.autoQuad(src); angle = 0f }, Modifier.weight(1f)) { Icon(Icons.Filled.AutoFixHigh, null); Text(" Auto", fontWeight = FontWeight.Bold) }
                OutlinedButton({ vm.cropSource = CropEngine.rotate(src, 90f) }, Modifier.weight(1f)) { Icon(Icons.Filled.RotateRight, null); Text(" 90°", fontWeight = FontWeight.Bold) }
                OutlinedButton({ quad = CropEngine.defaultQuad(src); zoom = 1f; pan = Offset.Zero; angle = 0f }, Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, null); Text(" Reset", fontWeight = FontWeight.Bold) }
            }
        }
        GoldButton("Choose Another Image") { pick() }
        PrimaryButton("Crop & Preview Texture", enabled = !busy) {
            scope.launch {
                busy = true
                try { vm.cropResult = withContext(Dispatchers.Default) { CropEngine.warp(src, quad) }; go("texture") }
                catch (e: Throwable) { vm.toast = "Crop failed: try a smaller selection" }
                busy = false
            }
        }
    }
}

@Composable
fun TextureScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val bmp = vm.cropResult
    val pal = LocalPalette.current
    var name by remember { mutableStateOf("My texture") }
    var grout by remember { mutableDoubleStateOf(vm.draft.design(vm.selectedSurface).groutMm) }
    val sel = vm.selectedSurface
    ScreenFrame("Texture Preview", back) {
        if (bmp == null) { Text("No cropped texture yet. Use the Atomic Crop Editor first."); PrimaryButton("Open crop editor") { back() }; return@ScreenFrame }
        NHCard {
            SectionTitle("Extracted Single Tile")
            Image(
                bmp.asImageBitmap(),
                null,
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 210.dp)
                    .softShadow(8.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit
            )
        }
        NHCard {
            SectionTitle("Seamless Repeating Grid")
            Text(
                "Each tile preserves its natural aspect ratio without stretching.",
                fontSize = 12.sp,
                color = pal.subAccent,
                fontWeight = FontWeight.SemiBold
            )
            val tiled by produceState<Bitmap?>(null, bmp, grout) {
                value = withContext(Dispatchers.Default) {
                    PatternComposer.compose(bmp, true, SurfaceDesign("custom:preview", 0.34, 0.34, TilePattern.STRAIGHT, grout, vm.draft.design(sel).groutColor), 1.0, 1.0, 512)
                }
            }
            tiled?.let {
                Image(
                    it.asImageBitmap(),
                    null,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .softShadow(10.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Grout preview width", fontSize = 13.sp, color = pal.label, fontWeight = FontWeight.SemiBold)
                Text("${fmt(grout)} mm", fontSize = 14.sp, color = pal.metric, fontWeight = FontWeight.ExtraBold)
            }
            Slider(grout.toFloat(), { grout = it.toDouble() }, valueRange = 0f..20f)
        }
        NHCard {
            SectionTitle("Save & Apply")
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Material name", fontWeight = FontWeight.SemiBold) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().softShadow(4.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors()
            )
            Text("Apply to surface", color = pal.subAccent, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            ChipRow(Surf.values().toList(), sel, { it.label }) { vm.selectedSurface = it }
        }
        PrimaryButton("Save & Apply to ${sel.label}") {
            vm.saveCustomMaterial(name, bmp, sel, grout); go(if (sel == Surf.FLOOR) "studio/floor" else "studio/wall")
        }
        GoldButton("Save to Library Only") { vm.saveCustomMaterial(name, bmp, null) }
    }
}

@Composable
fun PreviewScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val p = vm.draft
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(24f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var compare by remember { mutableStateOf(false) }
    var split by remember { mutableFloatStateOf(0.5f) }
    val after by rememberSceneBitmaps(p, p.designs)
    val before by rememberSceneBitmaps(p, Scene.before(), compare)
    val boxes = remember(p.furniture, p.showFurniture, p.sections) { FurnitureLib.layout(p) }
    ScreenFrame("Room 3D Preview", back, actions = {
        IconButton({ go("fullview") }) { Icon(Icons.Filled.Fullscreen, "Full screen", tint = pal.subAccent) }
    }) {
        Box(Modifier.fillMaxWidth().height(325.dp)) {
            RoomCanvas(after, if (compare) before else null, p, yaw, pitch, zoom, split, Modifier.fillMaxSize(), boxes) { pan, z ->
                yaw += pan.x * 0.3f; pitch = (pitch + pan.y * 0.3f).coerceIn(-80f, 80f); zoom = (zoom * z).coerceIn(0.5f, 3f)
            }
            if (compare) {
                Tag("AFTER", Modifier.align(Alignment.TopStart).padding(12.dp))
                Tag("BEFORE", Modifier.align(Alignment.TopEnd).padding(12.dp))
            } else {
                Tag(
                    "Angle ${yaw.toInt()}°  •  Zoom ${fmt2(zoom.toDouble())}×",
                    Modifier.align(Alignment.TopStart).padding(12.dp)
                )
            }
        }
        Text(
            "Drag to rotate 360°  •  Pinch to zoom  •  Tap Full Screen for walkthrough",
            fontSize = 12.sp,
            color = pal.subAccent,
            fontWeight = FontWeight.SemiBold
        )
        PrimaryButton("Launch Full Screen 3D Walkthrough") { go("fullview") }

        NHCard {
            SectionTitle("Camera & Comparison Controls")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Before / After Split", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = pal.ink)
                Switch(compare, { compare = it })
            }
            if (compare) Slider(split, { split = it }, Modifier.fillMaxWidth(), valueRange = 0.05f..0.95f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Show 3D Furniture", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = pal.ink)
                Switch(p.showFurniture, { v -> vm.update { it.copy(showFurniture = v) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ yaw = 0f; pitch = 24f; zoom = 1f }, Modifier.weight(1f)) { Text("Reset Camera", fontWeight = FontWeight.Bold) }
                OutlinedButton({ yaw = 90f * ((yaw / 90f).toInt() + 1) }, Modifier.weight(1f)) { Text("Rotate 90°", fontWeight = FontWeight.Bold) }
            }
        }

        NHCard {
            SectionTitle("Capture Current 3D Angle")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    val y = yaw; val pi = pitch; val z = zoom
                    scope.launch {
                        val path = withContext(Dispatchers.Default) {
                            val b = Scene.render(p, p.designs, 1920, 1440, y, pi, z, 1280)
                            try { Exporter.saveToGallery(ctx, b, p.name + "_3dview") } finally { b.recycle() }
                        }
                        vm.toast = if (path != null) "3D image saved to $path" else "Could not save image"
                    }
                }, Modifier.weight(1f)) { Icon(Icons.Filled.Download, null); Text(" Save Gallery", fontWeight = FontWeight.Bold) }
                OutlinedButton({
                    val y = yaw; val pi = pitch; val z = zoom
                    scope.launch {
                        val f = withContext(Dispatchers.Default) {
                            val b = Scene.render(p, p.designs, 1920, 1440, y, pi, z, 1280)
                            try { Exporter.savePng(ctx, b, p.name + "_3dview") } finally { b.recycle() }
                        }
                        Exporter.share(ctx, f, "image/png")
                    }
                }, Modifier.weight(1f)) { Icon(Icons.Filled.Share, null); Text(" Share HD", fontWeight = FontWeight.Bold) }
            }
        }

        NHCard {
            SectionTitle("Quick Material Switch")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.selectedSurface = Surf.FLOOR; go("studio/floor") }, Modifier.weight(1f)) { Text("Floor Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton({ vm.selectedSurface = Surf.BACK; go("studio/wall") }, Modifier.weight(1f)) { Text("Wall Studio", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        GoldButton("Furniture & Room Templates") { go("furniture") }
        GoldButton("Export Design (PNG / PDF / 3D OBJ)") { go("export") }
    }
}
