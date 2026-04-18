package com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.SettingConfirmationData
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.territory.SettingCostResult
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.domain.component.PermissionSetting
import com.imyvm.iwg.domain.component.RuleKey
import com.imyvm.iwg.domain.component.RuleSetting
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.inter.api.UtilApi
import com.mojang.authlib.GameProfile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Component
import java.util.UUID

fun getPermissionButtonItemStack(
    item: Item,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey
): ItemStack {
    val itemStack = ItemStack(item)

    val loreLines = mutableListOf<Component>()
    val hasPermission = community.getRegion()?.let {
        RegionDataApi.getPermissionValueRegion(
            it,
            scope,
            playerObject?.id,
            permissionKey
        )
    }
    if (hasPermission != null) {
        loreLines.add(Translator.tr("ui.admin.member.setting.lore.permission", hasPermission.toString()) ?: Component.literal("Permission: $hasPermission"))
        scope?.let {
            loreLines.add(Translator.tr("ui.admin.member.setting.lore.scope", scope.scopeName) ?: Component.literal("Scope: ${scope.scopeName}"))
        }
        playerObject?.let {
            loreLines.add(Translator.tr("ui.admin.member.setting.lore.player", playerObject.name) ?: Component.literal("Player: ${playerObject.name}"))
        }
    }
    val unitPrice = TerritoryPricing.getPermissionCoefficientPerUnit(permissionKey)
    if (unitPrice > 0) {
        loreLines.add(Translator.tr("ui.admin.region.setting.lore.unit_price", String.format("%.2f", unitPrice / 100.0)) ?: Component.literal("Unit price: ${String.format("%.2f", unitPrice / 100.0)} per 10000m²"))
    }

    return getLoreButton(itemStack, loreLines)
}

fun runTogglingPermissionSetting(
    playerExecutor: ServerPlayer,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey,
    runBack: (ServerPlayer) -> Unit
) {
    val permission = com.imyvm.community.domain.policy.permission.AdminPrivilege.MODIFY_REGION_SETTINGS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        val region = community.getRegion() ?: return@executeWithPermission
        val regionId = community.regionNumberId ?: return@executeWithPermission

        if (WorldGeoCommunityAddon.pendingOperations.containsKey(regionId)) {
            playerExecutor.closeContainer()
            playerExecutor.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val oldValue = RegionDataApi.getPermissionValueRegion(region, scope, playerObject?.id, permissionKey)
        val newValue = !oldValue

        val areaByDimension = getPricingAreaByDimension(region, scope)
        val area = areaByDimension.values.sum()

        val defaultValue = RegionDataApi.getPermissionValueRegion(null, null, null, permissionKey)
        val isRestoringDefault = (newValue == defaultValue)
        val costResult = TerritoryPricing.calculatePermissionSettingCostResult(
            areaByDimension = areaByDimension,
            permissionKey = permissionKey,
            isManor = community.isManor(),
            isPlayerTarget = playerObject != null
        )
        val cost = if (isRestoringDefault) {
            -(costResult.cost * PricingConfig.AREA_REFUND_RATE.value).toLong()
        } else {
            costResult.cost
        }

        playerExecutor.closeContainer()

        addPendingOperation(
            regionId = regionId,
            type = PendingOperationType.SETTING_CONFIRMATION,
            expireMinutes = 5,
            inviterUUID = playerExecutor.uuid,
            settingData = SettingConfirmationData(
                regionNumberId = regionId,
                scopeName = scope?.scopeName,
                executorUUID = playerExecutor.uuid,
                permissionKeyStr = permissionKey.toString(),
                newValue = newValue,
                targetPlayerUUID = playerObject?.id,
                cost = cost
            )
        )

        sendSettingOrderSummary(playerExecutor, community, scope, playerObject, permissionKey, oldValue, newValue, area, cost, defaultValue, costResult)
        sendInteractiveSettingConfirmation(playerExecutor, regionId)
    }
}

fun getRuleButtonItemStack(
    item: Item,
    community: Community,
    scope: GeoScope?,
    ruleKey: RuleKey
): ItemStack {
    val itemStack = ItemStack(item)
    val loreLines = mutableListOf<Component>()
    val currentValue = community.getRegion()?.let {
        RegionDataApi.getRuleValueForRegion(it, scope, ruleKey)
    }
    if (currentValue != null) {
        loreLines.add(Translator.tr("ui.admin.member.setting.lore.permission", currentValue.toString()) ?: Component.literal("Permission: $currentValue"))
        scope?.let {
            loreLines.add(Translator.tr("ui.admin.member.setting.lore.scope", scope.scopeName) ?: Component.literal("Scope: ${scope.scopeName}"))
        }
    }
    val unitPrice = TerritoryPricing.getRuleCoefficientPerUnit(ruleKey)
    loreLines.add(Translator.tr("ui.admin.region.setting.lore.unit_price", String.format("%.2f", unitPrice / 100.0)) ?: Component.literal("Unit price: ${String.format("%.2f", unitPrice / 100.0)} per 10000m²"))
    return getLoreButton(itemStack, loreLines)
}

fun runTogglingRuleSetting(
    playerExecutor: ServerPlayer,
    community: Community,
    scope: GeoScope?,
    ruleKey: RuleKey,
    runBack: (ServerPlayer) -> Unit
) {
    val privilege = com.imyvm.community.domain.policy.permission.AdminPrivilege.MODIFY_REGION_SETTINGS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, privilege) }
    ) {
        val region = community.getRegion() ?: return@executeWithPermission
        val regionId = community.regionNumberId ?: return@executeWithPermission

        if (WorldGeoCommunityAddon.pendingOperations.containsKey(regionId)) {
            playerExecutor.closeContainer()
            playerExecutor.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val oldValue = RegionDataApi.getRuleValueForRegion(region, scope, ruleKey) ?: false
        val newValue = !oldValue

        val areaByDimension = getPricingAreaByDimension(region, scope)
        val area = areaByDimension.values.sum()

        val defaultValue = RegionDataApi.getRuleValueForRegion(null, null, ruleKey) ?: false
        val isRestoringDefault = (newValue == defaultValue)
        val costResult = TerritoryPricing.calculateRuleSettingCostResult(
            areaByDimension = areaByDimension,
            ruleKey = ruleKey,
            isManor = community.isManor()
        )
        val cost = if (isRestoringDefault) {
            -(costResult.cost * PricingConfig.AREA_REFUND_RATE.value).toLong()
        } else {
            costResult.cost
        }

        playerExecutor.closeContainer()

        addPendingOperation(
            regionId = regionId,
            type = PendingOperationType.SETTING_CONFIRMATION,
            expireMinutes = 5,
            inviterUUID = playerExecutor.uuid,
            settingData = SettingConfirmationData(
                regionNumberId = regionId,
                scopeName = scope?.scopeName,
                executorUUID = playerExecutor.uuid,
                permissionKeyStr = ruleKey.toString(),
                newValue = newValue,
                targetPlayerUUID = null,
                cost = cost,
                isRuleSetting = true
            )
        )

        sendRuleSettingOrderSummary(playerExecutor, community, scope, ruleKey, oldValue, newValue, area, cost, costResult)
        sendInteractiveSettingConfirmation(playerExecutor, regionId)
    }
}

fun onConfirmSettingChange(playerExecutor: ServerPlayer, regionNumberId: Int): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.settingData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.SETTING_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid) {
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.not_yours"))
        return 0
    }
    if (System.currentTimeMillis() > pendingOperation.expireAt) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.expired"))
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    val region = community?.getRegion()
    if (community == null || region == null) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(Translator.tr("community.not_found.region"))
        return 0
    }

    val scope = request.scopeName?.let { scopeN ->
        region.geometryScope.firstOrNull { it.scopeName.equals(scopeN, ignoreCase = true) }
    }

    if (request.isRuleSetting) {
        val ruleKey = RuleKey.valueOf(request.permissionKeyStr)
        val hasIdenticalRuleSetting = if (scope == null) {
            RegionDataApi.getRegionGlobalSettings(region).filterIsInstance<RuleSetting>()
                .any { it.key == ruleKey && it.value == request.newValue }
        } else {
            RegionDataApi.getScopeGlobalSettings(scope).filterIsInstance<RuleSetting>()
                .any { it.key == ruleKey && it.value == request.newValue }
        }
        if (hasIdenticalRuleSetting) {
            WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.already_set"))
            return 0
        }

        if (request.cost > 0 && community.getTotalAssets() < request.cost) {
            WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            playerExecutor.sendSystemMessage(
                Translator.tr(
                    "community.modification.error.insufficient_assets",
                    String.format("%.2f", request.cost / 100.0),
                    String.format("%.2f", community.getTotalAssets() / 100.0)
                )
            )
            return 0
        }

        val newValueStr = request.newValue.toString()
        if (scope == null) {
            PlayerInteractionApi.removeSettingRegion(playerExecutor, region, request.permissionKeyStr, null)
            PlayerInteractionApi.addSettingRegion(playerExecutor, region, request.permissionKeyStr, newValueStr, null)
        } else {
            PlayerInteractionApi.removeSettingScope(playerExecutor, region, scope.scopeName, request.permissionKeyStr, null)
            PlayerInteractionApi.addSettingScope(playerExecutor, region, scope.scopeName, request.permissionKeyStr, newValueStr, null)
        }

        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)

        if (request.cost > 0) {
            community.expenditures.add(Turnover(request.cost, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.setting_change", listOf(request.permissionKeyStr)))
        } else if (request.cost < 0) {
            val refundAmount = -request.cost
            val ownerUUID = community.getOwnerUUID()
            val ownerAccount = ownerUUID?.let { community.member[it] }
            ownerAccount?.turnover?.add(Turnover(refundAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.setting_refund", listOf(request.permissionKeyStr)))
        }
        CommunityDatabase.save()

        playerExecutor.sendSystemMessage(
            Translator.tr(
                "community.setting.confirmation.completed",
                getRuleKeyDisplayName(ruleKey),
                newValueStr,
                String.format("%.2f", request.cost / 100.0)
            )
        )

        val communityName = region.name
        val notification = Translator.tr(
            "community.notification.setting_changed",
            getRuleKeyDisplayName(ruleKey),
            (!request.newValue).toString(),
            newValueStr,
            request.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region").string ?: ""),
            Translator.tr("community.setting.confirmation.target.global").string ?: "",
            playerExecutor.name.string,
            communityName
        ) ?: Component.literal("Setting changed")
        notifyFormalMembers(community, playerExecutor.level().server, notification)

        return 1
    }

    val permissionKey = PermissionKey.valueOf(request.permissionKeyStr)
    val hasIdenticalSetting = if (request.targetPlayerUUID == null) {
        if (scope == null) {
            RegionDataApi.getRegionGlobalSettings(region).filterIsInstance<PermissionSetting>()
                .any { it.key == permissionKey && it.value == request.newValue }
        } else {
            RegionDataApi.getScopeGlobalSettings(scope).filterIsInstance<PermissionSetting>()
                .any { it.key == permissionKey && it.value == request.newValue }
        }
    } else {
        if (scope == null) {
            RegionDataApi.getRegionPersonalSettings(region, request.targetPlayerUUID).filterIsInstance<PermissionSetting>()
                .any { it.key == permissionKey && it.value == request.newValue }
        } else {
            RegionDataApi.getScopePersonalSettings(scope, request.targetPlayerUUID).filterIsInstance<PermissionSetting>()
                .any { it.key == permissionKey && it.value == request.newValue }
        }
    }
    if (hasIdenticalSetting) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.already_set"))
        return 0
    }

    if (request.cost > 0 && community.getTotalAssets() < request.cost) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendSystemMessage(
            Translator.tr(
                "community.modification.error.insufficient_assets",
                String.format("%.2f", request.cost / 100.0),
                String.format("%.2f", community.getTotalAssets() / 100.0)
            )
        )
        return 0
    }

    val targetPlayerIdStr = request.targetPlayerUUID?.let { UtilApi.getPlayerName(playerExecutor.level().server, it) }
    val newValueStr = request.newValue.toString()
    if (scope == null) {
        PlayerInteractionApi.removeSettingRegion(playerExecutor, region, request.permissionKeyStr, targetPlayerIdStr)
        PlayerInteractionApi.addSettingRegion(playerExecutor, region, request.permissionKeyStr, newValueStr, targetPlayerIdStr)
    } else {
        PlayerInteractionApi.removeSettingScope(playerExecutor, region, scope.scopeName, request.permissionKeyStr, targetPlayerIdStr)
        PlayerInteractionApi.addSettingScope(playerExecutor, region, scope.scopeName, request.permissionKeyStr, newValueStr, targetPlayerIdStr)
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)

    if (request.cost > 0) {
        community.expenditures.add(Turnover(request.cost, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.setting_change", listOf(request.permissionKeyStr)))
    } else if (request.cost < 0) {
        val refundAmount = -request.cost
        val ownerUUID = community.getOwnerUUID()
        val ownerAccount = ownerUUID?.let { community.member[it] }
        ownerAccount?.turnover?.add(Turnover(refundAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.setting_refund", listOf(request.permissionKeyStr)))
    }
    CommunityDatabase.save()

    playerExecutor.sendSystemMessage(
        Translator.tr(
            "community.setting.confirmation.completed",
            getPermissionKeyDisplayName(permissionKey),
            newValueStr,
            String.format("%.2f", request.cost / 100.0)
        )
    )

    val communityName = region.name
    val notification = Translator.tr(
        "community.notification.setting_changed",
        getPermissionKeyDisplayName(permissionKey),
        (!request.newValue).toString(),
        newValueStr,
        request.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region").string ?: ""),
        request.targetPlayerUUID?.let { UtilApi.getPlayerName(playerExecutor.level().server, it) } ?: (Translator.tr("community.setting.confirmation.target.global").string ?: ""),
        playerExecutor.name.string,
        communityName
    ) ?: Component.literal("Setting changed")
    notifyFormalMembers(community, playerExecutor.level().server, notification)

    if (scope == null && request.targetPlayerUUID == null &&
        permissionKey != PermissionKey.PVP && permissionKey != PermissionKey.FLY) {
        val defaultValue = RegionDataApi.getPermissionValueRegion(null, null, null, permissionKey)
        if (request.newValue != defaultValue) {
            autoGrantExistingMembersOnSettingChange(permissionKey, defaultValue, playerExecutor, community, region)
        }
    }

    return 1
}

fun onCancelSettingChange(playerExecutor: ServerPlayer, regionNumberId: Int): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.settingData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.SETTING_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid) {
        playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    playerExecutor.sendSystemMessage(Translator.tr("community.setting.confirmation.cancelled"))
    return 1
}

fun autoGrantDefaultPermissions(newMemberUUID: UUID, executorPlayer: ServerPlayer, community: Community) {
    val region = community.getRegion() ?: return

    for (permissionKey in PermissionKey.entries) {
        if (permissionKey == PermissionKey.PVP || permissionKey == PermissionKey.FLY) continue
        val defaultValue = RegionDataApi.getPermissionValueRegion(null, null, null, permissionKey)

        val globalSetting = RegionDataApi.getRegionGlobalSettings(region)
            .filterIsInstance<PermissionSetting>()
            .firstOrNull { it.key == permissionKey }

        if (globalSetting == null || globalSetting.value == defaultValue) continue

        val alreadyHasPersonalSetting = RegionDataApi.getRegionPersonalSettings(region, newMemberUUID)
            .filterIsInstance<PermissionSetting>()
            .any { it.key == permissionKey }

        if (alreadyHasPersonalSetting) continue

        val newMemberName = UtilApi.getPlayerName(executorPlayer.level().server, newMemberUUID)
        if (newMemberName == newMemberUUID.toString()) continue

        PlayerInteractionApi.addSettingRegion(
            executorPlayer,
            region,
            permissionKey.toString(),
            defaultValue.toString(),
            newMemberName
        )
    }
}

fun revokeGrantedPermissions(memberUUID: UUID, community: Community) {
    val region = community.getRegion() ?: return
    region.settings.removeIf { it is PermissionSetting && it.isPersonal && it.playerUUID == memberUUID }
    region.geometryScope.forEach { scope ->
        scope.settings.removeIf { it is PermissionSetting && it.isPersonal && it.playerUUID == memberUUID }
    }
}

private fun autoGrantExistingMembersOnSettingChange(
    permissionKey: PermissionKey,
    defaultValue: Boolean,
    executorPlayer: ServerPlayer,
    community: Community,
    region: Region
) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT ||
            memberAccount.basicRoleType == MemberRoleType.REFUSED) return@forEach

        val alreadyHasPersonalSetting = RegionDataApi.getRegionPersonalSettings(region, memberUUID)
            .filterIsInstance<PermissionSetting>()
            .any { it.key == permissionKey }

        if (alreadyHasPersonalSetting) return@forEach

        val memberName = UtilApi.getPlayerName(executorPlayer.level().server, memberUUID)
        if (memberName == memberUUID.toString()) return@forEach

        PlayerInteractionApi.addSettingRegion(
            executorPlayer,
            region,
            permissionKey.toString(),
            defaultValue.toString(),
            memberName
        )

        val notification = Translator.tr(
            "community.notification.auto_grant",
            getPermissionKeyDisplayName(permissionKey),
            defaultValue.toString(),
            region.name
        ) ?: Component.literal("Your ${permissionKey.toString().lowercase()} permission was auto-adjusted in ${region.name}")
        executorPlayer.level().server.playerList.getPlayer(memberUUID)?.sendSystemMessage(notification)
        memberAccount.mail.add(notification)
    }
}

private fun getPermissionKeyDisplayName(permissionKey: PermissionKey): String {
    val key = "community.permission.key.${permissionKey.toString().lowercase()}"
    return Translator.tr(key).string ?: permissionKey.toString().lowercase().replace("_", " ")
}

private fun getRuleKeyDisplayName(ruleKey: RuleKey): String {
    val key = "community.rule.key.${ruleKey.toString().lowercase()}"
    return Translator.tr(key).string ?: ruleKey.toString().lowercase().replace("_", " ")
}

private fun sendSettingOrderSummary(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey,
    oldValue: Boolean,
    newValue: Boolean,
    area: Double,
    cost: Long,
    defaultValue: Boolean,
    costResult: SettingCostResult
) {
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val permissionName = getPermissionKeyDisplayName(permissionKey)

    player.sendSystemMessage(Translator.tr("community.setting.confirmation.header") ?: Component.literal("§6§l====== SETTING MODIFICATION CONFIRMATION ======§r"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.community", communityName) ?: Component.literal("§eCommunity: §f$communityName"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.permission", permissionName) ?: Component.literal("§ePermission: §f$permissionName"))

    if (scope != null) {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.layer.scope", scope.scopeName) ?: Component.literal("§eLayer: §fScope - ${scope.scopeName}"))
    } else {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.layer.region") ?: Component.literal("§eLayer: §fRegion"))
    }

    if (playerObject != null) {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.target.player", playerObject.name) ?: Component.literal("§eTarget: §f${playerObject.name}"))
    } else {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.target.global") ?: Component.literal("§eTarget: §fAll members"))
    }

    player.sendSystemMessage(Translator.tr("community.setting.confirmation.change", oldValue.toString(), newValue.toString()) ?: Component.literal("§eChange: §c$oldValue §e-> §a$newValue"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.area", String.format("%.2f", area)) ?: Component.literal("§eArea: §f${String.format("%.2f", area)} m²"))
    sendDimensionLegend(player, costResult.dimensionCosts.map { it.dimensionId })

    if (cost != 0L) {
        val coefficientPerUnit = TerritoryPricing.getPermissionCoefficientPerUnit(permissionKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
        sendDimensionBreakdown(player, costResult, coefficientPerUnit.toDouble() / unitSize)
        if (costResult.denominator > 1L) {
            player.sendSystemMessage(
                Translator.tr("community.setting.confirmation.player_factor", costResult.denominator.toString())
                    ?: Component.literal("§7  Player factor: §fx1/${costResult.denominator}")
            )
        }
        if (cost < 0) {
            val refundPct = (PricingConfig.AREA_REFUND_RATE.value * 100).toInt()
            player.sendSystemMessage(Translator.tr("community.pricing.refund_summary", refundPct.toString(), String.format("%.2f", -cost / 100.0))
                ?: Component.literal("  × $refundPct% refund = ${String.format("%.2f", -cost / 100.0)}"))
        }
        val totalKey = if (cost < 0) "community.setting.confirmation.refund_total" else "community.setting.confirmation.total_cost"
        player.sendSystemMessage(
            Translator.tr(totalKey, String.format("%.2f", Math.abs(cost) / 100.0))
                ?: Component.literal("§eCost: §c§l${String.format("%.2f", cost / 100.0)}§r")
        )
    } else {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.free") ?: Component.literal("§eCost: §a§lFree§r §7(restoring to default)"))
    }

    val assetsAfter = community.getTotalAssets() - cost
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.assets", String.format("%.2f", community.getTotalAssets() / 100.0), String.format("%.2f", assetsAfter / 100.0)) ?: Component.literal("§eCommunity Assets: §f${String.format("%.2f", community.getTotalAssets() / 100.0)} §e-> §f${String.format("%.2f", assetsAfter / 100.0)}"))
}

private fun sendRuleSettingOrderSummary(
    player: ServerPlayer,
    community: Community,
    scope: GeoScope?,
    ruleKey: RuleKey,
    oldValue: Boolean,
    newValue: Boolean,
    area: Double,
    cost: Long,
    costResult: SettingCostResult
) {
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val ruleName = getRuleKeyDisplayName(ruleKey)

    player.sendSystemMessage(Translator.tr("community.setting.confirmation.header") ?: Component.literal("§6§l====== SETTING MODIFICATION CONFIRMATION ======§r"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.community", communityName) ?: Component.literal("§eCommunity: §f$communityName"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.rule", ruleName) ?: Component.literal("§eRule: §f$ruleName"))

    if (scope != null) {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.layer.scope", scope.scopeName) ?: Component.literal("§eLayer: §fScope - ${scope.scopeName}"))
    } else {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.layer.region") ?: Component.literal("§eLayer: §fRegion"))
    }

    player.sendSystemMessage(Translator.tr("community.setting.confirmation.target.global") ?: Component.literal("§eTarget: §fAll members"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.change", oldValue.toString(), newValue.toString()) ?: Component.literal("§eChange: §c$oldValue §e-> §a$newValue"))
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.area", String.format("%.2f", area)) ?: Component.literal("§eArea: §f${String.format("%.2f", area)} m²"))
    sendDimensionLegend(player, costResult.dimensionCosts.map { it.dimensionId })

    val coefficientPerUnit = TerritoryPricing.getRuleCoefficientPerUnit(ruleKey)
    val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
    if (cost != 0L) {
        sendDimensionBreakdown(player, costResult, coefficientPerUnit.toDouble() / unitSize)
        if (cost < 0) {
            val refundPct = (PricingConfig.AREA_REFUND_RATE.value * 100).toInt()
            player.sendSystemMessage(Translator.tr("community.pricing.refund_summary", refundPct.toString(), String.format("%.2f", -cost / 100.0))
                ?: Component.literal("  × $refundPct% refund = ${String.format("%.2f", -cost / 100.0)}"))
        }
        val totalKey = if (cost < 0) "community.setting.confirmation.refund_total" else "community.setting.confirmation.total_cost"
        player.sendSystemMessage(
            Translator.tr(totalKey, String.format("%.2f", Math.abs(cost) / 100.0))
                ?: Component.literal("§eCost: §c§l${String.format("%.2f", cost / 100.0)}§r")
        )
    } else {
        player.sendSystemMessage(Translator.tr("community.setting.confirmation.free") ?: Component.literal("§eCost: §a§lFree§r"))
    }

    val assetsAfter = community.getTotalAssets() - cost
    player.sendSystemMessage(Translator.tr("community.setting.confirmation.assets", String.format("%.2f", community.getTotalAssets() / 100.0), String.format("%.2f", assetsAfter / 100.0)) ?: Component.literal("§eCommunity Assets: §f${String.format("%.2f", community.getTotalAssets() / 100.0)} §e-> §f${String.format("%.2f", assetsAfter / 100.0)}"))
}

private fun getPricingAreaByDimension(region: Region, scope: GeoScope?): Map<String, Double> {
    return if (scope == null) {
        TerritoryPricing.getRegionAreaByDimension(region)
    } else {
        TerritoryPricing.getScopeAreaByDimension(scope, RegionDataApi.getScopeArea(scope) ?: 0.0)
    }
}

private fun sendDimensionLegend(player: ServerPlayer, dimensionIds: Collection<String>) {
    if (dimensionIds.isEmpty()) return
    val orderedIds = dimensionIds.distinct().sortedBy {
        when (TerritoryPricing.normalizeDimensionId(it)) {
            TerritoryPricing.DIMENSION_OVERWORLD -> 0
            TerritoryPricing.DIMENSION_NETHER -> 1
            TerritoryPricing.DIMENSION_END -> 2
            else -> 3
        }
    }
    val parts = orderedIds.map {
        val normalized = TerritoryPricing.normalizeDimensionId(it)
        val multiplierKey = when (normalized) {
            TerritoryPricing.DIMENSION_NETHER -> "community.pricing.dimension.multiplier.nether"
            TerritoryPricing.DIMENSION_END -> "community.pricing.dimension.multiplier.end"
            else -> "community.pricing.dimension.multiplier.overworld"
        }
        Translator.tr(multiplierKey, TerritoryPricing.getDimensionMultiplier(normalized).toString())?.string
            ?: "$normalized x${TerritoryPricing.getDimensionMultiplier(normalized)}"
    }
    player.sendSystemMessage(
        Translator.tr("community.pricing.dimension.legend", parts.joinToString("§7, "))
            ?: Component.literal("§7Dimension multipliers: ${parts.joinToString(", ")}")
    )
}

private fun sendDimensionBreakdown(
    player: ServerPlayer,
    costResult: SettingCostResult,
    baseUnitPrice: Double
) {
    for (dimensionCost in costResult.dimensionCosts) {
        val dimensionKey = TerritoryPricing.getDimensionDisplayKey(dimensionCost.dimensionId)
        val dimensionName = Translator.tr(dimensionKey)?.string ?: dimensionCost.dimensionId
        player.sendSystemMessage(
            Translator.tr(
                "community.pricing.dimension.header",
                dimensionName,
                String.format("%.2f", dimensionCost.areaAfter),
                dimensionCost.dimensionMultiplier.toString(),
                String.format("%.2f", dimensionCost.grossCost / 100.0)
            ) ?: Component.literal("§7  $dimensionName: ${String.format("%.2f", dimensionCost.areaAfter)} m² ×${dimensionCost.dimensionMultiplier} = ${String.format("%.2f", dimensionCost.grossCost / 100.0)}")
        )
        for (bracket in dimensionCost.brackets) {
            val unitPrice = baseUnitPrice * bracket.bracketMultiplier.toDouble() * dimensionCost.dimensionMultiplier.toDouble() / 100.0
            player.sendSystemMessage(
                Translator.tr(
                    "community.pricing.bracket_line",
                    bracket.tierNum.toString(),
                    String.format("%.2f", bracket.bracketLow),
                    String.format("%.2f", bracket.bracketHigh),
                    String.format("%.2f", bracket.areaInBracket),
                    String.format("%.3f", unitPrice),
                    String.format("%.2f", bracket.cost / 100.0)
                ) ?: Component.literal("  Tier ${bracket.tierNum} (${String.format("%.2f", bracket.bracketLow)} ~ ${String.format("%.2f", bracket.bracketHigh)} m²): ${String.format("%.2f", bracket.areaInBracket)} m² ×${String.format("%.3f", unitPrice)}/m² = ${String.format("%.2f", bracket.cost / 100.0)}")
            )
        }
    }
}

private fun sendInteractiveSettingConfirmation(player: ServerPlayer, regionNumberId: Int) {
    val confirmText = Translator.tr("community.setting.confirmation.confirm_button") ?: Component.literal("§a§l[CONFIRM]§r")
    val cancelText = Translator.tr("community.setting.confirmation.cancel_button") ?: Component.literal("§c§l[CANCEL]§r")

    val confirmButton = confirmText.copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand( "/_commun confirm_setting $regionNumberId"))
            .withHoverEvent(HoverEvent.ShowText( Translator.tr("community.setting.confirmation.confirm_hover")))
    }

    val cancelButton = cancelText.copy().withStyle { style ->
        style.withClickEvent(ClickEvent.RunCommand( "/_commun cancel_setting $regionNumberId"))
            .withHoverEvent(HoverEvent.ShowText( Translator.tr("community.setting.confirmation.cancel_hover")))
    }

    val prompt = (Translator.tr("community.setting.confirmation.prompt") ?: Component.literal("§e§l[ACTION REQUIRED]§r"))
        .copy()
        .append(Component.literal(" "))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)
    player.sendSystemMessage(prompt)
}

private fun notifyFormalMembers(community: Community, server: MinecraftServer, message: Component) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerList.getPlayer(memberUUID)?.sendSystemMessage(message)
        memberAccount.mail.add(message)
    }
}
