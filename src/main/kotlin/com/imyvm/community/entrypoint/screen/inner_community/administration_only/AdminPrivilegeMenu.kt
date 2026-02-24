package com.imyvm.community.entrypoint.screen.inner_community.administration_only

import com.imyvm.community.application.interaction.screen.inner_community.administration_only.runToggleAdminPrivilege
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.policy.permission.AdminPrivilege
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import java.util.*

class AdminPrivilegeMenu(
    syncId: Int,
    private val playerExecutor: ServerPlayerEntity,
    private val community: Community,
    private val targetUUID: UUID,
    runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = generateTitle(community, targetUUID, playerExecutor),
    runBack = runBack
) {

    init {
        val privileges = community.member[targetUUID]?.adminPrivileges
        var slot = 10
        for (privilege in AdminPrivilege.entries) {
            val enabled = privileges?.isEnabled(privilege) ?: false
            addButton(
                slot = slot,
                name = (Translator.tr(privilege.displayKey)?.string ?: privilege.name) +
                    ": " + (Translator.tr(if (enabled) "ui.community.admin_privilege.state.enabled"
                                          else "ui.community.admin_privilege.state.disabled")?.string
                            ?: if (enabled) "Enabled" else "Disabled"),
                item = if (enabled) Items.LIME_CONCRETE else Items.RED_CONCRETE
            ) {
                runToggleAdminPrivilege(
                    playerExecutor = playerExecutor,
                    community = community,
                    targetUUID = targetUUID,
                    privilege = privilege,
                    runBack = runBack
                )
            }
            slot++
            if (slot == 17) slot = 19
        }
    }

    companion object {
        private fun generateTitle(community: Community, targetUUID: UUID, player: ServerPlayerEntity): Text {
            val name = UtilApi.getPlayerName(player, targetUUID)
            return Text.of(
                community.generateCommunityMark() +
                " - " + name + " " +
                (Translator.tr("ui.community.admin_privilege.title.component")?.string ?: "- Privileges")
            )
        }
    }
}
