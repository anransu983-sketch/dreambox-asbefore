package com.suanran.dreambox.feature.settings.presentation.module

import com.suanran.dreambox.core.contract.OverrideApplier
import com.suanran.dreambox.core.contract.OverrideConfigRepository
import com.suanran.dreambox.core.contract.ProfileBindingReader
import com.suanran.dreambox.core.model.override.OverrideConfig
import com.suanran.dreambox.core.model.override.OverrideContentType
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.core.model.profile.ProfileBinding

/**
 * 轻模块 ↔ 流量规则的桥：已启用的模块聚合成一份覆写。
 *
 * 做法照抄 QuickRulesStore（data/.../QuickRulesStore.kt）：
 * 一份固定 id 的覆写 + rules-start 置顶 + 首次创建时绑到当前活动配置 +
 * 改完 reapplyActiveProfileIfUsingOverride()。
 *
 * 不给每个模块各建一份覆写：多份覆写的叠加顺序和绑定管理都比一份聚合难验证，
 * v1 先求开关当场生效、删得干净。
 */
class ModuleOverrideBridge(
    private val configRepo: OverrideConfigRepository,
    private val bindingReader: ProfileBindingReader,
    private val applier: OverrideApplier,
    private val queryActiveProfile: suspend () -> Profile?,
) {
    /**
     * 按当前已启用模块重算聚合覆写并重应用。
     * enabled 为空时删掉聚合覆写（如果存在）并重应用，等于全部停用。
     */
    suspend fun syncEnabled(enabled: List<ModuleDef>) {
        val now = System.currentTimeMillis()
        val existing = configRepo.getById(AGGREGATE_ID)
        if (enabled.isEmpty()) {
            if (existing != null) {
                configRepo.delete(AGGREGATE_ID)
                applier.reapplyActiveProfileOverride()
            }
            return
        }
        val content = renderContent(enabled)
        val config = (existing ?: OverrideConfig(
            id = AGGREGATE_ID,
            name = "模块市场",
            description = "已启用的轻模块聚合规则，排在配置自带规则之前",
            contentType = OverrideContentType.Yaml,
            content = content,
            createdAt = now,
            updatedAt = now,
        )).copy(content = content, updatedAt = now)
        configRepo.save(config)
        if (existing == null) {
            queryActiveProfile()?.let { profile ->
                val binding = bindingReader.getBinding(profile.uuid.toString())
                val updated = (binding ?: ProfileBinding.disabled(profile.uuid.toString()))
                    .addOverride(AGGREGATE_ID)
                bindingReader.setBinding(updated)
            }
        }
        applier.reapplyActiveProfileIfUsingOverride(AGGREGATE_ID)
    }

    private fun renderContent(enabled: List<ModuleDef>): String {
        val sb = StringBuilder()
        sb.append("# 梦盒模块市场聚合规则（rules-start = 排在配置规则之前）\n")
        sb.append("# 已启用：")
        sb.append(enabled.joinToString("、") { "${it.name} v${it.version}" })
        sb.append("\n")
        sb.append("rules-start:\n")
        val seen = LinkedHashSet<String>()
        for (def in enabled) {
            for (rule in def.rules) {
                if (seen.add(rule)) {
                    sb.append("  - \"").append(rule.replace("\"", "")).append("\"\n")
                }
            }
        }
        return sb.toString()
    }

    companion object {
        const val AGGREGATE_ID = "dreambox-modules"
    }
}
