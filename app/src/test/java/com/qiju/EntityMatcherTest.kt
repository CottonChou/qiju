package com.qiju

import com.qiju.data.ha.EntityMatcher
import com.qiju.data.ha.HaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** HA 实体自动匹配逻辑 */
class EntityMatcherTest {

    private val entities = listOf(
        HaEntity("light.living_room", "on", mapOf("friendly_name" to "客厅灯")),
        HaEntity("light.bedroom", "off", mapOf("friendly_name" to "主卧灯")),
        HaEntity("switch.fan", "on", mapOf("friendly_name" to "风扇")),
        HaEntity("climate.ac", "cool", mapOf("friendly_name" to "主卧空调")),
        HaEntity("cover.curtain", "open", mapOf("friendly_name" to "客厅窗帘"))
    )

    @Test
    fun `按名称包含匹配`() {
        val m = EntityMatcher.match("light", "客厅灯", entities, emptySet())
        assertEquals("light.living_room", m?.entityId)
    }

    @Test
    fun `域名不匹配的不参与`() {
        // "风扇"是 switch 域，不应匹配给灯
        val m = EntityMatcher.match("light", "风扇", entities, emptySet())
        assertNull(m)
    }

    @Test
    fun `空调匹配 climate 域`() {
        val m = EntityMatcher.match("ac", "主卧空调", entities, emptySet())
        assertEquals("climate.ac", m?.entityId)
    }

    @Test
    fun `窗帘匹配 cover 域`() {
        val m = EntityMatcher.match("curtain", "客厅窗帘", entities, emptySet())
        assertEquals("cover.curtain", m?.entityId)
    }

    @Test
    fun `已占用实体被排除`() {
        val m = EntityMatcher.match("light", "主卧灯", entities, used = setOf("light.bedroom"))
        assertNull(m)
    }

    @Test
    fun `未知类型回退 switch 域`() {
        val m = EntityMatcher.match("something", "风扇", entities, emptySet())
        assertEquals("switch.fan", m?.entityId)
    }

    @Test
    fun `双向包含得分一致`() {
        val a = EntityMatcher.match("light", "客厅灯", entities, emptySet())
        val b = EntityMatcher.match("light", "客厅灯主灯", entities, emptySet())
        // 两个方向都能命中同一实体
        assertEquals(a?.entityId, b?.entityId)
    }
}
