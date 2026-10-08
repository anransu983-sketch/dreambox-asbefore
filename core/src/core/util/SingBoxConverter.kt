/*
 * This file is part of DreamBox.
 *
 * DreamBox is a rebranded fork of FlyCat/YumeBox by YumeYucca (lm-firefly).
 * Original: https://github.com/lm-firefly/yumebox
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * Modified for DreamBox: adds best-effort sing-box JSON -> Mihomo proxy
 * config conversion.
 */

package com.suanran.dreambox.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/**
 * Best-effort converter: sing-box JSON config (`outbounds`) -> Mihomo proxy
 * list YAML (`proxies:`). Unknown / unsupported fields are ignored and logged;
 * conversion never throws for malformed input (returns null instead).
 */
object SingBoxConverter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ConvertResult(
        val yaml: String,
        val converted: Int,
        val skipped: Int,
        val warnings: List<String>,
    )

    /** True when [text] looks like a sing-box JSON config (has an `outbounds` array). */
    fun isSingBoxConfig(text: String): Boolean =
        try {
            val trimmed = text.trimStart()
            if (!trimmed.startsWith("{")) return false
            val root = json.parseToJsonElement(trimmed).jsonObject
            root["outbounds"] is JsonArray
        } catch (_: Exception) {
            false
        }

    /** Convert sing-box JSON text to a Mihomo `proxies:` YAML document, or null if not convertible. */
    fun convert(text: String): ConvertResult? =
        try {
            val root = json.parseToJsonElement(text.trim()).jsonObject
            val outbounds = root["outbounds"] as? JsonArray ?: return null
            convertOutbounds(outbounds)
        } catch (e: Exception) {
            Timber.w(e, "SingBoxConverter: failed to parse input")
            null
        }

    private fun convertOutbounds(outbounds: JsonArray): ConvertResult? {
        val warnings = mutableListOf<String>()
        val proxies = mutableListOf<Map<String, Any?>>()
        val usedNames = mutableSetOf<String>()
        var skipped = 0
        outbounds.forEachIndexed { index, element ->
            val ob = element as? JsonObject ?: run { skipped++; return@forEachIndexed }
            val proxy =
                try {
                    convertOutbound(ob, warnings)
                } catch (e: Exception) {
                    warnings += "outbound #$index (${ob.str("tag") ?: ob.str("type")}): ${e.message}"
                    Timber.w(e, "SingBoxConverter: failed to convert outbound #$index")
                    null
                }
            if (proxy == null) {
                skipped++
            } else {
                val baseName = (proxy["name"] as? String).orEmpty().ifBlank { "sb-${proxy["type"]}-$index" }
                var name = baseName
                var dup = 2
                while (!usedNames.add(name)) name = "$baseName-${dup++}"
                proxies += proxy + ("name" to name)
            }
        }
        if (proxies.isEmpty()) return null
        val doc =
            mapOf(
                "proxies" to proxies.map { deepClean(it) },
            )
        val yaml =
            buildString {
                appendLine("# Converted from sing-box JSON by DreamBox (best-effort).")
                appendLine("# ${proxies.size} proxies converted, $skipped skipped.")
                if (warnings.isNotEmpty()) {
                    appendLine("# Warnings:")
                    warnings.take(20).forEach { appendLine("# - ${it.take(160)}") }
                }
                append(YamlCodec.dumpMap(doc))
            }
        return ConvertResult(yaml, proxies.size, skipped, warnings)
    }

    private fun convertOutbound(ob: JsonObject, warnings: MutableList<String>): Map<String, Any?>? {
        val tag = ob.str("tag").orEmpty()
        return when (ob.str("type")) {
            "vless" -> {
                val m = linkedMapOf<String, Any?>("type" to "vless")
                m.putCommon(ob, tag)
                m["uuid"] = ob.str("uuid")
                ob.str("flow")?.ifBlank { null }?.let { m["flow"] = it }
                applyTls(ob, m, warnings, tag)
                applyTransport(ob, m, warnings, tag)
                m["packet-encoding"] = ob.str("packet_encoding")
                m
            }
            "vmess" -> {
                val m = linkedMapOf<String, Any?>("type" to "vmess")
                m.putCommon(ob, tag)
                m["uuid"] = ob.str("uuid")
                ob.int("alter_id")?.let { m["alterId"] = it }
                ob.str("security")?.let { m["cipher"] = it }
                applyTls(ob, m, warnings, tag)
                applyTransport(ob, m, warnings, tag)
                m
            }
            "trojan" -> {
                val m = linkedMapOf<String, Any?>("type" to "trojan")
                m.putCommon(ob, tag)
                m["password"] = ob.str("password")
                applyTls(ob, m, warnings, tag)
                applyTransport(ob, m, warnings, tag)
                m
            }
            "shadowsocks" -> {
                val m = linkedMapOf<String, Any?>("type" to "ss")
                m.putCommon(ob, tag)
                m["cipher"] = ob.str("method")
                m["password"] = ob.str("password")
                if (ob.obj("plugin") != null) warnings += "[$tag] shadowsocks plugin ignored (unsupported by mihomo)"
                m
            }
            "hysteria2" -> {
                val m = linkedMapOf<String, Any?>("type" to "hysteria2")
                m.putCommon(ob, tag)
                m["password"] = ob.str("password") ?: ob.str("auth")
                ob.obj("obfs")?.let { obfs ->
                    obfs.str("type")?.let { m["obfs"] = it }
                    obfs.str("password")?.let { m["obfs-password"] = it }
                }
                ob.int("up_mbps")?.let { m["up"] = "$it Mbps" }
                ob.int("down_mbps")?.let { m["down"] = "$it Mbps" }
                applyTls(ob, m, warnings, tag)
                m
            }
            "hysteria" -> {
                val m = linkedMapOf<String, Any?>("type" to "hysteria")
                m.putCommon(ob, tag)
                val auth = ob.obj("auth")
                m["auth-str"] = ob.str("auth_str") ?: auth?.str("value") ?: auth?.str("password")
                // sing-box v1 obfs is a plain string (obfs password); mihomo hysteria uses `obfs` the same way.
                ob.str("obfs")?.let { m["obfs"] = it }
                ob.int("up_mbps")?.let { m["up"] = "$it Mbps" }
                ob.int("down_mbps")?.let { m["down"] = "$it Mbps" }
                ob.str("recv_window_conn")?.let { warnings += "[$tag] hysteria recv_window_conn ignored" }
                applyTls(ob, m, warnings, tag)
                m
            }
            "tuic" -> {
                val m = linkedMapOf<String, Any?>("type" to "tuic")
                m.putCommon(ob, tag)
                m["token"] = ob.str("uuid")
                m["password"] = ob.str("password")
                ob.str("congestion_control")?.let { m["congestion-controller"] = it }
                ob.str("udp_relay_mode")?.let { m["udp-relay-mode"] = it }
                ob.int("udp_over_stream")?.let { /* mihomo uses udp-relay-mode instead */ }
                applyTls(ob, m, warnings, tag)
                m
            }
            "wireguard" -> {
                val m = linkedMapOf<String, Any?>("type" to "wireguard")
                m.putCommon(ob, tag)
                m["private-key"] = ob.str("private_key")
                // sing-box local_address is usually a list like ["10.0.0.2/32"]; mihomo accepts string or list.
                when (val la = ob["local_address"]) {
                    is JsonArray -> la.mapNotNull { it.str() }.takeIf { it.isNotEmpty() }?.let { m["ip"] = it }
                    is JsonPrimitive -> la.takeIf { it.isString }?.content?.let { m["ip"] = it }
                    else -> Unit
                }
                ob.int("mtu")?.let { m["mtu"] = it }
                val peers =
                    ob.arr("peers")?.mapNotNull { pe ->
                        val p = pe as? JsonObject ?: return@mapNotNull null
                        val ep = p.str("server")?.let { s -> "$s:${p.int("server_port") ?: 0}" }
                        linkedMapOf<String, Any?>(
                            "public-key" to p.str("public_key"),
                            "endpoint" to ep,
                        ).apply {
                            p.str("pre_shared_key")?.let { put("pre-shared-key", it) }
                            p.arr("allowed_ips")?.mapNotNull { it.str() }?.let { put("allowed-ips", it) }
                        }
                    }
                if (!peers.isNullOrEmpty()) m["peers"] = peers
                warnings += "[$tag] wireguard: verify endpoint/allowed-ips after import"
                m
            }
            "anytls" -> {
                warnings += "[$tag] anytls has no mihomo equivalent, skipped"
                null
            }
            "selector", "urltest", "direct", "block", "dns" -> {
                warnings += "[$tag] outbound type '${ob.str("type")}' is not a proxy, skipped"
                null
            }
            else -> {
                warnings += "[$tag] unsupported outbound type '${ob.str("type")}', skipped"
                null
            }
        }
    }

    private fun MutableMap<String, Any?>.putCommon(ob: JsonObject, tag: String) {
        put("name", tag)
        ob.str("server")?.let { put("server", it) }
        ob.int("server_port")?.let { put("port", it) }
        put("udp", true)
    }

    private fun applyTls(ob: JsonObject, m: MutableMap<String, Any?>, warnings: MutableList<String>, tag: String) {
        val tls = ob.obj("tls") ?: return
        if (tls.bool("enabled") == false) return
        m["tls"] = true
        tls.str("server_name")?.let { m["servername"] = it }
        if (tls.bool("insecure") == true) m["skip-cert-verify"] = true
        tls.arr("alpn")?.mapNotNull { it.str() }?.takeIf { it.isNotEmpty() }?.let { m["alpn"] = it }
        tls.obj("utls")?.str("fingerprint")?.let { m["fingerprint"] = it }
        tls.obj("reality")?.let { reality ->
            if (reality.bool("enabled") != false) {
                val opts = linkedMapOf<String, Any?>()
                reality.str("public_key")?.let { opts["public-key"] = it }
                reality.str("short_id")?.let { opts["short-id"] = it }
                if (opts.isNotEmpty()) m["reality-opts"] = opts
            }
        }
    }

    private fun applyTransport(ob: JsonObject, m: MutableMap<String, Any?>, warnings: MutableList<String>, tag: String) {
        val transport = ob.obj("transport") ?: return
        when (transport.str("type")) {
            "ws" -> {
                val opts = linkedMapOf<String, Any?>()
                transport.str("path")?.let { opts["path"] = it }
                transport.obj("headers")?.let { headers ->
                    val host = headers.str("Host") ?: headers.str("host")
                    if (host != null) opts["headers"] = mapOf("Host" to host)
                }
                transport.int("max_early_data")?.let { opts["max-early-data"] = it }
                transport.str("early_data_header_name")?.let { opts["early-data-header-name"] = it }
                if (opts.isNotEmpty()) m["ws-opts"] = opts
            }
            "grpc" -> {
                val opts = linkedMapOf<String, Any?>()
                transport.str("service_name")?.let { opts["grpc-service-name"] = it }
                if (opts.isNotEmpty()) m["grpc-opts"] = opts
            }
            "http" -> {
                val opts = linkedMapOf<String, Any?>()
                transport.str("method")?.let { opts["method"] = it }
                transport.arr("path")?.mapNotNull { it.str() }?.firstOrNull()
                    ?: transport.str("path")?.let { opts["path"] = it }
                transport.arr("host")?.mapNotNull { it.str() }?.takeIf { it.isNotEmpty() }?.let { opts["host"] = it }
                if (opts.isNotEmpty()) m["http-opts"] = opts
            }
            "tcp" -> {
                val header = transport.obj("header") ?: return
                if (header.str("type") == "http") {
                    val request = header.obj("request") ?: return
                    val opts = linkedMapOf<String, Any?>()
                    request.str("method")?.let { opts["method"] = it }
                    request.arr("path")?.mapNotNull { it.str() }?.firstOrNull()?.let { opts["path"] = it }
                    request.obj("headers")?.let { headers ->
                        val host = headers.str("Host") ?: headers.str("host")
                        if (host != null) opts["headers"] = mapOf("Host" to host)
                    }
                    if (opts.isNotEmpty()) m["http-opts"] = opts
                }
            }
            else -> warnings += "[$tag] transport '${transport.str("type")}' not mapped, ignored"
        }
    }

    /** Recursively drop null map values / null list items so YAML stays clean. */
    private fun deepClean(value: Any?): Any? =
        when (value) {
            is Map<*, *> ->
                LinkedHashMap<String, Any?>().apply {
                    value.forEach { (k, v) ->
                        val cleaned = deepClean(v)
                        if (cleaned != null) put(k.toString(), cleaned)
                    }
                }
            is Iterable<*> -> value.mapNotNull(::deepClean)
            else -> value
        }

    // ---- tiny JsonObject helpers (null-safe, never throw) ----
    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        when (val p = this[key] as? JsonPrimitive) {
            null -> null
            else -> p.intOrNull ?: p.longOrNull?.toInt() ?: p.doubleOrNull?.toInt()
        }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}
