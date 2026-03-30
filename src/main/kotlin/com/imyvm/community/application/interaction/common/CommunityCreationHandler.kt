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
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent

fun onCreateCommunityRequest(
    player: ServerPlayer,
    communityType: String,
    communityName: String
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

    val shapeType = when (val hs = ImyvmWorldGeo.pointSelectingPlayers[player.uuid]?.hypotheticalShape) {
        is HypotheticalShape.Normal -> hs.shapeType
        else -> GeoShapeType.RECTANGLE
    }

    val region = PlayerInteractionApi.createAndGetRegion(player, communityName)
    if (region == null) {
        player.sendSystemMessage(Translator.tr("community.create.region.error"))
        return 0
    }

    val isManor = communityType.equals("manor", ignoreCase = true)
    val area = region.calculateTotalArea()
    val costResult = calculateCreationCost(area, isManor)

    val playerAccount = EconomyMod.data.getOrCreate(player)
    if (playerAccount.money < costResult.totalCost) {
        player.sendSystemMessage(Translator.tr("community.create.money.error", costResult.totalCost / 100.0))
        PlayerInteractionApi.deleteRegion(player, region)
        return 0
    }

    val regionNumberId = region.numberID

    val confirmationMessages = generateCreationConfirmationMessage(
        communityName = communityName,
        geoShapeType = shapeType,
        isManor = isManor,
        costResult = costResult
    )
    confirmationMessages.forEach { msg ->
        player.sendSystemMessage(msg)
    }

    addPendingOperation(
        regionId = regionNumberId,
        type = PendingOperationType.CREATE_COMMUNITY_CONFIRMATION,
        expireMinutes = 5,
        creationData = CreationConfirmationData(
            communityName = communityName,
            communityType = communityType,
            shapeName = shapeType.name,
            regionNumberId = regionNumberId,
            creatorUUID = player.uuid,
            totalCost = costResult.totalCost
        )
    )

    sendInteractiveConfirmation(player, regionNumberId)

    return 1
}


fun onConfirmCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

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

    playerAccount.addMoney(-creationData.totalCost)
    player.sendSystemMessage(Translator.tr("community.create.money.checked", creationData.totalCost / 100.0))

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)

    initialRequest(player, creationData.communityName, creationData.communityType, regionNumberId, creationData.totalCost)
    handleRequestBranches(player, creationData.communityType, regionNumberId)

    return 1
}

fun onCancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    return cancelCommunityCreation(player, regionNumberId)
}

private fun cancelCommunityCreation(player: ServerPlayer, regionNumberId: Int): Int {
    val pendingOp = WorldGeoCommunityAddon.pendingOperations[regionNumberId]

    if (pendingOp == null || pendingOp.type != PendingOperationType.CREATE_COMMUNITY_CONFIRMATION) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_found"))
        return 0
    }

    val creationData = pendingOp.creationData
    if (creationData == null || creationData.creatorUUID != player.uuid) {
        player.sendSystemMessage(Translator.tr("community.create.confirmation.not_yours"))
        return 0
    }

    val region = com.imyvm.iwg.inter.api.RegionDataApi.getRegion(regionNumberId)
    if (region != null) {
        PlayerInteractionApi.deleteRegion(player, region)
    }

    WorldGeoCommunityAddon.pendingOperations.remove(regionNumberId)

    player.sendSystemMessage(Translator.tr("community.create.confirmation.cancelled"))
    return 1
}

private fun initialRequest(player: ServerPlayer, name: String, communityType: String, regionNumberId: Int, creationCost: Long) {
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
}

private fun handleRequestBranches(player: ServerPlayer, communityType: String, regionNumberId: Int) {
    if (communityType.equals("manor", ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("community.create.request.sent"))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.AUDITING_COMMUNITY_REQUEST,
            expireHours = CommunityConfig.AUDITING_EXPIRE_HOURS.value
        )
        notifyOPsAndOwnerAboutCreationRequest(player, regionNumberId)
    } else if (communityType.equals("realm", ignoreCase = true)) {
        player.sendSystemMessage(Translator.tr("community.create.request.recruitment", CommunityConfig.MIN_NUMBER_MEMBER_REALM.value))
        addPendingOperation(
            regionId = regionNumberId,
            type = PendingOperationType.CREATE_COMMUNITY_REALM_REQUEST_RECRUITMENT,
            expireHours = CommunityConfig.REALM_REQUEST_EXPIRE_HOURS.value
        )
    }
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
    val confirmButton = Component.literal("§a§l[CONFIRM]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun confirm_creation $regionNumberId"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§aClick to confirm creation")
            ))
        }
    
    val cancelButton = Component.literal("§c§l[CANCEL]§r")
        .withStyle { style ->
            style.withClickEvent(ClickEvent.RunCommand("/_commun cancel_creation $regionNumberId"))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§cClick to cancel creation")
            ))
        }
    
    val promptMessage = Component.empty()
        .append(Component.literal("§e§l[ACTION REQUIRED]§r §ePlease confirm within §c§l5 minutes§r§e: "))
        .append(confirmButton)
        .append(Component.literal(" "))
        .append(cancelButton)
    
    player.sendSystemMessage(promptMessage)
}
