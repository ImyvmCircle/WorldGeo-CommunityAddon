package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.event.addPendingOperation
import com.imyvm.community.application.interaction.common.helper.calculateCreationCost
import com.imyvm.community.application.interaction.common.helper.checkPlayerMembershipCreation
import com.imyvm.community.application.interaction.common.helper.generateCreationConfirmationMessage
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.CreationConfirmationData
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.PendingOperationType
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.ImyvmWorldGeo
import com.imyvm.iwg.domain.component.GeoShapeType
import com.imyvm.iwg.domain.component.HypotheticalShape
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.infra.RegionDatabase
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import com.imyvm.community.application.event.getPendingOperation
import com.imyvm.community.application.event.removePendingOperation
import com.imyvm.community.application.event.restorePendingOperation

fun onCreateCommunityRequest(
    player: ServerPlayer,
    communityType: String,
    communityName: String,
    requestedShapeType: GeoShapeType? = null
): Int {
    if (!checkPlayerMembershipCreation(player, communityType)) return 0

    val existingPending = WorldGeoCommunityAddon.pendingOperations.values.find {
        it.type == PendingOperationType.CREATE_COMMUNITY_CONFIRMATION &&
        it.creationData?.creatorUUID == player.uuid
    }
    if (existingPending != null) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.pending"))
        return 0
    }

    if (requestedShapeType != null && ImyvmWorldGeo.pointSelectingPlayers.containsKey(player.uuid)) {
        if (PlayerInteractionApi.setSelectionShape(player, requestedShapeType) == 0) return 0
    }

    val shapeType = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> requestedShapeType ?: GeoShapeType.RECTANGLE
    }

    val region = PlayerInteractionApi.createAndGetRegion(player, communityName, idMark = 2)
    if (region == null) {
        player.sendSystemMessage(Translator.tr("community.create.region.error"))
        return 0
    }

    val isManor = communityType.equals("manor", ignoreCase = true)
    val costResult = calculateCreationCost(region, isManor)

    val playerAccount = EconomyMod.data.getOrCreate(player)
    if (playerAccount.money < costResult.totalCost) {
        player.sendSystemMessage(Translator.tr("community.create.money.error", costResult.totalCost / 100.0))
        deleteCreationRegion(region.numberID, player)
        return 0
    }

    val regionNumberId = region.numberID
    val actualShapeType = region.geometryScope.firstOrNull()?.geoShape?.geoShapeType ?: shapeType

    val confirmationMessages = generateCreationConfirmationMessage(
        communityName = communityName,
        geoShapeType = actualShapeType,
        isManor = isManor,
        costResult = costResult
    )
    confirmationMessages.forEach { msg ->
        player.sendSystemMessage(msg)
    }

    try {
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.CREATE_COMMUNITY_CONFIRMATION,
            expireMinutes = 5,
            creationData = CreationConfirmationData(
                communityName = communityName,
                communityType = communityType,
                shapeName = actualShapeType.name,
                regionNumberId = regionNumberId,
                creatorUUID = player.uuid,
                totalCost = costResult.totalCost
            )
        )

        sendInteractiveConfirmation(player, regionNumberId)
    } catch (e: Exception) {
        removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
        deleteCreationRegion(regionNumberId, player)
        WorldGeoCommunityAddon.logger.error("Failed to prepare community creation confirmation for region $regionNumberId", e)
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    return 1
}


fun onConfirmCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = getPendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    if (pendingOp == null || pendingOp.type != PendingOperationType.CREATE_COMMUNITY_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_found"))
        return 0
    }

    val creationData = pendingOp.creationData
    if (creationData == null || creationData.creatorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_yours"))
        return 0
    }

    if (System.currentTimeMillis() > pendingOp.expireAt) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.expired"))
        return 0
    }

    val playerAccount = EconomyMod.data.getOrCreate(player)
    if (playerAccount.money < creationData.totalCost) {
        player.sendSystemMessage(Translator.tr("community.create.money.error", creationData.totalCost / 100.0))
        cancelCommunityCreation(player, regionNumberId)
        return 0
    }

    var createdCommunity: Community? = null
    var removedConfirmation: com.imyvm.community.domain.model.PendingOperation? = null
    var branchPendingType: PendingOperationType? = null
    var moneyDeducted = false

    return try {
        playerAccount.addMoney(-creationData.totalCost)
        moneyDeducted = true
        player.sendSystemMessage(Translator.tr("community.create.money.checked", creationData.totalCost / 100.0))

        removedConfirmation = removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
        createdCommunity = initialRequest(player, creationData.communityName, creationData.communityType, regionNumberId, creationData.totalCost)
        branchPendingType = handleRequestBranches(player, creationData.communityType, regionNumberId)
        CommunityDatabase.save()

        1
    } catch (e: Exception) {
        createdCommunity?.let { CommunityDatabase.removeCommunity(it) }
        branchPendingType?.let { removePendingOperation(regionNumberId, it) }
        if (moneyDeducted) playerAccount.addMoney(creationData.totalCost)
        val cleaned = deleteCreationRegion(regionNumberId, player)
        if (!cleaned) {
            removedConfirmation?.let {
                restorePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION, it)
            }
        } else {
            removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)
        }
        WorldGeoCommunityAddon.logger.error("Failed to confirm community creation for region $regionNumberId", e)
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        0
    }
}

fun onCancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    return cancelCommunityCreation(player, regionNumberId)
}

private fun cancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = getPendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    if (pendingOp == null || pendingOp.type != PendingOperationType.CREATE_COMMUNITY_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_found"))
        return 0
    }

    val creationData = pendingOp.creationData
    if (creationData == null || creationData.creatorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_yours"))
        return 0
    }

    if (!deleteCreationRegion(regionNumberId, player)) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.failed"))
        return 0
    }

    removePendingOperation(regionNumberId, PendingOperationType.CREATE_COMMUNITY_CONFIRMATION)

    player.sendSystemMessage(Translator.tr("community.create.confirmation.cancelled"))
    return 1
}

internal fun deleteCreationRegion(regionNumberId: Int, player: ServerPlayer? = null): Boolean {
    val region = RegionDataApi.getRegion(regionNumberId) ?: return true

    if (player != null) {
        try {
            PlayerInteractionApi.deleteRegion(player, region)
            if (RegionDataApi.getRegion(regionNumberId) == null) return true
            WorldGeoCommunityAddon.logger.warn("Core API did not remove creation region $regionNumberId; falling back to RegionDatabase")
        } catch (e: Exception) {
            WorldGeoCommunityAddon.logger.warn("Core API failed to remove creation region $regionNumberId; falling back to RegionDatabase", e)
        }
    }

    return try {
        RegionDatabase.removeRegion(region)
        RegionDatabase.save()
        WorldGeoCommunityAddon.logger.info("Deleted creation region $regionNumberId by RegionDatabase fallback")
        true
    } catch (e: Exception) {
        WorldGeoCommunityAddon.logger.error("Failed to delete creation region $regionNumberId", e)
        false
    }
}

private fun initialRequest(player: ServerPlayer, name: String, communityType: String, regionNumberId: Int, creationCost: Long): Community {
    val community = Community(
        regionNumberId = regionNumberId,
        member = hashMapOf(player.uuid to MemberAccount(
            joinedTime = System.currentTimeMillis(),
            basicRoleType = MemberRoleType.OWNER
        )),
        joinPolicy = CommunityJoinPolicy.OPEN,
        status = if (communityType.equals("manor", ignoreCase = true)) {
            CommunityStatus.PENDING_MANOR
        } else {
            CommunityStatus.RECRUITING_REALM
        },
        creationCost = creationCost
    )

    CommunityDatabase.addCommunity(community)
    player.sendSystemMessage(Translator.tr("community.create.request.initial.success", name, community.regionNumberId))
    return community
}

private fun handleRequestBranches(player: ServerPlayer, communityType: String, regionNumberId: Int): PendingOperationType? {
    if (communityType.equals("manor", ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("community.create.request.sent"))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.AUDITING_COMMUNITY_REQUEST,
            expireHours = CommunityConfig.AUDITING_EXPIRE_HOURS.value
        )
        notifyOPsAndOwnerAboutCreationRequest(player, regionNumberId)
        return PendingOperationType.AUDITING_COMMUNITY_REQUEST
    } else if (communityType.equals("realm", ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("community.create.request.recruitment", CommunityConfig.MIN_NUMBER_MEMBER_REALM.value))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT,
            expireHours = CommunityConfig.REALM_REQUEST_EXPIRE_HOURS.value
        )
        return PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT
    }
    return null
}

internal fun notifyOPsAndOwnerAboutCreationRequest(creator: ServerPlayer, regionNumberId: Int) {
    val message = Translator.tr(
        "community.create.notification.new_request",
        creator.name.string,
        regionNumberId
    )
    
    creator.level().server.playerList.players.forEach { player ->
        if (net.minecraft.commands.Commands.LEVEL_GAMEMASTERS.check(player.permissions()) || player.uuid == creator.uuid) {
            player.sendSystemMessage(message)
        }
    }
}

private fun sendInteractiveConfirmation(player: ServerPlayer, regionNumberId: Int) {
    val confirmButton = Translator.tr("community.create.confirmation.button.confirm")
        .copy()
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_creation $regionNumberId"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.create.confirmation.button.confirm.hover")))
        }

    val cancelButton = Translator.tr("community.create.confirmation.button.cancel")
        .copy()
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_creation $regionNumberId"))
                .withHoverEvent(HoverEvent.ShowText(Translator.tr("community.create.confirmation.button.cancel.hover")))
        }

    val promptMessage = Component.empty()
        .append(Translator.tr("community.create.confirmation.interactive_prompt"))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)

    player.sendSystemMessage(promptMessage)
}
