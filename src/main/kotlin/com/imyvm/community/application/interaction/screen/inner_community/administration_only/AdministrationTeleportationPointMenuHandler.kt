package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.TeleportPointConfirmationData
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
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
            playerExecutor.sendMessage(
                Translator.tr(
                    "ui.community.administration.teleport_point.toggle.result",
                    Translator.tr(stateKey)?.string ?: if (isPublic) "Public" else "Private"
                )
            )
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
        if (region == null) {
            playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
            return@executeWithPermission
        }

        val regionId = community.regionNumberId ?: return@executeWithPermission
        val existingPending = WorldGeoCommunityAddon.pendingOperations[regionId]
        if (existingPending != null) {
            playerExecutor.sendMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val (cost, reasonKey) = calculateTeleportPointSettingCost(region, scope)
        val currentAssets = community.getTotalAssets()
        if (cost > 0 && currentAssets < cost) {
            playerExecutor.sendMessage(
                Translator.tr(
                    "community.modification.error.insufficient_assets",
                    String.format("%.2f", cost / 100.0),
                    String.format("%.2f", currentAssets / 100.0)
                )
            )
            return@executeWithPermission
        }

        addPendingOperation(
            regionId = regionId,
            type = PendingOperationType.TELEPORT_POINT_CONFIRMATION,
            expireMinutes = 5,
            inviterUUID = playerExecutor.uuid,
            teleportPointData = TeleportPointConfirmationData(
                regionNumberId = regionId,
                scopeName = scope.scopeName,
                executorUUID = playerExecutor.uuid,
                cost = cost,
                reasonKey = reasonKey
            )
        )

        playerExecutor.sendMessage(
            Translator.tr(
                "community.teleport_point.confirmation.summary",
                scope.scopeName,
                String.format("%.2f", cost / 100.0),
                Translator.tr(reasonKey)?.string ?: reasonKey
            )
        )
        sendInteractiveTeleportPointConfirmation(playerExecutor, regionId, scope.scopeName)
    }
}

fun onConfirmTeleportPointSetting(playerExecutor: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.teleportPointData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.TELEPORT_POINT_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid || !request.scopeName.equals(scopeName, ignoreCase = true)) {
        playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.not_yours"))
        return 0
    }
    if (System.currentTimeMillis() > pendingOperation.expireAt) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.expired"))
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    val region = community?.getRegion()
    val scope = region?.geometryScope?.firstOrNull { it.scopeName.equals(request.scopeName, ignoreCase = true) }
    if (community == null || region == null || scope == null) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
        return 0
    }

    val currentAssets = community.getTotalAssets()
    if (request.cost > 0 && currentAssets < request.cost) {
        playerExecutor.sendMessage(
            Translator.tr(
                "community.modification.error.insufficient_assets",
                String.format("%.2f", request.cost / 100.0),
                String.format("%.2f", currentAssets / 100.0)
            )
        )
        return 0
    }

    val result = PlayerInteractionApi.addTeleportPoint(playerExecutor, region, scope)
    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    if (result == 0) return 0

    if (request.cost > 0) {
        community.expenditures.add(Turnover(request.cost, System.currentTimeMillis()))
    }
    CommunityDatabase.save()

    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val costText = String.format("%.2f", request.cost / 100.0)
    val newPos = PlayerInteractionApi.getTeleportPoint(scope)
    playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.completed", scope.scopeName, costText))

    val notification = Translator.tr(
        "community.notification.teleport_point_set",
        scope.scopeName,
        newPos?.x ?: "?",
        newPos?.y ?: "?",
        newPos?.z ?: "?",
        playerExecutor.name.string,
        communityName,
        costText,
        Translator.tr(request.reasonKey)?.string ?: request.reasonKey
    ) ?: Text.literal("Teleport point set")
    notifyFormalMembers(community, playerExecutor.server, notification)
    return 1
}

fun onCancelTeleportPointSetting(playerExecutor: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.teleportPointData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.TELEPORT_POINT_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid || !request.scopeName.equals(scopeName, ignoreCase = true)) {
        playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    playerExecutor.sendMessage(Translator.tr("community.teleport_point.confirmation.cancelled", request.scopeName))
    return 1
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
            ) ?: net.minecraft.text.Text.literal("Teleport point for scope ${scope.scopeName} was reset in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.server, notification, playerExecutor)

            com.imyvm.community.infra.CommunityDatabase.save()
        } else {
            playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runTeleportToPoint(playerExecutor: ServerPlayerEntity, community: Community, scope: GeoScope) {
    playerExecutor.closeHandledScreen()
    com.imyvm.community.application.interaction.screen.inner_community.startCommunityTeleportExecution(
        player = playerExecutor,
        community = community,
        scope = scope
    )
}

private fun calculateTeleportPointSettingCost(region: Region, scope: GeoScope): Pair<Long, String> {
    val currentPoint = PlayerInteractionApi.getTeleportPoint(scope)
    if (currentPoint != null) {
        return PricingConfig.TELEPORT_POINT_MODIFY_COST.value to "community.teleport_point.cost_reason.modify_existing"
    }

    val activeCount = region.geometryScope.count { PlayerInteractionApi.getTeleportPoint(it) != null }
    if (activeCount <= 0) {
        return 0L to "community.teleport_point.cost_reason.first_free"
    }

    val exponent = (activeCount - 1).coerceAtMost(20)
    val multiplier = 1L shl exponent
    return PricingConfig.TELEPORT_POINT_SECOND_POINT_BASE_COST.value * multiplier to "community.teleport_point.cost_reason.new_additional"
}

private fun sendInteractiveTeleportPointConfirmation(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String) {
    val confirmText = Translator.tr("community.teleport_point.confirmation.confirm_button") ?: Text.literal("§a§l[CONFIRM]§r")
    val cancelText = Translator.tr("community.teleport_point.confirmation.cancel_button") ?: Text.literal("§c§l[CANCEL]§r")

    val confirmButton = confirmText.copy().styled { style ->
        style.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/community confirm_teleport_point_set $regionNumberId $scopeName"))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Translator.tr("community.teleport_point.confirmation.confirm_hover")))
    }

    val cancelButton = cancelText.copy().styled { style ->
        style.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/community cancel_teleport_point_set $regionNumberId $scopeName"))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Translator.tr("community.teleport_point.confirmation.cancel_hover")))
    }

    val prompt = (Translator.tr("community.teleport_point.confirmation.prompt") ?: Text.literal("§e§l[ACTION REQUIRED]§r"))
        .copy()
        .append(Text.literal(" "))
        .append(confirmButton)
        .append(Text.literal(" "))
        .append(cancelButton)
    player.sendMessage(prompt)
}

private fun notifyFormalMembers(
    community: Community,
    server: net.minecraft.server.MinecraftServer,
    message: Text
) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerManager.getPlayer(memberUUID)?.sendMessage(message)
        memberAccount.mail.add(message)
    }
}
