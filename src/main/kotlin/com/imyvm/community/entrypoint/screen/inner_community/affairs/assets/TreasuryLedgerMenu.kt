package com.imyvm.community.entrypoint.screen.inner_community.affairs.assets

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.text.SimpleDateFormat
import java.util.*

class TreasuryLedgerMenu(
    syncId: Int,
    val community: Community,
    val playerExecutor: ServerPlayerEntity,
    page: Int = 0,
    val runBack: (ServerPlayerEntity) -> Unit
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.treasury.ledger.title"),
    page = page,
    runBack = runBack
) {

    private val itemsPerPage = 45
    private val startSlot = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        val entries = buildLedgerEntries()
        renderList(entries, itemsPerPage, startSlot) { (isIncome, turnover), slot, _ ->
            val amountFormatted = "%.2f".format(turnover.amount / 100.0)
            val timeStr = dateFormat.format(Date(turnover.timestamp))
            val sourceKey = "community.treasury.source.${turnover.source.name.lowercase()}"
            val sourceText = Translator.tr(sourceKey)?.string ?: turnover.source.name
            val descText = if (turnover.descriptionKey != null) {
                Translator.tr(turnover.descriptionKey, *turnover.descriptionArgs.toTypedArray())?.string
                    ?: turnover.descriptionKey
            } else {
                null
            }
            val lore = buildList {
                add(Translator.tr("ui.treasury.ledger.time", timeStr) ?: Text.of("§7$timeStr"))
                add(Translator.tr("ui.treasury.ledger.source", sourceText) ?: Text.of("§7$sourceText"))
                if (descText != null) {
                    add(Translator.tr("ui.treasury.ledger.desc", descText) ?: Text.of("§7$descText"))
                }
            }
            val icon = if (isIncome) ItemStack(Items.GOLD_INGOT) else ItemStack(Items.RED_STAINED_GLASS_PANE)
            addButton(
                slot = slot,
                itemStack = getLoreButton(icon, lore),
                name = (if (isIncome)
                    Translator.tr("ui.treasury.ledger.income", amountFormatted)
                else
                    Translator.tr("ui.treasury.ledger.expenditure", amountFormatted))?.string
                    ?: (if (isIncome) "+\$$amountFormatted" else "-\$$amountFormatted")
            ) {}
        }
        handlePageWithSize(entries.size, itemsPerPage)
    }

    private fun buildLedgerEntries(): List<Pair<Boolean, Turnover>> {
        val entries = mutableListOf<Pair<Boolean, Turnover>>()
        community.member.values.forEach { memberAccount ->
            memberAccount.turnover.forEach { entries.add(true to it) }
        }
        community.communityIncome.forEach { entries.add(true to it) }
        community.expenditures.forEach { entries.add(false to it) }
        entries.sortByDescending { it.second.timestamp }
        return entries
    }

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            TreasuryLedgerMenu(syncId, community, playerExecutor, newPage, runBack)
        }
    }
}
