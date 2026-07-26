package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runBackToCommunityMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.api.CommunityApi
import com.imyvm.community.entrypoint.screen.ConfirmMenu
import com.imyvm.community.entrypoint.screen.component.ConfirmTaskType
import com.imyvm.community.entrypoint.screen.inner_community.affairs.BuildingMenu
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

fun runOpenBuildingMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    refreshBuildingProgress(community, player)
    CommunityMenuOpener.open(player) { syncId ->
        BuildingMenu(syncId, player, community) {
            runBackToCommunityMenu(player, community, runBackGrandfather)
        }
    }
}

fun runOpenBuildingClaimConfirmMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    refreshBuildingProgress(community, player)
    val snapshot = snapshotBuildingReward(community, player.uuid)
    if (snapshot.claimableAmount <= 0L) {
        player.closeContainer()
        player.sendSystemMessage(Translator.tr("community.building.claim.none"))
        return
    }

    val cautions = listOf(
        Translator.tr("ui.building.confirm.claim.reward", formatAmount(snapshot.claimableAmount)).string,
        Translator.tr("ui.building.confirm.claim.blocks", snapshot.claimableBlocks).string,
        Translator.tr("ui.building.confirm.claim.period", currentBuildingRewardPeriodId()).string
    )

    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.CLAIM_BUILDING_REWARD,
            cautions = cautions,
            runBack = { runOpenBuildingMenu(player, community, runBackGrandfather) },
            targetCommunity = community
        )
    }
}

fun runClaimBuildingReward(player: ServerPlayer, community: Community) {
    val regionId = community.regionNumberId
    if (regionId == null) {
        player.sendSystemMessage(Translator.tr("community.building.claim.failed", "missing region id"))
        return
    }

    refreshBuildingProgress(community, player)
    val snapshot = snapshotBuildingReward(community, player.uuid)
    if (snapshot.claimableAmount <= 0L) {
        player.sendSystemMessage(Translator.tr("community.building.claim.none"))
        return
    }

    val periodId = currentBuildingRewardPeriodId()
    CommunityApi.claimBuildingReward(regionId, player.uuid, periodId)
        .onSuccess { amount ->
            if (amount <= 0L) {
                player.sendSystemMessage(Translator.tr("community.building.claim.none"))
            } else {
                player.sendSystemMessage(
                    Translator.tr(
                        "community.building.claim.success",
                        formatAmount(amount),
                        snapshot.claimableBlocks,
                        periodId
                    )
                )
            }
        }
        .onFailure { error ->
            player.sendSystemMessage(
                Translator.tr(
                    "community.building.claim.failed",
                    error.message ?: error::class.java.simpleName
                )
            )
        }
}

data class BuildingRewardMenuSnapshot(
    val totalBlocks: Long,
    val claimedBlocks: Long,
    val claimableBlocks: Long,
    val blockValue: Long,
    val claimableAmount: Long,
    val lastClaimedPeriodId: String?
)

fun snapshotBuildingReward(community: Community, playerUUID: UUID): BuildingRewardMenuSnapshot {
    val ledger = community.buildingRewardLedgers[playerUUID]
    val totalBlocks = community.developmentBlockPlaceTotal
    val claimedBlocks = ledger?.claimedBlockPlaceCount ?: 0L
    val claimableBlocks = (totalBlocks - claimedBlocks)
        .coerceAtLeast(0L)
        .coerceAtMost(CommunityConfig.BUILDING_REWARD_DEFAULT_BLOCK_LIMIT.value.toLong())
    val blockValue = PricingConfig.BUILDING_REWARD_BLOCK_VALUE.value
    return BuildingRewardMenuSnapshot(
        totalBlocks = totalBlocks,
        claimedBlocks = claimedBlocks,
        claimableBlocks = claimableBlocks,
        blockValue = blockValue,
        claimableAmount = claimableBlocks * blockValue,
        lastClaimedPeriodId = ledger?.lastClaimedPeriodId
    )
}

private fun refreshBuildingProgress(community: Community, player: ServerPlayer) {
    val regionId = community.regionNumberId ?: return
    CommunityApi.refreshDevelopment(regionId).onFailure {
        player.sendSystemMessage(
            Translator.tr(
                "community.building.refresh.failed",
                it.message ?: it::class.java.simpleName
            )
        )
    }
}

private fun currentBuildingRewardPeriodId(): String {
    val now = LocalDate.now(ZoneId.of(CommunityConfig.TIMEZONE.value))
    val weekFields = WeekFields.ISO
    val year = now.get(weekFields.weekBasedYear())
    val week = now.get(weekFields.weekOfWeekBasedYear())
    return String.format(Locale.ROOT, "%04d-W%02d", year, week)
}

private fun formatAmount(amount: Long): String = String.format(Locale.ROOT, "%.2f", amount / 100.0)
