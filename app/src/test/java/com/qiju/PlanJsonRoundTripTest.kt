package com.qiju

import com.qiju.data.Plan
import com.qiju.data.PlanRepository
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan.json 序列化往返（Moshi：尊重 Kotlin 默认值与可空性） */
class PlanJsonRoundTripTest {

    private val gson = Gson()

    @Test
    fun `默认户型序列化后无损还原`() {
        val plan = PlanRepository.defaultPlan()
        val json = gson.toJson(plan)
        val back = PlanRepository.parsePlan(json)
        assertNotNull(back)
        back!!
        assertEquals(plan.rooms, back.rooms)
        assertEquals(plan.openings, back.openings)
        assertEquals(plan.devices.map { it.id }, back.devices.map { it.id })
        assertEquals(plan.scenes.map { it.id }, back.scenes.map { it.id })
        assertEquals(plan.home, back.home)
    }

    @Test
    fun `缺失可选字段用默认值兜底`() {
        val json = """{"rooms":[{"id":"a","name":"房间A","x":0,"y":0,"w":2,"h":2}]}"""
        val p = PlanRepository.parsePlan(json)
        assertNotNull(p)
        p!!
        assertEquals(1, p.rooms.size)
        assertEquals("living", p.rooms[0].type)
        assertEquals("#8A7B66", p.rooms[0].floorColor)
        assertTrue(p.openings.isEmpty())
        assertTrue(p.devices.isEmpty())
        assertTrue(p.scenes.isEmpty())
        assertEquals(2.6f, p.home.wallHeight, 1e-6f)
    }

    @Test
    fun `坏数据返回 null 而不是注入 null`() {
        assertNull(PlanRepository.parsePlan("not json at all"))
        assertNull(PlanRepository.parsePlan("""{"rooms":"oops"}"""))
    }
}
