package com.qiju.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.qiju.data.DeviceState
import kotlin.math.max
import kotlin.math.min

data class Cam(
    var yawDeg: Float = -32f,
    var pitchDeg: Float = 36f,
    var dist: Float = 14f,
    val target: Vec3 = Vec3(0f, 0f, 0f)
)

/**
 * 软件渲染器：透视投影 + 画家算法 + Lambert 面光照。
 * 白天：太阳方向直射光 + 地面阴影；夜晚：低环境光 + 灯具点光源（暖光随距离衰减）。
 * dayFactor: 0=夜 1=昼，切换时可做插值动画。
 */
object Renderer3D {

    private val toSun = Vec3(0.35f, 0.95f, 0.45f).normalized()

    // 夜/昼两组背景与氛围色
    private val bgNightTop = Color.parseColor("#070D1E")
    private val bgNightBot = Color.parseColor("#131E3C")
    private val bgDayTop = Color.parseColor("#8FA8C8")
    private val bgDayBot = Color.parseColor("#C9C2B2")

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun render(
        canvas: Canvas,
        w: Float,
        h: Float,
        geo: SceneGeometry,
        cam: Cam,
        dayFactor: Float,
        states: Map<String, DeviceState>,
        selectedRoomId: String?,
        selectedDeviceId: String?
    ) {
        val t = dayFactor.coerceIn(0f, 1f)
        // 天空渐变
        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            lerpColor(bgNightTop, bgDayTop, t), lerpColor(bgNightBot, bgDayBot, t),
            Shader.TileMode.CLAMP
        )
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val cy = Math.cos(Math.toRadians(cam.yawDeg.toDouble())).toFloat()
        val sy = Math.sin(Math.toRadians(cam.yawDeg.toDouble())).toFloat()
        val cp = Math.cos(Math.toRadians(cam.pitchDeg.toDouble())).toFloat()
        val sp = Math.sin(Math.toRadians(cam.pitchDeg.toDouble())).toFloat()
        val focal = h * 1.05f
        val cxScreen = w / 2f
        val cyScreen = h / 2f

        fun viewZ(p: Vec3): Float {
            val px = p.x - cam.target.x
            val pz = p.z - cam.target.z
            val x1 = px * cy + pz * sy
            val z1 = -px * sy + pz * cy
            val z2 = -p.y * sp + z1 * cp
            return z2 + cam.dist
        }

        fun project(p: Vec3): FloatArray {
            val px = p.x - cam.target.x
            val py = p.y - cam.target.y
            val pz = p.z - cam.target.z
            val x1 = px * cy + pz * sy
            val z1 = -px * sy + pz * cy
            val y1 = py * cp + z1 * sp
            val z2 = -py * sp + z1 * cp
            val vz = z2 + cam.dist
            return floatArrayOf(cxScreen + x1 * focal / vz, cyScreen - y1 * focal / vz, vz)
        }

        // 活跃灯具（开灯才有贡献）
        val activeLamps = geo.lamps.mapNotNull { l ->
            val s = states[l.deviceId]
            if (s != null && s.on && !s.unavailable) l to (s.brightness / 100f) else null
        }

        // ---- 阴影与地面（永远最先画） ----
        for (pass in 0..1) {
            for (f in geo.faces) {
                if (pass == 0 && f.kind != 3) continue
                if (pass == 1 && f.kind != 4) continue
                if (f.kind == 4) {
                    val a = 0.32f * t
                    if (a <= 0.01f) continue
                    if (!fillPath(projectedOrNull(f, ::project) ?: continue, canvas, paint, path)) continue
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb((a * 255).toInt(), 2, 4, 10)
                    paint.shader = null
                    canvas.drawPath(path, paint)
                } else {
                    if (!fillPath(projectedOrNull(f, ::project) ?: continue, canvas, paint, path)) continue
                    // 地面颜色随昼夜变化
                    val night = Vec3(0.045f, 0.06f, 0.11f)
                    val day = Vec3(0.42f, 0.44f, 0.40f)
                    val alb = night.lerp(day, t)
                    val amb = lerp(0.9f, 1.15f, t)
                    paint.style = Paint.Style.FILL
                    paint.shader = null
                    paint.color = Color.rgb(
                        (alb.x * amb * 255f).toInt().coerceIn(0, 255),
                        (alb.y * amb * 255f).toInt().coerceIn(0, 255),
                        (alb.z * amb * 255f).toInt().coerceIn(0, 255)
                    )
                    canvas.drawPath(path, paint)
                }
            }
        }

        // ---- 主要几何：按深度排序后绘制（画家算法） ----
        val ambDay = 0.48f
        val ambNight = 0.14f
        val sunStrength = 0.62f

        val orderList = ArrayList<Pair<Float, Face>>()
        for (f in geo.faces) {
            if (f.kind == 3 || f.kind == 4) continue
            var ok = true
            var depth = 0f
            for (p in f.pts) {
                val vz = viewZ(p)
                if (vz < 0.25f) { ok = false; break }
                depth += vz
            }
            if (!ok) continue
            orderList.add(depth / f.pts.size to f)
        }
        orderList.sortByDescending { it.first }

        for ((depth, f) in orderList) {
            var ok = true
            val proj = ArrayList<FloatArray>(f.pts.size)
            for (p in f.pts) {
                val pr = project(p)
                if (pr[2] < 0.25f) { ok = false; break }
                proj.add(pr)
            }
            if (!ok) continue

            // 中心点用于点光衰减
            val c = f.pts.fold(Vec3(0f, 0f, 0f)) { acc, p -> acc + p } * (1f / f.pts.size)

            // 环境光 + 太阳
            val amb = ambNight + (ambDay - ambNight) * t
            var lit = amb
            if (t > 0.01f) {
                val ndl = max(0f, f.n.dot(toSun))
                lit += t * sunStrength * ndl
            }
            // 灯具点光（昼夜都生效，夜晚更明显）
            var warmR = 0f; var warmG = 0f; var warmB = 0f
            if (f.kind != 2) {
                for ((lamp, inten) in activeLamps) {
                    val lv = lamp.pos - c
                    val dist2 = lv.length()
                    if (dist2 > 8f) continue
                    val atten = inten * 1.5f / (1f + 0.6f * dist2 * dist2)
                    val ndl = max(0.05f, f.n.dot(lv * (1f / max(dist2, 0.01f))))
                    val k = atten * ndl
                    warmR += k * 1.00f; warmG += k * 0.78f; warmB += k * 0.52f
                }
            }

            var alb = f.albedo
            var r = alb.x * lit + warmR
            var g = alb.y * lit + warmG
            var b = alb.z * lit + warmB

            // 选中房间高亮
            if (f.roomId != null && f.roomId == selectedRoomId && f.kind == 0) {
                r += 0.05f; g += 0.09f; b += 0.16f
            }

            r = min(1f, r); g = min(1f, g); b = min(1f, b)

            val alpha = if (f.kind == 2) ((0.34f * (1f - t * 0.4f)) * 255f).toInt() else 255
            if (!fillPath(proj, canvas, paint, path)) continue
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = Color.argb(alpha, (r * 255f).toInt(), (g * 255f).toInt(), (b * 255f).toInt())
            canvas.drawPath(path, paint)
            if (f.kind == 1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f
                paint.color = Color.argb(60, 0, 0, 0)
                canvas.drawPath(path, paint)
            }
        }

        // ---- 开灯地面光晕 ----
        for ((lamp, inten) in activeLamps) {
            val p0 = Vec3(lamp.pos.x, 0.03f, lamp.pos.z)
            val pr = project(p0)
            if (pr[2] < 0.3f) continue
            val radius = 1.25f * focal / pr[2]
            val a = (1f - t * 0.5f) * inten * 0.34f
            if (a <= 0.02f) continue
            paint.shader = RadialGradient(
                pr[0], pr[1], radius,
                Color.argb((a * 255f).toInt(), 255, 196, 120),
                Color.argb(0, 255, 196, 120),
                Shader.TileMode.CLAMP
            )
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pr[0], pr[1], radius, paint)
            paint.shader = null
        }

        // ---- 房间标签 ----
        textPaint.isFakeBoldText = true
        for (lb in geo.labels) {
            val pr = project(lb.pos)
            if (pr[2] < 0.3f) continue
            val size = (0.42f * focal / pr[2]).coerceIn(10f, 34f)
            textPaint.textSize = size
            textPaint.color = Color.argb((140 + 60 * t).toInt(), 255, 255, 255)
            canvas.drawText(lb.text, pr[0], pr[1] + size * 0.35f, textPaint)
        }

        // ---- 设备标记 ----
        for (m in geo.markers) {
            val pr = project(m.pos)
            if (pr[2] < 0.3f) continue
            val radius = (0.34f * focal / pr[2]).coerceIn(11f, 30f)
            val s = states[m.deviceId]
            val on = s?.on == true && s.unavailable == false
            val selected = m.deviceId == selectedDeviceId
            // 外圈光晕（开灯时）
            if (on && m.type == "light") {
                paint.shader = RadialGradient(
                    pr[0], pr[1], radius * 2.4f,
                    Color.argb(110, 255, 200, 120), Color.argb(0, 255, 200, 120),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(pr[0], pr[1], radius * 2.4f, paint)
                paint.shader = null
            }
            paint.style = Paint.Style.FILL
            paint.color = if (selected) Color.parseColor("#5B8DEF")
                else if (on) Color.parseColor("#3A3428") else Color.parseColor("#E0101828")
            canvas.drawCircle(pr[0], pr[1], radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1.2f, radius * 0.09f)
            paint.color = if (on && m.type == "light") Color.parseColor("#F5B85C")
                else Color.argb(150, 255, 255, 255)
            canvas.drawCircle(pr[0], pr[1], radius, paint)
            // 字形
            textPaint.isFakeBoldText = false
            textPaint.textSize = radius * 1.15f
            canvas.drawText(m.glyph, pr[0], pr[1] + radius * 0.42f, textPaint)
        }
    }

    private fun projectedOrNull(f: Face, project: (Vec3) -> FloatArray): List<FloatArray>? {
        val out = ArrayList<FloatArray>(f.pts.size)
        for (p in f.pts) {
            val pr = project(p)
            if (pr[2] < 0.25f) return null
            out.add(pr)
        }
        return out
    }

    private fun fillPath(proj: List<FloatArray>, canvas: Canvas, paint: Paint, path: Path): Boolean {
        if (proj.size < 3) return false
        path.reset()
        path.moveTo(proj[0][0], proj[0][1])
        for (i in 1 until proj.size) path.lineTo(proj[i][0], proj[i][1])
        path.close()
        return true
    }

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val r = lerp(Color.red(c1).toFloat(), Color.red(c2).toFloat(), t).toInt()
        val g = lerp(Color.green(c1).toFloat(), Color.green(c2).toFloat(), t).toInt()
        val b = lerp(Color.blue(c1).toFloat(), Color.blue(c2).toFloat(), t).toInt()
        return Color.rgb(r, g, b)
    }
}
