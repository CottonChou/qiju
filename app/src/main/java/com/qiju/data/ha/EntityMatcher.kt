package com.qiju.data.ha

/**
 * 设备 ↔ HA 实体的自动匹配（纯逻辑，便于单元测试）：
 * 域名匹配 + friendly_name 包含关系打分。
 */
object EntityMatcher {

    /** 设备类型 → 可匹配的 HA 域名 */
    val candidateDomains: Map<String, List<String>> = mapOf(
        "light" to listOf("light"),
        "ac" to listOf("climate"),
        "curtain" to listOf("cover"),
        "sensor" to listOf("sensor", "binary_sensor"),
        "camera" to listOf("camera"),
        "switch" to listOf("switch", "input_boolean"),
        "lock" to listOf("lock")
    )

    /**
     * 为一个设备挑选最合适的实体。
     * 打分：双向名称包含 = 10 + 较短名长度；仅前两字命中 = 3。
     */
    fun match(deviceType: String, deviceName: String, candidates: List<HaEntity>, used: Set<String>): HaEntity? {
        val domains = candidateDomains[deviceType] ?: listOf("switch")
        var best: HaEntity? = null
        var bestScore = 0
        candidates.filter { it.domain in domains && it.entityId !in used }.forEach { e ->
            var score = 0
            val dn = deviceName.replace(" ", "")
            val en = e.friendlyName.replace(" ", "")
            if (en.contains(dn) || dn.contains(en)) {
                score = 10 + minOf(en.length, dn.length)
            } else if (dn.length >= 2 && en.contains(dn.substring(0, 2))) {
                score = 3
            }
            if (score > bestScore) {
                bestScore = score
                best = e
            }
        }
        return best
    }
}
