package com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.domain.MemberAccount
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.element.CommunityMemberMenu
import com.imyvm.community.entrypoints.screen.inner_community.multi_parent.CommunityRegionScopeMenu
import com.imyvm.community.entrypoints.screen.inner_community.administration_only.NotificationMenuAnvil
import com.imyvm.community.util.Translator
import com.imyvm.community.util.Translator.trMenu
import com.mojang.authlib.GameProfile
import net.minecraft.server.network.ServerPlayerEntity

fun runOpenPlayerRegionScopeChoice(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    runBackGrandfather: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityRegionScopeMenu(
            syncId = syncId,
            playerExecutor = playerExecutor,
            community = community,
            geographicFunctionType = GeographicFunctionType.SETTING_ADJUSTMENT,
            playerObject = playerObject
        ) { runBackToMemberMenu(playerExecutor, community, playerObject, runBackGrandfather) }
    }
}

fun runRemoveMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    val memberValue = community.member[playerObject.id]
    if (memberValue == null) {
        trMenu(
            playerExecutor,
            "community.operation.member.remove.fail.not_member",
            playerObject.name
        )
        return
    }

    community.member.remove(playerObject.id)
    trMenu(
        playerExecutor,
        "community.operation.member.remove.success",
        playerObject.name
    )
}

fun runNotifyMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    val handler = NotificationMenuAnvil(
        playerExecutor,
        initialName = Translator.tr("ui.community.operation.member.notify.to_edit")?.string ?: "(Edit your notification here)",
        playerObject = playerObject,
        community = community
    )
    handler.open()
}

fun runPromoteMember(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    governorship: Int = -1,
    isPromote: Boolean = true
) {
    if (isPromote) {
        if (governorship == -1) {
            handleRolePromotion(community, playerExecutor, playerObject)
        } else {
            handleGovernorshipUpdate(community, playerExecutor, playerObject, governorship)
        }
    } else {
        handleRoleDemotion(community, playerExecutor, playerObject)
    }
}

private fun handleRolePromotion(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    if (community.getMemberRole(playerObject.id) != MemberRoleType.MEMBER) {
        trMenu(
            playerExecutor,
            "community.operation.member.promote.promote.fail.not_member",
            playerObject.name
        )
        return
    }

    val memberValue = getMemberOrNotify(community, playerExecutor, playerObject, "promote.promote.fail.not_member")
    if (memberValue != null) {
        memberValue.basicRoleType = MemberRoleType.ADMIN
        trMenu(
            playerExecutor,
            "community.operation.member.promote.promote.success",
            playerObject.name
        )
    }
}

private fun handleRoleDemotion(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile
) {
    if (community.getMemberRole(playerObject.id) != MemberRoleType.ADMIN) {
        trMenu(
            playerExecutor,
            "community.operation.member.promote.demote.fail.not_admin",
            playerObject.name
        )
        return
    }

    val memberValue = getMemberOrNotify(community, playerExecutor, playerObject, "promote.demote.fail.not_member")
    if (memberValue != null) {
        memberValue.basicRoleType = MemberRoleType.MEMBER
        trMenu(
            playerExecutor,
            "community.operation.member.promote.demote.success",
            playerObject.name
        )
    }
}

private fun handleGovernorshipUpdate(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    governorship: Int
) {
    val memberValue = getMemberOrNotify(community, playerExecutor, playerObject, "promote.governorship.fail.not_member")
    if (memberValue != null) {
        memberValue.governorship = governorship
        trMenu(
            playerExecutor,
            "community.operation.member.promote.governorship.success",
            playerObject.name,
            governorship
        )
    }
}

private fun getMemberOrNotify(
    community: Community,
    playerExecutor: ServerPlayerEntity,
    playerObject: GameProfile,
    failTranslationKeySuffix: String
): MemberAccount? {
    val memberValue = community.member[playerObject.id]
    if (memberValue == null) {
        trMenu(
            playerExecutor,
            "community.operation.member.$failTranslationKeySuffix",
            playerObject.name
        )
    }
    return memberValue
}

private fun runBackToMemberMenu(
    playerExecutor: ServerPlayerEntity,
    community: Community,
    playerObject: GameProfile,
    runBack: (ServerPlayerEntity) -> Unit
) {
    CommunityMenuOpener.open(playerExecutor) { syncId ->
        CommunityMemberMenu(
            syncId = syncId,
            community = community,
            playerObject = playerObject,
            playerExecutor = playerExecutor,
            runBack = runBack
        )
    }
}