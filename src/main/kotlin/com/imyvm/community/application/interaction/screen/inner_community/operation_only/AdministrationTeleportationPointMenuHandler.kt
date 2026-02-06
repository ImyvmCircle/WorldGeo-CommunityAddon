package com.imyvm.community.application.interaction.screen.inner_community.operation_only

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
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
        loreLines.add(Translator.tr("ui.community.operation.teleport_point.inquiry.lore.no_point")!!)
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
                    "ui.community.operation.teleport_point.inquiry.success.result",
                    blockPos.x,
                    blockPos.y,
                    blockPos.z
                )
            )
        } else {
            playerExecutor.sendMessage(Translator.tr("ui.community.operation.teleport_point.inquiry.success.no_point"))
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
    val region = community.getRegion()
    if (region != null) {
        PlayerInteractionApi.toggleTeleportPointAccessibility(scope)
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

fun runSettingTeleportPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    playerExecutor.closeHandledScreen()

    val region = community.getRegion()
    if (region != null) {
        PlayerInteractionApi.addTeleportPoint(playerExecutor, region, scope)
    } else {
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
    }
}

fun runResetTeleportPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    playerExecutor.closeHandledScreen()

    val region = community.getRegion()
    if (region != null) {
        PlayerInteractionApi.resetTeleportPoint(playerExecutor, region, scope)
    } else {
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
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