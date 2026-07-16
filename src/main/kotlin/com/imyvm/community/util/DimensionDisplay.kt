package com.imyvm.community.util

import com.imyvm.community.domain.policy.territory.TerritoryPricing
import net.minecraft.server.level.ServerPlayer

fun getPlayerDimensionId(player: ServerPlayer): String {
    return TerritoryPricing.normalizeDimensionId(player.level().dimension().toString())
}

fun getColoredDimensionName(dimensionId: String): String {
    val translated = Translator.tr(TerritoryPricing.getDimensionDisplayKey(dimensionId)).string
    return translated.ifEmpty { TerritoryPricing.normalizeDimensionId(dimensionId) }
}
