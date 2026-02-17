package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.application.interaction.common.helper.calculateModificationCost
import com.imyvm.community.application.interaction.common.helper.generateModificationConfirmationMessage
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.runTeleportCommunity
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.ScopeModificationConfirmationData
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

fun runExecuteRegion(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    if (geographicFunctionType == GeographicFunctionType.SETTING_ADJUSTMENT) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            TargetSettingMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                playerObject = playerObject
            ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
        }
    } else if (geographicFunctionType == GeographicFunctionType.TELEPORT_POINT_EXECUTION) {
        runTeleportCommunity(playerExecutor, community)
    }
}

fun runExecuteScope(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    when (geographicFunctionType){
        GeographicFunctionType.GEOMETRY_MODIFICATION -> {
            val permission = com.imyvm.community.domain.policy.permission.AdministrationPermission.MODIFY_REGION_GEOMETRY
            com.imyvm.community.application.permission.PermissionCheck.executeWithPermission(
                playerExecutor,
                { com.imyvm.community.application.permission.PermissionCheck.canExecuteAdministration(playerExecutor, community, permission) }
            ) {
                val communityRegion = community.getRegion()
                if (communityRegion == null) {
                    playerExecutor.sendMessage(Translator.tr("community.modification.error.no_region"))
                    playerExecutor.closeHandledScreen()
                    return@executeWithPermission
                }
                
                val areaEstimation = PlayerInteractionApi.estimateScopeAreaChange(playerExecutor, communityRegion, scope.scopeName)
                
                when (areaEstimation) {
                    is AreaEstimationResult.Error -> {
                        val errorKey = when (areaEstimation.error) {
                            is com.imyvm.iwg.domain.CreationError.InsufficientPoints -> "community.modification.error.insufficient_points"
                            is com.imyvm.iwg.domain.CreationError.DuplicatedPoints -> "community.modification.error.duplicated_points"
                            is com.imyvm.iwg.domain.CreationError.CoincidentPoints -> "community.modification.error.coincident_points"
                            is com.imyvm.iwg.domain.CreationError.IntersectionBetweenScopes -> "community.modification.error.overlap_detected"
                            else -> "community.modification.error.unknown"
                        }
                        val errorMessage = Translator.tr(errorKey) ?: Text.literal("Modification error")
                        playerExecutor.sendMessage(errorMessage)
                        playerExecutor.closeHandledScreen()
                        return@executeWithPermission
                    }
                    is AreaEstimationResult.Success -> {
                        val areaChange = areaEstimation.area
                        val currentTotalArea = communityRegion.calculateTotalArea()
                        val isManor = community.isManor()
                        
                        val costResult = calculateModificationCost(areaChange, currentTotalArea, isManor)
                        val currentAssets = community.getTotalAssets()
                        
                        if (costResult.cost > 0 && currentAssets < costResult.cost) {
                            playerExecutor.sendMessage(Translator.tr("community.modification.error.insufficient_assets",
                                String.format("%.2f", costResult.cost / 100.0),
                                String.format("%.2f", currentAssets / 100.0)
                            ) ?: Text.literal("Insufficient assets: need ${costResult.cost / 100.0}, have ${currentAssets / 100.0}"))
                            playerExecutor.closeHandledScreen()
                            return@executeWithPermission
                        }
                        
                        playerExecutor.closeHandledScreen()
                        
                        val confirmationMessages = generateModificationConfirmationMessage(
                            scopeName = scope.scopeName,
                            costResult = costResult,
                            isManor = isManor,
                            currentAssets = currentAssets
                        )
                        
                        confirmationMessages.forEach { msg ->
                            playerExecutor.sendMessage(msg)
                        }
                        
                        addPendingOperation(
                            regionId = community.regionNumberId!!,
                            type = PendingOperationType.MODIFY_SCOPE_CONFIRMATION,
                            expireMinutes = 5,
                            modificationData = ScopeModificationConfirmationData(
                                regionNumberId = community.regionNumberId!!,
                                scopeName = scope.scopeName,
                                executorUUID = playerExecutor.uuid,
                                cost = costResult.cost
                            )
                        )
                        
                        sendInteractiveScopeModificationConfirmation(playerExecutor, community.regionNumberId!!, scope.scopeName)
                    }
                }
            }
        }
        GeographicFunctionType.SETTING_ADJUSTMENT -> {
            CommunityMenuOpener.open(playerExecutor) { syncId ->
                TargetSettingMenu(
                    syncId = syncId,
                    playerExecutor = playerExecutor,
                    community = community,
                    scope = scope,
                    playerObject = playerObject
                ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
            }
        }
        GeographicFunctionType.TELEPORT_POINT_LOCATING -> {
            CommunityMenuOpener.open(playerExecutor) { syncId ->
                AdministrationTeleportPointMenu(
                    syncId = syncId,
                    playerExecutor = playerExecutor,
                    community = community,
                    scope = scope
                ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
            }
        }
        GeographicFunctionType.TELEPORT_POINT_EXECUTION -> {
            val communityRegion = community.getRegion()
            communityRegion?.let { PlayerInteractionApi.teleportPlayerToScope(playerExecutor, it, scope) }
            playerExecutor.closeHandledScreen()
        }
    }
}

private fun runBackRegionScopeMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = playerExecutor,
            community = community,
            geographicFunctionType = geographicFunctionType,
            runBack = runBack
        )
    }
}

private fun sendInteractiveScopeModificationConfirmation(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String) {
    val confirmButton = Text.literal("§a§l[CONFIRM]§r")
        .styled { style ->
            style.withClickEvent(net.minecraft.text.ClickEvent(
                net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                "/community confirm_modification $regionNumberId $scopeName"
            ))
            .withHoverEvent(net.minecraft.text.HoverEvent(
                net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                Text.literal("§aClick to confirm modification")
            ))
        }
    
    val cancelButton = Text.literal("§c§l[CANCEL]§r")
        .styled { style ->
            style.withClickEvent(net.minecraft.text.ClickEvent(
                net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                "/community cancel_modification $regionNumberId $scopeName"
            ))
            .withHoverEvent(net.minecraft.text.HoverEvent(
                net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                Text.literal("§cClick to cancel modification")
            ))
        }
    
    val promptMessage = Text.empty()
        .append(Text.literal("§e§l[ACTION REQUIRED]§r §ePlease confirm within §c§l5 minutes§r§e: "))
        .append(confirmButton)
        .append(Text.literal(" "))
        .append(cancelButton)
    
    player.sendMessage(promptMessage)
}
