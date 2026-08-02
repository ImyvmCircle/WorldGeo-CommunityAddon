package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.inner_community.runAdmConfirmFiscalPolicy
import com.imyvm.community.application.interaction.screen.inner_community.runOpenFiscalPolicyMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.fiscal.CommunityFiscalPolicy
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items

class FiscalPolicyMenu(
    syncId: Int,
    private val player: ServerPlayer,
    private val community: Community,
    private val selectedPolicy: CommunityFiscalPolicy,
    private val runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.admin.fiscal.title", community.generateCommunityMark()),
    runBack = runBack
) {
    init {
        addButton(20, Translator.tr("ui.admin.fiscal.button.previous").string, Items.ARROW) {
            runOpenFiscalPolicyMenu(it, community, previousPolicy(selectedPolicy), runBack)
        }
        addButton(
            22,
            fiscalPolicyName(selectedPolicy),
            Items.EMERALD,
            fiscalPolicyLore(community, selectedPolicy)
        ) {}
        addButton(24, Translator.tr("ui.admin.fiscal.button.next").string, Items.ARROW) {
            runOpenFiscalPolicyMenu(it, community, nextPolicy(selectedPolicy), runBack)
        }
        addButton(39, Translator.tr("ui.admin.fiscal.button.confirm").string, Items.EMERALD_BLOCK) {
            runAdmConfirmFiscalPolicy(player, community, selectedPolicy, runBack)
        }
        addButton(41, Translator.tr("ui.admin.fiscal.button.cancel").string, Items.BARRIER) {
            runBack(it)
        }
    }

    private fun fiscalPolicyLore(community: Community, selectedPolicy: CommunityFiscalPolicy): List<Component> {
        val pending = community.fiscalState.pendingPolicy
        val lines = mutableListOf(
            Translator.tr("ui.admin.fiscal.lore.active", fiscalPolicyName(community.fiscalState.activePolicy)),
            Translator.tr("ui.admin.fiscal.lore.selected", fiscalPolicyName(selectedPolicy))
        )
        if (pending != null) {
            lines.add(Translator.tr("ui.admin.fiscal.lore.pending", fiscalPolicyName(pending.policy), pending.effectiveWeekKey))
            lines.add(Translator.tr("ui.admin.fiscal.lore.cooldown", pending.cooldownUntilWeekKey))
        }
        return lines
    }

    private fun previousPolicy(policy: CommunityFiscalPolicy): CommunityFiscalPolicy = when (policy) {
        CommunityFiscalPolicy.NEOLIBERALISM -> CommunityFiscalPolicy.ANARCHISM
        CommunityFiscalPolicy.VISIBLE_HAND -> CommunityFiscalPolicy.NEOLIBERALISM
        CommunityFiscalPolicy.HEAVEN_ON_EARTH -> CommunityFiscalPolicy.VISIBLE_HAND
        CommunityFiscalPolicy.ANARCHISM -> CommunityFiscalPolicy.HEAVEN_ON_EARTH
    }

    private fun nextPolicy(policy: CommunityFiscalPolicy): CommunityFiscalPolicy = when (policy) {
        CommunityFiscalPolicy.NEOLIBERALISM -> CommunityFiscalPolicy.VISIBLE_HAND
        CommunityFiscalPolicy.VISIBLE_HAND -> CommunityFiscalPolicy.HEAVEN_ON_EARTH
        CommunityFiscalPolicy.HEAVEN_ON_EARTH -> CommunityFiscalPolicy.ANARCHISM
        CommunityFiscalPolicy.ANARCHISM -> CommunityFiscalPolicy.NEOLIBERALISM
    }

    private fun fiscalPolicyName(policy: CommunityFiscalPolicy): String =
        Translator.tr("community.fiscal.policy.${policy.name.lowercase()}").string
}
