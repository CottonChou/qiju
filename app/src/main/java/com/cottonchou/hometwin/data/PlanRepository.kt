package com.cottonchou.hometwin.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** plan.json 存取：filesDir/plan.json，可导入导出 */
class PlanRepository(private val context: Context) {
    private val gson = Gson()
    private val planFile: File get() = File(context.filesDir, "plan.json")

    fun load(): Plan {
        return try {
            if (planFile.exists()) {
                gson.fromJson(planFile.readText(), Plan::class.java) ?: defaultPlan()
            } else defaultPlan()
        } catch (e: Exception) {
            defaultPlan()
        }
    }

    fun save(plan: Plan) {
        planFile.writeText(gson.toJson(plan))
    }

    fun exportJson(plan: Plan): String = gson.toJson(plan)

    fun importJson(json: String): Plan = gson.fromJson(json, Plan::class.java)

    companion object {
        /** 默认示例户型：三室两厅，门洞位置与真实动线一致 */
        fun defaultPlan(): Plan {
            val rooms = listOf(
                Room("living", "客厅", "living", 1.6f, 4.4f, 4.6f, 3.4f, "#8A7B66"),
                Room("dining", "餐厅", "dining", 6.2f, 4.4f, 2.4f, 3.4f, "#8A7B66"),
                Room("kitchen", "厨房", "kitchen", 8.6f, 4.4f, 1.8f, 3.4f, "#6F7B80"),
                Room("hall", "玄关与储物", "hall", 0f, 0f, 1.6f, 7.8f, "#7A7062"),
                Room("bath", "卫生间", "bath", 1.6f, 0f, 1.8f, 2.0f, "#5F6B72"),
                Room("closet", "衣帽间", "storage", 1.6f, 2.0f, 1.8f, 1.4f, "#6E6A5E"),
                Room("master", "主卧", "bedroom", 3.4f, 0f, 4.0f, 4.4f, "#7E7466"),
                Room("second", "次卧", "bedroom", 7.4f, 0f, 3.0f, 4.4f, "#7E7466"),
                Room("balcony", "阳台", "balcony", 6.2f, 7.8f, 2.4f, 1.6f, "#7C8378")
            )
            val openings = listOf(
                Opening("entrance", "hall", 3, 6.4f, 1.0f, "door"),   // 入户门（西外墙）
                Opening("d_bath", "bath", 3, 1.0f, 0.7f, "door"),     // 卫生间 -> 玄关
                Opening("d_closet", "closet", 3, 0.7f, 0.7f, "door"), // 衣帽间 -> 玄关
                Opening("d_living", "hall", 1, 6.0f, 1.0f, "door"),   // 玄关 -> 客厅
                Opening("d_master", "master", 2, 1.0f, 0.9f, "door"), // 主卧 -> 客厅
                Opening("d_second", "second", 2, 1.5f, 0.9f, "door"), // 次卧 -> 餐厅
                Opening("d_kitchen", "kitchen", 3, 1.7f, 0.9f, "door"),// 厨房 -> 餐厅
                Opening("d_balcony", "balcony", 0, 1.2f, 1.2f, "door"),// 阳台推拉门
                Opening("w_living", "living", 2, 2.3f, 2.4f, "window"),// 客厅南向大窗
                Opening("w_master", "master", 0, 2.0f, 1.8f, "window"),
                Opening("w_second", "second", 0, 1.5f, 1.5f, "window"),
                Opening("w_second_e", "second", 1, 2.2f, 1.5f, "window"),
                Opening("w_kitchen", "kitchen", 1, 1.7f, 1.2f, "window"),
                Opening("w_dining", "dining", 2, 1.2f, 1.5f, "window"),
                Opening("w_balcony", "balcony", 2, 1.2f, 1.8f, "window")
            )
            val devices = listOf(
                Device("light.living", "客厅灯", "light", "living", 3.9f, 6.1f),
                Device("light.hall", "玄关灯", "light", "hall", 0.8f, 6.0f),
                Device("light.master", "主卧灯", "light", "master", 5.4f, 2.2f),
                Device("light.closet", "衣帽间灯", "light", "closet", 2.5f, 2.7f),
                Device("light.dining", "餐厅灯", "light", "dining", 7.4f, 6.1f),
                Device("ac.living", "客厅空调", "ac", "living", 2.0f, 4.7f,
                    capabilities = listOf("power", "temperature", "mode", "fan")),
                Device("ac.master", "主卧空调", "ac", "master", 7.0f, 0.4f,
                    capabilities = listOf("power", "temperature", "mode", "fan")),
                Device("curtain.living", "客厅窗帘", "curtain", "living", 3.9f, 7.4f),
                Device("sensor.living", "客厅温湿度", "sensor", "living", 5.8f, 4.8f,
                    capabilities = listOf("read")),
                Device("camera.hall", "玄关相机", "camera", "hall", 0.5f, 5.5f),
                Device("lock.hall", "入户门锁", "lock", "hall", 0.3f, 6.5f)
            )
            val scenes = listOf(
                Scene("home", "归家", "home", listOf(
                    SceneAction("light.living", true, 90),
                    SceneAction("light.hall", true, 70)
                )),
                Scene("away", "离家", "logout", listOf(
                    SceneAction("light.living", false),
                    SceneAction("light.hall", false),
                    SceneAction("light.master", false),
                    SceneAction("light.closet", false),
                    SceneAction("light.dining", false),
                    SceneAction("ac.living", false),
                    SceneAction("ac.master", false)
                )),
                Scene("sleep", "睡眠", "bedtime", listOf(
                    SceneAction("light.living", false),
                    SceneAction("light.hall", false),
                    SceneAction("light.master", false),
                    SceneAction("light.closet", false),
                    SceneAction("light.dining", false)
                ))
            )
            return Plan(rooms = rooms, openings = openings, devices = devices, scenes = scenes)
        }
    }
}
