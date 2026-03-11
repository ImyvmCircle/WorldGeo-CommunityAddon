package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.RenameConfirmationData
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class AdministrationRenameMenuAnvil(
    player: ServerPlayerEntity,
    private val community: Community,
    private val scopeName: String?,
    private val runBackGrandfather: ((ServerPlayerEntity) -> Unit),
    errorHint: String? = null
): AbstractRenameMenuAnvil(
    player = player,
    initialName = if (scopeName == null) {
        community.regionNumberId?.let { RegionDataApi.getRegion(it)?.name } ?: "Unknown Name"
    } else {
        scopeName
    },
    errorHint = errorHint
) {
    override fun processRenaming(finalName: String) {
        val regionId = community.regionNumberId ?: return
        val nameKey = scopeName ?: "global"
        val cost = if (scopeName == null) PricingConfig.RENAME_GLOBAL_COST.value else PricingConfig.RENAME_SCOPE_COST.value

        val cooldownMs = community.nameChangeCooldowns[nameKey] ?: 0L
        val daysSince = (System.currentTimeMillis() - cooldownMs) / (1000L * 60 * 60 * 24)
        if (daysSince < 30) {
            val daysLeft = 30 - daysSince
            player.closeHandledScreen()
            player.sendMessage(Translator.tr("community.rename.error.cooldown", daysLeft.toString(), nameKey))
            return
        }

        val existingPending = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionId]
        if (existingPending != null) {
            player.closeHandledScreen()
            player.sendMessage(Translator.tr("community.modification.confirmation.pending"))
            return
        }

        val currentAssets = community.getTotalAssets()
        if (cost > 0 && currentAssets < cost) {
            player.closeHandledScreen()
            player.sendMessage(Translator.tr(
                "community.modification.error.insufficient_assets",
                String.format("%.2f", cost / 100.0),
                String.format("%.2f", currentAssets / 100.0)
            ))
            return
        }

        addPendingOperation(
            regionId = regionId,
            type = PendingOperationType.RENAME_CONFIRMATION,
            expireMinutes = 5,
            renameData = RenameConfirmationData(
                regionNumberId = regionId,
                nameKey = nameKey,
                newName = finalName,
                executorUUID = player.uuid,
                cost = cost
            )
        )

        player.closeHandledScreen()
        sendInteractiveRenameConfirmation(player, regionId, nameKey, finalName, cost)
    }

    override fun reopenWith(errorHint: String?, currentInput: String) {
        AdministrationRenameMenuAnvil(player, community, scopeName, runBackGrandfather, errorHint).open()
    }

    override fun getMenuTitle(): Text {
        val base = if (scopeName == null) {
            Translator.tr("ui.admin.rename.title") ?: Text.of("Rename Community")
        } else {
            Translator.tr("ui.admin.rename.scope.title", scopeName) ?: Text.of("Rename Scope: $scopeName")
        }
        return buildTitle(base)
    }

    private fun sendInteractiveRenameConfirmation(player: ServerPlayerEntity, regionNumberId: Int, nameKey: String, newName: String, cost: Long) {
        val costDisplay = String.format("%.2f", cost / 100.0)
        player.sendMessage(Translator.tr("community.rename.bill", nameKey, newName, costDisplay))

        val confirmButton = Text.literal("§a§l[确认]§r")
            .styled { style ->
                style.withClickEvent(net.minecraft.text.ClickEvent(
                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                    "/community confirm_rename $regionNumberId ${if (nameKey.contains(' ')) "\"$nameKey\"" else nameKey}"
                )).withHoverEvent(net.minecraft.text.HoverEvent(
                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                    Translator.tr("community.rename.confirm.hover") ?: Text.literal("Click to confirm rename")
                ))
            }

        val cancelButton = Text.literal("§c§l[取消]§r")
            .styled { style ->
                style.withClickEvent(net.minecraft.text.ClickEvent(
                    net.minecraft.text.ClickEvent.Action.RUN_COMMAND,
                    "/community cancel_rename $regionNumberId ${if (nameKey.contains(' ')) "\"$nameKey\"" else nameKey}"
                )).withHoverEvent(net.minecraft.text.HoverEvent(
                    net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                    Translator.tr("community.rename.cancel.hover") ?: Text.literal("Click to cancel rename")
                ))
            }

        val promptMessage = Text.empty()
            .append(Text.literal("§e§l[等待确认]§r §e请在 §c§l5分钟§r§e 内操作: "))
            .append(confirmButton)
            .append(Text.literal(" "))
            .append(cancelButton)

        player.sendMessage(promptMessage)
    }
}