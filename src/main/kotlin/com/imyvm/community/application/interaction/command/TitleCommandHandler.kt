package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.title.CommunityTitleService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

fun onTitleStatus(player: ServerPlayer, community: Community): Int {
    val state = community.titleState.normalized()
    val holders = state.foremanSlots.map { slot -> "#${slot.index}=${slot.holderUuid?.toString() ?: "empty"}" }.joinToString(", ")
    player.sendSystemMessage(Translator.tr("command.community.title.status", community.generateCommunityMark(), CommunityTitleService.foremanSlotLimit(community).toString(), state.foremanSlots.size.toString(), holders))
    return 1
}

fun onTitleBuyForemanSlot(player: ServerPlayer, community: Community): Int {
    if (CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    if (CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    return CommunityTitleService.buyForemanSlot(community).fold(
        onSuccess = { cost ->
            player.sendSystemMessage(Translator.tr("command.community.title.buy.success", community.generateCommunityMark(), format(cost)))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.title.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onTitleGrantForeman(player: ServerPlayer, community: Community, target: UUID): Int {
    if (CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    if (CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    return CommunityTitleService.grantForeman(community, target).fold(
        onSuccess = { slot ->
            player.sendSystemMessage(Translator.tr("command.community.title.grant.success", community.generateCommunityMark(), target.toString(), slot.toString()))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.title.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onTitleRevokeForeman(player: ServerPlayer, community: Community, target: UUID): Int {
    if (CommunityPermissionPolicy.canExecuteAdministration(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    if (CommunityPermissionPolicy.canExecuteOperationInProto(player, community, AdminPrivilege.MANAGE_TITLES).isDenied()) return 0
    return CommunityTitleService.revokeForeman(community, target).fold(
        onSuccess = { slot ->
            player.sendSystemMessage(Translator.tr("command.community.title.revoke.success", community.generateCommunityMark(), target.toString(), slot.toString()))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.title.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

fun onTitleSelect(player: ServerPlayer, community: Community): Int {
    return CommunityTitleService.selectDisplay(community, player.uuid).fold(
        onSuccess = {
            player.sendSystemMessage(Translator.tr("command.community.title.select.success", community.generateCommunityMark()))
            1
        },
        onFailure = { error ->
            player.sendSystemMessage(Translator.tr("command.community.title.failed", error.message ?: error::class.java.simpleName))
            0
        }
    )
}

private fun format(amount: Long): String = "%.2f".format(amount / 100.0)
