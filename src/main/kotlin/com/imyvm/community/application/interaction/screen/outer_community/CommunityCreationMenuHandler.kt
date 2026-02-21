package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationMenu
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationRenameMenuAnvil
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity

fun runRenameNewCommunity(
    player: ServerPlayerEntity,
    currentName: String,
    currentShape: GeoShapeType,
    isManor: Boolean,
    runBackGrandfatherMenu: (ServerPlayerEntity) -> Unit
) {
    CommunityCreationRenameMenuAnvil(player, currentName, currentShape, isManor, runBackGrandfatherMenu).open()
}

fun runSwitchCommunityShape(
    player: ServerPlayerEntity,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    runBack: (ServerPlayerEntity) -> Unit
) {
    val newType = when (geoShapeType) {
        GeoShapeType.CIRCLE -> GeoShapeType.RECTANGLE
        GeoShapeType.RECTANGLE -> GeoShapeType.POLYGON
        GeoShapeType.POLYGON -> GeoShapeType.CIRCLE
        GeoShapeType.UNKNOWN -> GeoShapeType.RECTANGLE
    }

    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationMenu(syncId, communityName, newType, isManor, player, runBack)
    }
}

fun runSwitchCommunityType(
    player: ServerPlayerEntity,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationMenu(syncId, communityName, geoShapeType, !isManor, player, runBack)
    }
}

fun runConfirmCommunityCreation(
    player: ServerPlayerEntity,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean
) {
    val communityType = if (isManor) "manor" else "realm"
    val shapeName = geoShapeType.toString()

    com.imyvm.community.application.interaction.common.onCreateCommunityRequest(
        player,
        communityType,
        communityName,
        shapeName
    )

    player.closeHandledScreen()
}