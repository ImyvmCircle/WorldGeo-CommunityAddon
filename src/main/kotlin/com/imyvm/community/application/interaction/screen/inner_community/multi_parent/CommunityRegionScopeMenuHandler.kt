package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.helper.calculateModificationCost
import com.imyvm.community.application.interaction.common.helper.generateModificationConfirmationMessage
import com.imyvm.community.application.interaction.common.helper.generateScopeAdditionConfirmationMessage
import com.imyvm.community.application.interaction.common.helper.generateScopeDeletionConfirmationMessage
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.canUseCommunityTeleport
import com.imyvm.community.application.interaction.screen.inner_community.runTeleportCommunity
import com.imyvm.community.application.interaction.screen.inner_community.startCommunityTeleportExecution
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.GeographicFunctionType
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.RenameConfirmationData
import com.imyvm.community.domain.model.ScopeModificationConfirmationData
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.policy.territory.SettingItemCostChange
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationRenameMenuAnvil
import com.imyvm.community.entrypoint.screen.inner_community.administration_only.AdministrationTeleportPointMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionGlobalGeometryMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.util.SelectionReturnContext
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getColoredDimensionName
import com.imyvm.community.util.getPlayerDimensionId
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.AreaEstimationResult
import com.imyvm.iwg.domain.CreationError
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

fun runExecuteRegion(
    playerExecutor: ServerPlayer,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayer) -> Unit
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
            playerExecutor.closeContainer()
            playerExecutor.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.no_scope"))
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
    } else if (geographicFunctionType == GeographicFunctionType.NAME_MODIFICATION) {
        CommunityPermissionPolicy.executeWithPermission(
            playerExecutor,
            { CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, AdminPrivilege.RENAME_COMMUNITY) }
        ) {
            val nameKey = "global"
            val cooldownMs = community.nameChangeCooldowns[nameKey] ?: 0L
            val daysSince = (System.currentTimeMillis() - cooldownMs) / (1000L * 60 * 60 * 24)
            if (daysSince < 30) {
                playerExecutor.closeContainer()
                playerExecutor.sendSystemMessage(Translator.tr("community.rename.error.cooldown", (30 - daysSince).toString(), nameKey))
                community.regionNumberId?.let { regionId ->
                    playerExecutor.sendSystemMessage(
                        Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                            style.withClickEvent(ClickEvent.RunCommand( "/community open_rename_menu $regionId"))
                        }
                    )
                }
            } else {
                AdministrationRenameMenuAnvil(
                    player = playerExecutor,
                    community = community,
                    scopeName = null,
                    runBackGrandfather = { p -> runBackRegionScopeMenu(p, community, geographicFunctionType, runBackGrandfatherMenu) }
                ).open()
            }
        }
    }
}

fun runOpenScopeCreationMenu(
    player: ServerPlayer,
    community: Community,
    runBack: (ServerPlayer) -> Unit
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

fun runConfirmScopeCreation(
    player: ServerPlayer,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
) {
    player.closeContainer()
    onCreateScopeRequest(player, community, scopeName, geoShapeType)
}

fun runUnimplementedGeometryGlobalAction(player: ServerPlayer) {
    player.closeContainer()
    player.sendSystemMessage(Translator.tr("ui.admin.region.global.unimplemented"))
}

private fun onCreateScopeRequest(
    player: ServerPlayer,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
): Int {
    val regionId = community.regionNumberId ?: return 0
    val existingPending = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionId]
    if (existingPending != null && existingPending.type == PendingOperationType.MODIFY_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
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
            player.sendSystemMessage(Translator.tr(errorKey) ?: Component.literal("Scope creation error"))
            return 0
        }
        is AreaEstimationResult.Success -> areaEstimation.area
    }

    val isManor = community.isManor()
    val currentAreaByDimension = TerritoryPricing.getRegionAreaByDimension(communityRegion)
    val newScopeDimensionId = TerritoryPricing.normalizeDimensionId(player.level().dimension().toString())
    val areaAfterByDimension = TerritoryPricing.applyAreaChange(currentAreaByDimension, newScopeDimensionId, newScopeArea)
    val landCostResult = calculateModificationCost(currentAreaByDimension, areaAfterByDimension, isManor)
    val landCostChange = landCostResult.cost
    val fixedCostBase = if (isManor) {
        PricingConfig.SCOPE_ADDITION_BASE_COST_MANOR.value
    } else {
        PricingConfig.SCOPE_ADDITION_BASE_COST_REALM.value
    }
    val fixedCostResult = TerritoryPricing.applyGeoscopePriceMultiplier(fixedCostBase, newScopeDimensionId)

    val settingChanges = calculateRegionSettingsCostChanges(communityRegion, areaAfterByDimension, isManor)
    val settingTotal = settingChanges.sumOf { it.costChange }
    val currentTotalArea = communityRegion.calculateTotalArea()

    val formalMemberCount = community.member.count {
        it.value.basicRoleType != MemberRoleType.APPLICANT && it.value.basicRoleType != MemberRoleType.REFUSED
    }
    // Ceiling division: ceil(members / 2)
    val maxScopesAllowed = (formalMemberCount + 1) / 2
    val existingScopeCount = communityRegion.geometryScope.size
    val excessCount = maxOf(0, existingScopeCount + 1 - maxScopesAllowed)
    val multiplier = PricingConfig.SCOPE_ADDITION_SOFT_LIMIT_MULTIPLIER.value

    // Surcharge applies to the entire creation cost (base + land + settings)
    val rawTotal = fixedCostResult.totalCost + landCostChange + settingTotal
    val adjustedTotal = if (excessCount > 0) {
        (rawTotal * Math.pow(multiplier, excessCount.toDouble())).toLong()
    } else rawTotal
    val surcharge = adjustedTotal - rawTotal

    val currentAssets = community.getTotalAssets()

    if (excessCount > 0) {
        player.sendSystemMessage(Translator.tr(
            "community.scope_add.warning.soft_limit_surcharge",
            excessCount.toString(),
            maxScopesAllowed.toString(),
            formalMemberCount.toString(),
            String.format("%.2f", adjustedTotal / 100.0),
            String.format("%.2f", rawTotal / 100.0)
        ))
    }

    val confirmationMessages = generateScopeAdditionConfirmationMessage(
        regionName = communityRegion.name,
        scopeName = scopeName,
        shapeType = geoShapeType,
        area = newScopeArea,
        fixedCostBase = fixedCostResult.totalCost,
        landCostResult = landCostResult,
        settingChanges = settingChanges,
        isManor = isManor,
        currentAssets = currentAssets,
        currentTotalArea = currentTotalArea,
        scopeDimensionId = newScopeDimensionId,
        rawTotal = rawTotal,
        adjustedTotal = adjustedTotal,
        excessCount = excessCount,
        maxScopesAllowed = maxScopesAllowed,
        formalMemberCount = formalMemberCount,
        multiplier = multiplier
    )
    confirmationMessages.forEach { msg -> player.sendSystemMessage(msg) }

    addPendingOperation(
        regionId = regionId,
        type = PendingOperationType.MODIFY_SCOPE_CONFIRMATION,
        expireMinutes = 5,
        modificationData = ScopeModificationConfirmationData(
            regionNumberId = regionId,
            scopeName = scopeName,
            executorUUID = player.uuid,
            cost = adjustedTotal,
            isScopeCreation = true,
            shapeName = geoShapeType.toString(),
            softLimitSurcharge = surcharge
        )
    )

    SelectionReturnContext.clearContext(player.uuid)
    sendInteractiveScopeModificationConfirmation(player, regionId, scopeName)
    return 1
}

fun onConfirmRename(player: ServerPlayer, regionNumberId: Int, nameKey: String): Int {
    val pendingOp = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.RENAME_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_found"))
        return 0
    }
    val renameData = pendingOp.renameData ?: return 0
    if (renameData.executorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return 0
    }
    if (renameData.nameKey != nameKey) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_found"))
        return 0
    }
    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.expired"))
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    if (community == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.community_not_found"))
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val currentAssets = community.getTotalAssets()
    if (renameData.cost > 0 && currentAssets < renameData.cost) {
        player.sendSystemMessage(Translator.tr(
            "community.modification.error.insufficient_assets",
            String.format("%.2f", renameData.cost / 100.0),
            String.format("%.2f", currentAssets / 100.0)
        ))
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (renameData.nameKey == "global") {
        val oldName = communityRegion.name
        val renameResult = PlayerInteractionApi.renameRegion(player, communityRegion, renameData.newName)
        if (renameResult == 0) {
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            return 0
        }
        community.nameChangeCooldowns["global"] = System.currentTimeMillis()
        if (renameData.cost > 0) {
            community.expenditures.add(com.imyvm.community.domain.model.Turnover(
                amount = renameData.cost,
                timestamp = System.currentTimeMillis(),
                source = TurnoverSource.SYSTEM,
                descriptionKey = "community.treasury.desc.rename_community",
                descriptionArgs = listOf(renameData.newName)
            ))
        }
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        CommunityDatabase.save()
        player.sendSystemMessage(Translator.tr("community.rename.success.global", oldName, renameData.newName))
    } else {
        val scope = communityRegion.geometryScope.firstOrNull { it.scopeName == renameData.nameKey }
        if (scope == null) {
            player.sendSystemMessage(Translator.tr("community.rename.error.scope_not_found", renameData.nameKey))
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            return 0
        }
        val oldScopeName = scope.scopeName
        val renameResult = PlayerInteractionApi.renameScope(player, communityRegion, oldScopeName, renameData.newName)
        if (renameResult == 0) {
            com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            return 0
        }
        community.nameChangeCooldowns.remove(renameData.nameKey)
        community.nameChangeCooldowns[renameData.newName] = System.currentTimeMillis()
        if (renameData.cost > 0) {
            community.expenditures.add(com.imyvm.community.domain.model.Turnover(
                amount = renameData.cost,
                timestamp = System.currentTimeMillis(),
                source = TurnoverSource.SYSTEM,
                descriptionKey = "community.treasury.desc.rename_scope",
                descriptionArgs = listOf(renameData.nameKey, renameData.newName)
            ))
        }
        com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        CommunityDatabase.save()
        player.sendSystemMessage(Translator.tr("community.rename.success.scope", oldScopeName, renameData.newName))
    }
    return 1
}

fun onCancelRename(player: ServerPlayer, regionNumberId: Int, nameKey: String): Int {
    val pendingOp = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.RENAME_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_found"))
        return 0
    }
    val renameData = pendingOp.renameData ?: return 0
    if (renameData.executorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return 0
    }
    if (renameData.nameKey != nameKey) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_found"))
        return 0
    }
    com.imyvm.community.WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    player.sendSystemMessage(Translator.tr("community.rename.cancelled"))
    return 1
}

fun runExecuteScope(
    playerExecutor: ServerPlayer,
    community: Community,
    scope: GeoScope,
    geographicFunctionType: GeographicFunctionType,
    playerObject: GameProfile? = null,
    runBackGrandfatherMenu: (ServerPlayer) -> Unit
) {
    when (geographicFunctionType){
        GeographicFunctionType.GEOMETRY_MODIFICATION -> {
            com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
                playerExecutor,
                { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, AdminPrivilege.MODIFY_REGION_GEOMETRY) }
            ) {
                val hypotheticalShape = ImyvmWorldGeo.pointSelectingPlayers[playerExecutor.uuid]?.hypotheticalShape
                    if (hypotheticalShape is HypotheticalShape.ModifyExisting && hypotheticalShape.scope.scopeName == scope.scopeName) {
                        if (!isPlayerInScopeDimension(playerExecutor, scope)) {
                            sendCrossDimensionModifyError(
                                player = playerExecutor,
                                community = community,
                                scope = scope,
                                messageKey = "ui.territory.modify.dimension_mismatch.confirm",
                                returnCommand = getModifyMenuReturnCommand(community)
                            )
                            return@executeWithPermission
                        }
                    runOpenScopeModificationConfirmation(playerExecutor, community, scope) { p ->
                        runBackRegionScopeMenu(p, community, geographicFunctionType, runBackGrandfatherMenu)
                    }
                } else {
                    if (!isPlayerInScopeDimension(playerExecutor, scope)) {
                        sendCrossDimensionModifyError(
                            player = playerExecutor,
                            community = community,
                            scope = scope,
                            messageKey = "ui.territory.modify.dimension_mismatch.start",
                            returnCommand = getModifyMenuReturnCommand(community)
                        )
                        return@executeWithPermission
                    }
                    if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(playerExecutor.uuid)) {
                        PlayerInteractionApi.stopSelection(playerExecutor)
                        SelectionReturnContext.clearContext(playerExecutor.uuid)
                    }
                    PlayerInteractionApi.startSelectionForModify(playerExecutor, scope)
                    community.regionNumberId?.let { id ->
                        SelectionReturnContext.setModifyContext(playerExecutor.uuid, id, scope.scopeName)
                    }
                    playerExecutor.closeContainer()
                    playerExecutor.sendSystemMessage(
                        Translator.tr(
                            "ui.territory.modify.scope_started",
                            scope.scopeName,
                            getColoredDimensionName(TerritoryPricing.getScopeDimensionId(scope))
                        )
                    )
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
                playerExecutor.closeContainer()
                playerExecutor.sendSystemMessage(Translator.tr("ui.community.button.interaction.teleport.execution.error.not_public"))
                return
            }
            playerExecutor.closeContainer()
            startCommunityTeleportExecution(playerExecutor, community, scope)
        }
        GeographicFunctionType.NAME_MODIFICATION -> {
            CommunityPermissionPolicy.executeWithPermission(
                playerExecutor,
                { CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, AdminPrivilege.RENAME_COMMUNITY) }
            ) {
                val nameKey = scope.scopeName
                val cooldownMs = community.nameChangeCooldowns[nameKey] ?: 0L
                val daysSince = (System.currentTimeMillis() - cooldownMs) / (1000L * 60 * 60 * 24)
                if (daysSince < 30) {
                    playerExecutor.closeContainer()
                    playerExecutor.sendSystemMessage(Translator.tr("community.rename.error.cooldown", (30 - daysSince).toString(), nameKey))
                    community.regionNumberId?.let { regionId ->
                        playerExecutor.sendSystemMessage(
                            Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                                style.withClickEvent(ClickEvent.RunCommand( "/community open_rename_menu $regionId"))
                            }
                        )
                    }
                } else {
                    AdministrationRenameMenuAnvil(
                        player = playerExecutor,
                        community = community,
                        scopeName = scope.scopeName,
                        runBackGrandfather = { p -> runBackRegionScopeMenu(p, community, geographicFunctionType, runBackGrandfatherMenu) }
                    ).open()
                }
            }
        }
        GeographicFunctionType.SCOPE_DELETION -> {
            CommunityPermissionPolicy.executeWithPermission(
                playerExecutor,
                {
                    val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
                    if (adminCheck.isDenied()) return@executeWithPermission adminCheck
                    CommunityPermissionPolicy.canExecuteOperationInProto(playerExecutor, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
                }
            ) {
                executeScopeDeletion(playerExecutor, community, scope)
            }
        }
        GeographicFunctionType.SCOPE_TRANSFER -> {
            CommunityPermissionPolicy.executeWithPermission(
                playerExecutor,
                {
                    val adminCheck = CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
                    if (adminCheck.isDenied()) return@executeWithPermission adminCheck
                    CommunityPermissionPolicy.canExecuteOperationInProto(playerExecutor, community, AdminPrivilege.MODIFY_REGION_GEOMETRY)
                }
            ) {
                CommunityMenuOpener.open(playerExecutor) { syncId ->
                    com.imyvm.community.entrypoint.screen.inner_community.multi_parent.ScopeTransferTargetListMenu(
                        syncId = syncId,
                        sourceCommunity = community,
                        scope = scope,
                        runBack = runBackGrandfatherMenu
                    ) { player, targetCommunity ->
                        runTransferScopeToTarget(player, community, scope, targetCommunity, runBackGrandfatherMenu)
                    }
                }
            }
        }
    }
}

fun runOpenScopeModificationConfirmation(
    playerExecutor: ServerPlayer,
    community: Community,
    scope: GeoScope,
    runBackToScopeList: (ServerPlayer) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        com.imyvm.community.entrypoint.screen.inner_community.multi_parent.ScopeGeometryModificationConfirmMenu(
            syncId = syncId,
            playerExecutor = playerExecutor,
            community = community,
            scope = scope,
            runBack = runBackToScopeList
        )
    }
}

fun runToggleScopeMod(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope,
    runBack: (ServerPlayer) -> Unit
) {
    PlayerInteractionApi.stopSelection(player)
    SelectionReturnContext.clearContext(player.uuid)
    player.sendSystemMessage(Translator.tr("community.selection_mode.disabled"))
    runBack(player)
}

private fun isPlayerInScopeDimension(player: ServerPlayer, scope: GeoScope): Boolean {
    return getPlayerDimensionId(player) == TerritoryPricing.getScopeDimensionId(scope)
}

private fun getModifyMenuReturnCommand(community: Community): String {
    return community.regionNumberId?.let { "/community open_modify_menu $it" } ?: "/community"
}

private fun sendCrossDimensionModifyError(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope,
    messageKey: String,
    returnCommand: String
) {
    player.closeContainer()
    val scopeDimensionName = getColoredDimensionName(TerritoryPricing.getScopeDimensionId(scope))
    val playerDimensionName = getColoredDimensionName(getPlayerDimensionId(player))
    player.sendSystemMessage(
        Translator.tr(messageKey, scope.scopeName, scopeDimensionName, playerDimensionName)
            ?: Component.literal("Dimension mismatch while modifying ${scope.scopeName}")
    )

    val returnButton = (Translator.tr("ui.territory.modify.dimension_mismatch.return_button")
        ?: Component.literal("§b§l[RETURN]§r")).copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand(returnCommand))
            .withHoverEvent(
                HoverEvent.ShowText(
                    Translator.tr(
                        "ui.territory.modify.dimension_mismatch.return_hover",
                        community.getRegion()?.name ?: ""
                    ) ?: Component.literal("Return")
                )
            )
    }
    val prompt = (Translator.tr("ui.territory.modify.dimension_mismatch.return_prompt")
        ?: Component.literal("§7Back to interface")).copy()
        .append(Component.literal(" "))
        .append(returnButton)
    player.sendSystemMessage(prompt)
}

private fun runBackRegionScopeMenu(
    playerExecutor: ServerPlayer,
    community: Community,
    geographicFunctionType: GeographicFunctionType,
    runBack: (ServerPlayer) -> Unit
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

private fun sendInteractiveScopeModificationConfirmation(player: ServerPlayer, regionNumberId: Int, scopeName: String) {
    val quotedScopeName = if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName
    val confirmButton = Component.literal("§a§l[CONFIRM]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_modification $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§aClick to confirm modification")
            ))
        }

    val cancelButton = Component.literal("§c§l[CANCEL]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_modification $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§cClick to cancel modification")
            ))
        }

    val promptMessage = Component.empty()
        .append(Component.literal("§e§l[ACTION REQUIRED]§r §ePlease confirm within §c§l5 minutes§r§e: "))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)

    player.sendSystemMessage(promptMessage)
}

private fun calculateRegionSettingsCostChanges(
    communityRegion: Region,
    areaAfterByDimension: Map<String, Double>,
    isManor: Boolean
): List<SettingItemCostChange> {
    val currentAreaByDimension = TerritoryPricing.getRegionAreaByDimension(communityRegion)
    val currentTotalArea = communityRegion.calculateTotalArea()
    val newTotalArea = areaAfterByDimension.values.sum()
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
            areaOldByDimension = currentAreaByDimension,
            areaNewByDimension = areaAfterByDimension,
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
            costChange = costChange,
            areaOldByDimension = currentAreaByDimension,
            areaNewByDimension = areaAfterByDimension
        )
    }
}

private fun calculateScopeSettingsCostChanges(
    scope: GeoScope,
    newScopeArea: Double,
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
        val dimensionId = TerritoryPricing.getScopeDimensionId(scope)

        val costChange = TerritoryPricing.calculateSettingCostChange(
            areaOldByDimension = TerritoryPricing.buildAreaMap(dimensionId, currentScopeArea),
            areaNewByDimension = TerritoryPricing.buildAreaMap(dimensionId, newScopeArea),
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
            costChange = costChange,
            areaOldByDimension = TerritoryPricing.buildAreaMap(dimensionId, currentScopeArea),
            areaNewByDimension = TerritoryPricing.buildAreaMap(dimensionId, newScopeArea)
        )
    }
}

internal fun executeScopeModification(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope
) {
    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        player.closeContainer()
        return
    }
    if (!isPlayerInScopeDimension(player, scope)) {
        sendCrossDimensionModifyError(
            player = player,
            community = community,
            scope = scope,
            messageKey = "ui.territory.modify.dimension_mismatch.execute",
            returnCommand = "/community"
        )
        return
    }

    val areaEstimation = PlayerInteractionApi.estimateScopeAreaChange(player, communityRegion, scope.scopeName)

    when (areaEstimation) {
        is AreaEstimationResult.Error -> {
            val errorKey = when (areaEstimation.error) {
                is CreationError.InsufficientPoints -> "community.modification.error.insufficient_points"
                is CreationError.DuplicatedPoints -> "community.modification.error.duplicated_points"
                is CreationError.CoincidentPoints -> "community.modification.error.coincident_points"
                is CreationError.IntersectionBetweenScopes -> "community.modification.error.overlap_detected"
                else -> "community.modification.error.unknown"
            }
            player.sendSystemMessage(Translator.tr(errorKey) ?: Component.literal("Modification error"))
            community.regionNumberId?.let { regionId ->
                player.sendSystemMessage(
                    Translator.tr("ui.button.return_to_menu").copy().withStyle { style ->
                        style.withClickEvent(ClickEvent.RunCommand( "/community open_modify_menu $regionId"))
                    }
                )
            }
            player.closeContainer()
        }
        is AreaEstimationResult.Success -> {
            val areaChange = areaEstimation.area
            val isManor = community.isManor()
            val currentAreaByDimension = TerritoryPricing.getRegionAreaByDimension(communityRegion)
            val areaAfterByDimension = TerritoryPricing.applyAreaChange(
                currentAreaByDimension,
                TerritoryPricing.getScopeDimensionId(scope),
                areaChange
            )
            val currentScopeArea = RegionDataApi.getScopeArea(scope) ?: 0.0

            val costResult = calculateModificationCost(currentAreaByDimension, areaAfterByDimension, isManor)
            val regionSettingChanges = calculateRegionSettingsCostChanges(communityRegion, areaAfterByDimension, isManor)
            val scopeSettingChanges = calculateScopeSettingsCostChanges(scope, currentScopeArea + areaChange, isManor)
            val allSettingChanges = regionSettingChanges + scopeSettingChanges
            val totalCost = costResult.cost + allSettingChanges.sumOf { it.costChange }
            val currentAssets = community.getTotalAssets()

            if (totalCost > 0 && currentAssets < totalCost) {
                player.sendSystemMessage(Translator.tr("community.modification.error.insufficient_assets",
                    String.format("%.2f", totalCost / 100.0),
                    String.format("%.2f", currentAssets / 100.0)
                ) ?: Component.literal("Insufficient assets"))
                player.closeContainer()
                return
            }

            player.closeContainer()

            SelectionReturnContext.clearContext(player.uuid)
            generateModificationConfirmationMessage(
                regionName = communityRegion.name,
                scopeName = scope.scopeName,
                scopeAreaBefore = currentScopeArea,
                scopeAreaAfter = currentScopeArea + areaChange,
                costResult = costResult,
                isManor = isManor,
                currentAssets = currentAssets,
                settingChanges = allSettingChanges
            ).forEach { player.sendSystemMessage(it) }

            addPendingOperation(
                regionId = community.regionNumberId!!,
                type = PendingOperationType.MODIFY_SCOPE_CONFIRMATION,
                expireMinutes = 5,
                modificationData = ScopeModificationConfirmationData(
                    regionNumberId = community.regionNumberId!!,
                    scopeName = scope.scopeName,
                    executorUUID = player.uuid,
                    cost = totalCost
                )
            )

            sendInteractiveScopeModificationConfirmation(player, community.regionNumberId!!, scope.scopeName)
        }
    }
}

internal fun executeScopeDeletion(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope
) {
    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        player.closeContainer()
        return
    }

    val existingPending = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[community.regionNumberId]
    if (existingPending != null) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
        player.closeContainer()
        return
    }

    if (communityRegion.geometryScope.size <= 1) {
        player.sendSystemMessage(Translator.tr("community.scope_delete.error.last_scope"))
        player.closeContainer()
        return
    }

    val scopeArea = RegionDataApi.getScopeArea(scope) ?: 0.0
    val isManor = community.isManor()
    val currentAreaByDimension = TerritoryPricing.getRegionAreaByDimension(communityRegion)
    val areaAfterByDimension = TerritoryPricing.applyAreaChange(
        currentAreaByDimension,
        TerritoryPricing.getScopeDimensionId(scope),
        -scopeArea
    )

    val costResult = calculateModificationCost(currentAreaByDimension, areaAfterByDimension, isManor)
    val regionSettingChanges = calculateRegionSettingsCostChanges(communityRegion, areaAfterByDimension, isManor)
    val scopeSettingChanges = calculateScopeSettingsCostChanges(scope, 0.0, isManor)
    val allSettingChanges = regionSettingChanges + scopeSettingChanges
    val totalCost = costResult.cost + allSettingChanges.sumOf { it.costChange }
    val currentAssets = community.getTotalAssets()

    player.closeContainer()

    generateScopeDeletionConfirmationMessage(
        regionName = communityRegion.name,
        scopeName = scope.scopeName,
        scopeArea = scopeArea,
        costResult = costResult,
        isManor = isManor,
        currentAssets = currentAssets,
        settingChanges = allSettingChanges
    ).forEach { player.sendSystemMessage(it) }

    addPendingOperation(
        regionId = community.regionNumberId!!,
        type = PendingOperationType.DELETE_SCOPE_CONFIRMATION,
        expireMinutes = 5,
        modificationData = ScopeModificationConfirmationData(
            regionNumberId = community.regionNumberId!!,
            scopeName = scope.scopeName,
            executorUUID = player.uuid,
            cost = totalCost,
            isScopeDeletion = true
        )
    )

    sendInteractiveScopeDeletionConfirmation(player, community.regionNumberId!!, scope.scopeName)
}

private fun sendInteractiveScopeDeletionConfirmation(player: ServerPlayer, regionNumberId: Int, scopeName: String) {
    val quotedScopeName = if (!scopeName.all { it.isLetterOrDigit() && it.code < 128 }) "\"$scopeName\"" else scopeName
    val confirmButton = Component.literal("§a§l[CONFIRM]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_delete_scope $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§aClick to confirm selling scope")
            ))
        }

    val cancelButton = Component.literal("§c§l[CANCEL]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_delete_scope $regionNumberId $quotedScopeName"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§cClick to cancel")
            ))
        }

    val promptMessage = Component.empty()
        .append(Component.literal("§e§l[ACTION REQUIRED]§r §ePlease confirm within §c§l5 minutes§r§e: "))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)

    player.sendSystemMessage(promptMessage)
}
