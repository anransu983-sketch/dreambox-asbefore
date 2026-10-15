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

package com.suanran.dreambox.runtime.service.session

import com.suanran.dreambox.core.model.OverrideSpec
import java.io.File

/**
 * 将「统一延迟 / TCP 并发」两个 mihomo 原生开关注入编译覆写链。
 * 只在开关打开时才写入对应键；全部关闭时写入空文件，等价于无覆写。
 * 排在用户覆写之前，用户手写的覆写仍然优先。
 */
object DelayTuningOverride {
    const val FILE_NAME = "__delay_tuning_override__.yaml"

    fun buildYaml(unifiedDelay: Boolean, tcpConcurrent: Boolean): String {
        val sb = StringBuilder()
        if (unifiedDelay) sb.append("unified-delay: true\n")
        if (tcpConcurrent) sb.append("tcp-concurrent: true\n")
        return sb.toString()
    }

    fun materialize(
        dir: File,
        unifiedDelay: Boolean,
        tcpConcurrent: Boolean,
    ): OverrideSpec {
        dir.mkdirs()
        val file = File(dir, FILE_NAME)
        file.writeText(buildYaml(unifiedDelay, tcpConcurrent))
        return OverrideSpec(path = file.absolutePath, ext = "yaml")
    }
}
