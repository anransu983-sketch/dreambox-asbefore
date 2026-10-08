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

package com.suanran.dreambox.feature.profiles.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.suanran.dreambox.locale.FlyTxt
import com.suanran.dreambox.presentation.theme.AppTheme
import com.suanran.dreambox.presentation.theme.UiDp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 手动添加支持的协议。 */
enum class ManualNodeProtocol(val titleKey: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    SOCKS("Socks"),
    HTTP("HTTP"),
    HYSTERIA2("Hysteria2"),
    WIREGUARD("WireGuard"),
}

/** 手动节点表单数据（全协议通用字段池）。 */
class ManualNodeFormState {
    var name by mutableStateOf(TextFieldValue(""))
    var server by mutableStateOf(TextFieldValue(""))
    var port by mutableStateOf(TextFieldValue(""))
    var uuid by mutableStateOf(TextFieldValue(""))       // vless/vmess/tuic
    var password by mutableStateOf(TextFieldValue(""))   // trojan/ss/hysteria2/socks/http
    var cipher by mutableStateOf("aes-128-gcm")         // vmess/ss
    var alterId by mutableStateOf(TextFieldValue("0"))  // vmess
    var network by mutableStateOf("tcp")                // vless/vmess/trojan transport
    var tls by mutableStateOf(false)                    // vless/vmess/trojan/http
    var sni by mutableStateOf(TextFieldValue(""))
    var wsPath by mutableStateOf(TextFieldValue("/"))
    var wsHost by mutableStateOf(TextFieldValue(""))
    var grpcServiceName by mutableStateOf(TextFieldValue(""))
    var flow by mutableStateOf(TextFieldValue(""))      // vless xtls flow
    var skipCertVerify by mutableStateOf(false)
    var username by mutableStateOf(TextFieldValue(""))   // socks/http
    var obfs by mutableStateOf("none")                  // hysteria2
    var obfsPassword by mutableStateOf(TextFieldValue(""))
    var privateKey by mutableStateOf(TextFieldValue("")) // wireguard
    var publicKey by mutableStateOf(TextFieldValue(""))  // wireguard peer
    var presharedKey by mutableStateOf(TextFieldValue(""))
    var localIp by mutableStateOf(TextFieldValue(""))   // wireguard e.g. 10.0.0.2/32
    var dns by mutableStateOf(TextFieldValue(""))
    var mtu by mutableStateOf(TextFieldValue("1280"))

    fun validate(protocol: ManualNodeProtocol): String? {
        if (server.text.isBlank()) return FlyTxt.ManualNode.Validation.ServerRequired
        val p = port.text.toIntOrNull()
        if (p == null || p <= 0 || p > 65535) return FlyTxt.ManualNode.Validation.PortInvalid
        when (protocol) {
            ManualNodeProtocol.VMESS, ManualNodeProtocol.VLESS -> {
                if (uuid.text.isBlank()) return FlyTxt.ManualNode.Validation.UuidRequired
            }
            ManualNodeProtocol.TROJAN, ManualNodeProtocol.HYSTERIA2 -> {
                if (password.text.isBlank()) return FlyTxt.ManualNode.Validation.PasswordRequired
            }
            ManualNodeProtocol.SHADOWSOCKS -> {
                if (password.text.isBlank()) return FlyTxt.ManualNode.Validation.PasswordRequired
            }
            ManualNodeProtocol.WIREGUARD -> {
                if (privateKey.text.isBlank()) return FlyTxt.ManualNode.Validation.PrivateKeyRequired
                if (publicKey.text.isBlank()) return FlyTxt.ManualNode.Validation.PublicKeyRequired
            }
            else -> {}
        }
        return null
    }

    /** 生成 Mihomo/Clash 格式的 proxy Map。 */
    fun buildProxyMap(protocol: ManualNodeProtocol): Map<String, Any?> {
        val nodeName = name.text.ifBlank { "${server.text}:${port.text}" }
        val portInt = port.text.toIntOrNull() ?: 0
        return when (protocol) {
            ManualNodeProtocol.VMESS -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "vmess",
                    "server" to server.text, "port" to portInt,
                    "uuid" to uuid.text, "alterId" to (alterId.text.toIntOrNull() ?: 0),
                    "cipher" to cipher,
                )
                m.applyManualTransport()
                m
            }
            ManualNodeProtocol.VLESS -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "vless",
                    "server" to server.text, "port" to portInt,
                    "uuid" to uuid.text, "encryption" to "none",
                )
                if (flow.text.isNotBlank()) m["flow"] = flow.text
                m.applyManualTransport()
                m
            }
            ManualNodeProtocol.TROJAN -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "trojan",
                    "server" to server.text, "port" to portInt,
                    "password" to password.text,
                )
                if (sni.text.isNotBlank()) m["sni"] = sni.text
                if (skipCertVerify) m["skip-cert-verify"] = true
                m.applyManualTransport()
                m
            }
            ManualNodeProtocol.SHADOWSOCKS -> linkedMapOf(
                "name" to nodeName, "type" to "ss",
                "server" to server.text, "port" to portInt,
                "cipher" to cipher, "password" to password.text,
            )
            ManualNodeProtocol.SOCKS -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "socks5",
                    "server" to server.text, "port" to portInt,
                )
                if (username.text.isNotBlank()) {
                    m["username"] = username.text
                    m["password"] = password.text
                }
                if (tls) m["tls"] = true
                if (skipCertVerify) m["skip-cert-verify"] = true
                m
            }
            ManualNodeProtocol.HTTP -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "http",
                    "server" to server.text, "port" to portInt,
                )
                if (username.text.isNotBlank()) {
                    m["username"] = username.text
                    m["password"] = password.text
                }
                if (tls) m["tls"] = true
                if (skipCertVerify) m["skip-cert-verify"] = true
                m
            }
            ManualNodeProtocol.HYSTERIA2 -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "hysteria2",
                    "server" to server.text, "port" to portInt,
                    "password" to password.text,
                )
                if (sni.text.isNotBlank()) m["sni"] = sni.text
                if (skipCertVerify) m["skip-cert-verify"] = true
                if (obfs != "none") {
                    m["obfs"] = obfs
                    if (obfsPassword.text.isNotBlank()) m["obfs-password"] = obfsPassword.text
                }
                m
            }
            ManualNodeProtocol.WIREGUARD -> {
                val m = linkedMapOf<String, Any?>(
                    "name" to nodeName, "type" to "wireguard",
                    "server" to server.text, "port" to portInt,
                    "ip" to localIp.text.ifBlank { "10.0.0.2/32" },
                    "private-key" to privateKey.text,
                    "public-key" to publicKey.text,
                )
                if (presharedKey.text.isNotBlank()) m["preshared-key"] = presharedKey.text
                if (dns.text.isNotBlank()) m["dns"] = dns.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                m["mtu"] = mtu.text.toIntOrNull() ?: 1280
                m
            }
        }
    }

    private fun MutableMap<String, Any?>.applyManualTransport() {
        if (tls) {
            this["tls"] = true
            if (sni.text.isNotBlank()) this["servername"] = sni.text
            if (skipCertVerify) this["skip-cert-verify"] = true
        }
        when (network) {
            "ws" -> {
                this["network"] = "ws"
                val wsOpts = linkedMapOf<String, Any?>("path" to wsPath.text.ifBlank { "/" })
                if (wsHost.text.isNotBlank()) wsOpts["headers"] = mapOf("Host" to wsHost.text)
                this["ws-opts"] = wsOpts
            }
            "grpc" -> {
                this["network"] = "grpc"
                this["grpc-opts"] = linkedMapOf("grpc-service-name" to grpcServiceName.text)
            }
        }
    }
}

@Composable
internal fun ManualNodeContent(
    protocol: ManualNodeProtocol,
    form: ManualNodeFormState,
    error: String,
    onProtocolChange: (ManualNodeProtocol) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = AppTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(UiDp.dp16),
    ) {
        Card {
            WindowSpinnerPreference(
                title = FlyTxt.ManualNode.Protocol,
                items = ManualNodeProtocol.entries.map { DropdownItem(it.titleKey) },
                selectedIndex = protocol.ordinal,
                onSelectedIndexChange = { onProtocolChange(ManualNodeProtocol.entries[it]) },
            )
        }

        Card {
            Column(
                modifier = Modifier.padding(horizontal = UiDp.dp16, vertical = UiDp.dp8),
                verticalArrangement = Arrangement.spacedBy(UiDp.dp4),
            ) {
                NodeTextField(
                    value = form.name, onValueChange = { form.name = it },
                    label = FlyTxt.ManualNode.Name,
                )
                NodeTextField(
                    value = form.server, onValueChange = { form.server = it },
                    label = FlyTxt.ManualNode.Server,
                )
                NodeTextField(
                    value = form.port, onValueChange = { input -> val v = input.text.filter { c -> c.isDigit() }.take(5); form.port = TextFieldValue(v, androidx.compose.ui.text.TextRange(v.length)) },
                    label = FlyTxt.ManualNode.Port,
                )

                when (protocol) {
                    ManualNodeProtocol.VMESS -> {
                        NodeTextField(value = form.uuid, onValueChange = { form.uuid = it }, label = FlyTxt.ManualNode.Uuid)
                        NodeTextField(value = form.alterId, onValueChange = { form.alterId = it }, label = FlyTxt.ManualNode.AlterId)
                        CipherSelector(selected = form.cipher, onSelect = { form.cipher = it }, options = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none"))
                        TransportSelector(form)
                        TlsSwitch(form)
                    }
                    ManualNodeProtocol.VLESS -> {
                        NodeTextField(value = form.uuid, onValueChange = { form.uuid = it }, label = FlyTxt.ManualNode.Uuid)
                        NodeTextField(value = form.flow, onValueChange = { form.flow = it }, label = FlyTxt.ManualNode.Flow)
                        TransportSelector(form)
                        TlsSwitch(form)
                    }
                    ManualNodeProtocol.TROJAN -> {
                        NodeTextField(value = form.password, onValueChange = { form.password = it }, label = FlyTxt.ManualNode.Password)
                        NodeTextField(value = form.sni, onValueChange = { form.sni = it }, label = FlyTxt.ManualNode.Sni)
                        SkipCertVerifySwitch(form)
                        TransportSelector(form)
                    }
                    ManualNodeProtocol.SHADOWSOCKS -> {
                        CipherSelector(selected = form.cipher, onSelect = { form.cipher = it }, options = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305"))
                        NodeTextField(value = form.password, onValueChange = { form.password = it }, label = FlyTxt.ManualNode.Password)
                    }
                    ManualNodeProtocol.SOCKS, ManualNodeProtocol.HTTP -> {
                        NodeTextField(value = form.username, onValueChange = { form.username = it }, label = FlyTxt.ManualNode.Username)
                        NodeTextField(value = form.password, onValueChange = { form.password = it }, label = FlyTxt.ManualNode.Password)
                        TlsSwitch(form)
                    }
                    ManualNodeProtocol.HYSTERIA2 -> {
                        NodeTextField(value = form.password, onValueChange = { form.password = it }, label = FlyTxt.ManualNode.Password)
                        NodeTextField(value = form.sni, onValueChange = { form.sni = it }, label = FlyTxt.ManualNode.Sni)
                        ObfsSelector(form)
                        SkipCertVerifySwitch(form)
                    }
                    ManualNodeProtocol.WIREGUARD -> {
                        NodeTextField(value = form.privateKey, onValueChange = { form.privateKey = it }, label = FlyTxt.ManualNode.PrivateKey)
                        NodeTextField(value = form.publicKey, onValueChange = { form.publicKey = it }, label = FlyTxt.ManualNode.PublicKey)
                        NodeTextField(value = form.presharedKey, onValueChange = { form.presharedKey = it }, label = FlyTxt.ManualNode.PresharedKey)
                        NodeTextField(value = form.localIp, onValueChange = { form.localIp = it }, label = FlyTxt.ManualNode.LocalIp)
                        NodeTextField(value = form.dns, onValueChange = { form.dns = it }, label = FlyTxt.ManualNode.Dns)
                        NodeTextField(value = form.mtu, onValueChange = { form.mtu = it }, label = FlyTxt.ManualNode.Mtu)
                    }
                }
            }
        }

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.body2,
            )
        }

        TextButton(
            text = FlyTxt.ManualNode.Save,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NodeTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CipherSelector(selected: String, onSelect: (String) -> Unit, options: List<String>) {
    WindowSpinnerPreference(
        title = FlyTxt.ManualNode.Cipher,
        items = options.map { DropdownItem(it) },
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelectedIndexChange = { onSelect(options[it]) },
    )
}

@Composable
private fun TransportSelector(form: ManualNodeFormState) {
    val options = listOf("tcp", "ws", "grpc")
    WindowSpinnerPreference(
        title = FlyTxt.ManualNode.Transport,
        items = options.map { DropdownItem(it) },
        selectedIndex = options.indexOf(form.network).coerceAtLeast(0),
        onSelectedIndexChange = { form.network = options[it] },
    )
    if (form.network == "ws") {
        NodeTextField(value = form.wsPath, onValueChange = { form.wsPath = it }, label = FlyTxt.ManualNode.WsPath)
        NodeTextField(value = form.wsHost, onValueChange = { form.wsHost = it }, label = FlyTxt.ManualNode.WsHost)
    }
    if (form.network == "grpc") {
        NodeTextField(value = form.grpcServiceName, onValueChange = { form.grpcServiceName = it }, label = FlyTxt.ManualNode.GrpcServiceName)
    }
}

@Composable
private fun TlsSwitch(form: ManualNodeFormState) {
    BasicComponent(
        title = FlyTxt.ManualNode.Tls,
        endActions = {
            Switch(checked = form.tls, onCheckedChange = { form.tls = it })
        },
        onClick = { form.tls = !form.tls },
    )
    if (form.tls) {
        NodeTextField(value = form.sni, onValueChange = { form.sni = it }, label = FlyTxt.ManualNode.Sni)
        SkipCertVerifySwitch(form)
    }
}

@Composable
private fun SkipCertVerifySwitch(form: ManualNodeFormState) {
    BasicComponent(
        title = FlyTxt.ManualNode.SkipCertVerify,
        endActions = {
            Switch(checked = form.skipCertVerify, onCheckedChange = { form.skipCertVerify = it })
        },
        onClick = { form.skipCertVerify = !form.skipCertVerify },
    )
}

@Composable
private fun ObfsSelector(form: ManualNodeFormState) {
    val options = listOf("none", "salamander")
    WindowSpinnerPreference(
        title = FlyTxt.ManualNode.Obfs,
        items = options.map { DropdownItem(it) },
        selectedIndex = options.indexOf(form.obfs).coerceAtLeast(0),
        onSelectedIndexChange = { form.obfs = options[it] },
    )
    if (form.obfs != "none") {
        NodeTextField(value = form.obfsPassword, onValueChange = { form.obfsPassword = it }, label = FlyTxt.ManualNode.ObfsPassword)
    }
}
