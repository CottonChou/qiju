package com.cottonchou.hometwin.data

/**
 * 户型数据模型 —— 参考 house-control 项目的 plan.json 思路：
 * 房间都是轴对齐矩形（L 形拆成两个矩形拼接），相邻房间之间的墙自动生成，
 * 门/窗挂在某个房间的某条边上，设备绑定到房间并带平面坐标（米）。
 * 坐标单位：米。y 轴对应 3D 世界的 Z 轴（平面朝下为 +y）。
 */
data class Plan(
    val version: Int = 1,
    val home: Home = Home(),
    val rooms: List<Room> = emptyList(),
    val openings: List<Opening> = emptyList(),
    val devices: List<Device> = emptyList(),
    val scenes: List<Scene> = emptyList()
)

data class Home(
    val name: String = "我的家",
    val wallHeight: Float = 2.6f
)

data class Room(
    val id: String,
    val name: String,
    val type: String = ROOM_TYPES.first(),
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val floorColor: String = "#8A7B66"
) {
    val x2: Float get() = x + w
    val y2: Float get() = y + h
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
    fun contains(px: Float, py: Float) = px >= x && px <= x2 && py >= y && py <= y2

    companion object {
        val ROOM_TYPES = listOf("living", "dining", "kitchen", "bedroom", "bath", "storage", "balcony", "hall")
        val TYPE_NAMES = mapOf(
            "living" to "客厅", "dining" to "餐厅", "kitchen" to "厨房", "bedroom" to "卧室",
            "bath" to "卫生间", "storage" to "储物", "balcony" to "阳台", "hall" to "玄关"
        )
        val TYPE_FLOOR_COLORS = mapOf(
            "living" to "#8A7B66", "dining" to "#8A7B66", "kitchen" to "#6F7B80",
            "bedroom" to "#7E7466", "bath" to "#5F6B72", "storage" to "#6E6A5E",
            "balcony" to "#7C8378", "hall" to "#7A7062"
        )
    }
}

/** edge: 0=北( y=room.y, 沿 x ), 1=东( x=room.x2, 沿 y ), 2=南( y=room.y2 ), 3=西( x=room.x )。
 *  offset 为开口中心距边起点的距离（米），width 为开口宽度。type: door / window */
data class Opening(
    val id: String,
    val roomId: String,
    val edge: Int,
    val offset: Float,
    val width: Float = 0.9f,
    val type: String = "door"
)

/** 设备类型: light / ac / curtain / sensor / camera / switch / lock */
data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val x: Float,
    val y: Float,
    val entityId: String? = null,
    val capabilities: List<String> = listOf("power", "brightness")
) {
    companion object {
        val TYPE_NAMES = mapOf(
            "light" to "灯具", "ac" to "空调", "curtain" to "窗帘", "sensor" to "传感器",
            "camera" to "相机", "switch" to "开关", "lock" to "门锁"
        )
    }
}

data class Scene(
    val id: String,
    val name: String,
    val icon: String = "home",
    val actions: List<SceneAction> = emptyList()
)

data class SceneAction(val deviceId: String, val on: Boolean, val brightness: Int? = null)

/** 运行时设备状态（不进 plan.json，落另一个 state 文件也可以，先放内存） */
data class DeviceState(
    val on: Boolean = false,
    val brightness: Int = 80,
    val temperature: Float = 26f,
    val mode: String = "cool",
    val unavailable: Boolean = false,
    /** 传感器等设备的读数（接入 HA 后为真实值） */
    val value: String = ""
)
