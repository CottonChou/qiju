package com.cottonchou.hometwin.ui

import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.cottonchou.hometwin.data.HomeViewModel
import com.cottonchou.hometwin.engine.Cam
import com.cottonchou.hometwin.engine.Renderer3D
import com.cottonchou.hometwin.engine.SceneBuilder
import com.cottonchou.hometwin.engine.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** 3D 户型视图：拖动环绕、双指缩放、点选设备/房间 */
@Composable
fun Home3DView(vm: HomeViewModel, modifier: Modifier = Modifier) {
    var lowWalls by remember { mutableStateOf(false) }
    val geo = remember(vm.plan, lowWalls) { SceneBuilder.build(vm.plan, lowWalls) }

    val defaultCam = remember(geo) {
        Cam(
            yawDeg = -32f, pitchDeg = 36f,
            dist = maxOf(geo.bounds.spanX, geo.bounds.spanZ) * 1.35f + 4f,
            target = Vec3(geo.bounds.cx, 0.9f, geo.bounds.cz)
        )
    }
    var cam by remember(geo) { mutableStateOf(defaultCam.copy()) }

    // 昼夜切换动画
    val dayAnim = remember { Animatable(0f) }
    LaunchedEffect(vm.targetDay) {
        dayAnim.animateTo(if (vm.targetDay) 1f else 0f, tween(900))
    }

    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .pointerInput(geo) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        cam.yawDeg -= pan.x * 0.25f
                        cam.pitchDeg = (cam.pitchDeg + pan.y * 0.18f).coerceIn(10f, 72f)
                        val minD = maxOf(geo.bounds.spanX, geo.bounds.spanZ) * 0.7f + 2f
                        val maxD = maxOf(geo.bounds.spanX, geo.bounds.spanZ) * 2.6f + 6f
                        cam.dist = (cam.dist / zoom).coerceIn(minD, maxD)
                    }
                }
                .pointerInput(geo) {
                    detectTapGestures { pos ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val hit = pick(cam, w, h, geo, pos.x, pos.y, vm)
                        if (hit != null) {
                            vm.selectedDeviceId = hit
                            vm.selectedRoomId = vm.plan.devices.find { it.id == hit }?.roomId
                                ?: vm.selectedRoomId
                        } else {
                            val room = pickRoom(cam, w, h, geo, pos.x, pos.y)
                            vm.selectedRoomId = room
                        }
                    }
                }
        ) {
            val canvas = drawContext.canvas.nativeCanvas
            Renderer3D.render(
                canvas, size.width.toFloat(), size.height.toFloat(), geo, cam,
                dayAnim.value, vm.deviceStates, vm.selectedRoomId, vm.selectedDeviceId
            )
        }
    }

    // 供右上角按钮控制：把 lowWalls 暴露给调用方（通过 vm 之外的槽位不优雅，这里用副作用注册）
    ExternalControls.register(lowWalls) { lowWalls = it }
    ExternalControls.registerReset { cam = defaultCam.copy() }
}

/** 全屏拾取：返回命中的设备 id */
private fun pick(
    cam: Cam, w: Float, h: Float, geo: com.cottonchou.hometwin.engine.SceneGeometry,
    x: Float, y: Float, vm: HomeViewModel
): String? {
    val proj = projector(cam, w, h)
    // 优先设备标记
    var bestDev: Pair<String, Float>? = null
    for (m in geo.markers) {
        val p = proj(m.pos)
        val d = kotlin.math.hypot((p[0] - x).toDouble(), (p[1] - y).toDouble()).toFloat()
        val r = (0.34f * (h * 1.05f) / p[2]).coerceIn(11f, 30f) * 1.6f
        if (d < r) {
            if (bestDev == null || p[2] < bestDev.second) bestDev = m.deviceId to p[2]
        }
    }
    return bestDev?.first
}

private fun pickRoom(
    cam: Cam, w: Float, h: Float, geo: com.cottonchou.hometwin.engine.SceneGeometry,
    x: Float, y: Float
): String? {
    val proj = projector(cam, w, h)
    var best: Pair<String, Float>? = null
    // 只考虑地板面（kind==0）
    for (f in geo.faces) {
        if (f.kind != 0 || f.roomId == null) continue
        val pts = f.pts.map { proj(it) }
        if (pointInPoly(x, y, pts)) {
            val c = f.pts.fold(Vec3(0f, 0f, 0f)) { a, p -> a + p } * (1f / f.pts.size)
            val vz = proj(c)[2]
            if (best == null || vz < best.second) best = f.roomId to vz
        }
    }
    return best?.first
}

private fun pointInPoly(x: Float, y: Float, pts: List<FloatArray>): Boolean {
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        if ((pts[i][1] > y) != (pts[j][1] > y) &&
            x < (pts[j][0] - pts[i][0]) * (y - pts[i][1]) / (pts[j][1] - pts[i][1]) + pts[i][0]
        ) inside = !inside
        j = i
    }
    return inside
}

internal fun projector(cam: Cam, w: Float, h: Float): (Vec3) -> FloatArray {
    val cy = cos(Math.toRadians(cam.yawDeg.toDouble())).toFloat()
    val sy = sin(Math.toRadians(cam.yawDeg.toDouble())).toFloat()
    val cp = cos(Math.toRadians(cam.pitchDeg.toDouble())).toFloat()
    val sp = sin(Math.toRadians(cam.pitchDeg.toDouble())).toFloat()
    val focal = h * 1.05f
    val cx = w / 2f
    val cys = h / 2f
    return { p ->
        val px = p.x - cam.target.x
        val py = p.y - cam.target.y
        val pz = p.z - cam.target.z
        val x1 = px * cy + pz * sy
        val z1 = -px * sy + pz * cy
        val y1 = py * cp + z1 * sp
        val z2 = -py * sp + z1 * cp
        val vz = z2 + cam.dist
        floatArrayOf(cx + x1 * focal / vz, cys - y1 * focal / vz, vz)
    }
}

/** 简单桥：让工具栏按钮影响 3D 视图内部状态（单实例场景下够用） */
object ExternalControls {
    internal var wallsSetter: ((Boolean) -> Unit)? = null
    internal var resetSetter: (() -> Unit)? = null
    var lowWallsValue: Boolean = false

    fun register(current: Boolean, setter: (Boolean) -> Unit) {
        lowWallsValue = current
        wallsSetter = setter
    }

    fun registerReset(block: () -> Unit) { resetSetter = block }

    fun toggleWalls(): Boolean {
        lowWallsValue = !lowWallsValue
        wallsSetter?.invoke(lowWallsValue)
        return lowWallsValue
    }

    fun resetView() { resetSetter?.invoke() }
}
