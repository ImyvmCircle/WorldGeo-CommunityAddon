package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationMenu
import com.imyvm.community.entrypoint.screen.outer_community.CommunityCreationRenameMenuAnvil
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.level.ServerPlayer

fun runRenameNewCommunity(
    player: ServerPlayer,
    currentName: String,
    currentShape: GeoShapeType,
    isManor: Boolean,
    runBackGrandfatherMenu: (ServerPlayer) -> Unit
) {
    CommunityCreationRenameMenuAnvil(player, currentName, currentShape, isManor, runBackGrandfatherMenu).open()
}

fun runSwitchCommunityShape(
    player: ServerPlayer,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    runBack: (ServerPlayer) -> Unit
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
    player: ServerPlayer,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean,
    runBack: (ServerPlayer) -> Unit
) {
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationMenu(syncId, communityName, geoShapeType, !isManor, player, runBack)
    }
}

fun runConfirmCommunityCreation(
    player: ServerPlayer,
    communityName: String,
    geoShapeType: GeoShapeType,
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