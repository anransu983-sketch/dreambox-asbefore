package com.suanran.dreambox.core.contract

/**
 * 快速规则：一个固定的内置覆写（id=dreambox-quick-rules，rules-start 置顶），
 * 供连接页/规则页一键把某个域名或 IP 设为直连/代理/拦截，不用手写 YAML。
 * 首次添加时自动创建并绑定到当前活动配置，之后的新增会触发活动配置重应用。
 */
interface QuickRulesRepository {
    /** 加入一条规则行（如 "DOMAIN-SUFFIX,example.com,DIRECT"）。已存在返回 false。 */
    suspend fun addQuickRule(ruleLine: String): Boolean

    /** 当前快速规则行（新加的在前）。 */
    suspend fun quickRules(): List<String>

    /** 删除一条快速规则行。删到了返回 true。 */
    suspend fun removeQuickRule(ruleLine: String): Boolean
}
