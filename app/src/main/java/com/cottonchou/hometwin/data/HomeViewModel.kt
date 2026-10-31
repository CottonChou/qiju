package com.cottonchou.hometwin.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cottonchou.hometwin.data.ha.HaClient
import com.cottonchou.hometwin.data.ha.HaEntity
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket

/** 全局状态：户型、设备状态、昼夜模式、HA 连接、选中房间/设备 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlanRepository(app)
    private val prefs = app.getSharedPreferences("hometwin", Context.MODE_PRIVATE)

    var plan by mutableStateOf(repo.load())
        private set

    val deviceStates = mutableStateMapOf<String, DeviceState>().apply {
        plan.devices.forEach { put(it.id, DeviceState()) }
    }

    /** 0=深夜, 1=白天；切换时由 UI 动画驱动 */
    var dayFactor by mutableStateOf(0f)

    var targetDay by mutableStateOf(false)

    var selectedRoomId by mutableStateOf<String?>(null)

    var selectedDeviceId by mutableStateOf<String?>(null)

    var lastSceneId by mutableStateOf<String?>(null)

    // ---- Home Assistant 连接 ----
    var haUrl by mutableStateOf(prefs.getString("ha_url", "") ?: "")
    var haRemoteUrl by mutableStateOf(prefs.getString("ha_remote", "") ?: "")
    var haToken by mutableStateOf(prefs.getString("ha_token", "") ?: "")
    var haConnected by mutableStateOf(false)
    var haChannel by mutableStateOf("")   // 局域网 / 远程
    var haStatus by mutableStateOf("未连接")

    private var haClient: HaClient? = null
    private var haWs: WebSocket? = null
    private var haEntities: List<HaEntity> = emptyList()
    private var wantConnected = false
    private var reconnectJob: Job? = null

    fun state(id: String): DeviceState = deviceStates[id] ?: DeviceState()

    // ================= Home Assistant =================

    fun saveHaSettings() {
        prefs.edit()
            .putString("ha_url", haUrl.trim())
            .putString("ha_remote", haRemoteUrl.trim())
            .putString("ha_token", haToken.trim())
            .apply()
    }

    fun connectHa() {
        if (haToken.isBlank() || (haUrl.isBlank() && haRemoteUrl.isBlank())) {
            haStatus = "请至少填写一个地址和访问令牌"
            return
        }
        saveHaSettings()
        wantConnected = true
        reconnectJob?.cancel()
        connectInternal()
    }

    /** 探测顺序：局域网优先（低延迟），失败走远程；远程必须 https */
    private fun connectInternal() {
        haStatus = "正在连接…"
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = buildList {
                if (haUrl.isNotBlank()) add(haUrl.trim() to "局域网")
                if (haRemoteUrl.isNotBlank()) add(haRemoteUrl.trim() to "远程")
            }
            var picked: Pair<String, String>? = null
            var tokenBad = false
            for ((base, label) in candidates) {
                if (label == "远程" && !base.startsWith("https://")) {
                    haStatus = "远程地址需要 https（Nabu Casa / Cloudflare Tunnel）"
                    continue
                }
                haStatus = "正在尝试 $label 地址…"
                when (HaClient.probe(base, haToken.trim())) {
                    200 -> { picked = base to label; break }
                    401 -> tokenBad = true
                }
            }
            if (picked == null) {
                haStatus = if (tokenBad) "地址可达但令牌无效，请重新生成"
                else "局域网与远程地址均不可达，稍后自动重试"
                scheduleReconnect()
                return@launch
            }
            val (base, label) = picked
            haWs?.close(1000, null)
            haClient?.shutdown()
            val client = HaClient(base, haToken.trim())
            val states = client.fetchStates()
            if (states == null) {
                haStatus = "$label 地址可达但拉取实体失败"
                client.shutdown()
                scheduleReconnect()
                return@launch
            }
            haClient = client
            haEntities = states
            states.forEach { applyHaEntity(it) }
            haConnected = true
            haChannel = label
            haStatus = "已连接 · $label · ${states.size} 个实体"
            haWs = client.connectWebSocket(
                onEntity = { applyHaEntity(it) },
                onStatus = { ok, msg ->
                    if (!ok) {
                        haChannel = ""
                        if (wantConnected) {
                            haStatus = "$msg，5 秒后重连"
                            scheduleReconnect()
                        } else {
                            haStatus = msg
                        }
                    }
                }
            )
        }
    }

    private fun scheduleReconnect() {
        if (!wantConnected) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            delay(5000)
            if (wantConnected) connectInternal()
        }
    }

    fun disconnectHa() {
        wantConnected = false
        reconnectJob?.cancel()
        haWs?.close(1000, null)
        haWs = null
        haClient?.shutdown()
        haClient = null
        haConnected = false
        haChannel = ""
        haStatus = "未连接"
    }

    /** 设备类型 → HA 域名 */
    private fun domainOf(type: String): String = when (type) {
        "light" -> "light"
        "ac" -> "climate"
        "curtain" -> "cover"
        "sensor" -> "sensor"
        "camera" -> "camera"
        "lock" -> "lock"
        else -> "switch"
    }

    private val candidateDomains = mapOf(
        "light" to listOf("light"), "ac" to listOf("climate"),
        "curtain" to listOf("cover"), "sensor" to listOf("sensor", "binary_sensor"),
        "camera" to listOf("camera"), "switch" to listOf("switch", "input_boolean"),
        "lock" to listOf("lock")
    )

    /** 把 HA 实体状态映射到设备状态 */
    private fun applyHaEntity(e: HaEntity) {
        if (e.entityId.isBlank()) return
        val d = plan.devices.find { it.entityId == e.entityId } ?: return
        val unavailable = e.state == "unavailable" || e.state == "unknown"
        val on = when (e.domain) {
            "climate" -> e.state != "off"
            "lock" -> e.state == "unlocked"
            "cover" -> (e.attributes["current_position"]?.toFloatOrNull() ?: 0f) > 0f
            else -> e.state == "on"
        }
        val br = e.attributes["brightness"]?.toFloatOrNull()
            ?.let { (it / 255f * 100f).toInt().coerceIn(1, 100) }
        val cur = deviceStates[d.id] ?: DeviceState()
        deviceStates[d.id] = cur.copy(
            on = on,
            unavailable = unavailable,
            brightness = br ?: cur.brightness,
            temperature = e.attributes["temperature"]?.toFloatOrNull() ?: cur.temperature,
            value = if (e.domain == "sensor" || e.domain == "binary_sensor") {
                val unit = e.attributes["unit_of_device"] ?: e.attributes["unit_of_measurement"] ?: ""
                if (unit.isBlank()) e.state else "${e.state} $unit"
            } else cur.value
        )
    }

    /** 自动匹配：按域名 + 名称包含关系给设备绑定 entity_id，返回匹配数 */
    fun autoAssignEntities(): Int {
        if (haEntities.isEmpty()) return 0
        val used = plan.devices.mapNotNull { it.entityId }.toMutableSet()
        var matched = 0
        val updates = plan.devices.map { d ->
            if (!d.entityId.isNullOrBlank()) return@map d
            val domains = candidateDomains[d.type] ?: listOf("switch")
            var best: HaEntity? = null
            var bestScore = 0
            haEntities.filter { it.domain in domains && it.entityId !in used }.forEach { e ->
                var score = 0
                val dn = d.name.replace(" ", "")
                val en = e.friendlyName.replace(" ", "")
                if (en.contains(dn) || dn.contains(en)) score = 10 + minOf(en.length, dn.length)
                else if (dn.length >= 2 && en.contains(dn.substring(0, 2))) score = 3
                if (score > bestScore) { bestScore = score; best = e }
            }
            best?.let { used.add(it.entityId); matched++ }
            d.copy(entityId = best?.entityId)
        }
        plan = plan.copy(devices = updates)
        repo.save(plan)
        return matched
    }

    /** 手动绑定/解绑实体的入口（编辑面板也会改 plan，这里同步保存） */
    fun updateDeviceEntity(deviceId: String, entityId: String) {
        val d = plan.devices.find { it.id == deviceId } ?: return
        val bound = if (entityId.isBlank()) null else entityId.trim()
        plan = plan.copy(devices = plan.devices.map {
            if (it.id == deviceId) it.copy(entityId = bound) else it
        })
        repo.save(plan)
        bound?.let { id -> haEntities.find { it.entityId == id }?.let { applyHaEntity(it) } }
    }

    // ================= 设备控制（HA 优先，本地兜底） =================

    private fun callHa(d: Device, service: String, extra: JsonObject.() -> Unit = {}) {
        val entityId = d.entityId ?: return
        val client = haClient ?: return
        val parts = service.split("/")
        if (parts.size != 2) return
        val payload = JsonObject().apply {
            addProperty("entity_id", entityId)
            extra()
        }
        viewModelScope.launch(Dispatchers.IO) { client.callService(parts[0], parts[1], payload) }
    }

    fun toggleDevice(id: String) {
        val d = plan.devices.find { it.id == id }
        val s = deviceStates[id] ?: DeviceState()
        val newOn = !s.on
        if (haConnected && d != null && !d.entityId.isNullOrBlank()) {
            when (d.type) {
                "light" -> callHa(d, "light/${if (newOn) "turn_on" else "turn_off"}")
                "switch" -> callHa(d, "switch/${if (newOn) "turn_on" else "turn_off"}")
                "ac" -> if (newOn) callHa(d, "climate/set_hvac_mode") { addProperty("hvac_mode", "cool") }
                else callHa(d, "climate/turn_off")
                "curtain" -> callHa(d, "cover/${if (newOn) "open_cover" else "close_cover"}")
                "lock" -> callHa(d, "lock/${if (newOn) "unlock" else "lock"}")
                "camera" -> {}
                else -> callHa(d, "switch/${if (newOn) "turn_on" else "turn_off"}")
            }
        }
        deviceStates[id] = s.copy(on = newOn, unavailable = false)
    }

    fun setBrightness(id: String, v: Int) {
        val d = plan.devices.find { it.id == id }
        val s = deviceStates[id] ?: DeviceState()
        if (haConnected && d != null && !d.entityId.isNullOrBlank()) {
            callHa(d, "light/turn_on") { addProperty("brightness_pct", v.coerceIn(1, 100)) }
        }
        deviceStates[id] = s.copy(brightness = v.coerceIn(1, 100), on = true, unavailable = false)
    }

    fun setTemperature(id: String, v: Float) {
        val d = plan.devices.find { it.id == id }
        val s = deviceStates[id] ?: DeviceState()
        if (haConnected && d != null && !d.entityId.isNullOrBlank()) {
            callHa(d, "climate/set_temperature") { addProperty("temperature", v) }
        }
        deviceStates[id] = s.copy(temperature = v.coerceIn(16f, 30f))
    }

    fun applyScene(scene: Scene) {
        scene.actions.forEach { a ->
            val d = plan.devices.find { it.id == a.deviceId }
            if (haConnected && d != null && !d.entityId.isNullOrBlank()) {
                if (a.on) {
                    when (d.type) {
                        "light" -> callHa(d, "light/turn_on") {
                            a.brightness?.let { addProperty("brightness_pct", it) }
                        }
                        "ac" -> callHa(d, "climate/set_hvac_mode") { addProperty("hvac_mode", "cool") }
                        else -> callHa(d, "switch/turn_on")
                    }
                } else {
                    when (d.type) {
                        "light", "ac" -> callHa(d, "${d.type}/turn_off")
                        else -> callHa(d, "switch/turn_off")
                    }
                }
            }
            val cur = deviceStates[a.deviceId] ?: DeviceState()
            deviceStates[a.deviceId] = cur.copy(
                on = a.on, brightness = a.brightness ?: cur.brightness, unavailable = false
            )
        }
        lastSceneId = scene.id
    }

    fun toggleDayNight() {
        targetDay = !targetDay
    }

    // ---- 户型编辑 ----
    fun savePlan(p: Plan) {
        plan = p
        repo.save(p)
        // plan 变了，重新应用已连接实体的当前状态
        haEntities.forEach { applyHaEntity(it) }
    }

    fun exportPlan(): String = repo.exportJson(plan)

    fun importPlan(json: String): Boolean {
        return try {
            val p = repo.importJson(json) ?: return false
            plan = p
            repo.save(p)
            haEntities.forEach { applyHaEntity(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun resetPlan() {
        plan = PlanRepository.defaultPlan()
        repo.save(plan)
    }

    override fun onCleared() {
        haWs?.close(1000, null)
        haClient?.shutdown()
        super.onCleared()
    }
}
