package com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.component.getLoreButton
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.element.TargetSettingMenu
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PermissionKey
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.mojang.authlib.GameProfile
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

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
        loreLines.add(Text.literal(hasPermission.toString()))
        scope?.let {
            loreLines.add(Text.literal(scope.scopeName))
        }
        playerObject?.let {
            loreLines.add(Text.literal(playerObject.name))
        }
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
    togglePermissionSettingInRegion(playerExecutor, community, scope, playerObject, permissionKey)
    refreshSettingInMenu(playerExecutor, community, scope, playerObject, runBack)
}

private fun togglePermissionSettingInRegion(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    targetPlayer: GameProfile?,
    permissionKey: PermissionKey
) {
    val region = community.getRegion() ?: return
    val targetPlayerId = targetPlayer?.id

    val newValueStr = (!RegionDataApi.getPermissionValueRegion(region, scope, targetPlayerId, permissionKey)).toString()

    val permissionKeyStr = permissionKey.toString()
    val targetPlayerIdStr = targetPlayerId?.toString()

    if (scope == null) {
        setNewRegionSetting(
            playerExecutor, region, permissionKeyStr, newValueStr, targetPlayerIdStr
        )
    } else {
        setNewScopeSetting(
            playerExecutor, region, scope.scopeName, permissionKeyStr, newValueStr, targetPlayerIdStr
        )
    }
}

private fun refreshSettingInMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope?,
    playerProfile: GameProfile?,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        TargetSettingMenu(syncId, playerExecutor, community, scope, playerProfile, runBack)
    }
}

private fun setNewRegionSetting(
    playerExecutor: ServerPlayerEntity,
    region: Region,
    permissionKeyStr: String,
    newValueStr: String,
    targetPlayerIdStr: String?
) {
    PlayerInteractionApi.removeSettingRegion(
        playerExecutor, region, permissionKeyStr, targetPlayerIdStr
    )
    PlayerInteractionApi.addSettingRegion(
        playerExecutor, region, permissionKeyStr, newValueStr, targetPlayerIdStr
    )
}

private fun setNewScopeSetting(
    playerExecutor: ServerPlayerEntity,
    region: Region,
    scopeName: String,
    permissionKeyStr: String,
    newValueStr: String,
    targetPlayerIdStr: String?
) {
    PlayerInteractionApi.removeSettingScope(
        playerExecutor, region, scopeName, permissionKeyStr, targetPlayerIdStr
    )
    PlayerInteractionApi.addSettingScope(
        playerExecutor, region, scopeName, permissionKeyStr, newValueStr, targetPlayerIdStr
    )
}