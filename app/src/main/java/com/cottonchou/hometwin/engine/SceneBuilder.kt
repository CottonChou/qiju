package com.cottonchou.hometwin.engine

import com.cottonchou.hometwin.data.Opening
import com.cottonchou.hometwin.data.Plan
import kotlin.math.max
import kotlin.math.min

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = kotlin.math.sqrt(dot(this))
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) this else Vec3(x / l, y / l, z / l)
    }
    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)
}

/** 一个可渲染面：3-4 个顶点 + 法线 + 反照率。kind: 0=地板 1=墙 2=玻璃 3=地面 4=地面阴影 */
class Face(
    val pts: List<Vec3>,
    val n: Vec3,
    val albedo: Vec3,
    val kind: Int,
    val roomId: String? = null
)

data class LampSpot(val deviceId: String, val pos: Vec3)
data class Marker(val deviceId: String, val pos: Vec3, val glyph: String, val type: String)
data class Label(val roomId: String, val text: String, val pos: Vec3)

class Bounds {
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    fun add(x: Float, z: Float) {
        minX = min(minX, x); maxX = max(maxX, x)
        minZ = min(minZ, z); maxZ = max(maxZ, z)
    }
    val cx get() = (minX + maxX) / 2f
    val cz get() = (minZ + maxZ) / 2f
    val spanX get() = maxX - minX
    val spanZ get() = maxZ - minZ
}

/** 从 plan 构建的静态几何（与灯光状态无关，可缓存） */
class SceneGeometry(
    val faces: List<Face>,
    val bounds: Bounds,
    val lamps: List<LampSpot>,
    val markers: List<Marker>,
    val labels: List<Label>
)

object SceneBuilder {

    private fun parseColor(hex: String): Vec3 {
        return try {
            val c = android.graphics.Color.parseColor(hex)
            Vec3(
                android.graphics.Color.red(c) / 255f,
                android.graphics.Color.green(c) / 255f,
                android.graphics.Color.blue(c) / 255f
            )
        } catch (e: Exception) {
            Vec3(0.55f, 0.48f, 0.4f)
        }
    }

    fun build(plan: Plan, lowWalls: Boolean): SceneGeometry {
        val faces = mutableListOf<Face>()
        val bounds = Bounds()
        plan.rooms.forEach { bounds.add(it.x, it.y); bounds.add(it.x2, it.y2) }
        val H = if (lowWalls) 0.55f else plan.home.wallHeight

        val wallAlbedo = Vec3(0.85f, 0.83f, 0.78f)
        val glassAlbedo = Vec3(0.55f, 0.70f, 0.85f)
        val groundAlbedo = Vec3(0.10f, 0.12f, 0.17f)

        // 地板
        plan.rooms.forEach { r ->
            val alb = parseColor(r.floorColor)
            faces.add(Face(
                listOf(Vec3(r.x, 0f, r.y), Vec3(r.x2, 0f, r.y), Vec3(r.x2, 0f, r.y2), Vec3(r.x, 0f, r.y2)),
                Vec3(0f, 1f, 0f), alb, 0, r.id
            ))
        }

        // 大地面（比建筑范围大一圈）
        val pad = 2.5f
        faces.add(Face(
            listOf(
                Vec3(bounds.minX - pad, -0.06f, bounds.minZ - pad),
                Vec3(bounds.maxX + pad, -0.06f, bounds.minZ - pad),
                Vec3(bounds.maxX + pad, -0.06f, bounds.maxZ + pad),
                Vec3(bounds.minX - pad, -0.06f, bounds.maxZ + pad)
            ),
            Vec3(0f, 1f, 0f), groundAlbedo, 3, null
        ))

        // 白天地面阴影：沿太阳反方向偏移的平行四边形（渲染时按 dayFactor 控制 alpha）
        val sunToGround = Vec3(-0.9f, 0f, -1.15f) // 与渲染器太阳方向对应
        plan.rooms.forEach { r ->
            val s = sunToGround
            faces.add(Face(
                listOf(
                    Vec3(r.x, -0.05f, r.y), Vec3(r.x2, -0.05f, r.y),
                    Vec3(r.x2 + s.x, -0.05f, r.y + s.z), Vec3(r.x + s.x, -0.05f, r.y + s.z)
                ),
                Vec3(0f, 1f, 0f), Vec3(0f, 0f, 0f), 4, r.id
            ))
        }

        // 墙体：每个房间的四条边都生成（相邻房间会重复一面墙，画家算法下无碍）
        fun wallQuad(a: Vec3, b: Vec3, h0: Float, h1: Float, n: Vec3, roomId: String?) {
            if (h1 - h0 < 0.01f) return
            val pts = listOf(
                Vec3(a.x, h0, a.z), Vec3(b.x, h0, b.z),
                Vec3(b.x, h1, b.z), Vec3(a.x, h1, a.z)
            )
            faces.add(Face(pts, n, wallAlbedo, 1, roomId))
            faces.add(Face(pts.reversed(), -n, wallAlbedo, 1, roomId))
        }

        // edge: 0=北(y) 1=东(x2) 2=南(y2) 3=西(x)，起点/方向固定，便于开窗定位
        plan.rooms.forEach { r ->
            data class Seg(val ax: Float, val az: Float, val bx: Float, val bz: Float, val n: Vec3, val len: Float, val edge: Int)
            val segs = listOf(
                Seg(r.x, r.y, r.x2, r.y, Vec3(0f, 0f, -1f), r.w, 0),
                Seg(r.x2, r.y, r.x2, r.y2, Vec3(1f, 0f, 0f), r.h, 1),
                Seg(r.x, r.y2, r.x2, r.y2, Vec3(0f, 0f, 1f), r.w, 2),
                Seg(r.x, r.y, r.x, r.y2, Vec3(-1f, 0f, 0f), r.h, 3)
            )
            segs.forEach { seg ->
                // 每个开口：原始 id / 类型 / 沿边起点 / 沿边终点
                data class Op(val id: String, val type: String, val s0: Float, val s1: Float)
                val ops = plan.openings.filter { it.roomId == r.id && it.edge == seg.edge }
                    .map { o ->
                        val c = o.offset.coerceIn(0.2f, seg.len - o.width / 2f - 0.1f)
                        val w = min(o.width, seg.len - 0.2f)
                        Op(o.id, o.type, (c - w / 2f).coerceAtLeast(0f), (c + w / 2f).coerceAtMost(seg.len))
                    }.sortedBy { it.s0 }

                var cursor = 0f
                // 沿边分段：实墙 / 开口
                val pieces = mutableListOf<Triple<Float, Float, Op?>>()
                ops.forEach { op ->
                    if (op.s0 > cursor) pieces.add(Triple(cursor, op.s0, null))
                    pieces.add(Triple(op.s0, op.s1, op))
                    cursor = op.s1
                }
                if (cursor < seg.len) pieces.add(Triple(cursor, seg.len, null))

                val start = Vec3(seg.ax, 0f, seg.az)
                val dir = Vec3((seg.bx - seg.ax) / seg.len, 0f, (seg.bz - seg.az) / seg.len)
                pieces.forEach { (s0, s1, op) ->
                    val a = start + dir * s0
                    val b = start + dir * s1
                    if (op == null) {
                        wallQuad(a, b, 0f, H, seg.n, r.id)
                    } else if (op.type == "door") {                        val dh = min(H, 2.1f)
                        wallQuad(a, b, dh, H, seg.n, r.id)
                        if (lowWalls) wallQuad(a, b, 0f, H, seg.n, r.id) // 低墙模式门也留洞
                    } else { // window
                        if (H <= 0.6f) {
                            wallQuad(a, b, 0f, H, seg.n, r.id)
                        } else {
                            val y0 = min(0.9f, H * 0.35f)
                            val y1 = min(2.1f, H * 0.85f)
                            wallQuad(a, b, 0f, y0, seg.n, r.id)
                            wallQuad(a, b, y1, H, seg.n, r.id)
                            // 玻璃（双面）
                            val gpts = listOf(
                                Vec3(a.x, y0, a.z), Vec3(b.x, y0, b.z),
                                Vec3(b.x, y1, b.z), Vec3(a.x, y1, a.z)
                            )
                            faces.add(Face(gpts, seg.n, glassAlbedo, 2, r.id))
                            faces.add(Face(gpts.reversed(), -seg.n, glassAlbedo, 2, r.id))
                        }
                    }
                }
            }
        }

        // 灯位 / 设备标记 / 房间标签
        val lamps = plan.devices.filter { it.type == "light" }.map { LampSpot(it.id, Vec3(it.x, max(0.3f, H - 0.5f), it.y)) }
        val markers = plan.devices.map { Marker(it.id, Vec3(it.x, 0.12f, it.y), glyphFor(it.type), it.type) }
        val labels = plan.rooms.map { Label(it.id, it.name, Vec3(it.cx, 0.05f, it.cy)) }

        return SceneGeometry(faces, bounds, lamps, markers, labels)
    }

    fun glyphFor(type: String): String = when (type) {
        "light" -> "💡"; "ac" -> "❄️"; "curtain" -> "🪟"; "sensor" -> "🌡️"
        "camera" -> "📷"; "switch" -> "🔌"; "lock" -> "🔒"; else -> "⬜"
    }
}
