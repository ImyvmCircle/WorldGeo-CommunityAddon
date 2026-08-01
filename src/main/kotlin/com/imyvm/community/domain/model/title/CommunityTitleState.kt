package com.imyvm.community.domain.model.title

import java.util.UUID

enum class CommunityTitleKind(val displayKey: String) {
    FOREMAN("community.title.foreman")
}

data class CommunityTitleSlot(
    val index: Int,
    var holderUuid: UUID? = null,
    var cooldownUntilMillis: Long = 0L
)

data class CommunityTitleState(
    var foremanSlots: MutableList<CommunityTitleSlot> = mutableListOf(CommunityTitleSlot(0)),
    var selectedDisplay: MutableSet<UUID> = mutableSetOf()
) {
    fun normalized(): CommunityTitleState {
        if (foremanSlots.isEmpty()) foremanSlots.add(CommunityTitleSlot(0))
        foremanSlots.sortBy { it.index }
        return this
    }

    fun foremanHolderSlot(playerUuid: UUID): CommunityTitleSlot? = foremanSlots.firstOrNull { it.holderUuid == playerUuid }

    fun activeForemen(): Set<UUID> = foremanSlots.mapNotNull { it.holderUuid }.toSet()
}
