package com.imyvm.community.application.interaction.screen.inner_community.operation_only

import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.util.Translator.trMenu
import com.imyvm.community.util.constructAndSendMail
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity

fun runAccept(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    val objectAccount = community.member[playerObject.id]
    if (objectAccount?.basicRoleType?.name != "APPLICANT") {
        trMenu(
            playerExecutor,
            "ui.community.operation.audit.message.error.not_applicant"
        )
        return
    }

    objectAccount.basicRoleType = MemberRoleType.MEMBER
    objectAccount.joinedTime = System.currentTimeMillis()
    constructAndSendMail(
        objectAccount.mail,
        playerExecutor,
        community,
        "ui.community.operation.audit.message.accept.mail"
    )
    trMenu(
        playerExecutor,
        "ui.community.operation.audit.message.accept.success",
        playerObject.name
    )
}

fun runRefuse(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    val objectAccount = community.member[playerObject.id]
    if (objectAccount?.basicRoleType?.name != "APPLICANT") {
        trMenu(
            playerExecutor,
            "ui.community.operation.audit.message.error.not_applicant"
        )
        return
    }

    objectAccount.basicRoleType = MemberRoleType.REFUSED
    objectAccount.joinedTime = System.currentTimeMillis()
    constructAndSendMail(
        objectAccount.mail,
        playerExecutor,
        community,
        "ui.community.operation.audit.message.refuse.mail"
    )
    trMenu(
        playerExecutor,
        "ui.community.operation.audit.message.refuse.success",
        playerObject.name
    )
}