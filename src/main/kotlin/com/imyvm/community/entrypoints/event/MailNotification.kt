package com.imyvm.community.entrypoints.event

import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.infra.LazyTicker
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

fun registerMailCheck() {
    LazyTicker.registerTask { server ->
        for (player in server.playerManager.playerList) {
            val playerUnreadMails = checkPlayerMail(player)
            if (playerUnreadMails.isEmpty()) continue
            notifyPlayer(player, playerUnreadMails)
        }
    }
}

private fun checkPlayerMail(player: ServerPlayerEntity): List<Text> {
    val unreadMails = mutableListOf<Text>()
    val playerUuid = player.uuid

    for (community in CommunityDatabase.communities) {
        val memberData = community.member[playerUuid] ?: continue
        val mailBox = memberData.mail

        for (i in mailBox.indices) {
            val currentMail = mailBox[i]

            if (isMailUnread(currentMail)) {
                val readingMail = Text.of(currentMail.string.replaceFirst("[UNREAD]", "").trim())
                mailBox[i] = readingMail
                unreadMails.add(readingMail)
            }
        }
    }

    return unreadMails
}

private fun notifyPlayer(player: ServerPlayerEntity, unreadMails: List<Text>) {
    val size = unreadMails.size
    if (size == 1){
        player.sendMessage(Translator.tr("mail.notification.header.single"))
    } else {
        player.sendMessage(Translator.tr("mail.notification.header.multiple", size))
    }
    for (mail in unreadMails) {
        player.sendMessage(mail)
    }

}

private fun isMailUnread(mail: Text): Boolean {
    return mail.string.startsWith("[UNREAD]")
}