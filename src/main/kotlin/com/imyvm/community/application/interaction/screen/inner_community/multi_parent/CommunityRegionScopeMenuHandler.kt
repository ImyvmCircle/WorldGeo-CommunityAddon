package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.helper.calculateModificationCost
import com.imyvm.community.application.interaction.common.helper.generateModificationConfirmationMessage
import com.imyvm.community.application.interaction.common.helper.generateScopeAdditionConfirmationMessage
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.canUseCommunityTeleport
import com.imyvm.community.application.interaction.screen.inner_community.runTeleportCommunity
import com.imyvm.community.application.interaction.screen.inner_community.startCommunityTeleportExecution
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.ScopeModificationConfirmationData
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.territory.SettingItemCostChange
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionGlobalGeometryMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationRenameMenuAnvil
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
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
    } else if (geographicFunctionType == GeographicFunctionType.TELEPORT_POINT_LOCATING) {
        val region = community.getRegion()
        val mainScope = region?.geometryScope?.firstOrNull()
        if (mainScope == null) {
            playerExecutor.closeHandledScreen()
            playerExecutor.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"))
            return
        }
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            AdministrationTeleportPointMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                scope = mainScope
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
    player.sendMessage(Translator.tr("ui.admin.region.global.unimplemented"))
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
    val landCostChange = calculateModificationCost(newScopeArea, currentTotalArea, isManor).cost
    val fixedCost = if (isManor) {
        PricingConfig.SCOPE_ADDITION_BASE_COST_MANOR.value
    } else {
        PricingConfig.SCOPE_ADDITION_BASE_COST_REALM.value
    }
    val settingChanges = calculateRegionSettingsCostChanges(communityRegion, newScopeArea, isManor)
    val totalCost = fixedCost + landCostChange + settingChanges.sumOf { it.costChange }
    val currentAssets = community.getTotalAssets()

    val confirmationMessages = generateScopeAdditionConfirmationMessage(
        scopeName = scopeName,
        shapeType = geoShapeType,
        area = newScopeArea,
        fixedCost = fixedCost,
        landCostChange = landCostChange,
        settingChanges = settingChanges,
        isManor = isManor,
        currentAssets = currentAssets,
        currentTotalArea = currentTotalArea
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
            val permission = AdminPrivilege.MODIFY_REGION_GEOMETRY
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
                        val regionSettingChanges = calculateRegionSettingsCostChanges(communityRegion, areaChange, isManor)
                        val scopeSettingChanges = calculateScopeSettingsCostChanges(communityRegion, scope, areaChange, isManor)
                        val allSettingChanges = regionSettingChanges + scopeSettingChanges
                        val totalCost = costResult.cost + allSettingChanges.sumOf { it.costChange }
                        val currentAssets = community.getTotalAssets()

                        if (totalCost > 0 && currentAssets < totalCost) {
                            playerExecutor.sendMessage(Translator.tr("community.modification.error.insufficient_assets",
                                String.format("%.2f", totalCost / 100.0),
                                String.format("%.2f", currentAssets / 100.0)
                            ) ?: Text.literal("Insufficient assets: need ${totalCost / 100.0}, have ${currentAssets / 100.0}"))
                            playerExecutor.closeHandledScreen()
                            return@executeWithPermission
                        }

                        playerExecutor.closeHandledScreen()

                        val confirmationMessages = generateModificationConfirmationMessage(
                            scopeName = scope.scopeName,
                            costResult = costResult,
                            isManor = isManor,
                            currentAssets = currentAssets,
                            settingChanges = allSettingChanges
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
                                cost = totalCost
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
            if (!canUseCommunityTeleport(playerExecutor, community, scope)) {
                playerExecutor.closeHandledScreen()
                playerExecutor.sendMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
                return
            }
            playerExecutor.closeHandledScreen()
            startCommunityTeleportExecution(playerExecutor, community, scope)
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

private fun calculateRegionSettingsCostChanges(
    communityRegion: Region,
    areaChange: Double,
    isManor: Boolean
): List<SettingItemCostChange> {
    val currentTotalArea = communityRegion.calculateTotalArea()
    val newTotalArea = currentTotalArea + areaChange
    val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
    val refundRate = PricingConfig.AREA_REFUND_RATE.value

    return communityRegion.settings.mapNotNull { setting ->
        val key = setting.key
        val isDefault = when (key) {
            is PermissionKey -> {
                val default = RegionDataApi.getPermissionValueRegion(null, null, null, key)
                setting.value == default
            }
            is RuleKey -> {
                val default = RegionDataApi.getRuleValueForRegion(null, null, key)
                setting.value == default
            }
            else -> true
        }
        if (isDefault) return@mapNotNull null

        val isPlayerTarget = setting is PermissionSetting && setting.playerUUID != null
        val coefficientPerUnit = when (key) {
            is PermissionKey -> TerritoryPricing.getPermissionCoefficientPerUnit(key)
            is RuleKey -> TerritoryPricing.getRuleCoefficientPerUnit(key)
            else -> return@mapNotNull null
        }
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()

        val costChange = TerritoryPricing.calculateSettingCostChange(
            areaOld = currentTotalArea,
            areaNew = newTotalArea,
            coefficientPerUnit = coefficientPerUnit,
            unitSize = unitSize,
            isPlayerTarget = isPlayerTarget,
            freeArea = freeArea,
            refundRate = refundRate
        )
        SettingItemCostChange(
            settingKeyName = key.toString(),
            scopeName = null,
            playerName = null,
            areaOld = currentTotalArea,
            areaNew = newTotalArea,
            costChange = costChange
        )
    }
}

private fun calculateScopeSettingsCostChanges(
    communityRegion: Region,
    scope: GeoScope,
    areaChange: Double,
    isManor: Boolean
): List<SettingItemCostChange> {
    val freeArea = if (isManor) PricingConfig.MANOR_FREE_AREA.value else PricingConfig.REALM_FREE_AREA.value
    val refundRate = PricingConfig.AREA_REFUND_RATE.value

    return scope.settings.mapNotNull { setting ->
        val key = setting.key
        val isDefault = when (key) {
            is PermissionKey -> {
                val default = RegionDataApi.getPermissionValueRegion(null, null, null, key)
                setting.value == default
            }
            is RuleKey -> {
                val default = RegionDataApi.getRuleValueForRegion(null, null, key)
                setting.value == default
            }
            else -> true
        }
        if (isDefault) return@mapNotNull null

        val isPlayerTarget = setting is PermissionSetting && setting.playerUUID != null
        val coefficientPerUnit = when (key) {
            is PermissionKey -> TerritoryPricing.getPermissionCoefficientPerUnit(key)
            is RuleKey -> TerritoryPricing.getRuleCoefficientPerUnit(key)
            else -> return@mapNotNull null
        }
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value.toDouble()

        val currentScopeArea = RegionDataApi.getScopeArea(scope) ?: 0.0
        val newScopeArea = currentScopeArea + areaChange

        val costChange = TerritoryPricing.calculateSettingCostChange(
            areaOld = currentScopeArea,
            areaNew = newScopeArea,
            coefficientPerUnit = coefficientPerUnit,
            unitSize = unitSize,
            isPlayerTarget = isPlayerTarget,
            freeArea = freeArea,
            refundRate = refundRate
        )
        SettingItemCostChange(
            settingKeyName = key.toString(),
            scopeName = scope.scopeName,
            playerName = null,
            areaOld = currentScopeArea,
            areaNew = newScopeArea,
            costChange = costChange
        )
    }
}
