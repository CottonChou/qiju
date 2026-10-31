package com.cottonchou.qiju

import com.cottonchou.qiju.data.PlanRepository
import com.cottonchou.qiju.engine.SceneBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 场景构建（plan → 3D 网格）的几何正确性 */
class SceneBuilderGeometryTest {

    private val plan = PlanRepository.defaultPlan()

    @Test
    fun `每个房间有一块地板`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        val floors = geo.faces.filter { it.kind == 0 }
        assertEquals(plan.rooms.size, floors.size)
        floors.forEach { assertEquals(4, it.pts.size) }
    }

    @Test
    fun `窗户生成双面玻璃`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        val windows = plan.openings.count { it.type == "window" }
        val glass = geo.faces.filter { it.kind == 2 }
        assertEquals(windows * 2, glass.size)
    }

    @Test
    fun `低墙模式墙体不超过低墙高度`() {
        val geo = SceneBuilder.build(plan, lowWalls = true)
        val walls = geo.faces.filter { it.kind == 1 }
        assertTrue(walls.isNotEmpty())
        walls.forEach { f -> f.pts.forEach { p -> assertTrue(p.y <= 0.56f) } }
    }

    @Test
    fun `全墙模式墙体达到墙高`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        val walls = geo.faces.filter { it.kind == 1 }
        assertTrue(walls.any { f -> f.pts.any { p -> abs(p.y - plan.home.wallHeight) < 1e-3f } })
    }

    @Test
    fun `边界为所有房间的外包框`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        assertEquals(10.4f, geo.bounds.spanX, 1e-3f)
        assertEquals(9.4f, geo.bounds.spanZ, 1e-3f)
    }

    @Test
    fun `灯位与设备标记齐全`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        assertEquals(plan.devices.count { it.type == "light" }, geo.lamps.size)
        assertEquals(plan.devices.size, geo.markers.size)
        assertEquals(plan.rooms.size, geo.labels.size)
    }

    @Test
    fun `颜色解析为归一化 RGB`() {
        val c = SceneBuilder.parseColor("#8A7B66")
        assertEquals(0x8A / 255f, c.x, 1e-3f)
        assertEquals(0x7B / 255f, c.y, 1e-3f)
        assertEquals(0x66 / 255f, c.z, 1e-3f)
        // 非法输入回退默认色
        val bad = SceneBuilder.parseColor("not-a-color")
        assertTrue(bad.x > 0f && bad.x < 1f)
    }

    @Test
    fun `门洞两侧留实墙`() {
        val geo = SceneBuilder.build(plan, lowWalls = false)
        val hallDoor = plan.openings.first { it.id == "d_master" }
        val room = plan.rooms.first { it.id == hallDoor.roomId }
        // 门开在南边，南墙位置应存在高度到墙顶的墙（门上方的过梁）
        val lintels = geo.faces.filter { it.kind == 1 && it.roomId == room.id }
            .filter { f -> f.pts.any { p -> abs(p.y - plan.home.wallHeight) < 1e-3f } }
        assertTrue(lintels.isNotEmpty())
    }
}
