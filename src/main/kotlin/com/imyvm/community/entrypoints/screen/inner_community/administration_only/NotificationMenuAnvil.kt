package com.imyvm.community.entrypoints.screen.inner_community.administration_only

import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator.trMenu
import com.imyvm.community.util.constructAndSendMail
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class NotificationMenuAnvil(
    private val playerExecutor: ServerPlayerEntity,
    initialName: String,
    private val playerObject: GameProfile,
    val community: Community
): AbstractRenameMenuAnvil(
    playerExecutor,
    initialName
) {
    override fun processRenaming(finalName: String) {
        if (!checkPrerequisites(finalName)) return
        val member = community.member[playerObject.id]!!

        val messageSent = constructAndSendMail(member.mail, playerExecutor, community, finalName)
        if (messageSent) {
            trMenu(playerExecutor, "community.member_management.message.sent", playerObject.name)
        } else {
            trMenu(playerExecutor, "community.member_management.message.sent.error.empty", playerObject.name)
        }
    }

    override fun getMenuTitle(): Text  = Text.of("(Edit your notification here to ${playerObject.name})")

    private fun checkPrerequisites(finalName: String): Boolean {
        if (finalName.isBlank()) {
            trMenu(playerExecutor, "community.member_management.message.sent.error.empty", playerObject.name)
            return false
        }
        if (community.member[playerObject.id] == null) {
            trMenu(playerExecutor, "community.member_management.message.sent.error.not_member", playerObject.name)
            return false
        }
        return true
    }

}