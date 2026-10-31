package com.cottonchou.qiju.data.ha

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** HA 实体的精简快照 */
data class HaEntity(
    val entityId: String,
    val state: String,
    val attributes: Map<String, String>
) {
    val domain: String get() = entityId.substringBefore(".")
    val friendlyName: String get() = attributes["friendly_name"] ?: entityId
}

/**
 * Home Assistant 客户端：
 * - REST：GET /api/states 拉取全量实体；POST /api/services/... 下发控制
 * - WebSocket：/api/websocket 鉴权后订阅 state_changed，实时同步
 */
class HaClient(private val baseUrl: String, private val token: String) {

    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private fun rest(path: String) = baseUrl.trimEnd('/') + path

    private fun parseEntity(obj: JsonObject): HaEntity {
        val attrs = mutableMapOf<String, String>()
        obj.getAsJsonObject("attributes")?.entrySet()?.forEach { (k, v) ->
            if (v.isJsonPrimitive) attrs[k] = v.asJsonPrimitive.asString
        }
        return HaEntity(
            entityId = obj.get("entity_id")?.asString ?: "",
            state = obj.get("state")?.asString ?: "unknown",
            attributes = attrs
        )
    }

    /** 拉取全部实体，失败返回 null */
    fun fetchStates(): List<HaEntity>? {
        return try {
            val req = Request.Builder()
                .url(rest("/api/states"))
                .addHeader("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val root = JsonParser.parseString(resp.body!!.string()).asJsonArray
                root.mapNotNull { el ->
                    if (el.isJsonObject) parseEntity(el.asJsonObject) else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 调用服务，返回是否成功 */
    fun callService(domain: String, service: String, payload: JsonObject): Boolean {
        return try {
            val req = Request.Builder()
                .url(rest("/api/services/$domain/$service"))
                .addHeader("Authorization", "Bearer $token")
                .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /** 建立 WebSocket 并订阅 state_changed；onEntity 收到每条状态更新 */
    fun connectWebSocket(
        onEntity: (HaEntity) -> Unit,
        onStatus: (connected: Boolean, message: String) -> Unit
    ): WebSocket {
        val wsUrl = baseUrl.trimEnd('/').replace("http://", "ws://").replace("https://", "wss://") + "/api/websocket"
        val req = Request.Builder().url(wsUrl).build()
        return http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val obj = JsonParser.parseString(text).asJsonObject
                    when (obj.get("type")?.asString) {
                        "auth_required" -> {
                            val auth = JsonObject().apply {
                                addProperty("type", "auth")
                                addProperty("access_token", token)
                            }
                            ws.send(auth.toString())
                        }
                        "auth_ok" -> {
                            onStatus(true, "实时通道已建立")
                            ws.send("""{"id":1,"type":"subscribe_events","event_type":"state_changed"}""")
                        }
                        "auth_invalid" -> onStatus(false, "令牌无效")
                        "event" -> {
                            val data = obj.getAsJsonObject("event")?.getAsJsonObject("data") ?: return
                            val ns = data.getAsJsonObject("new_state") ?: return
                            onEntity(parseEntity(ns))
                        }
                    }
                } catch (e: Exception) {
                    // 忽略无法解析的消息
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, resp: Response?) {
                onStatus(false, "连接断开：${t.message ?: "未知错误"}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onStatus(false, "连接已关闭")
            }
        })
    }

    fun shutdown() {
        http.dispatcher.executorService.shutdown()
    }

    companion object {
        /** 探测地址可达性与鉴权：200=可用 401=地址通但令牌错 -1=不可达 */
        fun probe(baseUrl: String, token: String): Int {
            return try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(6, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/api/")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().use { resp ->
                    when {
                        resp.isSuccessful -> 200
                        resp.code == 401 -> 401
                        else -> -1
                    }
                }
            } catch (e: Exception) {
                -1
            }
        }
    }
}
