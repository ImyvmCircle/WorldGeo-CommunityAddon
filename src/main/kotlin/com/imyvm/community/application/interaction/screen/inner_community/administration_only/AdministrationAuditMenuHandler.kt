package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.util.Translator.trMenu
import com.imyvm.community.util.constructAndSendMail
import com.mojang.authlib.GameProfile
import net.minecraft.server.level.ServerPlayer

fun runAccept(
    community: Community,
    playerExecutor: ServerPlayer,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canAcceptApplicant(playerExecutor, community, playerObject.id) }
    ) {
        if (!com.imyvm.community.application.interaction.common.checkMemberNumberManor(playerExecutor, community)) {
            playerExecutor.closeContainer()
            return@executeWithPermission
        }
        
        val objectAccount = community.member[playerObject.id]
        if (objectAccount != null) {
            if (objectAccount.isInvited) {
                val cost = if (community.isManor()) 
                    PricingConfig.COMMUNITY_JOIN_COST_MANOR.value 
                    else PricingConfig.COMMUNITY_JOIN_COST_REALM.value
                
                if (community.getTotalAssets() < cost) {
                    playerExecutor.sendSystemMessage(
                        com.imyvm.community.util.Translator.tr(
                            "community.audit.error.insufficient_assets",
                            playerObject.name,
                            cost / 100.0
                        )
                    )
                    playerExecutor.closeContainer()
                    return@executeWithPermission
                }
                
                community.expenditures.add(
                    com.imyvm.community.domain.model.Turnover(
                        amount = cost,
                        timestamp = System.currentTimeMillis(),
                        source = TurnoverSource.SYSTEM,
                        descriptionKey = "community.treasury.desc.member_join_fee",
                        descriptionArgs = listOf(playerObject.name)
                    )
                )
            }
            
            objectAccount.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.MEMBER
            objectAccount.joinedTime = System.currentTimeMillis()
            val wasInvited = objectAccount.isInvited
            objectAccount.isInvited = false
            
            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            
            com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.autoGrantDefaultPermissions(
                playerObject.id, playerExecutor, community
            )
            
            val targetNotificationKey = if (wasInvited) {
                "community.notification.target.invitation_accepted"
            } else {
                "community.notification.target.accepted"
            }
            val targetNotification = com.imyvm.community.util.Translator.tr(
                targetNotificationKey,
                communityName,
                playerExecutor.name.string
            ) ?: net.minecraft.network.chat.Component.literal("You have been accepted to $communityName by ${playerExecutor.name.string}")
            com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                playerExecutor.level().server, playerObject.id, targetNotification, community
            )
            
            constructAndSendMail(
                objectAccount.mail,
                playerExecutor,
                community,
                "ui.admin.audit.message.accept.mail"
            )
            trMenu(
                playerExecutor,
                "ui.admin.audit.message.accept.success",
                playerObject.name
            )
            
            val notification = com.imyvm.community.util.Translator.tr("community.notification.member_accepted", playerObject.name, playerExecutor.name.string, communityName)
                ?: net.minecraft.network.chat.Component.literal("${playerObject.name} has been accepted to $communityName by ${playerExecutor.name.string}")
            notifyOfficials(community, playerExecutor.level().server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
            
            com.imyvm.community.application.event.checkAndPromoteRecruitingRealm(community)
        }
    }
}

private fun notifyOfficials(community: Community, server: net.minecraft.server.MinecraftServer, message: net.minecraft.network.chat.Component, executor: ServerPlayer? = null) {
    for ((memberUUID, memberAccount) in community.member) {
        val isOfficial = memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.OWNER ||
                        memberAccount.basicRoleType == com.imyvm.community.domain.model.community.MemberRoleType.ADMIN
        
        if (isOfficial && (executor == null || memberUUID != executor.uuid)) {
            val officialPlayer = server.playerList.getPlayer(memberUUID)
            if (officialPlayer != null) {
                officialPlayer.sendSystemMessage(message)
            }
            memberAccount.mail.add(message)
        }
    }
}

fun runRefuse(
    community: Community,
    playerExecutor: ServerPlayer,
    playerObject: GameProfile
) {
    CommunityPermissionPolicy.executeWithPermission(
        playerExecutor,
        { CommunityPermissionPolicy.canRefuseApplicant(playerExecutor, community, playerObject.id) }
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
                ) ?: net.minecraft.network.chat.Component.literal("Your invitation to $communityName was refused by ${playerExecutor.name.string}")
                
                val applicantPlayer = playerExecutor.level().server.playerList.getPlayer(playerObject.id)
                if (applicantPlayer != null) {
                    applicantPlayer.sendSystemMessage(targetNotification)
                }
            } else {
                val cost = if (community.isManor()) 
                    PricingConfig.COMMUNITY_JOIN_COST_MANOR.value 
                    else PricingConfig.COMMUNITY_JOIN_COST_REALM.value
                
                val applicantPlayer = playerExecutor.level().server.playerList.getPlayer(playerObject.id)
                if (applicantPlayer != null) {
                    val playerData = com.imyvm.economy.EconomyMod.data.getOrCreate(applicantPlayer)
                    playerData.money += cost
                    applicantPlayer.sendSystemMessage(
                        com.imyvm.community.util.Translator.tr(
                            "community.join.refund",
                            cost / 100.0
                        )
                    )
                }
                
                objectAccount.basicRoleType = com.imyvm.community.domain.model.community.MemberRoleType.REFUSED
                objectAccount.joinedTime = System.currentTimeMillis()
                
                val targetNotification = com.imyvm.community.util.Translator.tr(
                    "community.notification.target.application_refused",
                    communityName,
                    playerExecutor.name.string
                ) ?: net.minecraft.network.chat.Component.literal("Your application to $communityName was refused by ${playerExecutor.name.string}")
                com.imyvm.community.application.interaction.common.notifyTargetPlayer(
                    playerExecutor.level().server, playerObject.id, targetNotification, community
                )
                
                constructAndSendMail(
                    objectAccount.mail,
                    playerExecutor,
                    community,
                    "ui.admin.audit.message.refuse.mail"
                )
            }
            trMenu(
                playerExecutor,
                "ui.admin.audit.message.refuse.success",
                playerObject.name
            )
            
            val notification = com.imyvm.community.util.Translator.tr(
                "community.notification.application_refused",
                playerObject.name,
                playerExecutor.name.string,
                communityName
            ) ?: net.minecraft.network.chat.Component.literal("${playerObject.name}'s application to $communityName was refused by ${playerExecutor.name.string}")
            notifyOfficials(community, playerExecutor.level().server, notification, playerExecutor)
            
            com.imyvm.community.infra.CommunityDatabase.save()
        }
    }
}