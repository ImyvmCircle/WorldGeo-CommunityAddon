package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationRenameMenuAnvil
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationSelectionMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.network.ServerPlayerEntity

fun runResetSelectionInCreation(
    player: ServerPlayerEntity,
    currentName: String,
    isManor: Boolean,
    runBack: (ServerPlayerEntity) -> Unit
) {
    PlayerInteractionApi.resetSelection(player)
    player.sendMessage(Translator.tr("community.selection_mode.reset"))
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runToggleSelectionModeInCreation(
    player: ServerPlayerEntity,
    currentName: String,
    isManor: Boolean,
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
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runSwitchSelectionShapeInCreation(
    player: ServerPlayerEntity,
    currentName: String,
    isManor: Boolean,
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
        CommunityCreationSelectionMenu(syncId, currentName, isManor, player, runBack)
    }
}

fun runRenameNewCommunityFromSelectionMenu(
    player: ServerPlayerEntity,
    currentName: String,
    isManor: Boolean,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    val currentShape = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }
    CommunityCreationRenameMenuAnvil(player, currentName, currentShape, isManor, runBackGrandfatherMenu).open()
}

fun runSwitchCommunityTypeInSelectionMenu(
    player: ServerPlayerEntity,
    currentName: String,
    isManor: Boolean,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationSelectionMenu(syncId, currentName, !isManor, player, runBack)
    }
}

fun runConfirmCommunityCreationFromSelectionMenu(
    player: ServerPlayerEntity,
    communityName: String,
    isManor: Boolean
) {
    val communityType = if (isManor) "manor" else "realm"
    com.imyvm.community.application.interaction.common.onCreateCommunityRequest(
        player,
        communityType,
        communityName
    )
    player.closeHandledScreen()
}
