package com.imyvm.community.application.interaction.command

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.helper.refundNotCreated
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.level.ServerPlayer

fun onForceDeleteCommunity(player: ServerPlayer, targetCommunity: Community): Int {
    WorldGeoCommunityAddon.pendingOperations.remove(targetCommunity.regionNumberId)

    val region = targetCommunity.regionNumberId?.let { RegionDataApi.getRegion(it) }
    if (region != null) {
        PlayerInteractionApi.deleteRegion(player, region)
    }

    CommunityDatabase.removeCommunity(targetCommunity)


    if (region != null) {
        player.sendSystemMessage(Translator.tr("community.delete.success",
            region.name,
            targetCommunity.regionNumberId))
    } else {
        player.sendSystemMessage(Translator.tr("community.delete.success.null_region"))
    }

    return 1
}

fun onAudit(player: ServerPlayer, choice: String, targetCommunity: Community): Int {
    if (!checkPendingPreAuditing(player, targetCommunity)) return 0
    return handleAuditingChoices(player, choice, targetCommunity)
}


fun onForceRevoke(player: ServerPlayer, targetCommunity: Community): Int {
    revokeCommunity(targetCommunity)
    player.sendSystemMessage(Translator.tr("community.revoke.success", targetCommunity.regionNumberId))
    return 1
}

fun onForceActive(player: ServerPlayer, targetCommunity: Community): Int {
    when (targetCommunity.status) {
        CommunityStatus.REVOKED_MANOR -> promoteToActiveManor(player, targetCommunity)
        CommunityStatus.REVOKED_REALM -> promoteToActiveRealm(player, targetCommunity)
        else -> {
            player.sendSystemMessage(Translator.tr("community.force_active.error_invalid_status", targetCommunity.regionNumberId))
            return 0
        }
    }
    player.sendSystemMessage(Translator.tr("community.force_active.success", targetCommunity.regionNumberId))
    return 1
}

private fun checkPendingPreAuditing(player: ServerPlayer, targetCommunity: Community): Boolean {
    val regionId = targetCommunity.regionNumberId
    if (WorldGeoCommunityAddon.pendingOperations[regionId] == null) {
        player.sendSystemMessage(Translator.tr("community.audit.error.no_pending", regionId))
        return false
    }
    WorldGeoCommunityAddon.pendingOperations.remove(regionId)
    return true
}

private fun handleAuditingChoices(player: ServerPlayer, choice: String, targetCommunity: Community): Int {
    when (choice.lowercase()) {
        "yes" -> {
            when (targetCommunity.status) {
                CommunityStatus.PENDING_MANOR -> promoteToActiveManor(player, targetCommunity)
                CommunityStatus.PENDING_REALM -> promoteToActiveRealm(player, targetCommunity)
                else -> {
                    player.sendSystemMessage(Translator.tr("community.audit.error.invalid_status", targetCommunity.regionNumberId))
                    return 0
                }
            }
            player.sendSystemMessage(Translator.tr("community.audit.approved", targetCommunity.regionNumberId))
            notifyOPsAndOwnerAboutAuditApproved(player, targetCommunity)
            return 1
        }
        "no" -> {
            val owner = getOwnerPlayer(targetCommunity, player.level().server)
            val refundAmount = targetCommunity.creationCost / 100.0
            revokeCommunity(targetCommunity)
            if (owner != null) {
                refundNotCreated(owner, targetCommunity)
            }
            player.sendSystemMessage(Translator.tr("community.audit.denied", targetCommunity.regionNumberId))
            notifyOPsAndOwnerAboutAuditDenied(player, targetCommunity, refundAmount, owner)
            return 1
        }
        else -> {
            player.sendSystemMessage(Translator.tr("community.audit.error.invalid_choice", choice))
            return 0
        }
    }
}

private fun notifyOPsAndOwnerAboutAuditApproved(auditor: ServerPlayer, community: Community) {
    val ownerUUID = getOwnerUUID(community)
    val message = Translator.tr(
        "community.audit.notification.approved",
        community.regionNumberId,
        auditor.name.string
    )
    
    auditor.level().server.playerList.players.forEach { player ->
        if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || player.uuid == ownerUUID) {
            player.sendSystemMessage(message)
        }
    }
}

private fun notifyOPsAndOwnerAboutAuditDenied(auditor: ServerPlayer, community: Community, refundAmount: Double, owner: ServerPlayer?) {
    val ownerUUID = getOwnerUUID(community)
    val refundText = String.format("%.2f", refundAmount)
    val message = Translator.tr(
        "community.audit.notification.denied",
        community.regionNumberId,
        auditor.name.string,
        refundText
    )
    
    auditor.level().server.playerList.players.forEach { player ->
        if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || player.uuid == ownerUUID) {
            player.sendSystemMessage(message)
        }
    }
}

private fun getOwnerUUID(community: Community): java.util.UUID? {
    return community.member.entries.find { 
        community.getMemberRole(it.key) == com.imyvm.community.domain.model.community.MemberRoleType.OWNER 
    }?.key
}

private fun promoteToActiveManor(player: ServerPlayer, targetCommunity: Community) {
    targetCommunity.status = CommunityStatus.ACTIVE_MANOR
    player.sendSystemMessage(Translator.tr("community.audit.manor.activated", targetCommunity.regionNumberId))
    WorldGeoCommunityAddon.logger.info("Community ${targetCommunity.regionNumberId} promoted to ACTIVE_MANOR by player ${player.uuid}.")
}

private fun promoteToActiveRealm(player: ServerPlayer, targetCommunity: Community) {
    targetCommunity.status = CommunityStatus.ACTIVE_REALM
    player.sendSystemMessage(Translator.tr("community.audit.realm.activated", targetCommunity.regionNumberId))
    WorldGeoCommunityAddon.logger.info("Community ${targetCommunity.regionNumberId} promoted to ACTIVE_REALM by player ${player.uuid}.")
}

private fun revokeCommunity(targetCommunity: Community) {
    targetCommunity.status = when (targetCommunity.status) {
        CommunityStatus.PENDING_MANOR, CommunityStatus.ACTIVE_MANOR -> CommunityStatus.REVOKED_MANOR
        CommunityStatus.PENDING_REALM, CommunityStatus.RECRUITING_REALM, CommunityStatus.ACTIVE_REALM -> CommunityStatus.REVOKED_REALM
        else -> targetCommunity.status
    }
}

private fun getOwnerPlayer(community: Community, server: net.minecraft.server.MinecraftServer): ServerPlayer? {
    val ownerUUID = community.member.entries.find { 
        community.getMemberRole(it.key) == com.imyvm.community.domain.model.community.MemberRoleType.OWNER 
    }?.key
    return ownerUUID?.let { server.playerList.getPlayer(it) }
}

fun onAdminTreasuryDeposit(player: ServerPlayer, targetCommunity: Community, amountDisplay: Double, description: String?): Int {
    val amount = (amountDisplay * 100).toLong()
    if (amount <= 0) {
        player.sendSystemMessage(Translator.tr("community.treasury.admin.error.invalid_amount"))
        return 0
    }
    val descArgs = if (description.isNullOrBlank()) listOf("") else listOf(description)
    targetCommunity.communityIncome.add(
        Turnover(amount, System.currentTimeMillis(), TurnoverSource.SERVER_ADMIN, "community.treasury.desc.admin_deposit", descArgs)
    )
    CommunityDatabase.save()
    val amountFormatted = "%.2f".format(amountDisplay)
    player.sendSystemMessage(Translator.tr("community.treasury.admin.deposit.success", targetCommunity.generateCommunityMark(), amountFormatted))
    return 1
}

fun onAdminTreasuryWithdraw(player: ServerPlayer, targetCommunity: Community, amountDisplay: Double, description: String?): Int {
    val amount = (amountDisplay * 100).toLong()
    if (amount <= 0) {
        player.sendSystemMessage(Translator.tr("community.treasury.admin.error.invalid_amount"))
        return 0
    }
    if (targetCommunity.getTotalAssets() < amount) {
        player.sendSystemMessage(Translator.tr(
            "community.treasury.admin.error.insufficient_assets",
            "%.2f".format(amountDisplay),
            "%.2f".format(targetCommunity.getTotalAssets() / 100.0)
        ))
        return 0
    }
    val descArgs = if (description.isNullOrBlank()) listOf("") else listOf(description)
    targetCommunity.expenditures.add(
        Turnover(amount, System.currentTimeMillis(), TurnoverSource.SERVER_ADMIN, "community.treasury.desc.admin_withdrawal", descArgs)
    )
    CommunityDatabase.save()
    val amountFormatted = "%.2f".format(amountDisplay)
    player.sendSystemMessage(Translator.tr("community.treasury.admin.withdraw.success", targetCommunity.generateCommunityMark(), amountFormatted))
    return 1
}
