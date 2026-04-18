package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.RenameConfirmationData
import com.imyvm.community.domain.policy.territory.TerritoryPricing
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent

class AdministrationRenameMenuAnvil(
    player: ServerPlayer,
    private val community: Community,
    private val scopeName: String?,
    private val runBackGrandfather: ((ServerPlayer) -> Unit),
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
        val scopeDimensionId = if (scopeName == null) {
            null
        } else {
            community.regionNumberId?.let { RegionDataApi.getRegion(it) }
                ?.geometryScope
                ?.firstOrNull { it.scopeName.equals(scopeName, ignoreCase = true) }
                ?.let(TerritoryPricing::getScopeDimensionId)
        }
        val cost = if (scopeDimensionId == null) {
            PricingConfig.RENAME_GLOBAL_COST.value
        } else {
            TerritoryPricing.applyGeoscopePriceMultiplier(PricingConfig.RENAME_SCOPE_COST.value, scopeDimensionId).totalCost
        }

        val cooldownMs = community.nameChangeCooldowns[nameKey] ?: 0L
        val daysSince = (System.currentTimeMillis() - cooldownMs) / (1000L * 60 * 60 * 24)
        if (daysSince < 30) {
            val daysLeft = 30 - daysSince
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.rename.error.cooldown", daysLeft.toString(), nameKey))
            return
        }

        val existingPending = com.imyvm.community.WorldGeoCommunityAddon.pendingOperations[regionId]
        if (existingPending != null) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr("community.modification.confirmation.pending"))
            return
        }

        val currentAssets = community.getTotalAssets()
        if (cost > 0 && currentAssets < cost) {
            player.closeContainer()
            player.sendSystemMessage(Translator.tr(
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

        player.closeContainer()
        sendInteractiveRenameConfirmation(player, regionId, nameKey, finalName, cost, scopeDimensionId)
    }

    override fun reopenWith(errorHint: String?, currentInput: String) {
        AdministrationRenameMenuAnvil(player, community, scopeName, runBackGrandfather, errorHint).open()
    }

    override fun getMenuTitle(): Component {
        val base = if (scopeName == null) {
            Translator.tr("ui.admin.rename.title") ?: Component.literal("Rename Community")
        } else {
            Translator.tr("ui.admin.rename.scope.title", scopeName) ?: Component.literal("Rename Scope: $scopeName")
        }
        return buildTitle(base)
    }

    private fun sendInteractiveRenameConfirmation(player: ServerPlayer, regionNumberId: Int, nameKey: String, newName: String, cost: Long, scopeDimensionId: String?) {
        val costDisplay = String.format("%.2f", cost / 100.0)
        if (scopeDimensionId != null) {
            val multiplierKey = when (TerritoryPricing.normalizeDimensionId(scopeDimensionId)) {
                TerritoryPricing.DIMENSION_NETHER -> "community.pricing.dimension.multiplier.nether"
                TerritoryPricing.DIMENSION_END -> "community.pricing.dimension.multiplier.end"
                else -> "community.pricing.dimension.multiplier.overworld"
            }
            player.sendSystemMessage(
                Translator.tr(
                    "community.pricing.dimension.legend",
                    Translator.tr(multiplierKey, TerritoryPricing.getDimensionMultiplier(scopeDimensionId).toString())?.string
                        ?: "${scopeDimensionId} x${TerritoryPricing.getDimensionMultiplier(scopeDimensionId)}"
                )
            )
        }
        player.sendSystemMessage(Translator.tr("community.rename.bill", nameKey, newName, costDisplay))
        val quotedNameKey = if (!nameKey.all { it.isLetterOrDigit() && it.code < 128 }) "\"$nameKey\"" else nameKey

        val confirmButton = Component.literal("§a§l[确认]§r")
            .withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_rename $regionNumberId $quotedNameKey")).withHoverEvent(HoverEvent.ShowText(Translator.tr("community.rename.confirm.hover") ?: Component.literal("Click to confirm rename")
                ))
            }

        val cancelButton = Component.literal("§c§l[取消]§r")
            .withStyle { style ->
                style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_rename $regionNumberId $quotedNameKey")).withHoverEvent(HoverEvent.ShowText(Translator.tr("community.rename.cancel.hover") ?: Component.literal("Click to cancel rename")
                ))
            }

        val promptMessage = Component.empty()
            .append(Component.literal("§e§l[等待确认]§r §e请在 §c§l5分钟§r§e 内操作: "))
            .append(confirmButton)
            .append(Component.literal(" "))
            .append(cancelButton)

        player.sendSystemMessage(promptMessage)
    }
}
