package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.application.permission.PermissionCheck
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
    PermissionCheck.executeWithPermission(
        playerExecutor,
        { PermissionCheck.canAcceptApplicant(playerExecutor, community, playerObject.id) }
    ) {
        val objectAccount = community.member[playerObject.id]
        if (objectAccount != null) {
            objectAccount.basicRoleType = MemberRoleType.MEMBER
            objectAccount.joinedTime = System.currentTimeMillis()
            constructAndSendMail(
                objectAccount.mail,
                playerExecutor,
                community,
                "ui.community.administration.audit.message.accept.mail"
            )
            trMenu(
                playerExecutor,
                "ui.community.administration.audit.message.accept.success",
                playerObject.name
            )
        }
    }
}

fun runRefuse(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    PermissionCheck.executeWithPermission(
        playerExecutor,
        { PermissionCheck.canRefuseApplicant(playerExecutor, community, playerObject.id) }
    ) {
        val objectAccount = community.member[playerObject.id]
        if (objectAccount != null) {
            objectAccount.basicRoleType = MemberRoleType.REFUSED
            objectAccount.joinedTime = System.currentTimeMillis()
            constructAndSendMail(
                objectAccount.mail,
                playerExecutor,
                community,
                "ui.community.administration.audit.message.refuse.mail"
            )
            trMenu(
                playerExecutor,
                "ui.community.administration.audit.message.refuse.success",
                playerObject.name
            )
        }
    }
}