package com.suanran.dreambox.data.store

import com.suanran.dreambox.core.contract.OverrideApplier
import com.suanran.dreambox.core.contract.OverrideConfigRepository
import com.suanran.dreambox.core.contract.QuickRulesRepository
import com.suanran.dreambox.core.model.override.OverrideConfig
import com.suanran.dreambox.core.model.override.OverrideContentType
import com.suanran.dreambox.core.model.profile.Profile
import com.suanran.dreambox.data.repository.OverrideBindingRepository

/**
 * [QuickRulesRepository] 实现：快速规则就是一份普通用户覆写（覆写页里可见可改），
 * 内容形如：
 *   rules-start:
 *     - "DOMAIN-SUFFIX,example.com,DIRECT"
 * rules-start 的语义由编译器（liboverride）保证：这些规则排在配置自带规则之前。
 */
class QuickRulesStore(
    private val configRepo: OverrideConfigRepository,
    private val bindingRepository: OverrideBindingRepository,
    private val applier: OverrideApplier,
    private val queryActiveProfile: suspend () -> Profile?,
) : QuickRulesRepository {

    override suspend fun addQuickRule(ruleLine: String): Boolean {
        val line = ruleLine.trim()
        if (line.isBlank()) return false
        val existing = configRepo.getById(QUICK_RULES_ID)
        val current = parseRules(existing?.content)
        if (line in current) return false
        // 新规则放最前：在 rules-start 内部也是最先匹配，符合「刚加的说了算」。
        save(existing, listOf(line) + current, created = existing == null)
        return true
    }

    override suspend fun quickRules(): List<String> =
        parseRules(configRepo.getById(QUICK_RULES_ID)?.content)

    override suspend fun removeQuickRule(ruleLine: String): Boolean {
        val line = ruleLine.trim()
        val existing = configRepo.getById(QUICK_RULES_ID) ?: return false
        val current = parseRules(existing.content)
        if (line !in current) return false
        save(existing, current - line, created = false)
        return true
    }

    private suspend fun save(existing: OverrideConfig?, rules: List<String>, created: Boolean) {
        val now = System.currentTimeMillis()
        val content = renderContent(rules)
        val config = (existing ?: OverrideConfig(
            id = QUICK_RULES_ID,
            name = "快速规则",
            description = "连接页长按一键添加的规则，排在配置自带规则之前",
            contentType = OverrideContentType.Yaml,
            content = content,
            createdAt = now,
            updatedAt = now,
        )).copy(content = content, updatedAt = now)
        configRepo.save(config)
        if (created) {
            queryActiveProfile()?.let { profile ->
                bindingRepository.bindOverride(profile.uuid.toString(), QUICK_RULES_ID)
            }
        }
        applier.reapplyActiveProfileIfUsingOverride(QUICK_RULES_ID)
    }

    private fun renderContent(rules: List<String>): String {
        val sb = StringBuilder()
        sb.append("# 梦盒快速规则（连接页长按添加；rules-start = 排在配置规则之前）\n")
        sb.append("rules-start:\n")
        for (rule in rules) {
            sb.append("  - \"").append(rule.replace("\"", "")).append("\"\n")
        }
        return sb.toString()
    }

    private fun parseRules(content: String?): List<String> {
        if (content.isNullOrBlank()) return emptyList()
        val rules = mutableListOf<String>()
        var inSection = false
        for (rawLine in content.lineSequence()) {
            val line = rawLine.trimEnd()
            if (!inSection) {
                if (line.trimStart().startsWith("rules-start:")) inSection = true
                continue
            }
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) {
                val value = trimmed.removePrefix("-").trim()
                    .removeSurrounding("\"").removeSurrounding("'").trim()
                if (value.isNotBlank()) rules += value
            } else if (trimmed.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                break
            }
        }
        return rules
    }

    companion object {
        const val QUICK_RULES_ID = "dreambox-quick-rules"
    }
}
