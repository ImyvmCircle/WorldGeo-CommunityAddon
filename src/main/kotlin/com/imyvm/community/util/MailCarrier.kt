package com.imyvm.community.util

import com.imyvm.community.domain.model.Community
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

fun constructAndSendMail(
    mailBox: MutableList<Text>,
    playerExecutor: ServerPlayerEntity,
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