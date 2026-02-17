package com.imyvm.community.entrypoints.screen

import com.imyvm.community.application.interaction.screen.runConfirmDispatcher
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.component.ConfirmTaskType
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ConfirmMenu(
    syncId: Int,
    val playerExecutor: ServerPlayerEntity,
    val confirmTaskType: ConfirmTaskType,
    private val cautions: List<String>,
    val runBack: (ServerPlayerEntity) -> Unit,
    val communityType: String? = null,
    val communityName: String? = null,
    val shapeName: String? = null,
    val targetCommunity: Community? = null
): AbstractMenu(
    syncId = syncId,
    menuTitle = getConfirmMenuTitle(
        cautions.firstOrNull()
        ?: Translator.tr("ui.confirm.default")?.string ?: "<Error When Getting Target Operation>"),
    runBack = runBack
) {

    init {
        addCautionTextButtons()
        addConfirmButton()
    }

    private fun addCautionTextButtons() {
        val cautions= if (cautions.size >= 5) cautions.subList(0,5) else cautions
        cautions.forEachIndexed { index, string ->
            addButton(
                slot = 1 + index * 9,
                name = string,
                item = Items.REDSTONE_TORCH
            ) {}
        }
    }

    private fun addConfirmButton() {
        addButton(
            slot = 35,
            name = Translator.tr("ui.confirm.button.confirm")?.string ?: "Confirm",
            item = Items.GREEN_WOOL
        ) {
            runConfirmDispatcher(
                playerExecutor = playerExecutor,
                confirmTaskType = confirmTaskType,
                communityType = communityType,
                communityName = communityName,
                shapeName = shapeName,
                targetCommunity = targetCommunity
            )
        }
    }

    companion object {
        private fun getConfirmMenuTitle(cautionTitle: String): Text {
            return Translator.tr("ui.confirm.title", cautionTitle)
                ?: Text.of("Confirm: $cautionTitle")
        }
    }
}