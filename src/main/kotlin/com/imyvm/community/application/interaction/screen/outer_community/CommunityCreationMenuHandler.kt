package com.imyvm.community.application.interaction.screen.outer_community

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.entrypoints.screen.ConfirmMenu
import com.imyvm.community.entrypoints.screen.component.ConfirmTaskType
import com.imyvm.community.entrypoints.screen.outer_community.CommunityCreationMenu
import com.imyvm.community.entrypoints.screen.outer_community.CommunityCreationRenameMenuAnvil
import com.imyvm.community.util.Translator
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
    val price = if (isManor) CommunityConfig.PRICE_MANOR else CommunityConfig.PRICE_REALM
    val typeMarkerText = if (isManor) Translator.tr("community.type.manor") else Translator.tr("community.type.realm")
    val typeMarkerStr = typeMarkerText?.string ?: "Unknown-Type"
    val creationCautions = listOf(
        Translator.tr(
            "ui.confirm.create.caution.creation_cost",
            price.toString(),
            typeMarkerStr
        )?.string ?: "Creating this community request will cost $price coins as $typeMarkerStr.",
        Translator.tr(
            "ui.confirm.create.caution.refund"
        ) ?.string ?: "The creation cost will be refunded only when the creation is rejected.",
        Translator.tr(
            "ui.confirm.create.caution.region"
        ) ?.string ?: "The region you selected will be occupied by the community, but you can only modify it after administration approval.",
        Translator.tr(
            "ui.confirm.create.caution.final_check"
        )?.string ?: "Please make sure all the information is correct before confirming."
    )

    CommunityMenuOpener.open(player) { syncId ->
        ConfirmMenu(
            syncId = syncId,
            playerExecutor = player,
            confirmTaskType = ConfirmTaskType.CREATE_COMMUNITY,
            cautions = creationCautions,
            runBack =
            {
                CommunityMenuOpener.open(player) { syncId ->
                    CommunityCreationMenu(
                        syncId = syncId,
                        currentName = communityName,
                        currentShape = geoShapeType,
                        isCurrentCommunityTypeManor = isManor,
                        playerExecutor = player,
                    ) {}
                }
            },
            communityName = communityName,
            communityType = if (isManor) "manor" else "realm",
            shapeName = geoShapeType.toString()
        )
    }
}