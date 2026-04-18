package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.TeleportPointConfirmationData
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Component

fun getTeleportPointInformationItemStack(
    item: Item,
    scope: GeoScope
): ItemStack {
    val itemStack = ItemStack(item)

    val loreLines = mutableListOf<Component>()
    val blockPos = PlayerInteractionApi.getTeleportPoint(scope)
    if (blockPos != null) {
        loreLines.add(Component.literal("x=" + blockPos.x))
        loreLines.add(Component.literal("y=" + blockPos.y))
        loreLines.add(Component.literal("z=" + blockPos.z))
    } else {
        loreLines.add(Translator.tr("ui.admin.teleport_point.inquiry.lore.no_point")!!)
    }

    return getLoreButton(itemStack, loreLines)
}

fun runInquiryTeleportPoint(playerExecutor: ServerPlayer, community: Community, scope: GeoScope) {
    playerExecutor.closeContainer()

    val region = community.getRegion()
    if (region != null) {
        val blockPos = PlayerInteractionApi.getTeleportPoint(scope)
        if (blockPos != null) {
            playerExecutor.sendSystemMessage(
                Translator.tr(
                    "ui.admin.teleport_point.inquiry.success.result",
                    blockPos.x,
                    blockPos.y,
                    blockPos.z
                )
            )
        } else {
            playerExecutor.sendSystemMessage(Translator.tr("ui.admin.teleport_point.inquiry.success.no_point"))
        }
        val regionId = community.regionNumberId
        val scopeNameQuoted = if (!scope.scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"${scope.scopeName}\"" else scope.scopeName
        if (regionId != null) {
            playerExecutor.sendSystemMessage(
                Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                    style.withClickEvent(ClickEvent.RunCommand( "/community open_teleport_admin $regionId $scopeNameQuoted"))
                }
            )
        }
    } else {
        playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
    }
}

fun runToggleTeleportPointAccessibility(
    playerExecutor: ServerPlayer,
    community: Community,
    scope: GeoScope,
    runBack: (ServerPlayer) -> Unit
) {
    val permission = com.imyvm.community.domain.policy.permission.AdminPrivilege.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        val region = community.getRegion()
        if (region != null) {
            PlayerInteractionApi.toggleTeleportPointAccessibility(scope)
            val isPublic = com.imyvm.iwg.inter.api.RegionDataApi.inquireTeleportPointAccessibility(scope)
            val stateKey = if (isPublic) {
                "ui.admin.teleport_point.state.public"
            } else {
                "ui.admin.teleport_point.state.private"
            }
            playerExecutor.sendSystemMessage(
                Translator.tr(
                    "ui.admin.teleport_point.toggle.result",
                    Translator.tr(stateKey).string ?: if (isPublic) "Public" else "Private"
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
            playerExecutor.closeContainer()
            playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runSettingTeleportPoint(playerExecutor: ServerPlayer, community: Community, scope: GeoScope) {
    val permission = com.imyvm.community.domain.policy.permission.AdminPrivilege.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        playerExecutor.closeContainer()

        val region = community.getRegion()
        if (region == null) {
            playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
            return@executeWithPermission
        }

        val regionId = community.regionNumberId ?: return@executeWithPermission
        val existingPending = WorldGeoCommunityAddon.pendingOperations[regionId]
        if (existingPending != null) {
            playerExecutor.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val (cost, reasonKey) = calculateTeleportPointSettingCost(region, scope)
        val currentAssets = community.getTotalAssets()
        if (cost > 0 && currentAssets < cost) {
            playerExecutor.sendSystemMessage(
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

        val dimensionId = TerritoryPricing.getScopeDimensionId(scope)
        val multiplierKey = when (dimensionId) {
            TerritoryPricing.DIMENSION_NETHER -> "community.pricing.dimension.multiplier.nether"
            TerritoryPricing.DIMENSION_END -> "community.pricing.dimension.multiplier.end"
            else -> "community.pricing.dimension.multiplier.overworld"
        }
        playerExecutor.sendSystemMessage(
            Translator.tr(
                "community.pricing.dimension.legend",
                Translator.tr(multiplierKey, TerritoryPricing.getDimensionMultiplier(dimensionId).toString())?.string
                    ?: "$dimensionId x${TerritoryPricing.getDimensionMultiplier(dimensionId)}"
            )
        )
        playerExecutor.sendSystemMessage(
            Translator.tr(
                "community.teleport_point.confirmation.summary",
                scope.scopeName,
                String.format("%.2f", cost / 100.0),
                Translator.tr(reasonKey).string ?: reasonKey
            )
        )
        sendInteractiveTeleportPointConfirmation(playerExecutor, regionId, scope.scopeName)
    }
}

fun onConfirmTeleportPointSetting(playerExecutor: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.teleportPointData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.TELEPORT_POINT_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid || !request.scopeName.equals(scopeName, ignoreCase = true)) {
        playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.not_yours"))
        return 0
    }
    if (System.currentTimeMillis() > pendingOperation.expireAt) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.expired"))
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    val region = community?.getRegion()
    val scope = region?.geometryScope?.firstOrNull { it.scopeName.equals(request.scopeName, ignoreCase = true) }
    if (community == null || region == null || scope == null) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
        return 0
    }

    val currentAssets = community.getTotalAssets()
    if (request.cost > 0 && currentAssets < request.cost) {
        playerExecutor.sendSystemMessage(
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
        community.expenditures.add(Turnover(request.cost, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.teleport_point", listOf(scope.scopeName)))
    }
    CommunityDatabase.save()

    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val costText = String.format("%.2f", request.cost / 100.0)
    val newPos = PlayerInteractionApi.getTeleportPoint(scope)
    playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.completed", scope.scopeName, costText))

    val notification = Translator.tr(
        "community.notification.teleport_point_set",
        scope.scopeName,
        newPos?.x ?: "?",
        newPos?.y ?: "?",
        newPos?.z ?: "?",
        playerExecutor.name.string,
        communityName,
        costText,
        Translator.tr(request.reasonKey).string ?: request.reasonKey
    ) ?: Component.literal("Teleport point set")
    notifyFormalMembers(community, playerExecutor.level().server, notification)
    return 1
}

fun onCancelTeleportPointSetting(playerExecutor: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.teleportPointData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.TELEPORT_POINT_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid || !request.scopeName.equals(scopeName, ignoreCase = true)) {
        playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    playerExecutor.sendSystemMessage(Translator.tr("community.teleport_point.confirmation.cancelled", request.scopeName))
    return 1
}

fun runResetTeleportPoint(playerExecutor: ServerPlayer, community: Community, scope: GeoScope) {
    val permission = com.imyvm.community.domain.policy.permission.AdminPrivilege.MANAGE_TELEPORT_POINTS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        playerExecutor.closeContainer()

        val region = community.getRegion()
        if (region != null) {
            PlayerInteractionApi.resetTeleportPoint(playerExecutor, region, scope)

            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.teleport_point_reset",
                scope.scopeName,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.network.chat.Component.literal("Teleport point for scope ${scope.scopeName} was reset in $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyOfficials(community, playerExecutor.level().server, notification, playerExecutor)

            com.imyvm.community.infra.CommunityDatabase.save()
        } else {
            playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
        }
    }
}

fun runTeleportToPoint(playerExecutor: ServerPlayer, community: Community, scope: GeoScope) {
    playerExecutor.closeContainer()
    com.imyvm.community.application.interaction.screen.inner_community.startCommunityTeleportExecution(
        player = playerExecutor,
        community = community,
        scope = scope
    )
}

private fun calculateTeleportPointSettingCost(region: Region, scope: GeoScope): Pair<Long, String> {
    val currentPoint = PlayerInteractionApi.getTeleportPoint(scope)
    if (currentPoint != null) {
        return TerritoryPricing.applyGeoscopePriceMultiplier(
            PricingConfig.TELEPORT_POINT_MODIFY_COST.value,
            TerritoryPricing.getScopeDimensionId(scope)
        ).totalCost to "community.teleport_point.cost_reason.modify_existing"
    }

    val activeCount = region.geometryScope.count { PlayerInteractionApi.getTeleportPoint(it) != null }
    if (activeCount <= 0) {
        return 0L to "community.teleport_point.cost_reason.first_free"
    }

    val exponent = (activeCount - 1).coerceAtMost(20)
    val multiplier = 1L shl exponent
    return TerritoryPricing.applyGeoscopePriceMultiplier(
        PricingConfig.TELEPORT_POINT_SECOND_POINT_BASE_COST.value * multiplier,
        TerritoryPricing.getScopeDimensionId(scope)
    ).totalCost to "community.teleport_point.cost_reason.new_additional"
}

private fun sendInteractiveTeleportPointConfirmation(player: ServerPlayer, regionNumberId: Int, scopeName: String) {
    val confirmText = Translator.tr("community.teleport_point.confirmation.confirm_button") ?: Component.literal("§a§l[CONFIRM]§r")
    val cancelText = Translator.tr("community.teleport_point.confirmation.cancel_button") ?: Component.literal("§c§l[CANCEL]§r")
    val quotedScopeName = if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName

    val confirmButton = confirmText.copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand( "/_commun confirm_teleport_point_set $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText( Translator.tr("community.teleport_point.confirmation.confirm_hover")))
    }

    val cancelButton = cancelText.copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand( "/_commun cancel_teleport_point_set $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText( Translator.tr("community.teleport_point.confirmation.cancel_hover")))
    }

    val prompt = (Translator.tr("community.teleport_point.confirmation.prompt") ?: Component.literal("§e§l[ACTION REQUIRED]§r"))
        .copy()
        .append(Component.literal(" "))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)
    player.sendSystemMessage(prompt)
}

private fun notifyFormalMembers(
    community: Community,
    server: net.minecraft.server.MinecraftServer,
    message: Component
) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerList.getPlayer(memberUUID)?.sendSystemMessage(message)
        memberAccount.mail.add(message)
    }
}
