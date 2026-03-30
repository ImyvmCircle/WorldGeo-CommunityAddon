package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.interaction.common.filterCommunitiesByType
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.level.ServerPlayer

fun onHelpCommand(player: ServerPlayer): Int {
    player.sendSystemMessage(Translator.tr("community.command.help.header"))
    player.sendSystemMessage(Translator.tr("community.command.help.open_menu"))
    player.sendSystemMessage(Translator.tr("community.command.help.help"))
    player.sendSystemMessage(Translator.tr("community.command.help.list_communities"))
    player.sendSystemMessage(Translator.tr("community.command.help.query_region"))
    player.sendSystemMessage(Translator.tr("community.command.help.section_land"))
    player.sendSystemMessage(Translator.tr("community.command.help.select"))
    player.sendSystemMessage(Translator.tr("community.command.help.create"))
    player.sendSystemMessage(Translator.tr("community.command.help.section_join"))
    player.sendSystemMessage(Translator.tr("community.command.help.join"))
    player.sendSystemMessage(Translator.tr("community.command.help.leave"))
    player.sendSystemMessage(Translator.tr("community.command.help.accept_invitation"))
    player.sendSystemMessage(Translator.tr("community.command.help.reject_invitation"))
    player.sendSystemMessage(Translator.tr("community.command.help.section_chat"))
    player.sendSystemMessage(Translator.tr("community.command.help.chat"))
    player.sendSystemMessage(Translator.tr("community.command.help.chat_channel"))
    player.sendSystemMessage(Translator.tr("community.command.help.section_announce"))
    player.sendSystemMessage(Translator.tr("community.command.help.announcement_list"))
    player.sendSystemMessage(Translator.tr("community.command.help.announcement_view"))
    player.sendSystemMessage(Translator.tr("community.command.help.announcement_create"))
    player.sendSystemMessage(Translator.tr("community.command.help.announcement_delete"))
    return 1
}

fun onListCommunities(player: ServerPlayer, type: CommunityListFilterType): Int {
    val filtered = filterCommunitiesByType(type)
    displayCommunityList(player, type, filtered)
    return 1
}

fun onQueryCommunityRegion(player: ServerPlayer, region: Region): Int {
    PlayerInteractionApi.queryRegionInfo(player, region)
    return 1
}

private fun displayCommunityList(player: ServerPlayer, type: CommunityListFilterType, list: List<Community>) {
    if (list.isEmpty()) {
        player.sendSystemMessage(Translator.tr("community.list.empty"))
        return
    }
    val headerKey = when (type) {
        CommunityListFilterType.JOIN_ABLE -> "community.list.header.join_able"
        CommunityListFilterType.REVOKED -> "community.list.header.revoked"
        CommunityListFilterType.RECRUITING -> "community.list.header.recruiting"
        CommunityListFilterType.AUDITING -> "community.list.header.pending"
        CommunityListFilterType.ACTIVE -> "community.list.header.active"
        CommunityListFilterType.ALL -> "community.list.header.all"
    }

    player.sendSystemMessage(Translator.tr(headerKey))
    for (community in list) {
        displayCommunityEntry(player, community)
    }
}

private fun displayCommunityEntry(player: ServerPlayer, community: Community) {
    community.getRegion()?.let {
        player.sendSystemMessage(
            Translator.tr(
                "community.list.entry",
                it.name,
                community.regionNumberId,
                community.getFormattedFoundingTime(),
                community.status.name.lowercase(),
                community.joinPolicy.name.lowercase(),
                community.member.size
            )
        )
    }
}
