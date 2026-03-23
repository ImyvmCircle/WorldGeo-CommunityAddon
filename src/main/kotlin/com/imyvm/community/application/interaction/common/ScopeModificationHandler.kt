package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

private fun getAndValidatePendingOperation(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): com.imyvm.community.domain.model.PendingOperation? {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

    if (pendingOp == null || pendingOp.type != PendingOperationType.MODIFY_SCOPE_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.modification.confirmation.not_found"))
        return null
    }

    val modificationData = pendingOp.modificationData
    if (modificationData == null || modificationData.executorUUID != player.uuid || modificationData.scopeName != scopeName) {
        player.sendMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return null
    }

    return pendingOp
}

fun onConfirmScopeModification(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    val pendingOp = getAndValidatePendingOperation(player, regionNumberId, scopeName) ?: return 0
    val modificationData = pendingOp.modificationData!!

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendMessage(Translator.tr("community.modification.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    if (community == null) {
        player.sendMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val currentAssets = community.getTotalAssets()
    if (modificationData.cost > 0 && currentAssets < modificationData.cost) {
        player.sendMessage(Translator.tr("community.modification.error.insufficient_assets",
            String.format("%.2f", modificationData.cost / 100.0),
            String.format("%.2f", currentAssets / 100.0)
        ) ?: Text.literal("Insufficient assets: need ${modificationData.cost / 100.0}, have ${currentAssets / 100.0}"))
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendMessage(Translator.tr("community.modification.error.no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (modificationData.isScopeCreation) {
        val shapeName = modificationData.shapeName ?: "RECTANGLE"
        PlayerInteractionApi.addScope(player, communityRegion, scopeName)
        val createdScope = communityRegion.geometryScope.firstOrNull { it.scopeName.equals(scopeName, ignoreCase = true) }
        if (createdScope == null) {
            player.sendMessage(Translator.tr("community.scope_add.error.creation_failed", scopeName))
            return 0
        }

        PlayerInteractionApi.resetTeleportPoint(player, communityRegion, createdScope)
        if (RegionDataApi.inquireTeleportPointAccessibility(createdScope)) {
            PlayerInteractionApi.toggleTeleportPointAccessibility(createdScope)
        }

        if (modificationData.cost > 0) {
            community.expenditures.add(Turnover(
                amount = modificationData.cost,
                timestamp = System.currentTimeMillis()
            ))
        }

        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        CommunityDatabase.save()

        val costDisplay = String.format("%.2f", modificationData.cost / 100.0)
        val shapeText = when (shapeName.uppercase()) {
            "CIRCLE" -> Translator.tr("community.shape.circle")?.string ?: "circle"
            "POLYGON" -> Translator.tr("community.shape.polygon")?.string ?: "polygon"
            else -> Translator.tr("community.shape.rectangle")?.string ?: "rectangle"
        }
        player.sendMessage(Translator.tr("community.scope_add.success", scopeName, shapeText, costDisplay))
        if (modificationData.softLimitSurcharge > 0) {
            player.sendMessage(Translator.tr(
                "community.scope_add.success.surcharge_note",
                String.format("%.2f", modificationData.softLimitSurcharge / 100.0)
            ))
        }

        val territoryType = if (community.isManor()) {
            Translator.tr("community.region.territory.manor")?.string ?: "manor territory"
        } else {
            Translator.tr("community.region.territory.realm")?.string ?: "realm territory"
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
        ) ?: Text.literal("Administrative district $scopeName was added in $communityName by ${player.name.string}")
        notifyFormalMembers(community, player.server, notification)
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
            timestamp = System.currentTimeMillis()
        ))
    } else if (modificationData.cost < 0) {
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

    val costDisplay = String.format("%.2f", Math.abs(modificationData.cost) / 100.0)

    if (modificationData.cost >= 0) {
        player.sendMessage(Translator.tr("community.modification.success.charged", scopeName, costDisplay))
    } else {
        player.sendMessage(Translator.tr("community.modification.success.refunded", scopeName, costDisplay))
    }

    val territoryType = if (community.isManor()) {
        Translator.tr("community.region.territory.manor")?.string ?: "manor territory"
    } else {
        Translator.tr("community.region.territory.realm")?.string ?: "realm territory"
    }

    val districtType = Translator.tr("community.region.district")?.string ?: "administrative district"

    val communityName = communityRegion.name
    val notification = Translator.tr(
        "community.notification.geometry_modified",
        districtType,
        scopeName,
        territoryType,
        communityName,
        player.name.string
    ) ?: Text.literal("$districtType '$scopeName' was modified in $territoryType '$communityName' by ${player.name.string}")

    notifyOfficials(community, player.server, notification, player)

    return 1
}

fun onCancelScopeModification(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    if (getAndValidatePendingOperation(player, regionNumberId, scopeName) == null) return 0

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    player.sendMessage(Translator.tr("community.modification.confirmation.cancelled", scopeName))
    return 1
}

private fun getAndValidateDeletionPendingOperation(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): com.imyvm.community.domain.model.PendingOperation? {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

    if (pendingOp == null || pendingOp.type != PendingOperationType.DELETE_SCOPE_CONFIRMATION) {
        player.sendMessage(Translator.tr("community.scope_delete.confirmation.not_found"))
        return null
    }

    val modificationData = pendingOp.modificationData
    if (modificationData == null || modificationData.executorUUID != player.uuid || modificationData.scopeName != scopeName) {
        player.sendMessage(Translator.tr("community.modification.confirmation.not_yours"))
        return null
    }

    return pendingOp
}

fun onConfirmScopeDeletion(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    val pendingOp = getAndValidateDeletionPendingOperation(player, regionNumberId, scopeName) ?: return 0
    val modificationData = pendingOp.modificationData!!

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendMessage(Translator.tr("community.modification.confirmation.expired"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val community = CommunityDatabase.getCommunityById(regionNumberId)
    if (community == null) {
        player.sendMessage(Translator.tr("community.modification.error.community_not_found"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    val communityRegion = community.getRegion()
    if (communityRegion == null) {
        player.sendMessage(Translator.tr("community.modification.error.no_region"))
        WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
        return 0
    }

    if (communityRegion.geometryScope.size <= 1) {
        player.sendMessage(Translator.tr("community.scope_delete.error.last_scope"))
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
    player.sendMessage(Translator.tr("community.scope_delete.success", scopeName, refundDisplay))

    val territoryType = if (community.isManor()) {
        Translator.tr("community.region.territory.manor")?.string ?: "manor territory"
    } else {
        Translator.tr("community.region.territory.realm")?.string ?: "realm territory"
    }

    val communityName = communityRegion.name
    val notification = Translator.tr(
        "community.notification.scope_deleted",
        scopeName,
        territoryType,
        communityName,
        player.name.string,
        refundDisplay
    ) ?: Text.literal("Administrative district '$scopeName' was sold in $territoryType '$communityName' by ${player.name.string}")

    notifyFormalMembers(community, player.server, notification)
    return 1
}

fun onCancelScopeDeletion(player: ServerPlayerEntity, regionNumberId: Int, scopeName: String): Int {
    if (getAndValidateDeletionPendingOperation(player, regionNumberId, scopeName) == null) return 0

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)
    player.sendMessage(Translator.tr("community.scope_delete.confirmation.cancelled", scopeName))
    return 1
}


private fun notifyFormalMembers(
    community: com.imyvm.community.domain.model.Community,
    server: MinecraftServer,
    message: Text
) {
    community.member.forEach { (memberUUID, memberAccount) ->
        if (memberAccount.basicRoleType == MemberRoleType.APPLICANT || memberAccount.basicRoleType == MemberRoleType.REFUSED) {
            return@forEach
        }
        server.playerManager.getPlayer(memberUUID)?.sendMessage(message)
        memberAccount.mail.add(message)
    }
}
