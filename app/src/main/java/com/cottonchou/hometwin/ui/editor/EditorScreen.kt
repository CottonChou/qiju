package com.cottonchou.hometwin.ui.editor

import android.graphics.Paint as APaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cottonchou.hometwin.data.Device
import com.cottonchou.hometwin.data.HomeViewModel
import com.cottonchou.hometwin.data.Opening
import com.cottonchou.hometwin.data.Plan
import com.cottonchou.hometwin.data.Room
import com.cottonchou.hometwin.ui.Accent
import com.cottonchou.hometwin.ui.DarkBg
import com.cottonchou.hometwin.ui.TextPrimary
import com.cottonchou.hometwin.ui.Warm
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class DragMode { None, Pan, MoveRoom, ResizeTL, ResizeTR, ResizeBR, ResizeBL, MoveDevice, MoveOpening }

private class EditorState {
    var mode: DragMode = DragMode.None
    var id: String? = null
    var start: Offset = Offset.Zero
    fun set(m: DragMode, id: String?, p: Offset) { this.mode = m; this.id = id; this.start = p }
}

/**
 * 户型编辑器（2D 俯视）：
 * - 拖动房间移动；拖动选中房间四角调整尺寸；0.1m 网格吸附
 * - 点选房间/设备/门窗后在底部面板编辑、增删
 * - 参考 house-control 的做法：房间为轴对齐矩形，L 形用两个矩形拼接
 */
@Composable
fun EditorScreen(vm: HomeViewModel, onBack: () -> Unit) {
    var plan by remember {
        mutableStateOf(
            vm.plan.copy(
                rooms = vm.plan.rooms.map { it.copy() },
                openings = vm.plan.openings.map { it.copy() },
                devices = vm.plan.devices.map { it.copy() }
            )
        )
    }
    var dirty by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(56f) } // px / 米
    var offset by remember { mutableStateOf(Offset(80f, 140f)) }

    var selRoomId by remember { mutableStateOf<String?>(null) }
    var selDeviceId by remember { mutableStateOf<String?>(null) }
    var selOpeningId by remember { mutableStateOf<String?>(null) }

    val drag = remember { EditorState() }

    fun snap(v: Float) = (v * 10f).roundToInt() / 10f

    fun updateRoom(r: Room) {
        plan = plan.copy(rooms = plan.rooms.map { if (it.id == r.id) r else it }); dirty = true
    }
    fun updateDevice(d: Device) {
        plan = plan.copy(devices = plan.devices.map { if (it.id == d.id) d else it }); dirty = true
    }
    fun updateOpening(o: Opening) {
        plan = plan.copy(openings = plan.openings.map { if (it.id == o.id) o else it }); dirty = true
    }

    fun toScreen(px: Float, py: Float) = Offset(offset.x + px * scale, offset.y + py * scale)
    fun toPlanX(sx: Float) = (sx - offset.x) / scale
    fun toPlanY(sy: Float) = (sy - offset.y) / scale

    fun openingCenter(r: Room, o: Opening): Pair<Float, Float> = when (o.edge) {
        0 -> (r.x + o.offset) to r.y
        1 -> r.x2 to (r.y + o.offset)
        2 -> (r.x + o.offset) to r.y2
        else -> r.x to (r.y + o.offset)
    }

    // 命中检测：设备 > 选中房间角点 > 门窗 > 房间 > 空(平移)
    fun hitTest(p: Offset): Pair<DragMode, String?> {
        val px = toPlanX(p.x); val py = toPlanY(p.y)
        for (d in plan.devices.asReversed()) {
            if (abs(d.x - px) * scale < 20f && abs(d.y - py) * scale < 20f)
                return DragMode.MoveDevice to d.id
        }
        selRoomId?.let { rid ->
            plan.rooms.find { it.id == rid }?.let { r ->
                val tol = 26f / scale
                if (abs(r.x - px) < tol && abs(r.y - py) < tol) return DragMode.ResizeTL to rid
                if (abs(r.x2 - px) < tol && abs(r.y - py) < tol) return DragMode.ResizeTR to rid
                if (abs(r.x2 - px) < tol && abs(r.y2 - py) < tol) return DragMode.ResizeBR to rid
                if (abs(r.x - px) < tol && abs(r.y2 - py) < tol) return DragMode.ResizeBL to rid
            }
        }
        for (o in plan.openings) {
            val r = plan.rooms.find { it.id == o.roomId } ?: continue
            val (cx, cy) = openingCenter(r, o)
            if (abs(cx - px) * scale < 18f && abs(cy - py) * scale < 18f)
                return DragMode.MoveOpening to o.id
        }
        for (r in plan.rooms.asReversed()) {
            if (r.contains(px, py)) return DragMode.MoveRoom to r.id
        }
        return DragMode.Pan to null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = TextPrimary)
                }
                Text("户型编辑", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                if (dirty) {
                    Spacer(Modifier.width(8.dp))
                    Text("未保存", color = Warm, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scale = (scale * 1.25f).coerceAtMost(160f) }) { Text("＋", color = TextPrimary) }
                TextButton(onClick = { scale = (scale / 1.25f).coerceAtLeast(18f) }) { Text("－", color = TextPrimary) }
                TextButton(onClick = {
                    // 放在当前视口左上偏右的位置（屏幕坐标 160,260 对应的平面坐标）
                    val cx = toPlanX(160f)
                    val cy = toPlanY(260f)
                    val newRoom = Room(
                        id = "room${System.currentTimeMillis()}",
                        name = "新房间", type = "living",
                        x = snap(cx), y = snap(cy), w = 3f, h = 3f
                    )
                    plan = plan.copy(rooms = plan.rooms + newRoom)
                    selRoomId = newRoom.id; selDeviceId = null; selOpeningId = null
                    dirty = true
                }) { Text("添加房间", color = Accent) }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = { vm.savePlan(plan); dirty = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = dirty
                ) { Text("保存") }
                Spacer(Modifier.width(8.dp))
            }

            // 画布
            EditorCanvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                plan = plan,
                scale = scale,
                offset = offset,
                selRoomId = selRoomId,
                selDeviceId = selDeviceId,
                selOpeningId = selOpeningId,
                onTap = { p ->
                    val (mode, id) = hitTest(p)
                    when (mode) {
                        DragMode.MoveRoom -> { selRoomId = id; selDeviceId = null; selOpeningId = null }
                        DragMode.MoveDevice -> { selDeviceId = id; selRoomId = null; selOpeningId = null }
                        DragMode.MoveOpening -> { selOpeningId = id; selRoomId = null; selDeviceId = null }
                        else -> { selRoomId = null; selDeviceId = null; selOpeningId = null }
                    }
                },
                onDragStart = { p -> val (m, id) = hitTest(p); drag.set(m, id, p) },
                onDrag = { change, amount ->
                    change.consume()
                    val st = drag
                    when (st.mode) {
                        DragMode.Pan -> offset += amount
                        DragMode.MoveRoom -> {
                            val r = plan.rooms.find { it.id == st.id }
                            if (r != null) {
                                val sx = toPlanX(st.start.x); val sy = toPlanY(st.start.y)
                                val cx = toPlanX(change.position.x); val cy = toPlanY(change.position.y)
                                updateRoom(r.copy(x = snap(r.x + cx - sx), y = snap(r.y + cy - sy)))
                                drag.start = change.position
                            }
                        }
                        DragMode.ResizeTL -> {
                            val r = plan.rooms.find { it.id == st.id }
                            if (r != null) {
                                val px = toPlanX(change.position.x); val py = toPlanY(change.position.y)
                                val nx = min(px, r.x2 - 1f); val ny = min(py, r.y2 - 1f)
                                updateRoom(r.copy(x = snap(nx), y = snap(ny), w = snap(r.x2 - nx), h = snap(r.y2 - ny)))
                            }
                        }
                        DragMode.ResizeTR -> {
                            val r = plan.rooms.find { it.id == st.id }
                            if (r != null) {
                                val px = toPlanX(change.position.x); val py = toPlanY(change.position.y)
                                val nx2 = max(px, r.x + 1f); val ny = min(py, r.y2 - 1f)
                                updateRoom(r.copy(w = snap(nx2 - r.x), y = snap(ny), h = snap(r.y2 - ny)))
                            }
                        }
                        DragMode.ResizeBR -> {
                            val r = plan.rooms.find { it.id == st.id }
                            if (r != null) {
                                val px = toPlanX(change.position.x); val py = toPlanY(change.position.y)
                                val nx2 = max(px, r.x + 1f); val ny2 = max(py, r.y + 1f)
                                updateRoom(r.copy(w = snap(nx2 - r.x), h = snap(ny2 - r.y)))
                            }
                        }
                        DragMode.ResizeBL -> {
                            val r = plan.rooms.find { it.id == st.id }
                            if (r != null) {
                                val px = toPlanX(change.position.x); val py = toPlanY(change.position.y)
                                val nx = min(px, r.x2 - 1f); val ny2 = max(py, r.y + 1f)
                                updateRoom(r.copy(x = snap(nx), w = snap(r.x2 - nx), h = snap(ny2 - r.y)))
                            }
                        }
                        DragMode.MoveDevice -> {
                            val d = plan.devices.find { it.id == st.id }
                            if (d != null) {
                                updateDevice(d.copy(x = snap(toPlanX(change.position.x)), y = snap(toPlanY(change.position.y))))
                            }
                        }
                        DragMode.MoveOpening -> {
                            val o = plan.openings.find { it.id == st.id }
                            if (o != null) {
                                val r = plan.rooms.find { it.id == o.roomId }
                                if (r != null) {
                                    val px = toPlanX(change.position.x); val py = toPlanY(change.position.y)
                                    val len = if (o.edge == 0 || o.edge == 2) r.w else r.h
                                    val raw = if (o.edge == 0 || o.edge == 2) px - r.x else py - r.y
                                    val half = o.width / 2f
                                    updateOpening(o.copy(offset = snap(raw.coerceIn(half + 0.1f, len - half - 0.1f))))
                                }
                            }
                        }
                        else -> {}
                    }
                },
                onDragEnd = { drag.set(DragMode.None, null, Offset.Zero) },
                toScreen = { x, y -> toScreen(x, y) }
            )

            // 底部编辑面板
            SelectionPanel(
                plan = plan,
                selRoomId = selRoomId,
                selDeviceId = selDeviceId,
                selOpeningId = selOpeningId,
                onRoom = { updateRoom(it) },
                onDevice = { updateDevice(it) },
                onOpening = { updateOpening(it) },
                onDeleteRoom = { rid ->
                    plan = plan.copy(
                        rooms = plan.rooms.filter { it.id != rid },
                        devices = plan.devices.filter { it.roomId != rid },
                        openings = plan.openings.filter { it.roomId != rid }
                    )
                    selRoomId = null; dirty = true
                },
                onDeleteDevice = { did ->
                    plan = plan.copy(devices = plan.devices.filter { it.id != did })
                    selDeviceId = null; dirty = true
                },
                onDeleteOpening = { oid ->
                    plan = plan.copy(openings = plan.openings.filter { it.id != oid })
                    selOpeningId = null; dirty = true
                },
                onAddDevice = { type ->
                    val rid = selRoomId ?: return@SelectionPanel
                    val r = plan.rooms.find { it.id == rid } ?: return@SelectionPanel
                    val idx = plan.devices.count { it.roomId == rid && it.type == type } + 1
                    val d = Device(
                        id = "${type}.${rid}.${System.currentTimeMillis()}",
                        name = "${Device.TYPE_NAMES[type] ?: type} $idx",
                        type = type, roomId = rid, x = snap(r.cx), y = snap(r.cy)
                    )
                    plan = plan.copy(devices = plan.devices + d)
                    selDeviceId = d.id; dirty = true
                },
                onAddOpening = { type ->
                    val rid = selRoomId ?: return@SelectionPanel
                    val r = plan.rooms.find { it.id == rid } ?: return@SelectionPanel
                    val o = Opening(
                        id = "op${System.currentTimeMillis()}",
                        roomId = rid, edge = 2, offset = snap(r.w / 2f),
                        width = if (type == "door") 0.9f else 1.5f,
                        type = type
                    )
                    plan = plan.copy(openings = plan.openings + o)
                    selOpeningId = o.id; dirty = true
                }
            )
        }
    }
}

/** 编辑画布（把绘制与手势集中在一个组件，避免跨组件传 transform） */
@Composable
private fun EditorCanvas(
    modifier: Modifier = Modifier,
    plan: Plan,
    scale: Float,
    offset: Offset,
    selRoomId: String?,
    selDeviceId: String?,
    selOpeningId: String?,
    onTap: (Offset) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    toScreen: (Float, Float) -> Offset
) {
    val textPaint = remember {
        APaint(APaint.ANTI_ALIAS_FLAG).apply { textAlign = APaint.Align.CENTER }
    }
    Canvas(
        modifier
            .pointerInput(plan, selRoomId) {
                detectTapGestures { onTap(it) }
            }
            .pointerInput(plan, selRoomId) {
                detectDragGestures(
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
    ) {
        val w = size.width
        val h = size.height
        drawContext.canvas.nativeCanvas.apply {
            drawColor(DarkBg.toArgb())

            val linePaint = APaint().apply { color = Color(0x16FFFFFF).toArgb(); strokeWidth = 1f }
            val fillPaint = APaint().apply { style = APaint.Style.FILL }
            val borderPaint = APaint().apply { style = APaint.Style.STROKE; strokeWidth = 2.5f }

            // 网格（0.5m）
            var gx = offset.x % (scale * 0.5f)
            while (gx < w) { drawLine(gx, 0f, gx, h, linePaint); gx += scale * 0.5f }
            var gy = offset.y % (scale * 0.5f)
            while (gy < h) { drawLine(0f, gy, w, gy, linePaint); gy += scale * 0.5f }

            // 房间
            plan.rooms.forEach { r ->
                val p0 = toScreen(r.x, r.y)
                fillPaint.color = Color(android.graphics.Color.parseColor(r.floorColor)).copy(alpha = 0.72f).toArgb()
                drawRect(p0.x, p0.y, r.w * scale, r.h * scale, fillPaint)
                borderPaint.color = if (r.id == selRoomId) Accent.toArgb() else Color(0x66FFFFFF).toArgb()
                borderPaint.strokeWidth = if (r.id == selRoomId) 3.5f else 2.5f
                drawRect(p0.x, p0.y, r.w * scale, r.h * scale, borderPaint)
                textPaint.textSize = (scale * 0.22f).coerceIn(11f, 20f)
                textPaint.color = TextPrimary.copy(alpha = 0.9f).toArgb()
                textPaint.isFakeBoldText = true
                drawText(r.name, p0.x + r.w * scale / 2f, p0.y + r.h * scale / 2f, textPaint)
            }

            // 门窗：先用背景色在边框上开洞，再画标识线
            plan.openings.forEach { o ->
                val r = plan.rooms.find { it.id == o.roomId } ?: return@forEach
                val c = openingCenterOf(r, o)
                val half = o.width / 2f
                val horizontal = o.edge == 0 || o.edge == 2
                val a = if (horizontal) toScreen(c.first - half, c.second) else toScreen(c.first, c.second - half)
                val b = if (horizontal) toScreen(c.first + half, c.second) else toScreen(c.first, c.second + half)
                val gapPaint = APaint().apply {
                    style = APaint.Style.STROKE; strokeWidth = 8f
                    color = DarkBg.toArgb(); strokeCap = android.graphics.Paint.Cap.BUTT
                }
                drawLine(a.x, a.y, b.x, b.y, gapPaint)
                val markPaint = APaint().apply {
                    style = APaint.Style.STROKE; strokeWidth = 3f
                    color = if (o.type == "window") Color(0xFF6FB2FF).toArgb() else Color(0xFFE8D5A8).toArgb()
                }
                drawLine(a.x, a.y, b.x, b.y, markPaint)
                if (o.id == selOpeningId) {
                    fillPaint.color = Warm.toArgb()
                    drawCircle((a.x + b.x) / 2f, (a.y + b.y) / 2f, 7f, fillPaint)
                }
            }

            // 设备
            plan.devices.forEach { d ->
                val p = toScreen(d.x, d.y)
                fillPaint.color = Color(0xE6101828).toArgb()
                drawCircle(p.x, p.y, 14f, fillPaint)
                borderPaint.strokeWidth = 2f
                borderPaint.color = if (d.id == selDeviceId) Accent.toArgb() else Color(0x88FFFFFF).toArgb()
                drawCircle(p.x, p.y, 14f, borderPaint)
                textPaint.textSize = 14f
                textPaint.isFakeBoldText = false
                textPaint.color = android.graphics.Color.WHITE
                drawText(com.cottonchou.hometwin.engine.SceneBuilder.glyphFor(d.type), p.x, p.y + 5f, textPaint)
            }

            // 选中房间角点手柄
            selRoomId?.let { rid ->
                plan.rooms.find { it.id == rid }?.let { r ->
                    fillPaint.color = Accent.toArgb()
                    listOf(
                        toScreen(r.x, r.y), toScreen(r.x2, r.y),
                        toScreen(r.x2, r.y2), toScreen(r.x, r.y2)
                    ).forEach { c -> drawRect(c.x - 6f, c.y - 6f, 12f, 12f, fillPaint) }
                }
            }
        }
    }
}

private fun openingCenterOf(r: Room, o: Opening): Pair<Float, Float> = when (o.edge) {
    0 -> (r.x + o.offset) to r.y
    1 -> r.x2 to (r.y + o.offset)
    2 -> (r.x + o.offset) to r.y2
    else -> r.x to (r.y + o.offset)
}
