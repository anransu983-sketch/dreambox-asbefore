package com.suanran.dreambox.feature.settings.presentation.module

import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻模块（Loon 式 .plugin 的梦盒版）：一份 JSON 就是一个规则包。
 *
 * v1 只支持纯规则模块：[rules] 里的每一行都是一条 mihomo 规则，
 * 策略只允许 DIRECT / REJECT 系列 / PROXY（学 Loon 的限制）。
 * 重写 / 脚本 / MITM 不在 v1 里，[ruleProviders] 非空的模块安装时直接拒掉，
 * 不静默丢弃，避免“装上了却没生效”的假成功。
 */
data class ModuleDef(
    val id: String,
    val name: String,
    val desc: String,
    val author: String,
    val homepage: String,
    val version: String,
    val category: String,
    /** 安装来源：市场填 ""，URL 安装填用户粘贴的地址。 */
    val sourceUrl: String,
    val rules: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("desc", desc)
        .put("author", author)
        .put("homepage", homepage)
        .put("version", version)
        .put("category", category)
        .put("kind", "rules")
        .put("sourceUrl", sourceUrl)
        .put("rules", JSONArray(rules))
        .put("ruleProviders", JSONArray())

    companion object {
        val ALLOWED_STRATEGIES = setOf(
            "DIRECT", "REJECT", "REJECT-DROP", "REJECT-IMG", "REJECT-DICT", "REJECT-ARRAY", "PROXY",
        )

        /** v1 允许的规则类型：AND/OR/NOT 这类复合规则不在内，写了就拒。 */
        val ALLOWED_TYPES = setOf(
            "DOMAIN", "DOMAIN-SUFFIX", "DOMAIN-KEYWORD", "DOMAIN-REGEX",
            "IP-CIDR", "IP-CIDR6", "GEOIP", "IP-ASN",
            "SRC-PORT", "DST-PORT", "USER-AGENT", "URL-REGEX", "PROCESS-NAME",
        )

        private val ID_REGEX = Regex("^[a-z0-9][a-z0-9_-]*$")

        /** 校验一条规则行，返回 null 表示通过，否则返回原因。 */
        fun validateRule(line: String): String? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            val parts = trimmed.split(",")
            if (parts.size < 3) return "规则格式不对：$trimmed"
            if (parts[0].trim().uppercase() !in ALLOWED_TYPES) return "v1 不支持的规则类型：${parts[0].trim()}"
            if (parts[2].trim().uppercase() !in ALLOWED_STRATEGIES) return "只允许 DIRECT / REJECT / PROXY 策略：$trimmed"
            return null
        }

        fun parse(json: JSONObject): ModuleDef {
            val id = json.optString("id").trim()
            require(id.matches(ID_REGEX)) { "模块 id 不合法：$id" }
            val name = json.optString("name").trim()
            require(name.isNotEmpty()) { "模块缺少 name" }
            val version = json.optString("version").trim()
            require(version.isNotEmpty()) { "模块缺少 version" }
            val kind = json.optString("kind", "rules").trim()
            require(kind == "rules") { "v1 只支持 rules 模块，不支持 kind=$kind" }
            if (json.optJSONArray("ruleProviders")?.length() ?: 0 > 0) {
                throw IllegalArgumentException("v1 暂不支持远程规则源（ruleProviders），只支持内联 rules")
            }
            val rules = mutableListOf<String>()
            val arr = json.optJSONArray("rules") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val line = arr.optString(i).trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                validateRule(line)?.let { throw IllegalArgumentException(it) }
                rules += line
            }
            require(rules.isNotEmpty()) { "模块没有可用规则" }
            return ModuleDef(
                id = id,
                name = name,
                desc = json.optString("desc"),
                author = json.optString("author", "未知"),
                homepage = json.optString("homepage"),
                version = version,
                category = json.optString("category", "其他").ifBlank { "其他" },
                sourceUrl = json.optString("sourceUrl"),
                rules = rules,
            )
        }

        fun parseText(text: String): ModuleDef = parse(JSONObject(text))

        /** 远程索引：{"modules": [...]} 或直接 [...] 两种都认。 */
        fun parseIndex(text: String): List<ModuleDef> {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                return List(arr.length()) { parse(arr.getJSONObject(it)) }
            }
            val root = JSONObject(trimmed)
            val arr = root.optJSONArray("modules") ?: return emptyList()
            return List(arr.length()) { parse(arr.getJSONObject(it)) }
        }
    }
}
