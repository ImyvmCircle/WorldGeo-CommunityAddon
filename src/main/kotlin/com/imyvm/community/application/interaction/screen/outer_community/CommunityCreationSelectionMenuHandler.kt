package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationRenameMenuAnvil
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationSelectionMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.level.ServerPlayer

fun runResetSelectionInCreation(
    player: ServerPlayer,
    currentName: String,
    isManor: Boolean,
    runBack: (ServerPlayer) -> Unit
) {
    PlayerInteractionApi.resetSelection(player)
    player.sendSystemMessage(Translator.tr("community.selection_mode.reset"))
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runToggleSelectionModeInCreation(
    player: ServerPlayer,
    currentName: String,
    isManor: Boolean,
    runBack: (ServerPlayer) -> Unit
) {
    val isSelectionModeEnabled = ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)
    if (isSelectionModeEnabled) {
        PlayerInteractionApi.stopSelection(player)
        player.sendSystemMessage(Translator.tr("community.selection_mode.disabled"))
    } else {
        PlayerInteractionApi.startSelection(player, GeoShapeType.RECTANGLE)
        player.sendSystemMessage(Translator.tr("community.selection_mode.enabled"))
    }
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runSwitchSelectionShapeInCreation(
    player: ServerPlayer,
    currentName: String,
    isManor: Boolean,
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
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runRenameNewCommunityFromSelectionMenu(
    player: ServerPlayer,
    currentName: String,
    isManor: Boolean,
    runBackGrandfatherMenu: (ServerPlayer) -> Unit
) {
    val currentShape = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }
    CommunityCreationRenameMenuAnvil(player, currentName, currentShape, isManor, runBackGrandfatherMenu).open()
}

fun runSwitchCommunityTypeInSelectionMenu(
    player: ServerPlayer,
    currentName: String,
    isManor: Boolean,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(syncId, currentName, !isManor, player, runBack)
    }
}

fun runConfirmCommunityCreationFromSelectionMenu(
    player: ServerPlayer,
    communityName: String,
    isManor: Boolean
) {
    val communityType = if (isManor) "manor" else "realm"
    com.imyvm.community.application.interaction.common.onCreateCommunityRequest(
        player,
        communityType,
        communityName
    )
    player.closeContainer()
}
