package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.development.CommunityDevelopmentService
import com.imyvm.community.application.helper.CommunityBackgroundTasks
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.util.Collections

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

fun onDevelopmentStatus(player: ServerPlayer, community: Community): Int {
    val state = community.developmentState
    player.sendSystemMessage(Translator.tr("command.community.development.status.header", community.generateCommunityMark(), state.weekKey.ifBlank { "-" }))
    player.sendSystemMessage(Translator.tr("command.community.development.status.score", "%.4f".format(state.development), "%.4f".format(state.breakdown.building), "%.4f".format(state.breakdown.population), "%.4f".format(state.breakdown.habitation), "%.4f".format(state.breakdown.habitationModifier)))
    player.sendSystemMessage(Translator.tr("command.community.development.status.inputs", state.inputs.memberCount.toString(), state.inputs.weekActiveMemberCount.toString(), format(state.inputs.weekTheoreticalBuildingIncome), formatMillis(state.inputs.averageHabitationMillis)))
    player.sendSystemMessage(Translator.tr("command.community.development.status.updated", formatTime(state.updatedAtMillis)))
    return 1
}

fun onDevelopmentRefresh(player: ServerPlayer, community: Community): Int {
    val regionId = community.regionNumberId ?: return failed(player, "community region not bound")
    val key = "development:$regionId"
    if (!activeRefreshes.add(key)) {
        player.sendSystemMessage(Translator.tr("command.community.development.refresh.running", community.generateCommunityMark()))
        return 0
    }
    val playerUuid = player.uuid
    val server = player.level().server
    player.sendSystemMessage(Translator.tr("command.community.development.refresh.started", community.generateCommunityMark()))
    CommunityBackgroundTasks.supply { CommunityDevelopmentService.calculateDevelopmentFromCurrentState(community) }
        .whenComplete { result, error ->
            server.execute {
                activeRefreshes.remove(key)
                val online = server.playerList.getPlayer(playerUuid)
                if (error != null) {
                    online?.sendSystemMessage(Translator.tr("command.community.development.failed", error.message ?: error::class.java.simpleName))
                    return@execute
                }
                result.fold(
                    onSuccess = { state ->
                        community.developmentState = state
                        CommunityDatabase.save()
                        online?.sendSystemMessage(Translator.tr("command.community.development.refresh.success", community.generateCommunityMark(), state.weekKey, "%.4f".format(state.development)))
                    },
                    onFailure = { failure ->
                        online?.sendSystemMessage(Translator.tr("command.community.development.failed", failure.message ?: failure::class.java.simpleName))
                    }
                )
            }
        }
    return 1
}

fun onLandPriceStatus(player: ServerPlayer, community: Community): Int {
    val snapshot = community.developmentState.landPrice
    if (snapshot == null) {
        player.sendSystemMessage(Translator.tr("command.community.land_price.status.empty", community.generateCommunityMark()))
        return 1
    }
    player.sendSystemMessage(Translator.tr("command.community.land_price.status", community.generateCommunityMark(), snapshot.area.toString(), format(snapshot.totalPrice), format(snapshot.activePrice), format(snapshot.buildingPrice), format(snapshot.total25HabitationMillis)))
    return 1
}

fun onLandPriceRefresh(player: ServerPlayer, community: Community): Int {
    val regionId = community.regionNumberId ?: return failed(player, "community region not bound")
    val key = "land-price:$regionId"
    if (!activeRefreshes.add(key)) {
        player.sendSystemMessage(Translator.tr("command.community.land_price.refresh.running", community.generateCommunityMark()))
        return 0
    }
    val playerUuid = player.uuid
    val server = player.level().server
    player.sendSystemMessage(Translator.tr("command.community.land_price.refresh.started", community.generateCommunityMark()))
    CommunityBackgroundTasks.supply { CommunityDevelopmentService.calculateRegionLandPrice(community) }
        .whenComplete { result, error ->
            server.execute {
                activeRefreshes.remove(key)
                val online = server.playerList.getPlayer(playerUuid)
                if (error != null) {
                    online?.sendSystemMessage(Translator.tr("command.community.development.failed", error.message ?: error::class.java.simpleName))
                    return@execute
                }
                result.fold(
                    onSuccess = { snapshot ->
                        community.developmentState.landPrice = snapshot
                        CommunityDatabase.save()
                        online?.sendSystemMessage(Translator.tr("command.community.land_price.refresh.success", community.generateCommunityMark(), format(snapshot.totalPrice), snapshot.area.toString()))
                    },
                    onFailure = { failure ->
                        online?.sendSystemMessage(Translator.tr("command.community.development.failed", failure.message ?: failure::class.java.simpleName))
                    }
                )
            }
        }
    return 1
}

private fun failed(player: ServerPlayer, reason: String): Int {
    player.sendSystemMessage(Translator.tr("command.community.development.failed", reason))
    return 0
}

private val activeRefreshes = Collections.synchronizedSet(mutableSetOf<String>())

private fun formatMillis(value: Long): String = "%.2f".format(value / 3_600_000.0)

private fun formatTime(value: Long): String = if (value <= 0L) "-" else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").format(Instant.ofEpochMilli(value).atZone(ZoneId.of("Asia/Shanghai")))

private fun format(amount: Long): String = "%.2f".format(amount / 100.0)
