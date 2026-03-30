package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractRenameMenuAnvil
import com.imyvm.community.util.Translator.trMenu
import com.imyvm.community.util.constructAndSendMail
import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

class NotificationMenuAnvil(
    private val playerExecutor: ServerPlayer,
    initialName: String,
    private val playerObject: GameProfile,
    val community: Community
): AbstractRenameMenuAnvil(
    playerExecutor,
    initialName
) {
    override fun isNameValid(name: String): Boolean = name.isNotBlank()

    override fun reopenWith(errorHint: String?, currentInput: String) {
        NotificationMenuAnvil(playerExecutor, currentInput, playerObject, community).open()
    }
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

    override fun getMenuTitle(): Component  = Component.literal("(Edit your notification here to ${playerObject.name})")

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