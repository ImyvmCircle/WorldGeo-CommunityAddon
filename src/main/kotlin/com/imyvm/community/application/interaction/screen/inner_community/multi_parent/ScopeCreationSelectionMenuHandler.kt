package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationRenameMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.network.ServerPlayerEntity

fun runToggleSelectionModeInScopeCreation(
    player: ServerPlayerEntity,
    community: Community,
    currentName: String,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val isSelectionModeEnabled = ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)
    if (isSelectionModeEnabled) {
        PlayerInteractionApi.stopSelection(player)
        player.sendMessage(Translator.tr("community.selection_mode.disabled"))
    } else {
        PlayerInteractionApi.startSelection(player, GeoShapeType.RECTANGLE)
        player.sendMessage(Translator.tr("community.selection_mode.enabled"))
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, currentName, player, runBack)
    }
}

fun runSwitchScopeShapeInCreation(
    player: ServerPlayerEntity,
    community: Community,
    currentName: String,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val currentShape = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }
    val newShape = when (currentShape) {
        GeoShapeType.RECTANGLE -> GeoShapeType.POLYGON
        GeoShapeType.POLYGON -> GeoShapeType.CIRCLE
        GeoShapeType.CIRCLE -> GeoShapeType.RECTANGLE
        GeoShapeType.UNKNOWN -> GeoShapeType.RECTANGLE
    }
    PlayerInteractionApi.setSelectionShape(player, newShape)
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, currentName, player, runBack)
    }
}

fun runRenameNewScopeFromSelection(
    player: ServerPlayerEntity,
    community: Community,
    currentName: String,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    CommunityScopeCreationRenameMenuAnvil(player, community, currentName, runBackGrandfatherMenu).open()
}

fun runConfirmScopeCreationFromSelection(
    player: ServerPlayerEntity,
    community: Community,
    scopeName: String
) {
    val currentShape = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }
    player.closeHandledScreen()
    onCreateScopeRequestFromSelection(player, community, scopeName, currentShape)
}

private fun onCreateScopeRequestFromSelection(
    player: ServerPlayerEntity,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
) {
    runConfirmScopeCreation(player, community, scopeName, geoShapeType)
}
