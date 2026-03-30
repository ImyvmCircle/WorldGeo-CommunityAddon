package com.imyvm.community.application.interaction.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationMenu
import com.imyvm.community.entrypoint.screen.inner_community.multi_parent.CommunityScopeCreationRenameMenuAnvil
import com.imyvm.community.util.SelectionReturnContext
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.level.ServerPlayer

fun runResetSelectionInScopeCreation(
    player: ServerPlayer,
    community: Community,
    currentName: String,
    runBack: (ServerPlayer) -> Unit
) {
    PlayerInteractionApi.resetSelection(player)
    player.sendSystemMessage(Translator.tr("community.selection_mode.reset"))
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, currentName, player, runBack)
    }
}

fun runToggleSelectionModeInScopeCreation(
    player: ServerPlayer,
    community: Community,
    currentName: String,
    runBack: (ServerPlayer) -> Unit
) {
    val isInNormalSelectionMode = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape is HypotheticalShape.Normal
    if (isInNormalSelectionMode) {
        PlayerInteractionApi.stopSelection(player)
        SelectionReturnContext.clearContext(player.uuid)
        player.sendSystemMessage(Translator.tr("community.selection_mode.disabled"))
    } else {
        if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
            PlayerInteractionApi.stopSelection(player)
            SelectionReturnContext.clearContext(player.uuid)
        }
        PlayerInteractionApi.startSelection(player, GeoShapeType.RECTANGLE)
        community.regionNumberId?.let { id ->
            SelectionReturnContext.setCreateContext(player.uuid, id, currentName)
        }
        player.sendSystemMessage(Translator.tr("community.selection_mode.enabled"))
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityScopeCreationMenu(syncId, community, currentName, player, runBack)
    }
}

fun runSwitchScopeShapeInCreation(
    player: ServerPlayer,
    community: Community,
    currentName: String,
    runBack: (ServerPlayer) -> Unit
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
    player: ServerPlayer,
    community: Community,
    currentName: String,
    runBackGrandfatherMenu: (ServerPlayer) -> Unit
) {
    CommunityScopeCreationRenameMenuAnvil(player, community, currentName, runBackGrandfatherMenu).open()
}

fun runConfirmScopeCreationFromSelection(
    player: ServerPlayer,
    community: Community,
    scopeName: String
) {
    val currentShape = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }
    player.closeContainer()
    onCreateScopeRequestFromSelection(player, community, scopeName, currentShape)
}

private fun onCreateScopeRequestFromSelection(
    player: ServerPlayer,
    community: Community,
    scopeName: String,
    geoShapeType: GeoShapeType
) {
    runConfirmScopeCreation(player, community, scopeName, geoShapeType)
}
