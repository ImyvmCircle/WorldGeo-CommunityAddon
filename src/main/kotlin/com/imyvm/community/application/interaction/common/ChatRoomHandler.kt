package com.imyvm.community.application.interaction.common

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.CommunityMessage
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.domain.community.MessageType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

object ChatRoomHandler {

    fun sendChatMessage(player: ServerPlayerEntity, community: Community, message: String): Boolean {
        val memberAccount = community.member[player.uuid] ?: run {
            player.sendMessage(Translator.tr("community.chat.error.not_member"))
            return false
        }

        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || 
            memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            player.sendMessage(Translator.tr("community.chat.error.not_member"))
            return false
        }

        val chatMessage = CommunityMessage(
            type = MessageType.CHAT,
            content = Text.of(message),
            senderUUID = player.uuid
        )
        community.addMessage(chatMessage)

        broadcastChatMessage(community, player, message)
        
        CommunityDatabase.save()
        return true
    }

    fun broadcastChatMessage(community: Community, sender: ServerPlayerEntity, message: String) {
        val senderAccount = community.member[sender.uuid] ?: return
        val roleDisplay = getRoleDisplayName(senderAccount.basicRoleType, community.isManor())
        val communityColor = getCommunityColor(community.regionNumberId ?: 0)
        val communityName = community.generateCommunityMark()

        val prefix = "${communityColor}【${communityName}】§r${roleDisplay}"
        val formattedMessage = Text.literal("$prefix §f${sender.name.string}§7: §f$message")

        for ((memberUUID, memberAccount) in community.member) {
            if (isFormalMember(memberAccount.basicRoleType) && memberAccount.chatHistoryEnabled) {
                val memberPlayer = sender.server.playerManager.getPlayer(memberUUID)
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(formattedMessage)
                }
            }
        }
    }

    fun toggleChatChannel(player: ServerPlayerEntity, community: Community): Boolean {
        val memberAccount = community.member[player.uuid] ?: return false
        
        if (!isFormalMember(memberAccount.basicRoleType)) {
            player.sendMessage(Translator.tr("community.chat.error.not_member"))
            return false
        }

        val currentChannel = ChatChannelManager.getActiveChannel(player.uuid)
        val targetRegionId = community.regionNumberId ?: return false
        
        if (currentChannel == targetRegionId) {
            ChatChannelManager.clearChannel(player.uuid)
            player.sendMessage(Translator.tr("community.chat.channel.disabled", community.generateCommunityMark()))
        } else {
            ChatChannelManager.setActiveChannel(player.uuid, targetRegionId)
            player.sendMessage(Translator.tr("community.chat.channel.enabled", community.generateCommunityMark()))
        }
        
        return true
    }

    fun toggleChatHistoryView(player: ServerPlayerEntity, community: Community): Boolean {
        val memberAccount = community.member[player.uuid] ?: return false
        
        if (!isFormalMember(memberAccount.basicRoleType)) {
            player.sendMessage(Translator.tr("community.chat.error.not_member"))
            return false
        }

        memberAccount.chatHistoryEnabled = !memberAccount.chatHistoryEnabled
        CommunityDatabase.save()
        
        val status = if (memberAccount.chatHistoryEnabled) "enabled" else "disabled"
        player.sendMessage(Translator.tr("community.chat.toggle.view.$status"))
        return true
    }

    private fun isFormalMember(roleType: MemberRoleType): Boolean {
        return roleType == MemberRoleType.OWNER || 
               roleType == MemberRoleType.ADMIN || 
               roleType == MemberRoleType.MEMBER
    }

    private fun getRoleDisplayName(roleType: MemberRoleType, isManor: Boolean): String {
        return when (roleType) {
            MemberRoleType.OWNER -> if (isManor) "§6⚜Landowner⚜§r" else "§6♛Lord♛§r"
            MemberRoleType.ADMIN -> if (isManor) "§5✦HouseKeeper✦§r" else "§5★Steward★§r"
            MemberRoleType.MEMBER -> if (isManor) "§aResident§r" else "§aCitizen§r"
            else -> "§7${roleType.name}§r"
        }
    }

    private fun getCommunityColor(regionId: Int): String {
        val colors = listOf(
            "§c", // RED
            "§6", // GOLD
            "§e", // YELLOW
            "§a", // GREEN
            "§b", // AQUA
            "§9", // BLUE
            "§d", // LIGHT_PURPLE
            "§5", // DARK_PURPLE
            "§3", // DARK_AQUA
            "§2"  // DARK_GREEN
        )

        val colorIndex = (regionId * 31 + regionId.toString().hashCode()).mod(colors.size)
        return colors[colorIndex]
    }
}
