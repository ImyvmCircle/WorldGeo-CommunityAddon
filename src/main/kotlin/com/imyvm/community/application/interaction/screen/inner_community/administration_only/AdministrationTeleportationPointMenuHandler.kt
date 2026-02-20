package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

fun getTeleportPointInformationItemStack(
    item: Item,
    scope: GeoScope
): ItemStack {
    val itemStack = ItemStack(item)

    val loreLines = mutableListOf<Text>()
    val blockPos = PlayerInteractionApi.getTeleportPoint(scope)
    if (blockPos != null) {
        loreLines.add(Text.of("x=" + blockPos.x))
        loreLines.add(Text.of("y=" + blockPos.y))
        loreLines.add(Text.of("z=" + blockPos.z))
    } else {
        loreLines.add(Translator.tr("ui.community.administration.teleport_point.inquiry.lore.no_point")!!)
    }

    return getLoreButton(itemStack, loreLines)
}

fun runInquiryTeleportPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    playerExecutor.closeHandledScreen()

    val region = community.getRegion()
    if (region != null) {
        val blockPos = PlayerInteractionApi.getTeleportPoint(scope)
        if (blockPos != null) {
            playerExecutor.sendMessage(
                Translator.tr(
                    "ui.community.administration.teleport_point.inquiry.success.result",
                    blockPos.x,
                    blockPos.y,
                    blockPos.z
                )
            )
        } else {
            playerExecutor.sendMessage(Translator.tr("ui.community.administration.teleport_point.inquiry.success.no_point"))
        }
    } else {
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
    }
}

fun runToggleTeleportPointAccessibility(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val permission = com.imyvm.community.domain.policy.permission.AdministrationPermission.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        val region = community.getRegion()
        if (region != null) {
            PlayerInteractionApi.toggleTeleportPointAccessibility(scope)
            val isPublic = com.imyvm.iwg.inter.api.RegionDataApi.inquireTeleportPointAccessibility(scope)
            val stateKey = if (isPublic) {
                "ui.community.administration.teleport_point.state.public"
            } else {
                "ui.community.administration.teleport_point.state.private"
            }
            playerExecutor.sendMessage(Translator.tr(
                "ui.community.administration.teleport_point.toggle.result",
                Translator.tr(stateKey)?.string ?: if (isPublic) "Public" else "Private"
            ))
            CommunityMenuOpener.open(playerExecutor) { syncId ->
                AdministrationTeleportPointMenu(
                    syncId = syncId,
                    playerExecutor = playerExecutor,
                    community = community,
                    scope = scope,
                    runBack = runBack
                )
            }
        } else {
            playerExecutor.closeHandledScreen()
            playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runSettingTeleportPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    val permission = com.imyvm.community.domain.policy.permission.AdministrationPermission.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        playerExecutor.closeHandledScreen()

        val region = community.getRegion()
        if (region != null) {
            PlayerInteractionApi.addTeleportPoint(playerExecutor, region, scope)
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            val blockPos = playerExecutor.blockPos
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.teleport_point_set",
                scope.scopeName,
                blockPos.x,
                blockPos.y,
                blockPos.z,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("Teleport point for scope '${scope.scopeName}' was set to (${blockPos.x}, ${blockPos.y}, ${blockPos.z}) in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        } else {
            playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runResetTeleportPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    val permission = com.imyvm.community.domain.policy.permission.AdministrationPermission.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        playerExecutor.closeHandledScreen()

        val region = community.getRegion()
        if (region != null) {
            PlayerInteractionApi.resetTeleportPoint(playerExecutor, region, scope)
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.teleport_point_reset",
                scope.scopeName,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("Teleport point for scope '${scope.scopeName}' was reset in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        } else {
            playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runTeleportToPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    playerExecutor.closeHandledScreen()

    val region = community.getRegion()
    if (region != null) {
        PlayerInteractionApi.teleportPlayerToScope(playerExecutor, region, scope)
    } else {
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
    }
}