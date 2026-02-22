package com.imyvm.community.application.interaction.screen.helper

import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipCreation
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import net.minecraft.server.network.ServerPlayerEntity

fun generateCreationError(
    currentName: String,
    currentShape: GeoShapeType,
    isCurrentCommunityTypeManor: Boolean,
    playerEntity: ServerPlayerEntity
): String {
    val errors = validateBasicInfo(currentName, currentShape, playerEntity) { name ->
        CommunityDatabase.communities.any { it.getRegion()?.name == name }
    }

    val typeStr = if (isCurrentCommunityTypeManor) "manor" else "realm"
    val price = if (isCurrentCommunityTypeManor)
        PricingConfig.PRICE_MANOR.value
    else
        PricingConfig.PRICE_REALM.value

    if (EconomyMod.data.getOrCreate(playerEntity).money < price) {
        errors.add(Translator.tr("ui.create.error.money_$typeStr")?.string ?: "NotEnoughMoney${typeStr.replaceFirstChar { it.uppercase() }}")
    }

    if (!checkPlayerMembershipCreation(playerEntity, typeStr)) {
        errors.add(Translator.tr("ui.create.error.already_in_community_$typeStr")?.string ?: "AlreadyInCommunity${typeStr.replaceFirstChar { it.uppercase() }}")
    }

    return if (errors.isEmpty()) "" else " ERRORS:" + errors.joinToString(";")
}

fun generateScopeCreationError(
    currentName: String,
    currentShape: GeoShapeType,
    playerEntity: ServerPlayerEntity,
    existingScopeNames: Set<String>
): String {
    val errors = validateBasicInfo(currentName, currentShape, playerEntity) { name ->
        existingScopeNames.any { it.equals(name, ignoreCase = true) }
    }

    return if (errors.isEmpty()) "" else " ERRORS:" + errors.joinToString(";")
}

private fun validateBasicInfo(
    currentName: String,
    currentShape: GeoShapeType,
    playerEntity: ServerPlayerEntity,
    isNameDuplicate: (String) -> Boolean
): MutableList<String> {
    val errors = mutableListOf<String>()

    if (currentName.isBlank()) {
        errors.add(Translator.tr("ui.create.error.name_empty")?.string ?: "NameEmpty")
    } else if (isNameDuplicate(currentName)) {
        errors.add(Translator.tr("ui.create.error.name_duplicated")?.string ?: "NameDuplicated")
    }

    if (currentShape == GeoShapeType.UNKNOWN) {
        errors.add(Translator.tr("ui.create.error.shape_unknown")?.string ?: "ShapeUnknown")
    }

    val points = ImyvmWorldGeo.pointSelectingPlayers[playerEntity.uuid]
    when (currentShape) {
        GeoShapeType.POLYGON -> {
            if (points == null || points.size < 3) {
                errors.add(Translator.tr("ui.create.error.shape_polygon")?.string ?: "PolygonNeed3Points+Selected")
            }
        }
        GeoShapeType.CIRCLE, GeoShapeType.RECTANGLE -> {
            if (points == null || points.size < 2) {
                errors.add(Translator.tr("ui.create.error.shape_not_enough")?.string ?: "Need2Points+Selected")
            }
        }
        else -> {}
    }

    return errors
}
