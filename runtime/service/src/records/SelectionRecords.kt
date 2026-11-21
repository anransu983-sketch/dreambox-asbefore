package com.suanran.dreambox.runtime.service.records

import com.suanran.dreambox.core.Clash
import com.suanran.dreambox.core.model.proxy.Proxy
import com.suanran.dreambox.core.model.proxy.ProxyGroup
import com.suanran.dreambox.runtime.service.records.Selection
import com.suanran.dreambox.runtime.service.util.Log
import java.util.UUID
import kotlinx.coroutines.delay

object SelectionDao {
    private const val PIN_KEY_PREFIX = "pin_"

    fun persistForcePinnedSelection(
        profileUUID: UUID?,
        proxyGroup: String,
        requestedNode: String,
        patched: Boolean,
        supportsPinnedSelection: Boolean,
    ) {
        val uuid = profileUUID ?: return
        if (!patched) {
            removePinned(uuid, proxyGroup)
            return
        }
        if (!supportsPinnedSelection) {
            return
        }
        if (requestedNode.isBlank()) {
            removePinned(uuid, proxyGroup)
        } else {
            setPinned(uuid, proxyGroup, requestedNode)
        }
    }

    fun migrateLegacyIfNeeded() {
        ProfileStore.migrateLegacySelectionMemoryIfNeeded()
    }

    fun queryAll(): List<Selection> {
        migrateLegacyIfNeeded()
        return ProfileStore.loadSelections()
    }

    fun querySelections(profileUUID: UUID): List<Selection> {
        return queryAll().filter { it.uuid == profileUUID }
    }

    fun queryRestorableSelections(profileUUID: UUID): List<Selection> {
        return querySelections(profileUUID)
    }

    fun upsertManualSelection(profileUUID: UUID, groupName: String, selectedProxy: String) {
        upsertManualSelection(
            Selection(
                uuid = profileUUID,
                proxy = groupName.trim(),
                selected = selectedProxy.trim(),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun upsertManualSelection(selection: Selection) {
        migrateLegacyIfNeeded()
        val normalized =
            selection.copy(
                proxy = selection.proxy.trim(),
                selected = selection.selected.trim(),
                updatedAt =
                    if (selection.updatedAt > 0L) selection.updatedAt
                    else System.currentTimeMillis(),
            )
        if (normalized.proxy.isEmpty() || normalized.selected.isEmpty()) {
            return
        }
        val list = ProfileStore.loadSelections().toMutableList()
        val index = list.indexOfFirst { it.uuid == normalized.uuid && it.proxy == normalized.proxy }
        if (index >= 0) {
            list[index] = normalized
        } else {
            list.add(normalized)
        }
        ProfileStore.saveSelections(list)
    }

    fun clear(profileUUID: UUID) {
        migrateLegacyIfNeeded()
        val list = ProfileStore.loadSelections().toMutableList()
        list.removeAll { it.uuid == profileUUID }
        ProfileStore.saveSelections(list)
        removeAllPins(profileUUID)
    }

    fun remove(profileUUID: UUID, proxy: String) {
        migrateLegacyIfNeeded()
        val list = ProfileStore.loadSelections().toMutableList()
        list.removeAll { it.uuid == profileUUID && it.proxy == proxy }
        ProfileStore.saveSelections(list)
    }

    fun setPinned(profileUUID: UUID, proxyGroup: String, pinnedNode: String) {
        val normalizedGroup = proxyGroup.trim()
        val normalizedNode = pinnedNode.trim()
        if (normalizedGroup.isEmpty() || normalizedNode.isEmpty()) return
        ProfileStore.setSelectionScopeValue(makePinKey(profileUUID, normalizedGroup), normalizedNode)
    }

    fun getPinned(profileUUID: UUID, proxyGroup: String): String? {
        val normalizedGroup = proxyGroup.trim()
        if (normalizedGroup.isEmpty()) return null
        return ProfileStore.getSelectionScopeValue(makePinKey(profileUUID, normalizedGroup))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun removePinned(profileUUID: UUID, proxyGroup: String) {
        val normalizedGroup = proxyGroup.trim()
        if (normalizedGroup.isEmpty()) return
        ProfileStore.removeSelectionScopeValue(makePinKey(profileUUID, normalizedGroup))
    }

    fun getAllPins(profileUUID: UUID): Map<String, String> {
        val prefix = makePinKeyPrefix(profileUUID)
        return ProfileStore.querySelectionScopeValues(prefix)
            .mapNotNull { (key, value) ->
                val group = key.removePrefix(prefix).trim()
                val node = value.trim()
                if (group.isEmpty() || node.isEmpty()) null else group to node
            }
            .toMap()
    }

    private fun removeAllPins(profileUUID: UUID) {
        val prefix = makePinKeyPrefix(profileUUID)
        val keys = ProfileStore.querySelectionScopeValues(prefix).keys
        keys.forEach { scopedKey ->
            ProfileStore.removeSelectionScopeValue(scopedKey)
        }
    }

    private fun makePinKey(profileUUID: UUID, proxyGroup: String): String {
        return "${PIN_KEY_PREFIX}${profileUUID}_$proxyGroup"
    }

    private fun makePinKeyPrefix(profileUUID: UUID): String {
        return "${PIN_KEY_PREFIX}${profileUUID}_"
    }
}

internal object SelectionRestoreExecutor {
    private const val queryRetryCount = 3
    private const val queryRetryDelayMs = 150L

    suspend fun restore(
        profileUuid: UUID,
        selections: List<Selection>,
        pins: Map<String, String>,
        runtimeGroups: List<ProxyGroup>,
        tag: String,
    ) {
        val selectorGroups = runtimeGroups.associateBy { it.name }
        // 1.204：只有校验通过（或因快照未加载而保留）的记忆才算“有手动记忆”。
        // 绝不能拿删除前的旧列表当依据，否则刚被删掉记忆的组会被默认直连兜底漏掉。
        val survivingGroups = mutableSetOf<String>()
        selections.forEach { selection ->
            val group =
                selectorGroups[selection.proxy]
                    ?: run {
                        removeSelection(profileUuid, selection, tag, "group missing")
                        return@forEach
                    }
            if (group.type != Proxy.Type.Selector) {
                removeSelection(profileUuid, selection, tag, "group not selector")
                return@forEach
            }

            val currentNodes =
                group.proxies.mapNotNull { proxy -> proxy.name.trim().takeIf { it.isNotEmpty() } }
            val targetNode = selection.selected.trim()
            if (targetNode.isEmpty()) {
                removeSelection(profileUuid, selection, tag, "node missing")
                return@forEach
            }
            if (targetNode !in currentNodes) {
                // 1.204：订阅节点是远程拉取的，切配置后组名先出来、节点后到。
                // 组里一个直连节点都没有时，说明 provider 还没拉完，不能认定记忆里的
                // 节点已改名/删除——否则每次切配置都会把手动记忆删掉、回落到 Auto。
                // 只有组里已经有直连节点、目标仍不在其中时，才按节点已失效处理。
                if (!hasDirectNode(group)) {
                    Log.w(
                        "$tag keep selector memory: profile=$profileUuid group=${selection.proxy} " +
                            "node=$targetNode (provider not loaded yet)"
                    )
                    survivingGroups.add(selection.proxy.trim())
                    return@forEach
                }
                removeSelection(profileUuid, selection, tag, "node missing")
                return@forEach
            }

            survivingGroups.add(selection.proxy.trim())
            if (!patchSelectorWithRetry(selection.proxy, targetNode)) {
                Log.w(
                    "$tag restore selector patch failed: profile=$profileUuid group=${selection.proxy} node=$targetNode"
                )
            }
        }

        pins.forEach { (groupName, pinnedNode) ->
            val group = selectorGroups[groupName] ?: run {
                removePinned(profileUuid, groupName, pinnedNode, tag, "group missing")
                return@forEach
            }
            if (!group.type.supportsPinnedSelection()) {
                removePinned(profileUuid, groupName, pinnedNode, tag, "group does not support pin")
                return@forEach
            }
            val currentNodes = group.proxies
                .mapNotNull { proxy -> proxy.name.trim().takeIf { it.isNotEmpty() } }
            val targetNode = pinnedNode.trim()
            if (targetNode.isEmpty()) {
                removePinned(profileUuid, groupName, pinnedNode, tag, "node missing")
                return@forEach
            }
            if (targetNode !in currentNodes) {
                // 1.204：同上，provider 未拉完时不删 pin，只跳过本轮。
                if (!hasDirectNode(group)) {
                    Log.w(
                        "$tag keep pin: profile=$profileUuid group=$groupName " +
                            "node=$targetNode (provider not loaded yet)"
                    )
                    return@forEach
                }
                removePinned(profileUuid, groupName, pinnedNode, tag, "node missing")
                return@forEach
            }
            if (!patchForceSelectorWithRetry(groupName, targetNode)) {
                Log.w("$tag restore pin patch failed: profile=$profileUuid group=$groupName node=$targetNode")
            }
        }

        // DreamBox 1.203：切换节点不要默认 Auto。
        // 订阅模板常把 Auto（url-test 组）放 Proxy 组首位；mihomo 在没有手动记忆时
        // 默认选中首位，于是流量走 Auto、每 300 秒主动探测全部节点，弱协议节点
        // 会在探测中暴露（yuphy 2026-11-20）。启动/重载恢复选择后，对没有手动
        // 记忆、当前又停在 url-test/fallback 组上的选择器组，改选直连节点并记下来；
        // 已手动选过的组一律不动（包括用户自己点过 Auto 的组）。
        // 其他组（如 Steam/Emby）首位是 Proxy 选择器组，不受影响。
        // 1.205：兜底不再只取列表第一个——老订阅的 Proxy 顺序常把 CF 放直连前面，
        // 取第一个会落到 CF 上（2026-11-21 香港配置实测）。改取已测速里延迟最低的
        // 直连节点；都没测速时才按列表顺序取第一个。
        val storedGroups = survivingGroups
        selectorGroups.values.forEach { group ->
            if (group.type != Proxy.Type.Selector) return@forEach
            if (group.name.trim() in storedGroups) return@forEach
            val current = group.now.trim()
            val currentGroup = current.takeIf { it.isNotEmpty() }?.let { selectorGroups[it] }
            val sitsOnAutoProbe = current.isEmpty() ||
                (currentGroup != null &&
                    (currentGroup.type == Proxy.Type.URLTest || currentGroup.type == Proxy.Type.Fallback))
            if (!sitsOnAutoProbe) return@forEach
            val defaultDirect = defaultDirectNode(group)
            if (defaultDirect.isEmpty()) return@forEach
            if (patchSelectorWithRetry(group.name, defaultDirect)) {
                SelectionDao.upsertManualSelection(profileUuid, group.name, defaultDirect)
                Log.w(
                    "$tag default direct node: profile=$profileUuid group=${group.name} " +
                        "node=$defaultDirect (was ${current.ifEmpty { "<empty>" }})"
                )
            } else {
                Log.w(
                    "$tag default direct node patch failed: profile=$profileUuid group=${group.name} node=$defaultDirect"
                )
            }
        }
    }

    /** 组内直连节点（排除组引用与 DIRECT/REJECT 类伪节点），保持订阅里的列表顺序。 */
    private fun directNodes(group: ProxyGroup): List<Proxy> {
        return group.proxies.filter { proxy ->
            !proxy.isGroup &&
                proxy.type != Proxy.Type.Direct &&
                proxy.type != Proxy.Type.Reject &&
                proxy.type != Proxy.Type.RejectDrop
        }
    }

    /**
     * 组里是否已有直连节点。订阅节点是远程拉取的，切配置后组名先出来、节点后到；
     * 组里一个直连节点都没有时，说明 provider 还没拉完，不能据此认定记忆里的节点已失效。
     */
    private fun hasDirectNode(group: ProxyGroup): Boolean {
        return directNodes(group).isNotEmpty()
    }

    /**
     * 1.205 默认直连节点：优先取已测速（delay > 0）里延迟最低的直连节点；
     * 都没测速时才按列表顺序取第一个。只取列表第一个会在老订阅上落到 CF
     *（香港配置 Proxy 顺序是 [Auto、香港CF、直连v6]，CF 在直连前面）。
     */
    private fun defaultDirectNode(group: ProxyGroup): String {
        val direct = directNodes(group)
        val fastest = direct.filter { it.delay > 0 }.minByOrNull { it.delay }
        return (fastest ?: direct.firstOrNull())?.name?.trim().orEmpty()
    }

    private suspend fun patchSelectorWithRetry(group: String, node: String): Boolean {
        repeat(queryRetryCount) { attempt ->
            if (Clash.patchSelector(group, node)) {
                return true
            }
            if (attempt < queryRetryCount - 1) {
                delay(queryRetryDelayMs)
            }
        }
        return false
    }

    private suspend fun patchForceSelectorWithRetry(group: String, node: String): Boolean {
        repeat(queryRetryCount) { attempt ->
            if (Clash.patchForceSelector(group, node)) {
                return true
            }
            if (attempt < queryRetryCount - 1) {
                delay(queryRetryDelayMs)
            }
        }
        return false
    }

    private fun removeSelection(
        profileUuid: UUID,
        selection: Selection,
        tag: String,
        reason: String,
    ) {
        Log.w(
            "$tag remove invalid selector memory: profile=$profileUuid group=${selection.proxy} " +
                "node=${selection.selected} reason=$reason"
        )
        SelectionDao.remove(profileUuid, selection.proxy)
    }

    private fun removePinned(profileUuid: UUID, group: String, node: String, tag: String, reason: String) {
        Log.w(
            "$tag remove invalid pin memory: profile=$profileUuid group=$group node=$node reason=$reason",
        )
        SelectionDao.removePinned(profileUuid, group)
    }

    private fun String.supportsPinnedSelection(): Boolean {
        return this == Proxy.Type.URLTest || this == Proxy.Type.Fallback
    }
}
