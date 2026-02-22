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
import com.imyvm.iwg.util.text.Translator
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.network.ServerPlayerEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun runOpenOperationMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
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
            },
            voteCreationMode = false
        )
    }
}

fun runSendingCommunityDescription(player: ServerPlayerEntity, community: Community) {
    community.sendCommunityRegionDescription(player)
    player.closeHandledScreen()
}

fun runOpenMemberListMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMemberListMenu(syncId, community, player) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runOpenSettingMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
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

fun canUseCommunityTeleport(player: ServerPlayerEntity, community: Community, scope: GeoScope): Boolean {
    val role = community.getMemberRole(player.uuid)
    val isFormalMember = role == MemberRoleType.OWNER || role == MemberRoleType.ADMIN || role == MemberRoleType.MEMBER
    if (isFormalMember) return true
    return RegionDataApi.inquireTeleportPointAccessibility(scope)
}

fun runTeleportCommunity(player: ServerPlayerEntity, community: Community) {
    player.closeHandledScreen()

    val region = community.getRegion()
    if (region == null) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
        return
    }

    val mainScope = region.geometryScope.firstOrNull()
    if (mainScope == null) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"),)
        return
    }

    if (!canUseCommunityTeleport(player, community, mainScope)) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
        return
    }

    startCommunityTeleportExecution(player, community, mainScope)
}

fun startCommunityTeleportExecution(player: ServerPlayerEntity, community: Community, scope: GeoScope): Int {
    val region = community.getRegion()
    if (region == null) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
        return 0
    }
    if (!canUseCommunityTeleport(player, community, scope)) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
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
            player.sendMessage(
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
    player.sendMessage(
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
        val player = com.imyvm.community.WorldGeoCommunityAddon.server?.playerManager?.getPlayer(pending.playerUUID)
        if (player == null) {
            iterator.remove()
            continue
        }
        val community = com.imyvm.community.infra.CommunityDatabase.getCommunityById(pending.regionNumberId)
        if (community == null) {
            iterator.remove()
            continue
        }

        val moved = player.squaredDistanceTo(pending.startX, pending.startY, pending.startZ) > 0.01
        if (moved) {
            player.sendMessage(Translator.tr("community.teleport.execution.cancelled.moved"))
            iterator.remove()
            continue
        }

        if (player.hurtTime > 0) {
            player.sendMessage(Translator.tr("community.teleport.execution.cancelled.attacked"))
            iterator.remove()
            continue
        }

        if (now < pending.executeAt) {
            continue
        }

        val scope = community.getRegion()?.geometryScope?.firstOrNull { it.scopeName.equals(pending.scopeName, ignoreCase = true) }
        if (scope == null) {
            player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"))
            iterator.remove()
            continue
        }

        val result = executeCommunityTeleport(player, community, scope, pending.usageKey, pending.cost)
        iterator.remove()
    }
}

private fun executeCommunityTeleport(
    player: ServerPlayerEntity,
    community: Community,
    scope: GeoScope,
    usageKey: String,
    cost: Long
): Int {
    val region = community.getRegion() ?: return 0
    if (!canUseCommunityTeleport(player, community, scope)) {
        player.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
        return 0
    }

    if (cost > 0) {
        val playerAccount = EconomyMod.data.getOrCreate(player)
        if (playerAccount.money < cost) {
            player.sendMessage(Translator.tr("community.teleport.execution.cancelled.balance_changed"))
            return 0
        }
        playerAccount.money -= cost
    }

    PlayerInteractionApi.teleportPlayerToScope(player, region, scope)
    teleportUsageByDay[usageKey] = (teleportUsageByDay[usageKey] ?: 0) + 1

    player.addStatusEffect(StatusEffectInstance(StatusEffects.NAUSEA, CommunityConfig.TELEPORT_POST_EFFECT_TICKS.value, 0, false, false, true))
    player.serverWorld.spawnParticles(
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
    player.sendMessage(
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

fun runTeleportToScope(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = player,
            community = community,
            geographicFunctionType = GeographicFunctionType.TELEPORT_POINT_EXECUTION,
        ) { runBackToCommunityMenu(player, community, runBackGrandfather) }
    }
}

fun runShowLeaveConfirmMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather: (ServerPlayerEntity) -> Unit) {
    val permissionResult = CommunityPermissionPolicy.canQuitCommunity(player, community)
    if (!permissionResult.isAllowed()) {
        permissionResult.sendFeedback(player)
        return
    }

    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val cautions = listOf(
        com.imyvm.community.util.Translator.tr("ui.confirm.leave.caution", communityName)?.string
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

private fun runBackToCommunityMenu(player: ServerPlayerEntity, community: Community, runBackGrandfather : ((ServerPlayerEntity) -> Unit)) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityMenu(
            syncId = syncId,
            player = player,
            community = community,
            runBack = runBackGrandfather
        )
    }
}
