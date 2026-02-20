package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.helper.calculateModificationCost
import com.imyvm.community.application.interaction.common.helper.generateModificationConfirmationMessage
import com.imyvm.community.application.interaction.common.helper.generateScopeAdditionConfirmationMessage
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.canUseCommunityTeleport
import com.imyvm.community.application.interaction.screen.inner_community.runTeleportCommunity
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.ScopeModificationConfirmationData
import com.imyvm.community.domain.policy.permission.AdministrationPermission
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionGlobalGeometryMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityScopeCreationRenameMenuAnvil
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
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
    if (geographicFunctionType == GeographicFunctionType.GEOMETRY_MODIFICATION) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityRegionGlobalGeometryMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community
            ) { runBackRegionScopeMenu(playerExecutor, community, geographicFunctionType, runBackGrandfatherMenu) }
        }
    } else if (geographicFunctionType == GeographicFunctionType.SETTING_ADJUSTMENT) {
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

fun runOpenScopeCreationMenu(
    player: ServerPlayerEntity,
    community: Community,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(
            syncId = syncId,
            community = community,
            playerExecutor = player,
            runBack = runBack
        )
    }
}

fun runRenameNewScope(
    player: ServerPlayerEntity,
    community: Community,
    currentName: String,
    currentShape: GeoShapeType,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    CommunityScopeCreationRenameMenuAnvil(player, community, currentName, currentShape, runBackGrandfatherMenu).open()
}

fun runSwitchScopeShape(
    player: ServerPlayerEntity,
    community: Community,
    scopeName: String,
    shapeType: GeoShapeType,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val newType = when (shapeType) {
        GeoShapeType.CIRCLE -> GeoShapeType.RECTANGLE
        GeoShapeType.RECTANGLE -> GeoShapeType.POLYGON
        GeoShapeType.POLYGON -> GeoShapeType.CIRCLE
        GeoShapeType.UNKNOWN -> GeoShapeType.RECTANGLE
    }

    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, scopeName, newType, player, runBack)
    }
}

fun runConfirmScopeCreation(
    player: ServerPlayerEntity,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
) {
    player.closeHandledScreen()
    onCreateScopeRequest(player, community, scopeName, geoShapeType)
}

fun runUnimplementedGeometryGlobalAction(player: ServerPlayerEntity) {
    player.closeHandledScreen()
    player.sendMessage(Translator.tr("ui.community.administration.region.global.unimplemented"))
}

private fun onCreateScopeRequest(
    player: ServerPlayerEntity,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
): Int {
    val regionId = community.regionNumberId ?: return 0
    val existingPending = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionId]
    if (existingPending != null && existingPending.type == PendingOperationType.MODIFY_SCOPE_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.modification.confirmation.pending"))
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendMessage(Translator.tr("community.modification.error.no_region"))
        return 0
    }

    val areaEstimation = PlayerInteractionApi.estimateRegionArea(player, geoShapeType.toString())
    val newScopeArea = when (areaEstimation) {
        is AreaEstimationResult.Error -> {
            val errorKey = when (areaEstimation.error) {
                is CreationError.InsufficientPoints -> "community.modification.error.insufficient_points"
                is CreationError.DuplicatedPoints -> "community.modification.error.duplicated_points"
                is CreationError.CoincidentPoints -> "community.modification.error.coincident_points"
                is CreationError.IntersectionBetweenScopes -> "community.modification.error.overlap_detected"
                else -> "community.modification.error.unknown"
            }
            player.sendMessage(Translator.tr(errorKey) ?: Text.literal("Scope creation error"))
            return 0
        }
        is AreaEstimationResult.Success -> areaEstimation.area
    }

    val currentTotalArea = communityRegion.calculateTotalArea()
    val isManor = community.isManor()
    val variableCost = calculateModificationCost(newScopeArea, currentTotalArea, isManor).cost
    val fixedCost = if (isManor) {
        CommunityConfig.SCOPE_ADDITION_BASE_COST_MANOR.value
    } else {
        CommunityConfig.SCOPE_ADDITION_BASE_COST_REALM.value
    }
    val totalCost = fixedCost + variableCost
    val currentAssets = community.getTotalAssets()

    val confirmationMessages = generateScopeAdditionConfirmationMessage(
        scopeName = scopeName,
        shapeType = geoShapeType,
        area = newScopeArea,
        fixedCost = fixedCost,
        areaCost = variableCost,
        totalCost = totalCost,
        isManor = isManor,
        currentAssets = currentAssets
    )
    confirmationMessages.forEach { msg -> player.sendMessage(msg) }

    addPendingOperation(
        regionId = regionId,
        type = PendingOperationType.MODIFY_SCOPE_CONFIRMATION,
        expireMinutes = 5,
        modificationData = ScopeModificationConfirmationData(
            regionNumberId = regionId,
            scopeName = scopeName,
            executorUUID = player.uuid,
            cost = totalCost,
            isScopeCreation = true,
            shapeName = geoShapeType.toString()
        )
    )

    sendInteractiveScopeModificationConfirmation(player, regionId, scopeName)
    return 1
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
            val permission = AdministrationPermission.MODIFY_REGION_GEOMETRY
            com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
                playerExecutor,
                { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
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
                            is CreationError.InsufficientPoints -> "community.modification.error.insufficient_points"
                            is CreationError.DuplicatedPoints -> "community.modification.error.duplicated_points"
                            is CreationError.CoincidentPoints -> "community.modification.error.coincident_points"
                            is CreationError.IntersectionBetweenScopes -> "community.modification.error.overlap_detected"
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
            if (communityRegion == null) {
                playerExecutor.closeHandledScreen()
                playerExecutor.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_region"))
                return
            }
            if (!canUseCommunityTeleport(playerExecutor, community, scope)) {
                playerExecutor.closeHandledScreen()
                playerExecutor.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
                return
            }
            PlayerInteractionApi.teleportPlayerToScope(playerExecutor, communityRegion, scope)
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
