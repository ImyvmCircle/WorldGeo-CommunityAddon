package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

private fun getAndValidatePendingOperation(player: ServerPlayer, regionNumberId: Int, scopeName: String): com.imyvm.community.domain.model.PendingOperation? {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

    if (pendingOp == null || pendingOp.type != PendingOperationType.MODIFY_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_found"))
        return null
    }

    val modificationData = pendingOp.modificationData
    if (modificationData == null || modificationData.executorUUID != player.uuid || modificationData.scopeName != scopeName) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return null
    }

    return pendingOp
}

fun onConfirmScopeModification(player: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    val pendingOp = getAndValidatePendingOperation(player, regionNumberId, scopeName) ?: return 0
    val modificationData = pendingOp.modificationData!!

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    if (community == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val currentAssets = community.getTotalAssets()
    if (modificationData.cost > 0 && currentAssets < modificationData.cost) {
        player.sendSystemMessage(Translator.tr("community.modification.error.insufficient_assets",
            String.format("%.2f", modificationData.cost / 100.0),
            String.format("%.2f", currentAssets / 100.0)
        ) ?: Component.literal("Insufficient assets: need ${modificationData.cost / 100.0}, have ${currentAssets / 100.0}"))
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (modificationData.isScopeCreation) {
        val shapeName = modificationData.shapeName ?: "RECTANGLE"
        PlayerInteractionApi.addScope(player, communityRegion, scopeName)
        val createdScope = communityRegion.geometryScope.firstOrNull { it.scopeName.equals(scopeName, ignoreCase = true) }
        if (createdScope == null) {
            player.sendSystemMessage(Translator.tr("community.scope_add.error.creation_failed", scopeName))
            return 0
        }

        PlayerInteractionApi.resetTeleportPoint(player, communityRegion, createdScope)
        if (RegionDataApi.inquireTeleportPointAccessibility(createdScope)) {
            PlayerInteractionApi.toggleTeleportPointAccessibility(createdScope)
        }

        if (modificationData.cost > 0) {
            community.expenditures.add(Turnover(
                amount = modificationData.cost,
                timestamp = System.currentTimeMillis(),
                source = TurnoverSource.SYSTEM,
                descriptionKey = "community.treasury.desc.scope_creation",
                descriptionArgs = listOf(scopeName)
            ))
        }

        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        CommunityDatabase.save()

        val costDisplay = String.format("%.2f", modificationData.cost / 100.0)
        val shapeText = when (shapeName.uppercase()) {
            "CIRCLE" -> Translator.tr("community.shape.circle").string ?: "circle"
            "POLYGON" -> Translator.tr("community.shape.polygon").string ?: "polygon"
            else -> Translator.tr("community.shape.rectangle").string ?: "rectangle"
        }
        player.sendSystemMessage(Translator.tr("community.scope_add.success", scopeName, shapeText, costDisplay))
        if (modificationData.softLimitSurcharge > 0) {
            player.sendSystemMessage(Translator.tr(
                "community.scope_add.success.surcharge_note",
                String.format("%.2f", modificationData.softLimitSurcharge / 100.0)
            ))
        }

        val territoryType = if (community.isManor()) {
            Translator.tr("community.region.territory.manor").string ?: "manor territory"
        } else {
            Translator.tr("community.region.territory.realm").string ?: "realm territory"
        }
        val communityName = communityRegion.name
        val notification = Translator.tr(
            "community.notification.scope_added",
            scopeName,
            shapeText,
            territoryType,
            communityName,
            player.name.string,
            costDisplay
        ) ?: Component.literal("Administrative district $scopeName was added in $communityName by ${player.name.string}")
        notifyFormalMembers(community, player.level().server, notification)
        return 1
    }

    PlayerInteractionApi.modifyScope(player, communityRegion, scopeName)
    if (ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (modificationData.cost > 0) {
        community.expenditures.add(Turnover(
            amount = modificationData.cost,
            timestamp = System.currentTimeMillis(),
            source = TurnoverSource.SYSTEM,
            descriptionKey = "community.treasury.desc.scope_modification",
            descriptionArgs = listOf(scopeName)
        ))
    } else if (modificationData.cost < 0) {
        val refundAmount = -modificationData.cost
        val ownerUUID = community.getOwnerUUID()
        if (ownerUUID != null) {
            val ownerAccount = community.member[ownerUUID]
            ownerAccount?.turnover?.add(Turnover(
                amount = refundAmount,
                timestamp = System.currentTimeMillis(),
                source = TurnoverSource.SYSTEM,
                descriptionKey = "community.treasury.desc.scope_refund",
                descriptionArgs = listOf(scopeName)
            ))
        }
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    CommunityDatabase.save()

    val costDisplay = String.format("%.2f", Math.abs(modificationData.cost) / 100.0)

    if (modificationData.cost >= 0) {
        player.sendSystemMessage(Translator.tr("community.modification.success.charged", scopeName, costDisplay))
    } else {
        player.sendSystemMessage(Translator.tr("community.modification.success.refunded", scopeName, costDisplay))
    }

    val territoryType = if (community.isManor()) {
        Translator.tr("community.region.territory.manor").string ?: "manor territory"
    } else {
        Translator.tr("community.region.territory.realm").string ?: "realm territory"
    }

    val districtType = Translator.tr("community.region.district").string ?: "administrative district"

    val communityName = communityRegion.name
    val notification = Translator.tr(
        "community.notification.geometry_modified",
        districtType,
        scopeName,
        territoryType,
        communityName,
        player.name.string
    ) ?: Component.literal("$districtType '$scopeName' was modified in $territoryType '$communityName' by ${player.name.string}")

    notifyOfficials(community, player.level().server, notification, player)

    return 1
}

fun onCancelScopeModification(player: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    if (getAndValidatePendingOperation(player, regionNumberId, scopeName) == null) return 0

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    player.sendSystemMessage(Translator.tr("community.modification.confirmation.cancelled", scopeName))
    return 1
}

private fun getAndValidateDeletionPendingOperation(player: ServerPlayer, regionNumberId: Int, scopeName: String): com.imyvm.community.domain.model.PendingOperation? {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

    if (pendingOp == null || pendingOp.type != PendingOperationType.DELETE_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.scope_delete.confirmation.not_found"))
        return null
    }

    val modificationData = pendingOp.modificationData
    if (modificationData == null || modificationData.executorUUID != player.uuid || modificationData.scopeName != scopeName) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return null
    }

    return pendingOp
}

fun onConfirmScopeDeletion(player: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    val pendingOp = getAndValidateDeletionPendingOperation(player, regionNumberId, scopeName) ?: return 0
    val modificationData = pendingOp.modificationData!!

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    if (community == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (communityRegion.geometryScope.size <= 1) {
        player.sendSystemMessage(Translator.tr("community.scope_delete.error.last_scope"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    PlayerInteractionApi.deleteScope(player, communityRegion, scopeName)

    if (modificationData.cost < 0) {
        val refundAmount = -modificationData.cost
        val ownerUUID = community.getOwnerUUID()
        if (ownerUUID != null) {
            val ownerAccount = community.member[ownerUUID]
            ownerAccount?.turnover?.add(Turnover(
                amount = refundAmount,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    CommunityDatabase.save()

    val refundDisplay = String.format("%.2f", Math.abs(modificationData.cost) / 100.0)
    player.sendSystemMessage(Translator.tr("community.scope_delete.success", scopeName, refundDisplay))

    val territoryType = if (community.isManor()) {
        Translator.tr("community.region.territory.manor").string ?: "manor territory"
    } else {
        Translator.tr("community.region.territory.realm").string ?: "realm territory"
    }

    val communityName = communityRegion.name
    val notification = Translator.tr(
        "community.notification.scope_deleted",
        scopeName,
        territoryType,
        communityName,
        player.name.string,
        refundDisplay
    ) ?: Component.literal("Administrative district '$scopeName' was sold in $territoryType '$communityName' by ${player.name.string}")

    notifyFormalMembers(community, player.level().server, notification)
    return 1
}

fun onCancelScopeDeletion(player: ServerPlayer, regionNumberId: Int, scopeName: String): Int {
    if (getAndValidateDeletionPendingOperation(player, regionNumberId, scopeName) == null) return 0

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    player.sendSystemMessage(Translator.tr("community.scope_delete.confirmation.cancelled", scopeName))
    return 1
}

fun onAcceptTerritoryGrant(player: ServerPlayer, sourceRegionId: Int, scopeName: String): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TRANSFER_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_found"))
        return 0
    }

    val transferData = pendingOp.transferData
    if (transferData == null || transferData.scopeName != scopeName) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_found"))
        return 0
    }

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val targetCommunity = CommunityDatabase.getCommunityById(transferData.targetRegionNumberId)
    if (targetCommunity == null) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.error.target_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    if (!isEligibleGrantRecipient(player, targetCommunity)) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_eligible"))
        return 0
    }

    val sourceCommunity = CommunityDatabase.getCommunityById(sourceRegionId)
    if (sourceCommunity == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val sourceRegion = sourceCommunity.getRegion()
    if (sourceRegion == null) {
        player.sendSystemMessage(Translator.tr("community.modification.error.no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    if (sourceRegion.geometryScope.size <= 1) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.error.last_scope"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val targetRegion = targetCommunity.getRegion()
    if (targetRegion == null) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.error.target_no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
        return 0
    }

    val result = PlayerInteractionApi.transferScope(player, sourceRegion, scopeName, targetRegion)
    if (result == 0) return 0

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    CommunityDatabase.save()

    val sourceName = sourceCommunity.generateCommunityMark()
    val targetName = targetCommunity.generateCommunityMark()
    player.sendSystemMessage(Translator.tr("community.scope_transfer.success", scopeName, sourceName, targetName))

    val notification = Translator.tr(
        "community.notification.scope_transferred",
        scopeName,
        sourceName,
        targetName,
        player.name.string
    ) ?: Component.literal("§b§l[领土赠予]§r §e辖区 §b§l$scopeName§r §e已从 §f§l$sourceName§r §e赠予至 §a§l$targetName§r §e（接受者：§d§l${player.name.string}§r§e）")

    notifyFormalMembers(sourceCommunity, player.level().server, notification)
    notifyFormalMembers(targetCommunity, player.level().server, notification)

    return 1
}

fun onDeclineTerritoryGrant(player: ServerPlayer, sourceRegionId: Int, scopeName: String): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TRANSFER_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_found"))
        return 0
    }

    val transferData = pendingOp.transferData
    if (transferData == null || transferData.scopeName != scopeName) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_found"))
        return 0
    }

    val targetCommunity = CommunityDatabase.getCommunityById(transferData.targetRegionNumberId)
    if (targetCommunity != null && !isEligibleGrantRecipient(player, targetCommunity)) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_eligible"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.declined", scopeName))

    // Notify the initiator that the grant was declined
    val executorId = transferData.executorUUID
    player.level().server.playerList.getPlayer(executorId)?.sendSystemMessage(
        Translator.tr("community.scope_transfer.confirmation.declined_notify", scopeName, player.name.string)
    )
    return 1
}

fun onCancelTerritoryGrant(player: ServerPlayer, sourceRegionId: Int, scopeName: String): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[sourceRegionId]
    if (pendingOp == null || pendingOp.type != PendingOperationType.TRANSFER_SCOPE_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.not_found"))
        return 0
    }

    val transferData = pendingOp.transferData
    if (transferData == null || transferData.executorUUID != player.uuid || transferData.scopeName != scopeName) {
        player.sendSystemMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return 0
    }

    WorldGeoCommunityAddon.pendingOperations.remove(sourceRegionId)
    player.sendSystemMessage(Translator.tr("community.scope_transfer.confirmation.cancelled", scopeName))

    val targetCommunity = CommunityDatabase.getCommunityById(transferData.targetRegionNumberId)
    if (targetCommunity != null) {
        val cancelMsg = Translator.tr("community.scope_transfer.confirmation.cancelled_notify", scopeName, player.name.string)
        getEligibleGrantRecipients(targetCommunity, player.level().server).forEach { it.sendSystemMessage(cancelMsg) }
    }
    return 1
}

fun isEligibleGrantRecipient(player: ServerPlayer, community: Community): Boolean {
    val account = community.member[player.uuid] ?: return false
    return when (account.basicRoleType) {
        MemberRoleType.OWNER -> true
        MemberRoleType.ADMIN -> account.adminPrivileges?.isEnabled(AdminPrivilege.MODIFY_REGION_GEOMETRY) == true
        else -> false
    }
}

fun getEligibleGrantRecipients(community: Community, server: MinecraftServer): List<ServerPlayer> {
    return community.member.entries.mapNotNull { (uuid, account) ->
        val isEligible = when (account.basicRoleType) {
            MemberRoleType.OWNER -> true
            MemberRoleType.ADMIN -> account.adminPrivileges?.isEnabled(AdminPrivilege.MODIFY_REGION_GEOMETRY) == true
            else -> false
        }
        if (isEligible) server.playerList.getPlayer(uuid) else null
    }.filterNotNull()
}


private fun notifyFormalMembers(
    community: com.imyvm.community.domain.model.Community,
    server: MinecraftServer,
    message: Component
) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerList.getPlayer(memberUUID)?.sendSystemMessage(message)
        memberAccount.mail.add(message)
    }
}
