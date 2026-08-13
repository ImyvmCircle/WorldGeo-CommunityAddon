package com.imyvm.community.util

import com.imyvm.community.domain.model.Community
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

private const val UNREAD_PREFIX = "[UNREAD] "

fun addUnreadMail(mailBox: MutableList<Component>, message: Component) {
    if (message.string.startsWith("[UNREAD]") || message.string.startsWith("[未读]")) mailBox.add(message)
    else mailBox.add(Component.literal(UNREAD_PREFIX).append(message))
}

fun sendAndStoreMail(server: MinecraftServer, recipientUuid: java.util.UUID, mailBox: MutableList<Component>, message: Component) {
    val recipient = server.playerList.getPlayer(recipientUuid)
    if (recipient == null) addUnreadMail(mailBox, message)
    else {
        recipient.sendSystemMessage(message)
        mailBox.add(message)
    }
}

fun readMail(message: Component): Component =
    Component.literal(message.string.replaceFirst(Regex("^\\[(UNREAD|未读)]\\s*"), "").trim())

fun isUnreadMail(message: Component): Boolean =
    message.string.startsWith("[UNREAD]") || message.string.startsWith("[未读]")

fun constructAndSendMail(
    mailBox: MutableList<Component>,
    playerExecutor: ServerPlayer,
    community: Community,
    content: String
): Boolean {
    val formattedTime = getFormattedMillsHour(System.currentTimeMillis())
    val regionName = community.getRegion()?.name ?: "Community#${community.regionNumberId}"

    val message = Translator.tr(
        "mail.notification.community.message",
        formattedTime,
        regionName,
        playerExecutor.name.string,
        content
    )

    if (message.string.isNotEmpty()) {
        addUnreadMail(mailBox, message)
        return true
    }
    return false
}