package com.imyvm.community.application.title

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.TreasuryMutationResult
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.title.CommunityTitleKind
import com.imyvm.community.domain.model.title.CommunityTitleSlot
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.domain.model.transaction.ResourceDirection
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object CommunityTitleService {
    private val zoneId = ZoneId.of("Asia/Shanghai")
    const val FOREMAN_REWARD_PERCENT = 120L
    const val FOREMAN_EXTRA_WEEKLY_CAP = 60000L

    fun formalMemberCount(community: Community): Int = community.member.values.count {
        it.basicRoleType == MemberRoleType.OWNER || it.basicRoleType == MemberRoleType.ADMIN || it.basicRoleType == MemberRoleType.MEMBER
    }

    fun foremanSlotLimit(community: Community): Int {
        val divisor = if (community.isManor()) 2 else 5
        return 1 + formalMemberCount(community) / divisor
    }

    fun availableForemanSlots(community: Community): Int = (foremanSlotLimit(community) - community.titleState.normalized().foremanSlots.size).coerceAtLeast(0)

    fun buyForemanSlot(community: Community): Result<Long> {
        val regionId = community.regionNumberId ?: return Result.failure(IllegalStateException("community region not bound"))
        if (availableForemanSlots(community) <= 0) return Result.failure(IllegalStateException("no foreman slot qualification"))
        val cost = PricingConfig.TITLE_FOREMAN_SLOT_COST.value
        if (community.getTotalAssets() < cost) return Result.failure(IllegalStateException("insufficient treasury"))
        val slots = community.titleState.normalized().foremanSlots
        val nextIndex = (slots.maxOfOrNull { it.index } ?: -1) + 1
        slots.add(CommunityTitleSlot(nextIndex))
        return try {
            mutateTreasury(
                community,
                cost,
                ResourceDirection.DEBIT,
                "title",
                "community:title-slot:$regionId:$nextIndex:${System.currentTimeMillis()}",
                "title-foreman-slot",
                nextIndex.toString(),
                "community.treasury.desc.title_foreman_slot",
                listOf(nextIndex.toString())
            ).getOrThrow()
            Result.success(cost)
        } catch (error: Exception) {
            slots.removeIf { it.index == nextIndex }
            Result.failure(error)
        }
    }

    fun grantForeman(community: Community, targetUuid: UUID, nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true): Result<Int> {
        validateActiveFormalMember(community, targetUuid).getOrThrow()
        community.titleState.normalized().foremanHolderSlot(targetUuid)?.let { return Result.success(it.index) }
        val slot = community.titleState.foremanSlots.firstOrNull { it.holderUuid == null && it.cooldownUntilMillis <= nowMillis }
            ?: return Result.failure(IllegalStateException("no available foreman slot"))
        slot.holderUuid = targetUuid
        slot.cooldownUntilMillis = nextMonth(nowMillis)
        if (persist) CommunityDatabase.save()
        return Result.success(slot.index)
    }

    fun revokeForeman(community: Community, targetUuid: UUID, nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true): Result<Int> {
        val slot = community.titleState.normalized().foremanHolderSlot(targetUuid)
            ?: return Result.failure(NoSuchElementException("target has no foreman title"))
        slot.holderUuid = null
        slot.cooldownUntilMillis = nextMonth(nowMillis)
        community.titleState.selectedDisplay.remove(targetUuid)
        if (persist) CommunityDatabase.save()
        return Result.success(slot.index)
    }

    fun selectDisplay(community: Community, playerUuid: UUID, persist: Boolean = true): Result<Unit> {
        validateActiveFormalMember(community, playerUuid).getOrThrow()
        if (community.titleState.foremanHolderSlot(playerUuid) == null) return Result.failure(IllegalStateException("player has no displayable title"))
        for (other in CommunityDatabase.communities) other.titleState.selectedDisplay.remove(playerUuid)
        community.titleState.selectedDisplay.add(playerUuid)
        if (persist) CommunityDatabase.save()
        return Result.success(Unit)
    }

    fun displayLabel(community: Community, playerUuid: UUID): String? {
        if (!community.titleState.selectedDisplay.contains(playerUuid)) return null
        if (community.titleState.foremanHolderSlot(playerUuid) == null) return null
        return CommunityTitleKind.FOREMAN.displayKey
    }

    fun rewardPercent(community: Community, playerUuid: UUID): Long =
        if (community.titleState.foremanHolderSlot(playerUuid) != null) FOREMAN_REWARD_PERCENT else 100L

    fun extraWeeklyCap(community: Community, playerUuid: UUID): Long =
        if (community.titleState.foremanHolderSlot(playerUuid) != null) FOREMAN_EXTRA_WEEKLY_CAP else 0L

    private fun validateActiveFormalMember(community: Community, playerUuid: UUID): Result<Unit> {
        if (community.status != CommunityStatus.ACTIVE_MANOR && community.status != CommunityStatus.ACTIVE_REALM) {
            return Result.failure(IllegalStateException("community is not active"))
        }
        val role = community.getMemberRole(playerUuid) ?: return Result.failure(NoSuchElementException("target is not a member"))
        if (role == MemberRoleType.APPLICANT || role == MemberRoleType.REFUSED) return Result.failure(IllegalStateException("target is not a formal member"))
        return Result.success(Unit)
    }

    private fun nextMonth(nowMillis: Long): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        return now.plusMonths(1).toInstant().toEpochMilli()
    }
}
