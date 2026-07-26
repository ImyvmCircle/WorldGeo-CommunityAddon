package com.imyvm.community.entrypoint.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenBuildingClaimConfirmMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.snapshotBuildingReward
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.util.Translator
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

class BuildingMenu(
    syncId: Int,
    val player: ServerPlayer,
    val community: Community,
    val runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.building.title"),
    runBack = runBack
) {
    private val snapshot = snapshotBuildingReward(community, player.uuid)

    init {
        addSummaryButton()
        addClaimButton()
    }

    private fun addSummaryButton() {
        val lore = buildList {
            addLine("ui.building.summary.lore.total_blocks", snapshot.totalBlocks)
            addLine("ui.building.summary.lore.claimed_blocks", snapshot.claimedBlocks)
            addLine("ui.building.summary.lore.claimable_blocks", snapshot.claimableBlocks)
            addLine("ui.building.summary.lore.block_value", formatAmount(snapshot.blockValue))
            addLine("ui.building.summary.lore.claimable_amount", formatAmount(snapshot.claimableAmount))
            add(Translator.tr("ui.building.summary.lore.last_period", snapshot.lastClaimedPeriodId ?: Translator.trStringOrFallback("ui.building.summary.lore.last_period.none", "Never")))
        }

        addButton(
            slot = 12,
            itemStack = getLoreButton(ItemStack(Items.BOOK), lore),
            name = Translator.tr("ui.building.summary").string
        ) {}
    }

    private fun addClaimButton() {
        val lore = buildList {
            addLine("ui.building.claim.lore.amount", formatAmount(snapshot.claimableAmount))
            addLine("ui.building.claim.lore.blocks", snapshot.claimableBlocks)
            addLine("ui.building.claim.lore.limit", CommunityConfig.BUILDING_REWARD_DEFAULT_BLOCK_LIMIT.value)
        }
        val claimable = snapshot.claimableAmount > 0L

        addButton(
            slot = 14,
            itemStack = getLoreButton(
                ItemStack(if (claimable) Items.GOLD_INGOT else Items.BARRIER),
                lore
            ),
            name = Translator.tr(if (claimable) "ui.building.claim" else "ui.building.claim.disabled").string
        ) {
            if (claimable) {
                runOpenBuildingClaimConfirmMenu(player, community, runBack)
            }
        }
    }

    private fun MutableList<Component>.addLine(key: String, value: Any) {
        add(Translator.tr(key, value))
    }

    private fun formatAmount(amount: Long): String = String.format(Locale.ROOT, "%.2f", amount / 100.0)
}
