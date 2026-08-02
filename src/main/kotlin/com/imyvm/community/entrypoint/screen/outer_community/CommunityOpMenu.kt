package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.screen.inner_community.runOpenCommunityBuildingPoolMenu
import com.imyvm.community.application.interaction.screen.outer_community.runBackOrRefreshMainMenu
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class CommunityOpMenu(
    syncId: Int,
    private val player: ServerPlayer
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.op.title"),
    runBack = { runBackOrRefreshMainMenu(it) }
) {
    init {
        addButton(10, Translator.tr("ui.op.button.building_pool").string, Items.BRICKS) {
            runOpenCommunityBuildingPoolMenu(it, 0) { p ->
                com.imyvm.community.application.interaction.screen.CommunityMenuOpener.open(p) { s -> CommunityOpMenu(s, p) }
            }
        }
        addButton(12, Translator.tr("ui.op.button.money_issues").string, Items.GOLD_INGOT) { p ->
            p.closeContainer()
            sendMoneyIssuesAction(p)
        }
        addButton(14, Translator.tr("ui.op.button.building_pool_add").string, Items.WRITABLE_BOOK) { p ->
            p.closeContainer()
            sendBuildingPoolAddAction(p)
        }
    }

    private fun sendMoneyIssuesAction(player: ServerPlayer) {
        val button = Translator.tr("ui.op.message.money_issues.button").copy().withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/community money issues"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("ui.op.message.money_issues.hover")))
        }
        player.sendSystemMessage(Component.empty().append(Translator.tr("ui.op.message.money_issues")).append(button))
    }

    private fun sendBuildingPoolAddAction(player: ServerPlayer) {
        val command = "/community building pool add <blockId> <capacityCost> <reward> [linkedBlockId,linkedBlockId]"
        val button = Translator.tr("command.community.building.pool.add.button").copy().withStyle { style ->
            style.withClickEvent(ClickEvent.SuggestCommand(command))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("command.community.building.pool.add.hover")))
        }
        player.sendSystemMessage(Component.empty().append(Translator.tr("command.community.building.pool.add.usage")).append(button))
    }
}
