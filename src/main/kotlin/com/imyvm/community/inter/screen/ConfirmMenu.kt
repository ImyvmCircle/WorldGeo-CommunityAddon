package com.imyvm.community.inter.screen

import com.imyvm.community.inter.screen.component.ConfirmTaskType
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class ConfirmMenu(
    syncId: Int,
    playerExecutor: ServerPlayerEntity,
    confirmTaskType: ConfirmTaskType,
    private val cautions: List<String>,
    private val runExecutor: (ServerPlayerEntity) -> Unit,
    val runBack: (ServerPlayerEntity) -> Unit,
    communityType: String? = null,
    communityName: String? = null,
    shapeName: String? = null
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
        if (cautions.size >= 5){
            cautions.subList(0,5).forEachIndexed { index, string ->
                addButton(
                    slot = 1 + index * 9,
                    name = string,
                    item = Items.REDSTONE_TORCH
                ) {}
            }
        }
    }

    private fun addConfirmButton() {
        addButton(
            slot = 35,
            name = Translator.tr("ui.confirm.button.confirm")?.string ?: "Confirm",
            item = Items.GREEN_WOOL
        ) {
            runExecutor
        }
    }

    companion object {
        private fun getConfirmMenuTitle(cautionTitle: String): Text {
            return Translator.tr("ui.confirm.title", cautionTitle)
                ?: Text.of("Confirm: $cautionTitle")
        }
    }
}