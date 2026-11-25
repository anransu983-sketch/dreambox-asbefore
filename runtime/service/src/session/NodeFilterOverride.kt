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
import com.suanran.dreambox.core.util.NodeVisibilityStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Built-in JS override that removes hidden/deleted nodes (see [NodeVisibilityStore]) from the
 * compiled config: the node definitions themselves, every proxy-group member list, and any rule
 * whose target is one of those nodes. Without this, a hidden node would still be probed and
 * selected by url-test groups even though the UI no longer shows it.
 *
 * Applied last in the override chain so user overrides cannot resurrect a removed node, and
 * applied even when user overrides are disabled: node visibility is an app-level decision, not
 * a user override. Returns null when nothing is excluded, so the common case adds no JS pass.
 */
object NodeFilterOverride {
    const val FILE_NAME = "__node_filter__.js"

    fun materialize(profileDir: File): OverrideSpec? {
        val excluded = NodeVisibilityStore.load(profileDir).excluded
        if (excluded.isEmpty()) return null
        profileDir.mkdirs()
        val file = File(profileDir, FILE_NAME)
        file.writeText(buildScript(excluded))
        return OverrideSpec(path = file.absolutePath, ext = "js")
    }

    fun buildScript(excluded: Set<String>): String {
        val namesJson = JsonArray(excluded.sorted().map { JsonPrimitive(it) }).toString()
        return """
            |function main(profile) {
            |  var excluded = {};
            |  var names = $namesJson;
            |  for (var i = 0; i < names.length; i++) excluded[names[i]] = true;
            |  if (Array.isArray(profile.proxies)) {
            |    profile.proxies = profile.proxies.filter(function (p) {
            |      return p && !excluded[p.name];
            |    });
            |  }
            |  var groups = profile["proxy-groups"];
            |  if (Array.isArray(groups)) {
            |    for (var g = 0; g < groups.length; g++) {
            |      var group = groups[g];
            |      if (!group || !Array.isArray(group.proxies)) continue;
            |      group.proxies = group.proxies.filter(function (n) {
            |        return typeof n !== "string" || !excluded[n];
            |      });
            |      // A group left with no members and no provider source cannot load; keep it
            |      // valid with DIRECT. The UI blocks hiding/deleting a group's last member,
            |      // so this only covers subscriptions that later drop the remaining nodes.
            |      var hasUse = Array.isArray(group.use) && group.use.length > 0;
            |      if (group.proxies.length === 0 && !hasUse) group.proxies = ["DIRECT"];
            |    }
            |  }
            |  if (Array.isArray(profile.rules)) {
            |    profile.rules = profile.rules.filter(function (r) {
            |      if (typeof r !== "string") return true;
            |      var parts = r.split(",");
            |      var end = parts.length;
            |      while (end > 1 && (parts[end - 1].trim() === "no-resolve" || parts[end - 1].trim() === "src")) end--;
            |      var target = end >= 2 ? parts[end - 1].trim() : "";
            |      return !excluded[target];
            |    });
            |  }
            |  return profile;
            |}
            """.trimMargin()
    }
}
