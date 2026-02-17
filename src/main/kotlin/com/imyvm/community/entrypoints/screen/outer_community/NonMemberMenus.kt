package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class NonMemberCommunityMenu(
    syncId: Int,
    private val player: ServerPlayerEntity,
    private val community: Community,
    private val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.non_member.title", community.generateCommunityMark()) 
        ?: Text.literal("${community.generateCommunityMark()} - Not a Member"),
    runBack = runBack
) {
    init {
        addCommunityInfo()
        addJoinButton()
    }

    private fun addCommunityInfo() {
        addButton(
            slot = 13,
            name = community.generateCommunityMark(),
            item = Items.PLAYER_HEAD
        ) {}

        addButton(
            slot = 22,
            name = Translator.tr("ui.non_member.info")?.string ?: "Community Information",
            item = Items.BOOKSHELF
        ) { community.sendCommunityRegionDescription(player) }
    }

    private fun addJoinButton() {
        addButton(
            slot = 31,
            name = Translator.tr("ui.non_member.button.join")?.string ?: "Request to Join",
            item = Items.EMERALD
        ) { 
            player.closeHandledScreen()
            com.imyvm.community.application.interaction.common.onJoinCommunity(player, community)
        }
    }
}

class ApplicantStatusMenu(
    syncId: Int,
    private val player: ServerPlayerEntity,
    private val community: Community,
    private val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.applicant.title", community.generateCommunityMark())
        ?: Text.literal("${community.generateCommunityMark()} - Application Status"),
    runBack = runBack
) {
    init {
        addStatusInfo()
    }

    private fun addStatusInfo() {
        addButton(
            slot = 13,
            name = community.generateCommunityMark(),
            item = Items.PLAYER_HEAD
        ) {}

        addButton(
            slot = 22,
            name = Translator.tr("ui.applicant.status")?.string ?: "Application Pending",
            item = Items.CLOCK
        ) {}

        addButton(
            slot = 31,
            name = Translator.tr("ui.applicant.message")?.string ?: "Your application is being reviewed",
            item = Items.PAPER
        ) {}
    }
}

class RefusedStatusMenu(
    syncId: Int,
    private val player: ServerPlayerEntity,
    private val community: Community,
    private val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractMenu(
    syncId,
    menuTitle = Translator.tr("ui.refused.title", community.generateCommunityMark())
        ?: Text.literal("${community.generateCommunityMark()} - Application Refused"),
    runBack = runBack
) {
    init {
        addStatusInfo()
    }

    private fun addStatusInfo() {
        addButton(
            slot = 13,
            name = community.generateCommunityMark(),
            item = Items.PLAYER_HEAD
        ) {}

        addButton(
            slot = 22,
            name = Translator.tr("ui.refused.status")?.string ?: "Application Refused",
            item = Items.BARRIER
        ) {}

        addButton(
            slot = 31,
            name = Translator.tr("ui.refused.message")?.string ?: "Your application was not approved",
            item = Items.PAPER
        ) {}
    }
}
