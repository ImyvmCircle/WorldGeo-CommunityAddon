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
        val memberAccount = community.member[player.uuid] ?: return false

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
        val senderRole = getRoleDisplayName(senderAccount.basicRoleType)
        val communityName = community.generateCommunityMark()

        val formattedMessage = Translator.tr(
            "community.chat.message.format",
            communityName,
            senderRole,
            sender.name.string,
            message
        )

        for ((memberUUID, memberAccount) in community.member) {
            if (isFormalMember(memberAccount.basicRoleType) && memberAccount.chatHistoryEnabled) {
                val memberPlayer = sender.server.playerManager.getPlayer(memberUUID)
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(formattedMessage)
                }
            }
        }
    }

    fun toggleChatRoomSend(player: ServerPlayerEntity, community: Community): Boolean {
        val memberAccount = community.member[player.uuid] ?: return false
        
        if (!isFormalMember(memberAccount.basicRoleType)) {
            player.sendMessage(Translator.tr("community.chat.error.not_member"))
            return false
        }

        memberAccount.chatRoomSendEnabled = !memberAccount.chatRoomSendEnabled
        CommunityDatabase.save()
        
        val status = if (memberAccount.chatRoomSendEnabled) "enabled" else "disabled"
        player.sendMessage(Translator.tr("community.chat.toggle.send.$status"))
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

    private fun getRoleDisplayName(roleType: MemberRoleType): String {
        return when (roleType) {
            MemberRoleType.OWNER -> Translator.tr("community.role.owner")?.string ?: "Owner"
            MemberRoleType.ADMIN -> Translator.tr("community.role.admin")?.string ?: "Admin"
            MemberRoleType.MEMBER -> Translator.tr("community.role.member")?.string ?: "Member"
            else -> roleType.name
        }
    }
}
