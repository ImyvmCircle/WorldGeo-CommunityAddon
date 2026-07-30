package com.imyvm.community.application.interaction.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.common.runCommunityMutationOrRollback
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.community.infra.economy.EconomyWalletAdapter
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.util.Translator
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
            val wasInvited = objectAccount.isInvited
            val previousRole = objectAccount.basicRoleType
            val previousJoinedTime = objectAccount.joinedTime
            val previousInvited = objectAccount.isInvited
            var joinFeeTurnover: com.imyvm.community.domain.model.Turnover? = null

            if (wasInvited) {
                val cost = if (community.isManor())
                    PricingConfig.COMMUNITY_JOIN_COST_MANOR.value
                    else PricingConfig.COMMUNITY_JOIN_COST_REALM.value

                if (community.getTotalAssets() < cost) {
                    playerExecutor.sendSystemMessage(
                        Translator.tr(
                            "community.audit.error.insufficient_assets",
                            playerObject.name,
                            cost / 100.0
                        )
                    )
                    playerExecutor.closeContainer()
                    return@executeWithPermission
                }

                joinFeeTurnover = com.imyvm.community.domain.model.Turnover(
                    amount = cost,
                    timestamp = System.currentTimeMillis(),
                    source = TurnoverSource.SYSTEM,
                    descriptionKey = "community.treasury.desc.member_join_fee",
                    descriptionArgs = listOf(playerObject.name)
                )
            }

            val communityName = community.getRegion()?.name ?: "Community #${community.regionNumberId}"
            val operationName = Translator.tr("community.operation.audit_accept", playerObject.name).string

            if (!runCommunityMutationOrRollback(
                    operationName = operationName,
                    mutateCommunityState = {
                        joinFeeTurnover?.let { community.expenditures.add(it) }
                        objectAccount.basicRoleType = MemberRoleType.MEMBER
                        objectAccount.joinedTime = System.currentTimeMillis()
                        objectAccount.isInvited = false
                        com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.autoGrantDefaultPermissions(
                            playerObject.id, playerExecutor, community
                        )
                    },
                    restoreCommunityState = {
                        joinFeeTurnover?.let { community.expenditures.remove(it) }
                        objectAccount.basicRoleType = previousRole
                        objectAccount.joinedTime = previousJoinedTime
                        objectAccount.isInvited = previousInvited
                    },
                    rollbackCoreState = {
                        com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.revokeGrantedPermissions(
                            playerObject.id, community
                        )
                    },
                    saveCommunityState = { CommunityDatabase.save() },
                    notifyFailure = { playerExecutor.sendSystemMessage(Translator.tr("community.operation.save_failed", operationName)) }
                )
            ) {
                playerExecutor.closeContainer()
                return@executeWithPermission
            }

            val targetNotificationKey = if (wasInvited) {
                "community.notification.target.invitation_accepted"
            } else {
                "community.notification.target.accepted"
            }
            val targetNotification = Translator.tr(
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

            val notification = Translator.tr("community.notification.member_accepted", playerObject.name, playerExecutor.name.string, communityName)
                ?: net.minecraft.network.chat.Component.literal("${playerObject.name} has been accepted to $communityName by ${playerExecutor.name.string}")
            notifyOfficials(community, playerExecutor.level().server, notification, playerExecutor)

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
                    EconomyWalletAdapter.credit(applicantPlayer, cost)
                    applicantPlayer.sendSystemMessage(
                        Translator.tr(
                            "community.join.refund",
                            cost / 100.0
                        )
                    )
                } else {
                    objectAccount.pendingRefund += cost
                    playerExecutor.sendSystemMessage(
                        Translator.tr(
                            "community.audit.refund.deferred",
                            playerObject.name,
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