package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.interaction.common.filterCommunitiesByType
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.network.ServerPlayerEntity

fun onHelpCommand(player: ServerPlayerEntity): Int {
    player.sendMessage(Translator.tr("community.command.help.header"))
    player.sendMessage(Translator.tr("community.command.help.open_menu"))
    player.sendMessage(Translator.tr("community.command.help.help"))
    player.sendMessage(Translator.tr("community.command.help.list_communities"))
    player.sendMessage(Translator.tr("community.command.help.query_region"))
    player.sendMessage(Translator.tr("community.command.help.section_land"))
    player.sendMessage(Translator.tr("community.command.help.select"))
    player.sendMessage(Translator.tr("community.command.help.create"))
    player.sendMessage(Translator.tr("community.command.help.section_join"))
    player.sendMessage(Translator.tr("community.command.help.join"))
    player.sendMessage(Translator.tr("community.command.help.leave"))
    player.sendMessage(Translator.tr("community.command.help.accept_invitation"))
    player.sendMessage(Translator.tr("community.command.help.reject_invitation"))
    player.sendMessage(Translator.tr("community.command.help.section_chat"))
    player.sendMessage(Translator.tr("community.command.help.chat"))
    player.sendMessage(Translator.tr("community.command.help.chat_channel"))
    player.sendMessage(Translator.tr("community.command.help.section_announce"))
    player.sendMessage(Translator.tr("community.command.help.announcement_list"))
    player.sendMessage(Translator.tr("community.command.help.announcement_view"))
    player.sendMessage(Translator.tr("community.command.help.announcement_create"))
    player.sendMessage(Translator.tr("community.command.help.announcement_delete"))
    return 1
}

fun onListCommunities(player: ServerPlayerEntity, type: CommunityListFilterType): Int {
    val filtered = filterCommunitiesByType(type)
    displayCommunityList(player, type, filtered)
    return 1
}

fun onQueryCommunityRegion(player: ServerPlayerEntity, region: Region): Int {
    PlayerInteractionApi.queryRegionInfo(player, region)
    return 1
}

private fun displayCommunityList(player: ServerPlayerEntity, type: CommunityListFilterType, list: List<Community>) {
    if (list.isEmpty()) {
        player.sendMessage(Translator.tr("community.list.empty"))
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

    player.sendMessage(Translator.tr(headerKey))
    for (community in list) {
        displayCommunityEntry(player, community)
    }
}

private fun displayCommunityEntry(player: ServerPlayerEntity, community: Community) {
    community.getRegion()?.let {
        player.sendMessage(
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
