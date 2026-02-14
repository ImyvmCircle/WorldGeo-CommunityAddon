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
            if (objectAccount.isInvited) {
                val cost = if (community.isManor()) 
                    com.imyvm.community.infra.CommunityConfig.COMMUNITY_JOIN_COST_MANOR.value 
                    else com.imyvm.community.infra.CommunityConfig.COMMUNITY_JOIN_COST_REALM.value
                
                if (community.getTotalAssets() < cost) {
                    playerExecutor.sendMessage(
                        com.imyvm.community.util.Translator.tr(
                            "community.audit.error.insufficient_assets",
                            playerObject.name,
                            cost / 100.0
                        )
                    )
                    playerExecutor.closeHandledScreen()
                    return@executeWithPermission
                }
                
                community.expenditures.add(
                    com.imyvm.community.domain.Turnover(
                        amount = cost,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            
            objectAccount.basicRoleType = MemberRoleType.MEMBER
            objectAccount.joinedTime = System.currentTimeMillis()
            objectAccount.isInvited = false
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
            com.imyvm.community.infra.CommunityDatabase.save()
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
            if (objectAccount.isInvited) {
                community.member.remove(playerObject.id)
                trMenu(
                    playerExecutor,
                    "ui.community.administration.audit.message.refuse.success",
                    playerObject.name
                )
            } else {
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
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}