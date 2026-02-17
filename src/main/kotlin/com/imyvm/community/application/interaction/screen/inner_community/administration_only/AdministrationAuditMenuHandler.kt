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
        if (!com.imyvm.community.application.interaction.common.checkMemberNumberManor(playerExecutor, community)) {
            playerExecutor.closeHandledScreen()
            return@executeWithPermission
        }
        
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
            
            objectAccount.basicRoleType = com.imyvm.community.domain.community.MemberRoleType.MEMBER
            objectAccount.joinedTime = System.currentTimeMillis()
            val wasInvited = objectAccount.isInvited
            objectAccount.isInvited = false
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            
            val targetNotificationKey = if (wasInvited) {
                "community.notification.target.invitation_accepted"
            } else {
                "community.notification.target.accepted"
            }
            val targetNotification = com.imyvm.community.util.Translator.tr(
                targetNotificationKey,
                communityName,
                playerExecutor.name.string
            ) ?: net.minecraft.text.Text.literal("You have been accepted to $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                playerExecutor.server, playerObject.id, targetNotification, community
            )
            
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
            
            val notification = com.imyvm.community.util.Translator.tr("community.notification.member_accepted", playerObject.name, playerExecutor.name.string, communityName)
                ?: net.minecraft.text.Text.literal("${playerObject.name} has been accepted to $communityName by ${playerExecutor.name.string}")
            notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
            
            com.imyvm.community.application.event.checkAndPromoteRecruitingRealm(community)
        }
    }
}

private fun notifyOfficials(community: Community, server: net.minecraft.server.MinecraftServer, message: net.minecraft.text.Text, executor: ServerPlayerEntity? = null) {
    for ((memberUUID, memberAccount) in community.member) {
        val isOfficial = memberAccount.basicRoleType == com.imyvm.community.domain.community.MemberRoleType.OWNER ||
                        memberAccount.basicRoleType == com.imyvm.community.domain.community.MemberRoleType.ADMIN ||
                        memberAccount.isCouncilMember
        
        if (isOfficial && (executor == null || memberUUID != executor.uuid)) {
            val officialPlayer = server.playerManager.getPlayer(memberUUID)
            if (officialPlayer != null) {
                officialPlayer.sendMessage(message)
            }
            memberAccount.mail.add(message)
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
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            
            if (objectAccount.isInvited) {
                community.member.remove(playerObject.id)
                
                val targetNotification = com.imyvm.community.util.Translator.tr(
                    "community.notification.target.invitation_refused",
                    communityName,
                    playerExecutor.name.string
                ) ?: net.minecraft.text.Text.literal("Your invitation to $communityName was refused by ${playerExecutor.name.string}")
                
                val applicantPlayer = playerExecutor.server.playerManager.getPlayer(playerObject.id)
                if (applicantPlayer != null) {
                    applicantPlayer.sendMessage(targetNotification)
                }
            } else {
                val cost = if (community.isManor()) 
                    com.imyvm.community.infra.CommunityConfig.COMMUNITY_JOIN_COST_MANOR.value 
                    else com.imyvm.community.infra.CommunityConfig.COMMUNITY_JOIN_COST_REALM.value
                
                val applicantPlayer = playerExecutor.server.playerManager.getPlayer(playerObject.id)
                if (applicantPlayer != null) {
                    val playerData = com.imyvm.economy.EconomyMod.data.getOrCreate(applicantPlayer)
                    playerData.money += cost
                    applicantPlayer.sendMessage(
                        com.imyvm.community.util.Translator.tr(
                            "community.join.refund",
                            cost / 100.0
                        )
                    )
                }
                
                objectAccount.basicRoleType = com.imyvm.community.domain.community.MemberRoleType.REFUSED
                objectAccount.joinedTime = System.currentTimeMillis()
                
                val targetNotification = com.imyvm.community.util.Translator.tr(
                    "community.notification.target.application_refused",
                    communityName,
                    playerExecutor.name.string
                ) ?: net.minecraft.text.Text.literal("Your application to $communityName was refused by ${playerExecutor.name.string}")
                com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                    playerExecutor.server, playerObject.id, targetNotification, community
                )
                
                constructAndSendMail(
                    objectAccount.mail,
                    playerExecutor,
                    community,
                    "ui.community.administration.audit.message.refuse.mail"
                )
            }
            trMenu(
                playerExecutor,
                "ui.community.administration.audit.message.refuse.success",
                playerObject.name
            )
            
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.application_refused",
                playerObject.name,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.text.Text.literal("${playerObject.name}'s application to $communityName was refused by ${playerExecutor.name.string}")
            notifyOfficials(community, playerExecutor.server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}