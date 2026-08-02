package com.imyvm.community.application.weekly

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.townbuilding.CommunityBuildingService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.fiscal.CommunityFiscalLineStatus
import com.imyvm.community.domain.model.weekly.CommunityWeeklyReport
import com.imyvm.community.domain.model.weekly.WeeklyReportAudience
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.infra.weekly.CommunityWeeklyReportStore
import com.imyvm.community.util.Translator
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import java.util.Locale
import java.util.UUID

object CommunityWeeklyReportService {
    fun registerLoginNotice() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> notifyUnread(handler.player) }
    }

    fun publishWeek(weekKey: String) {
        var communityCount = 0
        var playerReportCount = 0
        var taxTotal = 0L
        var welfareTotal = 0L
        var buildingTotal = 0L
        for (community in CommunityDatabase.communities) {
            val regionId = community.regionNumberId ?: continue
            communityCount++
            val fiscal = community.fiscalState.settlements.firstOrNull { it.weekKey == weekKey }
            val buildingIncome = community.buildingState.communityWeekLedgers.firstOrNull { it.weekPeriodId == weekKey }?.settledAmount ?: 0L
            val tax = fiscal?.taxLines?.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }?.sumOf { it.taxAmount } ?: 0L
            val welfare = fiscal?.welfareLines?.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }?.sumOf { it.actualAmount } ?: 0L
            buildingTotal = Math.addExact(buildingTotal, buildingIncome)
            taxTotal = Math.addExact(taxTotal, tax)
            welfareTotal = Math.addExact(welfareTotal, welfare)
            playerReportCount += publishPlayerReports(community, weekKey)
            publishManagerReports(community, weekKey, buildingIncome, tax, welfare)
        }
        publishOpReport(weekKey, communityCount, playerReportCount, buildingTotal, taxTotal, welfareTotal)
    }

    fun notifyUnread(player: ServerPlayer) {
        val count = CommunityWeeklyReportStore.unreadCount(player.uuid, isOp(player))
        if (count > 0) sendReportNotice(player, "community.weekly_report.login", count.toString())
    }

    fun sendList(player: ServerPlayer): Int {
        val reports = CommunityWeeklyReportStore.listFor(player.uuid, isOp(player))
        if (reports.isEmpty()) {
            player.sendSystemMessage(Translator.tr("community.weekly_report.list.empty"))
            return 0
        }
        player.sendSystemMessage(Translator.tr("community.weekly_report.list.header", reports.size.toString()))
        reports.take(10).forEachIndexed { index, report ->
            val status = if (report.isReadBy(player.uuid)) Translator.tr("community.weekly_report.status.read").string else Translator.tr("community.weekly_report.status.unread").string
            val entry = Translator.tr("community.weekly_report.list.entry", (index + 1).toString(), status, audienceName(report.audience), report.weekKey, report.title).copy().withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand("/community report read ${index + 1}"))
                    .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.weekly_report.list.entry.hover")))
            }
            player.sendSystemMessage(entry)
        }
        player.sendSystemMessage(Translator.tr("community.weekly_report.list.hint"))
        return 1
    }

    fun sendRead(player: ServerPlayer, index: Int): Int {
        val report = CommunityWeeklyReportStore.listFor(player.uuid, isOp(player)).getOrNull(index - 1)
        if (report == null) {
            player.sendSystemMessage(Translator.tr("community.weekly_report.read.not_found"))
            return 0
        }
        player.sendSystemMessage(Translator.tr("community.weekly_report.read.header", audienceName(report.audience), report.weekKey, report.title))
        report.lines.forEach { player.sendSystemMessage(Component.literal(it)) }
        CommunityWeeklyReportStore.markRead(report, player.uuid)
        return 1
    }

    private fun publishPlayerReports(community: Community, weekKey: String): Int {
        val fiscal = community.fiscalState.settlements.firstOrNull { it.weekKey == weekKey }
        val building = community.buildingState.playerWeekLedgers.filterValues { it.weekPeriodId == weekKey }.mapValues { it.value.settledAmount }
        val tax = fiscal?.taxLines?.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }?.associate { it.playerUuid to it.taxAmount } ?: emptyMap()
        val welfare = fiscal?.welfareLines?.filter { it.status == CommunityFiscalLineStatus.SUCCEEDED }?.associate { it.playerUuid to it.actualAmount } ?: emptyMap()
        val recipients = (community.member.keys + building.keys + tax.keys + welfare.keys).distinct()
        recipients.forEach { uuid ->
            upsertAndNotify(CommunityWeeklyReport(
                id = "player:$weekKey:${community.regionNumberId}:$uuid",
                recipientUuid = uuid,
                audience = WeeklyReportAudience.PLAYER,
                weekKey = weekKey,
                title = community.generateCommunityMark(),
                lines = listOf(
                    Translator.tr("community.weekly_report.player.building", money(building[uuid] ?: 0L)).string,
                    Translator.tr("community.weekly_report.player.tax", money(tax[uuid] ?: 0L)).string,
                    Translator.tr("community.weekly_report.player.welfare", money(welfare[uuid] ?: 0L)).string
                )
            ))
        }
        return recipients.size
    }

    private fun publishManagerReports(community: Community, weekKey: String, buildingIncome: Long, tax: Long, welfare: Long) {
        val recipients = community.member.filterValues { it.basicRoleType == MemberRoleType.OWNER || it.basicRoleType == MemberRoleType.ADMIN }.keys
        val ranking = community.buildingState.playerWeekLedgers.entries
            .filter { it.value.weekPeriodId == weekKey }
            .sortedByDescending { it.value.settledAmount }
            .take(5)
            .mapIndexed { index, entry -> Translator.tr("community.weekly_report.manager.rank", (index + 1).toString(), entry.key.toString(), money(entry.value.settledAmount)).string }
        val baseLines = listOf(
            Translator.tr("community.weekly_report.manager.treasury", money(community.getTotalAssets())).string,
            Translator.tr("community.weekly_report.manager.building", money(buildingIncome)).string,
            Translator.tr("community.weekly_report.manager.tax", money(tax)).string,
            Translator.tr("community.weekly_report.manager.welfare", money(welfare)).string,
            Translator.tr("community.weekly_report.manager.pending", community.buildingState.pendingPayouts.size.toString()).string
        ) + (if (ranking.isEmpty()) listOf(Translator.tr("community.weekly_report.manager.rank.empty").string) else ranking)
        recipients.forEach { uuid ->
            upsertAndNotify(CommunityWeeklyReport(
                id = "manager:$weekKey:${community.regionNumberId}:$uuid",
                recipientUuid = uuid,
                audience = WeeklyReportAudience.MANAGER,
                weekKey = weekKey,
                title = community.generateCommunityMark(),
                lines = baseLines
            ))
        }
    }

    private fun publishOpReport(weekKey: String, communityCount: Int, playerReports: Int, building: Long, tax: Long, welfare: Long) {
        val unresolved = AccountSubsystem.runtimeOrNull()?.store?.scanUnresolved(null, 1000)?.join()?.items?.size ?: 0
        upsertAndNotify(CommunityWeeklyReport(
            id = "op:$weekKey",
            recipientUuid = null,
            audience = WeeklyReportAudience.OP,
            weekKey = weekKey,
            title = Translator.tr("community.weekly_report.op.title").string,
            lines = listOf(
                Translator.tr("community.weekly_report.op.communities", communityCount.toString()).string,
                Translator.tr("community.weekly_report.op.player_reports", playerReports.toString()).string,
                Translator.tr("community.weekly_report.op.building", money(building)).string,
                Translator.tr("community.weekly_report.op.tax", money(tax)).string,
                Translator.tr("community.weekly_report.op.welfare", money(welfare)).string,
                Translator.tr("community.weekly_report.op.unresolved", unresolved.toString()).string
            )
        ))
    }

    private fun upsertAndNotify(report: CommunityWeeklyReport) {
        if (!CommunityWeeklyReportStore.upsert(report)) return
        val server = WorldGeoCommunityAddon.server ?: return
        if (report.audience == WeeklyReportAudience.OP && report.recipientUuid == null) {
            server.playerList.players.filter(::isOp).forEach { sendReportNotice(it, "community.weekly_report.added", "1") }
            return
        }
        val target = report.recipientUuid?.let { server.playerList.getPlayer(it) } ?: return
        sendReportNotice(target, "community.weekly_report.added", "1")
    }

    private fun sendReportNotice(player: ServerPlayer, key: String, count: String) {
        val button = Translator.tr("community.weekly_report.notice.button").copy().withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/community report list"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.weekly_report.notice.hover")))
        }
        player.sendSystemMessage(Component.empty().append(Translator.tr(key, count)).append(button))
    }

    private fun isOp(player: ServerPlayer): Boolean = Commands.LEVEL_GAMEMASTERS.check(player.permissions())
    private fun money(value: Long): String = CommunityBuildingService.formatMoney(value)
    private fun audienceName(audience: WeeklyReportAudience): String = Translator.tr("community.weekly_report.audience.${audience.name.lowercase(Locale.ROOT)}").string
}
