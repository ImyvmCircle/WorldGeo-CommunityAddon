package com.imyvm.community.entrypoints.screen.inner_community.operation_only

import com.imyvm.community.application.interaction.screen.inner_community.operation_only.*
import com.imyvm.community.domain.Community
import com.imyvm.community.entrypoints.screen.AbstractMenu
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityOperationTeleportPointMenu(
    syncId: Int,
    playerExecutor: ServerPlayerEntity,
    community: Community,
    scope: GeoScope,
    runBack: (ServerPlayerEntity) -> Unit
): AbstractMenu(
    syncId = syncId,
    menuTitle = generateTeleportPointManagingMenuTitle(community, scope),
    runBack = runBack
) {
    init {
        addButton(
            slot = 10,
            name = Translator.tr("ui.community.operation.teleport_point.button.inquiry")?.string ?: "Inquiry Teleport Point",
            itemStack = getTeleportPointInformationItemStack(Items.COMMAND_BLOCK, scope)
        ) { runInquiryTeleportPoint(playerExecutor, community, scope) }

        addButton(
            slot = 12,
            name = Translator.tr("ui.community.operation.teleport_point.button.toggle")?.string ?: "Toggle teleport point accessibility",
            item = if(RegionDataApi.inquireTeleportPointAccessibility(scope)) {
                Items.GREEN_WOOL
            } else {
                Items.RED_WOOL
            }
        ) { runToggleTeleportPointAccessibility(playerExecutor, community, scope, runBack) }

        addButton(
            slot = 19,
            name = Translator.tr("ui.community.operation.teleport_point.button.set")?.string ?: "Set Location of Current Feet as Teleport Point",
            item = Items.COMPASS
        ) { runSettingTeleportPoint(playerExecutor, community, scope) }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.operation.teleport_point.button.reset")?.string ?: "Reset current teleport point to null",
            item = Items.BARRIER
        ) { runResetTeleportPoint(playerExecutor, community, scope) }

        addButton(
            slot = 23,
            name = Translator.tr("ui.community.operation.teleport_point.button.teleport")?.string ?: "Teleport to the teleport point of this scope",
            item = Items.ENDER_PEARL
        ) { runTeleportToPoint(playerExecutor, community, scope) }
    }
    companion object {
        private fun generateTeleportPointManagingMenuTitle(community: Community, scope: GeoScope): Text {
            return Text.of(
                Translator.tr("ui.community.operation.teleport_point.title",
                    community,
                    scope
                ))
        }
    }
}