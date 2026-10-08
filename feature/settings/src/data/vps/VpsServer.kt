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

package com.suanran.dreambox.feature.settings.data.vps

import kotlinx.serialization.Serializable
import java.util.UUID

/** VPS 服务器（SSH 密码登录）。密码以明文存于本机 MMKV，不打日志。 */
@Serializable
data class VpsServer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
)

/** 内置测试脚本。 */
enum class VpsTestScript(
    val id: String,
    val command: String,
) {
    /** nq：NodeQuality，沙箱无痕测试，整合 Yabs + IP 质量 + 网络质量。 */
    NodeQuality(
        id = "nq",
        command = "bash <(curl -sL https://run.NodeQuality.com)",
    ),

    /** tq：TcpQuality，三网回程路由质量测试。 */
    TcpQuality(
        id = "tq",
        command = "bash <(curl -fsSL https://raw.githubusercontent.com/ibsgss/TcpQuality/main/runTcpQuality.sh)",
    ),

    /** 硬件检测：HardwareQuality，CPU / 内存 / 硬盘 / 系统信息体检。 */
    HardwareQuality(
        id = "hw",
        command = "bash <(curl -Ls https://Hardware.Check.Place)",
    ),

    /** IP 质量：IPQuality，IP 归属 / 风险评分 / 流媒体解锁 / 黑名单检测。 */
    IpQuality(
        id = "ipq",
        command = "bash <(curl -Ls https://IP.Check.Place)",
    ),

    /** s-ui 面板一键安装（sing-box）。装完后在面板创建 API Token 以使用自动建节点。 */
    SuiInstall(
        id = "sui",
        command = "bash <(curl -Ls https://raw.githubusercontent.com/alireza0/s-ui/master/install.sh)",
    ),
}
