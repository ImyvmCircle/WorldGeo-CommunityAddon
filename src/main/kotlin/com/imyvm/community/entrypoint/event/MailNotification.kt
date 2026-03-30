package com.imyvm.community.entrypoint.event

import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.infra.LazyTicker
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

fun registerMailCheck() {
    LazyTicker.registerTask { server ->
        for (player in server.playerList.players) {
            val playerUnreadMails = checkPlayerMail(player)
            if (playerUnreadMails.isEmpty()) continue
            notifyPlayer(player, playerUnreadMails)
        }
    }
}

private fun checkPlayerMail(player: ServerPlayer): List<Component> {
    val unreadMails = mutableListOf<Component>()
    val playerUuid = player.uuid

    for (community in CommunityDatabase.communities) {
        val memberData = community.member[playerUuid] ?: continue
        val mailBox = memberData.mail

        for (i in mailBox.indices) {
            val currentMail = mailBox[i]

            if (isMailUnread(currentMail)) {
                val readingMail = Component.literal(currentMail.string.replaceFirst("[UNREAD]", "").trim())
                mailBox[i] = readingMail
                unreadMails.add(readingMail)
            }
        }
    }

    return unreadMails
}

private fun notifyPlayer(player: ServerPlayer, unreadMails: List<Component>) {
    val size = unreadMails.size
    if (size == 1){
        player.sendSystemMessage(Translator.tr("mail.notification.header.single"))
    } else {
        player.sendSystemMessage(Translator.tr("mail.notification.header.multiple", size))
    }
    for (mail in unreadMails) {
        player.sendSystemMessage(mail)
    }

}

private fun isMailUnread(mail: Component): Boolean {
    return mail.string.startsWith("[UNREAD]")
}