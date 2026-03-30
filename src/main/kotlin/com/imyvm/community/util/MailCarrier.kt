package com.imyvm.community.util

import com.imyvm.community.domain.model.Community
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

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

    if (message != null && message.string.isNotEmpty()) {
        mailBox.add(message)
        return true
    }
    return false
}