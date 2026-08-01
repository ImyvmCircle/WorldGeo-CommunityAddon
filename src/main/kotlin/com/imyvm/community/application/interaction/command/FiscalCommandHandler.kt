package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.fiscal.CommunityFiscalService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

fun onFiscalPolicy(player: ServerPlayer, community: Community, policyName: String): Int {
    val policy = runCatching { CommunityFiscalPolicy.valueOf(policyName.uppercase()) }.getOrNull()
    if (policy == null) {
        player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", "unknown policy"))
        return 0
    }
    return CommunityFiscalService.schedulePolicyForCurrentWeek(community, policy).fold(
        onSuccess = { (cost, nextWeek) ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.policy.success", community.generateCommunityMark(), policy.name, nextWeek, format(cost)))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onFiscalPolicy(player: ServerPlayer, community: Community, policyName: String, currentWeek: String, nextWeek: String, cooldownUntilWeek: String): Int {
    val policy = runCatching { CommunityFiscalPolicy.valueOf(policyName.uppercase()) }.getOrNull()
    if (policy == null) {
        player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", "unknown policy"))
        return 0
    }
    return CommunityFiscalService.schedulePolicy(community, policy, currentWeek, nextWeek, cooldownUntilWeek).fold(
        onSuccess = { cost ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.policy.success", community.generateCommunityMark(), policy.name, nextWeek, format(cost)))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onFiscalObserve(player: ServerPlayer, community: Community, target: UUID, weekKey: String, balance: Long): Int {
    return runCatching {
        CommunityFiscalService.recordObservation(community, target, weekKey, balance, System.currentTimeMillis())
        com.imyvm.community.infra.CommunityDatabase.save()
    }.fold(
        onSuccess = {
            player.sendSystemMessage(Translator.tr("command.community.fiscal.observe.success", community.generateCommunityMark(), target.toString(), weekKey, format(balance)))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onFiscalTaxPreview(player: ServerPlayer, community: Community, weekKey: String): Int {
    val lines = CommunityFiscalService.planCommunityTax(community, weekKey)
    val total = lines.fold(0L) { acc, line -> Math.addExact(acc, line.taxAmount) }
    player.sendSystemMessage(Translator.tr("command.community.fiscal.tax.preview", community.generateCommunityMark(), weekKey, lines.size.toString(), format(total)))
    return 1
}

fun onFiscalWelfarePreview(player: ServerPlayer, community: Community, weekKey: String): Int {
    val rewards = community.buildingState.playerWeekLedgers.mapValues { it.value.settledAmount }.filterValues { it > 0L }
    val plan = CommunityFiscalService.planWelfare(community, weekKey, rewards)
    val total = plan.lines.fold(0L) { acc, line -> Math.addExact(acc, line.actualAmount) }
    player.sendSystemMessage(Translator.tr("command.community.fiscal.welfare.preview", community.generateCommunityMark(), weekKey, plan.lines.size.toString(), format(total)))
    return 1
}

fun onFiscalSettle(player: ServerPlayer, weekKey: String): Int {
    return CommunityFiscalService.settleWeek(weekKey).fold(
        onSuccess = { summary ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.settle.success", weekKey, summary.frozenCommunities.toString(), summary.submittedTaxTransactions.toString(), format(summary.appliedTaxTotal), format(summary.welfareTotal)))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.fiscal.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onFiscalHistory(player: ServerPlayer, community: Community): Int {
    val settlements = CommunityFiscalService.settlementHistory(community).take(5)
    player.sendSystemMessage(Translator.tr("command.community.fiscal.history.header", community.generateCommunityMark(), settlements.size.toString()))
    settlements.forEach { settlement ->
        val tax = settlement.taxLines.fold(0L) { acc, line -> Math.addExact(acc, line.taxAmount) }
        val welfare = settlement.welfareLines.fold(0L) { acc, line -> Math.addExact(acc, line.actualAmount) }
        player.sendSystemMessage(Translator.tr("command.community.fiscal.history.line", settlement.weekKey, settlement.policy.name, settlement.status.name, settlement.taxLines.size.toString(), format(tax), settlement.welfareLines.size.toString(), format(welfare)))
    }
    return 1
}

private fun format(amount: Long): String = "%.2f".format(amount / 100.0)
