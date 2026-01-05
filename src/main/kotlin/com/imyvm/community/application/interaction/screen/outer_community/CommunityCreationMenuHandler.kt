package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.common.onCreateCommunity
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.inter.screen.ConfirmMenu
import com.imyvm.community.inter.screen.component.ConfirmTaskType
import com.imyvm.community.inter.screen.outer_community.CommunityCreationMenu
import com.imyvm.community.inter.screen.outer_community.CommunityCreationRenameMenuAnvil
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
){
    val newType = when(geoShapeType){
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
){
    CommunityMenuOpener.open(player) { syncId ->
        CommunityCreationMenu(syncId, communityName, geoShapeType, !isManor, player, runBack)
    }
}

fun runConfirmCommunityCreation(
    player: ServerPlayerEntity,
    communityName: String,
    geoShapeType: GeoShapeType,
    isManor: Boolean
){
    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.CREATE_COMMUNITY,
            cautions = listOf(
                ""
            ),
            runExecutor = { onCreateCommunity(
                player,
                if (isManor) "manor" else "realm",
                communityName,
                geoShapeType.toString()
            ) },
            runBack = { CommunityMenuOpener.open(player) { syncId ->
                CommunityCreationMenu(
                    syncId = syncId,
                    currentName = communityName,
                    currentShape = geoShapeType,
                    isCurrentCommunityTypeManor = isManor,
                    playerExecutor = player,
                ) {} }
            },
            communityName = communityName,
            communityType = if (isManor) "manor" else "realm",
            shapeName = geoShapeType.toString()
        )
    }
}