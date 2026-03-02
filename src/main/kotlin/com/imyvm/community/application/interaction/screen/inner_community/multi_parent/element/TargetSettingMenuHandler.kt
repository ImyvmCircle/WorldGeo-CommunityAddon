package com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.SettingConfirmationData
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.community.MemberRoleType
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
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import java.util.UUID

fun getPermissionButtonItemStack(
    item: Item,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey
): ItemStack {
    val itemStack = ItemStack(item)

    val loreLines = mutableListOf<Text>()
    val hasPermission = community.getRegion()?.let {
        RegionDataApi.getPermissionValueRegion(
            it,
            scope,
            playerObject?.id,
            permissionKey
        )
    }
    if (hasPermission != null) {
        loreLines.add(Translator.tr("ui.community.administration.member.setting.lore.permission", hasPermission.toString()) ?: Text.literal("Permission: $hasPermission"))
        scope?.let {
            loreLines.add(Translator.tr("ui.community.administration.member.setting.lore.scope", scope.scopeName) ?: Text.literal("Scope: ${scope.scopeName}"))
        }
        playerObject?.let {
            loreLines.add(Translator.tr("ui.community.administration.member.setting.lore.player", playerObject.name) ?: Text.literal("Player: ${playerObject.name}"))
        }
    }
    val unitPrice = TerritoryPricing.getPermissionCoefficientPerUnit(permissionKey)
    if (unitPrice > 0) {
        loreLines.add(Translator.tr("ui.community.administration.region.setting.lore.unit_price", String.format("%.2f", unitPrice / 100.0)) ?: Text.literal("Unit price: ${String.format("%.2f", unitPrice / 100.0)} per 10000m²"))
    }

    return getLoreButton(itemStack, loreLines)
}

fun runTogglingPermissionSetting(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val permission = com.imyvm.community.domain.policy.permission.AdminPrivilege.MODIFY_REGION_SETTINGS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, permission) }
    ) {
        val region = community.getRegion() ?: return@executeWithPermission
        val regionId = community.regionNumberId ?: return@executeWithPermission

        if (WorldGeoCommunityAddon.pendingOperations.containsKey(regionId)) {
            playerExecutor.closeHandledScreen()
            playerExecutor.sendMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val oldValue = RegionDataApi.getPermissionValueRegion(region, scope, playerObject?.id, permissionKey)
        val newValue = !oldValue

        val area = if (scope == null) RegionDataApi.getRegionArea(region) else RegionDataApi.getScopeArea(scope) ?: 0.0

        val defaultValue = RegionDataApi.getPermissionValueRegion(null, null, null, permissionKey)
        val cost = if (newValue != defaultValue && area > 0) {
            TerritoryPricing.calculatePermissionSettingCost(
                area = area,
                permissionKey = permissionKey,
                isManor = community.isManor(),
                isScope = scope != null,
                isPlayerTarget = playerObject != null
            )
        } else 0L

        playerExecutor.closeHandledScreen()

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

        sendSettingOrderSummary(playerExecutor, community, scope, playerObject, permissionKey, oldValue, newValue, area, cost, defaultValue)
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
    val loreLines = mutableListOf<Text>()
    val currentValue = community.getRegion()?.let {
        RegionDataApi.getRuleValueForRegion(it, scope, ruleKey)
    }
    if (currentValue != null) {
        loreLines.add(Translator.tr("ui.community.administration.member.setting.lore.permission", currentValue.toString()) ?: Text.literal("Permission: $currentValue"))
        scope?.let {
            loreLines.add(Translator.tr("ui.community.administration.member.setting.lore.scope", scope.scopeName) ?: Text.literal("Scope: ${scope.scopeName}"))
        }
    }
    val unitPrice = TerritoryPricing.getRuleCoefficientPerUnit(ruleKey)
    loreLines.add(Translator.tr("ui.community.administration.region.setting.lore.unit_price", String.format("%.2f", unitPrice / 100.0)) ?: Text.literal("Unit price: ${String.format("%.2f", unitPrice / 100.0)} per 10000m²"))
    return getLoreButton(itemStack, loreLines)
}

fun runTogglingRuleSetting(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    ruleKey: RuleKey,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val privilege = com.imyvm.community.domain.policy.permission.AdminPrivilege.MODIFY_REGION_SETTINGS
    com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy.canExecuteAdministration(playerExecutor, community, privilege) }
    ) {
        val region = community.getRegion() ?: return@executeWithPermission
        val regionId = community.regionNumberId ?: return@executeWithPermission

        if (WorldGeoCommunityAddon.pendingOperations.containsKey(regionId)) {
            playerExecutor.closeHandledScreen()
            playerExecutor.sendMessage(Translator.tr("community.modification.confirmation.pending"))
            return@executeWithPermission
        }

        val oldValue = RegionDataApi.getRuleValueForRegion(region, scope, ruleKey) ?: false
        val newValue = !oldValue

        val area = if (scope == null) RegionDataApi.getRegionArea(region) else RegionDataApi.getScopeArea(scope) ?: 0.0

        val cost = TerritoryPricing.calculateRuleSettingCost(
            area = area,
            ruleKey = ruleKey,
            isManor = community.isManor(),
            isScope = scope != null
        )

        playerExecutor.closeHandledScreen()

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

        sendRuleSettingOrderSummary(playerExecutor, community, scope, ruleKey, oldValue, newValue, area, cost)
        sendInteractiveSettingConfirmation(playerExecutor, regionId)
    }
}

fun onConfirmSettingChange(playerExecutor: ServerPlayerEntity, regionNumberId: Int): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.settingData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.SETTING_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid) {
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.not_yours"))
        return 0
    }
    if (System.currentTimeMillis() > pendingOperation.expireAt) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.expired"))
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    val region = community?.getRegion()
    if (community == null || region == null) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendMessage(Translator.tr("community.not_found.region"))
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
            playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.already_set"))
            return 0
        }

        if (request.cost > 0 && community.getTotalAssets() < request.cost) {
            WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
            playerExecutor.sendMessage(
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
            community.expenditures.add(Turnover(request.cost, System.currentTimeMillis()))
        }
        CommunityDatabase.save()

        playerExecutor.sendMessage(
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
            request.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region")?.string ?: ""),
            Translator.tr("community.setting.confirmation.target.global")?.string ?: "",
            playerExecutor.name.string,
            communityName
        ) ?: Text.literal("Setting changed")
        notifyFormalMembers(community, playerExecutor.server, notification)

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
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.already_set"))
        return 0
    }

    if (request.cost > 0 && community.getTotalAssets() < request.cost) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        playerExecutor.sendMessage(
            Translator.tr(
                "community.modification.error.insufficient_assets",
                String.format("%.2f", request.cost / 100.0),
                String.format("%.2f", community.getTotalAssets() / 100.0)
            )
        )
        return 0
    }

    val targetPlayerIdStr = request.targetPlayerUUID?.let { UtilApi.getPlayerName(playerExecutor.server, it) }
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
        community.expenditures.add(Turnover(request.cost, System.currentTimeMillis()))
    }
    CommunityDatabase.save()

    playerExecutor.sendMessage(
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
        request.scopeName ?: (Translator.tr("community.setting.confirmation.layer.region")?.string ?: ""),
        request.targetPlayerUUID?.let { UtilApi.getPlayerName(playerExecutor.server, it) } ?: (Translator.tr("community.setting.confirmation.target.global")?.string ?: ""),
        playerExecutor.name.string,
        communityName
    ) ?: Text.literal("Setting changed")
    notifyFormalMembers(community, playerExecutor.server, notification)

    return 1
}

fun onCancelSettingChange(playerExecutor: ServerPlayerEntity, regionNumberId: Int): Int {
    val pendingOperation = WorldGeoCommunityAddon.pendingOperations[regionNumberId]
    val request = pendingOperation?.settingData
    if (pendingOperation == null ||
        pendingOperation.type != PendingOperationType.SETTING_CONFIRMATION ||
        request == null
    ) {
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.not_found"))
        return 0
    }
    if (request.executorUUID != playerExecutor.uuid) {
        playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    playerExecutor.sendMessage(Translator.tr("community.setting.confirmation.cancelled"))
    return 1
}

fun autoGrantDefaultPermissions(newMemberUUID: UUID, executorPlayer: ServerPlayerEntity, community: Community) {
    val region = community.getRegion() ?: return

    for (permissionKey in PermissionKey.entries) {
        val defaultValue = RegionDataApi.getPermissionValueRegion(null, null, null, permissionKey)

        val globalSetting = RegionDataApi.getRegionGlobalSettings(region)
            .filterIsInstance<PermissionSetting>()
            .firstOrNull { it.key == permissionKey }

        if (globalSetting == null || globalSetting.value == defaultValue) continue

        val alreadyHasPersonalSetting = RegionDataApi.getRegionPersonalSettings(region, newMemberUUID)
            .filterIsInstance<PermissionSetting>()
            .any { it.key == permissionKey }

        if (alreadyHasPersonalSetting) continue

        val newMemberName = UtilApi.getPlayerName(executorPlayer.server, newMemberUUID)
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

private fun getPermissionKeyDisplayName(permissionKey: PermissionKey): String {
    val key = "community.permission.key.${permissionKey.toString().lowercase()}"
    return Translator.tr(key)?.string ?: permissionKey.toString().lowercase().replace("_", " ")
}

private fun getRuleKeyDisplayName(ruleKey: RuleKey): String {
    val key = "community.rule.key.${ruleKey.toString().lowercase()}"
    return Translator.tr(key)?.string ?: ruleKey.toString().lowercase().replace("_", " ")
}

private fun sendSettingOrderSummary(
    player: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    playerObject: GameProfile?,
    permissionKey: PermissionKey,
    oldValue: Boolean,
    newValue: Boolean,
    area: Double,
    cost: Long,
    defaultValue: Boolean
) {
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val permissionName = getPermissionKeyDisplayName(permissionKey)

    player.sendMessage(Translator.tr("community.setting.confirmation.header") ?: Text.literal("§6§l====== SETTING MODIFICATION CONFIRMATION ======§r"))
    player.sendMessage(Translator.tr("community.setting.confirmation.community", communityName) ?: Text.literal("§eCommunity: §f$communityName"))
    player.sendMessage(Translator.tr("community.setting.confirmation.permission", permissionName) ?: Text.literal("§ePermission: §f$permissionName"))

    if (scope != null) {
        player.sendMessage(Translator.tr("community.setting.confirmation.layer.scope", scope.scopeName) ?: Text.literal("§eLayer: §fScope - ${scope.scopeName}"))
    } else {
        player.sendMessage(Translator.tr("community.setting.confirmation.layer.region") ?: Text.literal("§eLayer: §fRegion"))
    }

    if (playerObject != null) {
        player.sendMessage(Translator.tr("community.setting.confirmation.target.player", playerObject.name) ?: Text.literal("§eTarget: §f${playerObject.name}"))
    } else {
        player.sendMessage(Translator.tr("community.setting.confirmation.target.global") ?: Text.literal("§eTarget: §fAll members"))
    }

    player.sendMessage(Translator.tr("community.setting.confirmation.change", oldValue.toString(), newValue.toString()) ?: Text.literal("§eChange: §c$oldValue §e-> §a$newValue"))
    player.sendMessage(Translator.tr("community.setting.confirmation.area", String.format("%.2f", area)) ?: Text.literal("§eArea: §f${String.format("%.2f", area)} m²"))

    if (cost > 0) {
        val isManor = community.isManor()
        val isPlayerTarget = playerObject != null
        val baseCostBeforeDiv = when {
            isManor && scope == null -> PricingConfig.PERMISSION_BASE_COST_MANOR_REGION.value
            !isManor && scope == null -> PricingConfig.PERMISSION_BASE_COST_REALM_REGION.value
            isManor -> PricingConfig.PERMISSION_BASE_COST_MANOR_SCOPE.value
            else -> PricingConfig.PERMISSION_BASE_COST_REALM_SCOPE.value
        }
        val coefficientPerUnit = TerritoryPricing.getPermissionCoefficientPerUnit(permissionKey)
        val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
        val areaCostBeforeDiv = (area / unitSize * coefficientPerUnit).toLong()
        val denominator = if (isPlayerTarget) PricingConfig.PERMISSION_TARGET_PLAYER_DENOMINATOR.value else 1L
        player.sendMessage(
            Translator.tr("community.setting.confirmation.base_cost", String.format("%.2f", baseCostBeforeDiv / 100.0))
                ?: Text.literal("§7  Base: §f${String.format("%.2f", baseCostBeforeDiv / 100.0)}")
        )
        if (areaCostBeforeDiv > 0) {
            player.sendMessage(
                Translator.tr(
                    "community.setting.confirmation.area_cost",
                    String.format("%.2f", area),
                    unitSize.toString(),
                    String.format("%.2f", coefficientPerUnit / 100.0),
                    String.format("%.2f", areaCostBeforeDiv / 100.0)
                ) ?: Text.literal("§7  Area: §f${String.format("%.2f", area)}m² / ${unitSize}m² × ${String.format("%.2f", coefficientPerUnit / 100.0)} = ${String.format("%.2f", areaCostBeforeDiv / 100.0)}")
            )
        }
        if (isPlayerTarget) {
            player.sendMessage(
                Translator.tr("community.setting.confirmation.player_factor", denominator.toString())
                    ?: Text.literal("§7  Player factor: §fx1/$denominator")
            )
        }
        player.sendMessage(
            Translator.tr("community.setting.confirmation.total_cost", String.format("%.2f", cost / 100.0))
                ?: Text.literal("§eCost: §c§l${String.format("%.2f", cost / 100.0)}§r")
        )
    } else {
        player.sendMessage(Translator.tr("community.setting.confirmation.free") ?: Text.literal("§eCost: §a§lFree§r §7(restoring to default)"))
    }

    val assetsAfter = community.getTotalAssets() - cost
    player.sendMessage(Translator.tr("community.setting.confirmation.assets", String.format("%.2f", community.getTotalAssets() / 100.0), String.format("%.2f", assetsAfter / 100.0)) ?: Text.literal("§eCommunity Assets: §f${String.format("%.2f", community.getTotalAssets() / 100.0)} §e-> §f${String.format("%.2f", assetsAfter / 100.0)}"))
}

private fun sendRuleSettingOrderSummary(
    player: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    ruleKey: RuleKey,
    oldValue: Boolean,
    newValue: Boolean,
    area: Double,
    cost: Long
) {
    val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
    val ruleName = getRuleKeyDisplayName(ruleKey)

    player.sendMessage(Translator.tr("community.setting.confirmation.header") ?: Text.literal("§6§l====== SETTING MODIFICATION CONFIRMATION ======§r"))
    player.sendMessage(Translator.tr("community.setting.confirmation.community", communityName) ?: Text.literal("§eCommunity: §f$communityName"))
    player.sendMessage(Translator.tr("community.setting.confirmation.rule", ruleName) ?: Text.literal("§eRule: §f$ruleName"))

    if (scope != null) {
        player.sendMessage(Translator.tr("community.setting.confirmation.layer.scope", scope.scopeName) ?: Text.literal("§eLayer: §fScope - ${scope.scopeName}"))
    } else {
        player.sendMessage(Translator.tr("community.setting.confirmation.layer.region") ?: Text.literal("§eLayer: §fRegion"))
    }

    player.sendMessage(Translator.tr("community.setting.confirmation.target.global") ?: Text.literal("§eTarget: §fAll members"))
    player.sendMessage(Translator.tr("community.setting.confirmation.change", oldValue.toString(), newValue.toString()) ?: Text.literal("§eChange: §c$oldValue §e-> §a$newValue"))
    player.sendMessage(Translator.tr("community.setting.confirmation.area", String.format("%.2f", area)) ?: Text.literal("§eArea: §f${String.format("%.2f", area)} m²"))

    val isManor = community.isManor()
    val baseCostBeforeDiv = when {
        isManor && scope == null -> PricingConfig.PERMISSION_BASE_COST_MANOR_REGION.value
        !isManor && scope == null -> PricingConfig.PERMISSION_BASE_COST_REALM_REGION.value
        isManor -> PricingConfig.PERMISSION_BASE_COST_MANOR_SCOPE.value
        else -> PricingConfig.PERMISSION_BASE_COST_REALM_SCOPE.value
    }
    val coefficientPerUnit = TerritoryPricing.getRuleCoefficientPerUnit(ruleKey)
    val unitSize = PricingConfig.PERMISSION_COEFFICIENT_UNIT_SIZE.value
    val areaCostBeforeDiv = (area / unitSize * coefficientPerUnit).toLong()
    player.sendMessage(
        Translator.tr("community.setting.confirmation.base_cost", String.format("%.2f", baseCostBeforeDiv / 100.0))
            ?: Text.literal("§7  Base: §f${String.format("%.2f", baseCostBeforeDiv / 100.0)}")
    )
    if (areaCostBeforeDiv > 0) {
        player.sendMessage(
            Translator.tr(
                "community.setting.confirmation.area_cost",
                String.format("%.2f", area),
                unitSize.toString(),
                String.format("%.2f", coefficientPerUnit / 100.0),
                String.format("%.2f", areaCostBeforeDiv / 100.0)
            ) ?: Text.literal("§7  Area: §f${String.format("%.2f", area)}m² / ${unitSize}m² × ${String.format("%.2f", coefficientPerUnit / 100.0)} = ${String.format("%.2f", areaCostBeforeDiv / 100.0)}")
        )
    }
    player.sendMessage(
        Translator.tr("community.setting.confirmation.total_cost", String.format("%.2f", cost / 100.0))
            ?: Text.literal("§eCost: §c§l${String.format("%.2f", cost / 100.0)}§r")
    )

    val assetsAfter = community.getTotalAssets() - cost
    player.sendMessage(Translator.tr("community.setting.confirmation.assets", String.format("%.2f", community.getTotalAssets() / 100.0), String.format("%.2f", assetsAfter / 100.0)) ?: Text.literal("§eCommunity Assets: §f${String.format("%.2f", community.getTotalAssets() / 100.0)} §e-> §f${String.format("%.2f", assetsAfter / 100.0)}"))
}

private fun sendInteractiveSettingConfirmation(player: ServerPlayerEntity, regionNumberId: Int) {
    val confirmText = Translator.tr("community.setting.confirmation.confirm_button") ?: Text.literal("§a§l[CONFIRM]§r")
    val cancelText = Translator.tr("community.setting.confirmation.cancel_button") ?: Text.literal("§c§l[CANCEL]§r")

    val confirmButton = confirmText.copy().styled { style ->
        style.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/community confirm_setting $regionNumberId"))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Translator.tr("community.setting.confirmation.confirm_hover")))
    }

    val cancelButton = cancelText.copy().styled { style ->
        style.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/community cancel_setting $regionNumberId"))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Translator.tr("community.setting.confirmation.cancel_hover")))
    }

    val prompt = (Translator.tr("community.setting.confirmation.prompt") ?: Text.literal("§e§l[ACTION REQUIRED]§r"))
        .copy()
        .append(Text.literal(" "))
        .append(confirmButton)
        .append(Text.literal(" "))
        .append(cancelButton)
    player.sendMessage(prompt)
}

private fun notifyFormalMembers(community: Community, server: MinecraftServer, message: Text) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerManager.getPlayer(memberUUID)?.sendMessage(message)
        memberAccount.mail.add(message)
    }
}

