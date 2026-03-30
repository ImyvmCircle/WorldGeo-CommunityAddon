package com.imyvm.community.application.interaction.screen.inner_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.entrypoint.screen.ConfirmMenu
import com.imyvm.community.entrypoint.screen.component.ConfirmTaskType
import com.imyvm.community.entrypoint.screen.inner_community.CommunityAdministrationMenu
import com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu
import com.imyvm.community.entrypoint.screen.inner_community.affairs.CommunitySettingMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityMemberListMenu
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.PricingConfig
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.community.util.Translator
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun runOpenOperationMenu(player: ServerPlayer, community: Community, runBackGrandfather : ((ServerPlayer) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityAdministrationMenu(
            syncId,
            community,
            player,
            runBack = {
                runBackToCommunityMenu(
                    player,
                    community,
                    runBackGrandfather
                )
            }
        )
    }
}

fun runSendingCommunityDescription(player: ServerPlayer, community: Community) {
    community.sendCommunityRegionDescription(player)
    val regionId = community.regionNumberId
    if (regionId != null) {
        player.sendSystemMessage(
            Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand( "/community open_menu $regionId"))
            }
        )
    }
    player.closeContainer()
}

fun runOpenMemberListMenu(player: ServerPlayer, community: Community, runBackGrandfather : ((ServerPlayer) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMemberListMenu(syncId, community, player) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenSettingMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunitySettingMenu(syncId, player, community) {
            runBackToCommunityMenu(
                player,
                community,
                runBackGrandfather
            )
        }
    }
}

fun canUseCommunityTeleport(player: ServerPlayer, community: Community, scope: GeoScope): Boolean {
    val role = community.getMemberRole(player.uuid)
    val isFormalMember = role == MemberRoleType.OWNER || role == MemberRoleType.ADMIN || role == MemberRoleType.MEMBER
    if (isFormalMember) return true
    return RegionDataApi.inquireTeleportPointAccessibility(scope)
}

fun runTeleportCommunity(player: ServerPlayer, community: Community) {
    player.closeContainer()

    val region = community.getRegion()
    if (region == null) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
        return
    }

    val mainScope = region.geometryScope.firstOrNull()
    if (mainScope == null) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"),)
        return
    }

    if (!canUseCommunityTeleport(player, community, mainScope)) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
        return
    }

    startCommunityTeleportExecution(player, community, mainScope)
}

fun startCommunityTeleportExecution(player: ServerPlayer, community: Community, scope: GeoScope): Int {
    val region = community.getRegion()
    if (region == null) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
        return 0
    }
    if (!canUseCommunityTeleport(player, community, scope)) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
        return 0
    }

    val regionId = community.regionNumberId ?: return 0
    val usageKey = getTeleportUsageKey(player.uuid, regionId)
    val usedTimes = teleportUsageByDay[usageKey] ?: 0
    val role = community.getMemberRole(player.uuid)
    val isFormalMember = role == MemberRoleType.OWNER || role == MemberRoleType.ADMIN || role == MemberRoleType.MEMBER
    val freeUses = if (isFormalMember) {
        CommunityConfig.TELEPORT_FREE_USES_FORMAL_MEMBER.value
    } else {
        CommunityConfig.TELEPORT_FREE_USES_NON_FORMAL.value
    }

    val paidTier = (usedTimes - freeUses).coerceAtLeast(0)
    val multiplier = 1L shl paidTier.coerceAtMost(20)
    val cost = if (usedTimes < freeUses) {
        0L
    } else {
        PricingConfig.TELEPORT_PAID_BASE_COST.value * multiplier
    }
    val delaySeconds = if (usedTimes < freeUses) {
        0
    } else {
        CommunityConfig.TELEPORT_PAID_BASE_DELAY_SECONDS.value * multiplier.toInt()
    }

    if (cost > 0) {
        val playerAccount = EconomyMod.data.getOrCreate(player)
        if (playerAccount.money < cost) {
            player.sendSystemMessage(
                Translator.tr(
                    "community.teleport.execution.error.insufficient_balance",
                    String.format("%.2f", cost / 100.0),
                    String.format("%.2f", playerAccount.money / 100.0)
                )
            )
            return 0
        }
    }

    if (delaySeconds <= 0) {
        return executeCommunityTeleport(player, community, scope, usageKey, cost)
    }

    pendingTeleportExecutions[player.uuid] = PendingTeleportExecution(
        playerUUID = player.uuid,
        regionNumberId = regionId,
        scopeName = scope.scopeName,
        usageKey = usageKey,
        cost = cost,
        executeAt = System.currentTimeMillis() + delaySeconds * 1000L,
        startX = player.x,
        startY = player.y,
        startZ = player.z
    )
    player.sendSystemMessage(
        Translator.tr(
            "community.teleport.execution.pending",
            scope.scopeName,
            String.format("%.2f", cost / 100.0),
            delaySeconds.toString()
        )
    )
    return 1
}

fun processPendingTeleportExecutions() {
    val iterator = pendingTeleportExecutions.entries.iterator()
    val now = System.currentTimeMillis()
    while (iterator.hasNext()) {
        val (_, pending) = iterator.next()
        val player = com.imyvm.community.WorldGeoCommunityAddon.server?.playerList?.getPlayer(pending.playerUUID)
        if (player == null) {
            iterator.remove()
            continue
        }
        val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(pending.regionNumberId)
        if (community == null) {
            iterator.remove()
            continue
        }

        val moved = player.distanceToSqr(pending.startX, pending.startY, pending.startZ) > 0.01
        if (moved) {
            player.sendSystemMessage(Translator.tr("community.teleport.execution.cancelled.moved"))
            iterator.remove()
            continue
        }

        if (player.hurtTime > 0) {
            player.sendSystemMessage(Translator.tr("community.teleport.execution.cancelled.attacked"))
            iterator.remove()
            continue
        }

        if (now < pending.executeAt) {
            continue
        }

        val scope = community.getRegion()?.geometryScope?.firstOrNull { it.scopeName.equals(pending.scopeName, ignoreCase = true) }
        if (scope == null) {
            player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"))
            iterator.remove()
            continue
        }

        val result = executeCommunityTeleport(player, community, scope, pending.usageKey, pending.cost)
        iterator.remove()
    }
}

private fun executeCommunityTeleport(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope,
    usageKey: String,
    cost: Long
): Int {
    val region = community.getRegion() ?: return 0
    if (!canUseCommunityTeleport(player, community, scope)) {
        player.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
        return 0
    }

    if (cost > 0) {
        val playerAccount = EconomyMod.data.getOrCreate(player)
        if (playerAccount.money < cost) {
            player.sendSystemMessage(Translator.tr("community.teleport.execution.cancelled.balance_changed"))
            return 0
        }
        playerAccount.money -= cost
    }

    PlayerInteractionApi.teleportPlayerToScope(player, region, scope)
    teleportUsageByDay[usageKey] = (teleportUsageByDay[usageKey] ?: 0) + 1

    player.addEffect(MobEffectInstance(MobEffects.NAUSEA, CommunityConfig.TELEPORT_POST_EFFECT_TICKS.value, 0, false, false, true))
    player.level().sendParticles(
        ParticleTypes.CRIT,
        player.x,
        player.y + 0.1,
        player.z,
        40,
        0.9,
        0.1,
        0.9,
        0.02
    )
    player.sendSystemMessage(
        Translator.tr(
            "community.teleport.execution.completed",
            scope.scopeName,
            String.format("%.2f", cost / 100.0)
        )
    )
    return 1
}

private fun getTeleportUsageKey(playerUUID: UUID, regionNumberId: Int): String {
    val day = LocalDate.now(ZoneId.systemDefault()).toString()
    return "$day:$playerUUID:$regionNumberId"
}

private data class PendingTeleportExecution(
    val playerUUID: UUID,
    val regionNumberId: Int,
    val scopeName: String,
    val usageKey: String,
    val cost: Long,
    val executeAt: Long,
    val startX: Double,
    val startY: Double,
    val startZ: Double
)

private val teleportUsageByDay: MutableMap<String, Int> = mutableMapOf()
private val pendingTeleportExecutions: MutableMap<UUID, PendingTeleportExecution> = mutableMapOf()

fun runTeleportToScope(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = GeographicFunctionType.TELEPORT_POINT_EXECUTION,
        ) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runShowLeaveConfirmMenu(player: ServerPlayer, community: Community, runBackGrandfather: (ServerPlayer) -> Unit) {
    val permissionResult = CommunityPermissionPolicy.canQuitCommunity(player, community)
    if (!permissionResult.isAllowed()) {
        permissionResult.sendSuccess(player)
        return
    }

    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val cautions = listOf(
        com.imyvm.community.util.Translator.tr("ui.confirm.leave.caution", communityName).string
            ?: "Leave $communityName? You cannot undo this action."
    )

    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.LEAVE_COMMUNITY,
            cautions = cautions,
            runBack = { runBackToCommunityMenu(player, community, runBackGrandfather) },
            targetCommunity = community
        )
    }
}

fun runBackToCommunityMenu(player: ServerPlayer, community: Community, runBackGrandfather : ((ServerPlayer) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMenu(
            syncId = syncId,
            player = player,
            community = community,
            runBack = runBackGrandfather
        )
    }
}

fun runLikeCommunity(player: ServerPlayer, community: Community) {
    player.closeContainer()

    val timezone = ZoneId.of(CommunityConfig.TIMEZONE.value)
    val today = LocalDate.now(timezone)
    val lastLikeTimestamp = community.lastLikedBy[player.uuid]
    val communityName = community.generateCommunityMark()
    val regionId = community.regionNumberId

    if (lastLikeTimestamp != null) {
        val lastLikeDate = java.time.Instant.ofEpochMilli(lastLikeTimestamp)
            .atZone(timezone).toLocalDate()
        if (!today.isAfter(lastLikeDate)) {
            player.sendSystemMessage(Translator.tr("community.like.already_liked"))
            sendReturnToMenuButton(player, regionId)
            return
        }
    }

    community.likeCount++
    community.lastLikedBy[player.uuid] = System.currentTimeMillis()

    val rank = calculateCommunityLikeRank(community)
    player.sendSystemMessage(Translator.tr("community.like.success", communityName, community.likeCount, rank))
    sendReturnToMenuButton(player, regionId)
}

private fun sendReturnToMenuButton(player: ServerPlayer, regionId: Int?) {
    if (regionId != null) {
        player.sendSystemMessage(
            Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand( "/community open_menu $regionId"))
            }
        )
    }
}

private fun calculateCommunityLikeRank(community: Community): Int {
    val sorted = com.imyvm.community.infra.CommunityDatabase.communities
        .sortedByDescending { it.likeCount }
    return sorted.indexOfFirst { it.regionNumberId == community.regionNumberId } + 1
}
