package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.development.CommunityDevelopmentService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.math.BigDecimal

fun onDevelopmentPreview(
    player: ServerPlayer,
    community: Community,
    weekKey: String,
    totalBuildingIncome: Long,
    weekBuildingIncome: Long,
    weekActiveMembers: Int,
    totalHabitationMillis: Long,
    averageHabitationMillis: Long
): Int {
    return runCatching {
        val memberCount = community.getMemberUUIDs().size + community.getAdminUUIDs().size + if (community.getOwnerUUID() != null) 1 else 0
        val inputs = CommunityDevelopmentInputs(memberCount, weekActiveMembers, totalBuildingIncome, weekBuildingIncome, totalHabitationMillis, averageHabitationMillis)
        val state = CommunityDevelopmentService.updateDevelopment(community, weekKey, inputs)
        com.imyvm.community.infra.CommunityDatabase.save()
        player.sendSystemMessage(Translator.tr("command.community.development.preview", community.generateCommunityMark(), weekKey, "%.4f".format(state.development), "%.4f".format(state.breakdown.habitationModifier)))
        1
    }.getOrElse { error ->
        player.sendSystemMessage(Translator.tr("command.community.development.failed", error.message ?: error::class.java.simpleName))
        0
    }
}

fun onLandPricePreview(
    player: ServerPlayer,
    community: Community,
    area: String,
    total25HabitationMillis: Long,
    theoreticalBuildingIncome: Long
): Int {
    return runCatching {
        val snapshot = CommunityDevelopmentService.updateLandPrice(community, BigDecimal(area), total25HabitationMillis, theoreticalBuildingIncome)
        com.imyvm.community.infra.CommunityDatabase.save()
        player.sendSystemMessage(Translator.tr("command.community.land_price.preview", community.generateCommunityMark(), snapshot.area.toString(), format(snapshot.totalPrice), format(snapshot.activePrice), format(snapshot.buildingPrice)))
        1
    }.getOrElse { error ->
        player.sendSystemMessage(Translator.tr("command.community.development.failed", error.message ?: error::class.java.simpleName))
        0
    }
}

private fun format(amount: Long): String = "%.2f".format(amount / 100.0)
