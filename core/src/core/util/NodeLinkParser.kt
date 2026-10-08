/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.suanran.dreambox.core.util

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.net.URLDecoder

/**
 * 单节点链接解析器：把 vless://、vmess://、trojan://、ss://、hysteria2:// 等
 * 分享链接解析为 Mihomo/Clash 格式的 proxy Map，可直接拼成 YAML 的 proxies 列表。
 *
 * 用法：NodeLinkParser.parse("vless://...") 返回 Map，失败返回 null。
 * NodeLinkParser.parseAll(text) 按行解析，返回 (成功列表, 失败行数)。
 */
object NodeLinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析单行链接，失败返回 null。 */
    fun parse(link: String): Map<String, Any?>? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                trimmed.startsWith("hysteria2://", ignoreCase = true) ||
                    trimmed.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(trimmed)
                trimmed.startsWith("tuic://", ignoreCase = true) -> parseTuic(trimmed)
                trimmed.startsWith("socks5://", ignoreCase = true) ||
                    trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks(trimmed)
                trimmed.startsWith("http://", ignoreCase = true) &&
                    looksLikeProxyLink(trimmed) -> parseHttp(trimmed)
                else -> null
            }
        }.getOrNull()
    }

    /**
     * 按行解析多行文本。
     * @return Pair(成功解析的 proxy 列表, 解析失败的行数)
     */
    fun parseAll(text: String): Pair<List<Map<String, Any?>>, Int> {
        val ok = mutableListOf<Map<String, Any?>>()
        var failed = 0
        text.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) return@forEach
            val parsed = parse(t)
            if (parsed != null) ok.add(parsed) else failed++
        }
        return ok to failed
    }

    // ---------- vless ----------

    private fun parseVless(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val uuid = uri.userInfo?.substringBefore(":")?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val q = uri.queryParams()
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment),
            "type" to "vless",
            "server" to host,
            "port" to port,
            "uuid" to uuid,
        )
        q["encryption"]?.let { m["encryption"] = it }
        q["flow"]?.let { m["flow"] = it }
        val security = q["security"]
        if (security == "tls" || security == "reality") {
            m["tls"] = true
            q["sni"]?.let { m["servername"] = it }
            if (security == "reality") {
                m["reality-opts"] = linkedMapOf(
                    "public-key" to (q["pbk"] ?: ""),
                    "short-id" to (q["sid"] ?: ""),
                )
            }
            q["fp"]?.let { m["client-fingerprint"] = it }
            if (q["insecure"] == "1") m["skip-cert-verify"] = true
        }
        applyTransport(m, q)
        return m
    }

    // ---------- vmess ----------

    private fun parseVmess(link: String): Map<String, Any?>? {
        val b64 = link.substringAfter("vmess://").substringBefore("#").trim()
        if (b64.isEmpty()) return null
        val decoded = decodeBase64(b64) ?: return null
        val o = JSONObject(decoded)
        val host = o.optString("add").takeIf { it.isNotBlank() } ?: return null
        val port = o.optString("port").toIntOrNull() ?: return null
        val m = linkedMapOf<String, Any?>(
            "name" to o.optString("ps").ifBlank { "$host:$port" },
            "type" to "vmess",
            "server" to host,
            "port" to port,
            "uuid" to o.optString("id"),
            "alterId" to (o.optString("aid").toIntOrNull() ?: 0),
            "cipher" to o.optString("scy").ifBlank { "auto" },
        )
        val net = o.optString("net")
        if (net.isNotBlank() && net != "tcp") {
            m["network"] = net
            when (net) {
                "ws" -> {
                    val wsOpts = linkedMapOf<String, Any?>()
                    o.optString("path").takeIf { it.isNotBlank() }?.let { wsOpts["path"] = it }
                    val hostHeader = o.optString("host").takeIf { it.isNotBlank() }
                    if (hostHeader != null) wsOpts["headers"] = mapOf("Host" to hostHeader)
                    if (wsOpts.isNotEmpty()) m["ws-opts"] = wsOpts
                }
                "grpc" -> {
                    m["grpc-opts"] = linkedMapOf(
                        "grpc-service-name" to o.optString("path").ifBlank { "" },
                    )
                }
                "h2" -> {
                    val h2Opts = linkedMapOf<String, Any?>()
                    o.optString("path").takeIf { it.isNotBlank() }?.let { h2Opts["path"] = it }
                    val hostHeader = o.optString("host").takeIf { it.isNotBlank() }
                    if (hostHeader != null) h2Opts["host"] = listOf(hostHeader)
                    if (h2Opts.isNotEmpty()) m["h2-opts"] = h2Opts
                }
            }
        }
        if (o.optString("tls") == "tls") {
            m["tls"] = true
            o.optString("sni").takeIf { it.isNotBlank() }?.let { m["servername"] = it }
            o.optString("fp").takeIf { it.isNotBlank() }?.let { m["client-fingerprint"] = it }
        }
        return m
    }

    // ---------- trojan ----------

    private fun parseTrojan(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val password = uri.userInfo?.substringBefore(":")?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val q = uri.queryParams()
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment),
            "type" to "trojan",
            "server" to host,
            "port" to port,
            "password" to password,
        )
        q["sni"]?.let { m["sni"] = it }
        if (q["insecure"] == "1" || q["allowInsecure"] == "1") m["skip-cert-verify"] = true
        applyTransport(m, q)
        return m
    }

    // ---------- shadowsocks ----------

    private fun parseShadowsocks(link: String): Map<String, Any?>? {
        var rest = link.substringAfter("ss://")
        // 移除 #name 片段
        val name = rest.substringAfter("#", "").let { decodeName(it.ifEmpty { null }) }
        rest = rest.substringBefore("#")
        // 移除查询参数
        rest = rest.substringBefore("?")
        // rest 可能是 base64(method:password)@host:port 或 base64(method:password@host:port)
        var method = ""
        var password = ""
        var host = ""
        var port = 0
        if (rest.contains("@")) {
            val userPart = rest.substringBefore("@")
            val hostPart = rest.substringAfter("@")
            // userPart 可能是明文 method:password 或 base64
            val decoded = if (userPart.contains(":")) userPart else decodeBase64(userPart)
            if (decoded == null) return null
            method = decoded.substringBefore(":")
            password = decoded.substringAfter(":")
            host = hostPart.substringBefore(":")
            port = hostPart.substringAfter(":").toIntOrNull() ?: return null
        } else {
            // 整体 base64
            val decoded = decodeBase64(rest) ?: return null
            // method:password@host:port
            val atIdx = decoded.lastIndexOf("@")
            if (atIdx < 0) return null
            val userPart = decoded.substring(0, atIdx)
            val hostPart = decoded.substring(atIdx + 1)
            method = userPart.substringBefore(":")
            password = userPart.substringAfter(":")
            host = hostPart.substringBefore(":")
            port = hostPart.substringAfter(":").toIntOrNull() ?: return null
        }
        if (method.isBlank() || host.isBlank() || port <= 0) return null
        return linkedMapOf(
            "name" to name.ifBlank { "$host:$port" },
            "type" to "ss",
            "server" to host,
            "port" to port,
            "cipher" to method,
            "password" to password,
        )
    }

    // ---------- hysteria2 ----------

    private fun parseHysteria2(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val password = uri.userInfo?.substringBefore(":")?.takeIf { it.isNotBlank() } ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val q = uri.queryParams()
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment),
            "type" to "hysteria2",
            "server" to host,
            "port" to port,
            "password" to password,
        )
        q["sni"]?.let { m["sni"] = it }
        if (q["insecure"] == "1") m["skip-cert-verify"] = true
        q["obfs"]?.let { obfs ->
            if (obfs != "none") {
                m["obfs"] = obfs
                q["obfs-password"]?.let { m["obfs-password"] = it }
            }
        }
        return m
    }

    // ---------- tuic ----------

    private fun parseTuic(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val userInfo = uri.userInfo ?: return null
        val uuid = userInfo.substringBefore(":").takeIf { it.isNotBlank() } ?: return null
        val password = userInfo.substringAfter(":", "")
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val q = uri.queryParams()
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment),
            "type" to "tuic",
            "server" to host,
            "port" to port,
            "uuid" to uuid,
        )
        if (password.isNotBlank()) m["password"] = password
        q["sni"]?.let { m["sni"] = it }
        if (q["insecure"] == "1" || q["allow_insecure"] == "1") m["skip-cert-verify"] = true
        return m
    }

    // ---------- socks / http ----------

    private fun parseSocks(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val userInfo = uri.userInfo ?: ""
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment).ifBlank { "$host:$port" },
            "type" to "socks5",
            "server" to host,
            "port" to port,
        )
        val username = userInfo.substringBefore(":").takeIf { it.isNotBlank() }
        val password = userInfo.substringAfter(":", "").takeIf { it.isNotBlank() }
        if (username != null) {
            m["username"] = username
            if (password != null) m["password"] = password
        }
        return m
    }

    private fun parseHttp(link: String): Map<String, Any?>? {
        val uri = Uri.parse(link) ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val userInfo = uri.userInfo ?: ""
        val m = linkedMapOf<String, Any?>(
            "name" to decodeName(uri.fragment).ifBlank { "$host:$port" },
            "type" to "http",
            "server" to host,
            "port" to port,
        )
        val username = userInfo.substringBefore(":").takeIf { it.isNotBlank() }
        val password = userInfo.substringAfter(":", "").takeIf { it.isNotBlank() }
        if (username != null) {
            m["username"] = username
            if (password != null) m["password"] = password
        }
        return m
    }

    // ---------- helpers ----------

    private fun applyTransport(m: MutableMap<String, Any?>, q: Map<String, String>) {
        when (q["type"]) {
            "ws" -> {
                m["network"] = "ws"
                val wsOpts = linkedMapOf<String, Any?>()
                q["path"]?.let { wsOpts["path"] = urlDecode(it) }
                q["host"]?.let { wsOpts["headers"] = mapOf("Host" to it) }
                if (wsOpts.isNotEmpty()) m["ws-opts"] = wsOpts
            }
            "grpc" -> {
                m["network"] = "grpc"
                m["grpc-opts"] = linkedMapOf("grpc-service-name" to (q["serviceName"] ?: ""))
            }
            "h2" -> {
                m["network"] = "h2"
                val h2Opts = linkedMapOf<String, Any?>()
                q["path"]?.let { h2Opts["path"] = urlDecode(it) }
                q["host"]?.let { h2Opts["host"] = listOf(it) }
                if (h2Opts.isNotEmpty()) m["h2-opts"] = h2Opts
            }
        }
    }

    private fun Uri.queryParams(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        queryParameterNames.forEach { name ->
            getQueryParameter(name)?.let { map[name] = it }
        }
        return map
    }

    private fun decodeName(fragment: String?): String {
        if (fragment.isNullOrBlank()) return ""
        return runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun decodeBase64(s: String): String? {
        return runCatching {
            var clean = s.trim().replace("-", "+").replace("_", "/")
            val pad = (4 - clean.length % 4) % 4
            clean += "=".repeat(pad)
            String(Base64.decode(clean, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    /** 避免把普通 http(s) 订阅链接误判为 http 代理。 */
    private fun looksLikeProxyLink(link: String): Boolean {
        val lower = link.lowercase()
        // 订阅链接通常带路径/查询且 host 不是裸 IP:port 形式；这里做保守判断：
        // 只有形如 http://user:pass@host:port 或 http://host:port 且无复杂路径时才认为是代理
        return try {
            val uri = Uri.parse(link) ?: return false
            val path = uri.path.orEmpty()
            (path.isEmpty() || path == "/") && uri.host?.isNotBlank() == true
        } catch (_: Exception) {
            false
        }
    }
}
