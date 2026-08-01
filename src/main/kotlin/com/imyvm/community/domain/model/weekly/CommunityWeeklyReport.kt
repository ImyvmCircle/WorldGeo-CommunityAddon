package com.imyvm.community.domain.model.weekly

import java.util.UUID

enum class WeeklyReportAudience {
    PLAYER,
    MANAGER,
    OP
}

data class CommunityWeeklyReport(
    val id: String,
    val recipientUuid: UUID?,
    val audience: WeeklyReportAudience,
    val weekKey: String,
    val title: String,
    val lines: List<String>,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val readBy: MutableSet<UUID> = mutableSetOf()
) {
    fun isReadBy(playerUuid: UUID): Boolean = readBy.contains(playerUuid)
}
