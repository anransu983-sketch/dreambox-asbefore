package com.suanran.dreambox.feature.settings.data.vps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

/**
 * s-ui (alireza0/s-ui, sing-box) 面板 API 客户端。
 *
 * 基于官方 Wiki 的 `/apiv2` Token 认证 REST API：
 * - Base: `<panelUrl>/apiv2/`（如 `http://host:2095/app/apiv2/`）
 * - 认证：请求头 `Token: <API Token>`（Token 需在面板 管理员页面 手动创建）
 * - 响应统一信封：`{"success":bool,"msg":str,"obj":...}`（失败也返回 HTTP 200）
 *
 * 自动建节点流程：
 * 1. `GET keypairs?k=reality` 生成 Reality 密钥对
 * 2. `POST save`（object=inbounds, action=new）创建 VLESS+Reality inbound
 * 3. 拼出 `vless://` 分享链接
 */
class SuiApiClient {

    data class RealityKeypair(val privateKey: String, val publicKey: String)

    data class NodeParams(
        val remark: String = "vless-reality",
        val listenPort: Int = 443,
        val serverName: String = "gateway.icloud.com",
        val dest: String = "gateway.icloud.com:443",
        val flow: String = "xtls-rprx-vision",
    )

    data class CreatedNode(
        val inboundId: Int,
        val uuid: String,
        val privateKey: String,
        val publicKey: String,
        val shortId: String,
        val shareLink: String,
        val serverAddress: String,
        val serverPort: Int,
        val serverName: String,
        val flow: String,
        val remark: String,
    ) {
        /** Clash YAML 订阅（单节点最小配置）。 */
        fun toClashYaml(): String = buildString {
            appendLine("proxies:")
            appendLine("  - name: \"$remark\"")
            appendLine("    type: vless")
            appendLine("    server: $serverAddress")
            appendLine("    port: $serverPort")
            appendLine("    uuid: $uuid")
            appendLine("    network: tcp")
            appendLine("    tls: true")
            appendLine("    udp: true")
            appendLine("    flow: $flow")
            appendLine("    servername: $serverName")
            appendLine("    reality-opts:")
            appendLine("      public-key: $publicKey")
            appendLine("      short-id: $shortId")
            appendLine("    client-fingerprint: chrome")
            appendLine("proxy-groups:")
            appendLine("  - name: Proxy")
            appendLine("    type: select")
            appendLine("    proxies:")
            appendLine("      - \"$remark\"")
            appendLine("rules:")
            appendLine("  - MATCH,Proxy")
        }

        /** sing-box JSON 订阅（单节点最小配置）。 */
        fun toSingBoxJson(): String = buildString {
            appendLine("{")
            appendLine("  \"outbounds\": [")
            appendLine("    {")
            appendLine("      \"type\": \"vless\",")
            appendLine("      \"tag\": \"$remark\",")
            appendLine("      \"server\": \"$serverAddress\",")
            appendLine("      \"server_port\": $serverPort,")
            appendLine("      \"uuid\": \"$uuid\",")
            appendLine("      \"flow\": \"$flow\",")
            appendLine("      \"tls\": {")
            appendLine("        \"enabled\": true,")
            appendLine("        \"server_name\": \"$serverName\",")
            appendLine("        \"utls\": { \"enabled\": true, \"fingerprint\": \"chrome\" },")
            appendLine("        \"reality\": {")
            appendLine("          \"enabled\": true,")
            appendLine("          \"public_key\": \"$publicKey\",")
            appendLine("          \"short_id\": \"$shortId\"")
            appendLine("        }")
            appendLine("      }")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
        }
    }

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val message: String) : Result<Nothing>
    }

    /** 规范化面板地址，去掉末尾斜杠。 */
    private fun normBase(panelUrl: String): String = panelUrl.trim().trimEnd('/')

    private fun get(url: String, token: String): Result<String> {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("Token", token)
                setRequestProperty("User-Agent", "DreamBox/s-ui-client")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code !in 200..299) Result.Err("HTTP $code: ${body.take(200)}")
            else Result.Ok(body)
        } catch (e: Exception) {
            Result.Err("请求失败: ${e.message}")
        }
    }

    private fun postForm(url: String, token: String, fields: Map<String, String>): Result<String> {
        return try {
            val body = fields.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Token", token)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "DreamBox/s-ui-client")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            conn.disconnect()
            if (code !in 200..299) Result.Err("HTTP $code: ${resp.take(200)}")
            else Result.Ok(resp)
        } catch (e: Exception) {
            Result.Err("请求失败: ${e.message}")
        }
    }

    /** 生成 Reality 密钥对。 */
    suspend fun generateRealityKeypair(panelUrl: String, token: String): Result<RealityKeypair> =
        withContext(Dispatchers.IO) {
            when (val r = get("${normBase(panelUrl)}/apiv2/keypairs?k=reality", token)) {
                is Result.Err -> r
                is Result.Ok -> try {
                    val json = JSONObject(r.value)
                    if (!json.optBoolean("success", false)) {
                        return@withContext Result.Err("API 错误: ${json.optString("msg")}")
                    }
                    val obj = json.optJSONObject("obj") ?: return@withContext Result.Err("API 返回缺少 obj")
                    // 兼容多种字段命名
                    val priv = obj.optString("privateKey").ifBlank { obj.optString("private_key") }
                    val pub = obj.optString("publicKey").ifBlank { obj.optString("public_key") }
                    if (priv.isBlank() || pub.isBlank()) {
                        Result.Err("密钥对解析失败: ${obj.toString().take(200)}")
                    } else {
                        Result.Ok(RealityKeypair(priv, pub))
                    }
                } catch (e: Exception) {
                    Result.Err("解析失败: ${e.message}")
                }
            }
        }

    /**
     * 创建 VLESS+Reality inbound 并返回分享链接。
     *
     * @param serverAddress 节点对外地址（IP 或域名），用于拼分享链接
     */
    suspend fun createVlessRealityNode(
        panelUrl: String,
        token: String,
        serverAddress: String,
        params: NodeParams = NodeParams(),
    ): Result<CreatedNode> = withContext(Dispatchers.IO) {
        // 1. 生成密钥对
        val kp = when (val r = generateRealityKeypair(panelUrl, token)) {
            is Result.Err -> return@withContext r
            is Result.Ok -> r.value
        }
        val uuid = UUID.randomUUID().toString()
        val shortId = randomHex(8)

        // 2. 构造 sing-box inbound JSON（panel 透传给 sing-box）
        val inbound = JSONObject().apply {
            put("type", "vless")
            put("tag", params.remark)
            put("listen", "::")
            put("listen_port", params.listenPort)
            put("users", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("uuid", uuid)
                    put("flow", params.flow)
                })
            })
            put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", params.serverName)
                put("reality", JSONObject().apply {
                    put("enabled", true)
                    put("handshake", JSONObject().apply {
                        put("server", params.serverName)
                        put("server_port", 443)
                    })
                    put("private_key", kp.privateKey)
                    put("short_id", org.json.JSONArray().apply { put(shortId) })
                })
            })
            // 订阅下发给客户端的 outbound 模板
            put("out_json", JSONObject().apply {
                put("type", "vless")
                put("server", serverAddress)
                put("server_port", params.listenPort)
                put("uuid", uuid)
                put("flow", params.flow)
                put("tls", JSONObject().apply {
                    put("enabled", true)
                    put("server_name", params.serverName)
                    put("utls", JSONObject().apply {
                        put("enabled", true)
                        put("fingerprint", "chrome")
                    })
                    put("reality", JSONObject().apply {
                        put("enabled", true)
                        put("public_key", kp.publicKey)
                        put("short_id", shortId)
                    })
                })
            })
            // 对外地址（订阅里生成节点用）
            put("addrs", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("server", serverAddress)
                    put("server_port", params.listenPort)
                    put("remark", "")
                })
            })
        }

        // 3. POST /apiv2/save
        val inboundId = when (val saveResult = postForm(
            "${normBase(panelUrl)}/apiv2/save",
            token,
            mapOf("object" to "inbounds", "action" to "new", "data" to inbound.toString()),
        )) {
            is Result.Err -> return@withContext saveResult
            is Result.Ok -> try {
                val json = JSONObject(saveResult.value)
                if (!json.optBoolean("success", false)) {
                    return@withContext Result.Err("创建失败: ${json.optString("msg")}")
                }
                val obj = json.optJSONObject("obj")
                // 响应里 inbounds 可能是对象或数组，尽量取 id
                val inb = obj?.optJSONObject("inbounds")
                    ?: obj?.optJSONArray("inbounds")?.optJSONObject(0)
                inb?.optInt("id", 0) ?: 0
            } catch (e: Exception) {
                return@withContext Result.Err("解析失败: ${e.message}")
            }
        }

        // 4. 拼 vless:// 分享链接
        val link = buildString {
            append("vless://").append(uuid).append("@").append(serverAddress)
            append(":").append(params.listenPort)
            append("?encryption=none")
            append("&flow=").append(params.flow)
            append("&security=reality")
            append("&sni=").append(params.serverName)
            append("&fp=chrome")
            append("&pbk=").append(kp.publicKey)
            append("&sid=").append(shortId)
            append("#").append(URLEncoder.encode(params.remark, "UTF-8"))
        }
        // 重新拿 inboundId（上一步已消费，这里简化处理）
        Result.Ok(
            CreatedNode(
                inboundId = inboundId,
                uuid = uuid,
                privateKey = kp.privateKey,
                publicKey = kp.publicKey,
                shortId = shortId,
                shareLink = link,
                serverAddress = serverAddress,
                serverPort = params.listenPort,
                serverName = params.serverName,
                flow = params.flow,
                remark = params.remark,
            )
        )
    }

    private fun randomHex(len: Int): String {
        val chars = "0123456789abcdef"
        return (1..len).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
}
