package com.cottonchou.qiju

import com.cottonchou.qiju.data.Device
import com.cottonchou.qiju.data.DeviceState
import com.cottonchou.qiju.ui.statusText
import org.junit.Assert.assertEquals
import org.junit.Test

/** 设备卡片状态文案 */
class StatusTextTest {

    private val light = Device("light.a", "灯", "light", "r", 0f, 0f)
    private val ac = Device("ac.a", "空调", "ac", "r", 0f, 0f)
    private val sensor = Device("sensor.a", "温湿度", "sensor", "r", 0f, 0f)

    @Test
    fun `不可用优先显示`() {
        assertEquals("设备不可用", statusText(light, DeviceState(on = true, unavailable = true)))
    }

    @Test
    fun `传感器显示读数`() {
        assertEquals("26.5 %", statusText(sensor, DeviceState(value = "26.5 %")))
    }

    @Test
    fun `关闭态`() {
        assertEquals("已关闭", statusText(light, DeviceState(on = false)))
    }

    @Test
    fun `灯具显示亮度`() {
        assertEquals("已开启 · 85%", statusText(light, DeviceState(on = true, brightness = 85)))
    }

    @Test
    fun `空调显示目标温度`() {
        assertEquals("已开启 · 26°C", statusText(ac, DeviceState(on = true, temperature = 26f)))
    }
}
