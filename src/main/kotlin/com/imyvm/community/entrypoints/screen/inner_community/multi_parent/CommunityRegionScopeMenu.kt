package com.imyvm.community.entrypoints.screen.inner_community.multi_parent

import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runExecuteRegion
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.runExecuteScope
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.GeographicFunctionType
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.util.Translator
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityRegionScopeMenu(
    syncId: Int,
    val playerExecutor: ServerPlayerEntity,
    val community: Community,
    private val geographicFunctionType: GeographicFunctionType,
    val playerObject: GameProfile? = null,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
): AbstractListMenu(
    syncId,
    menuTitle = generateMenuTitle(community, geographicFunctionType, playerObject),
    page = page,
    runBack = runBack
) {

    private val unitsPerPage = 35
    private val startSlot = 10
    private val endSlot = 44
    private val unitsInPageZero = unitsPerPage - 2
    private val startSlotInPageZero = startSlot + 2

    init {
        if (page == 0) addGlobalButton()
        addLocalButton()
    }

    private fun addGlobalButton() {
        addButton(
            slot = 10,
            name = Translator.tr("ui.community.operation.region.global")?.string ?: "Region Global",
            item = Items.ELYTRA
        ) { runExecuteRegion(playerExecutor, community, geographicFunctionType, playerObject, runBack) }
    }

    private fun addLocalButton() {
        val scopes = community.getRegion()?.geometryScope?: return
        val scopesInPage = if (page == 0) {
            scopes.take(unitsInPageZero)
        } else {
            scopes.drop((page - 1) * unitsPerPage + unitsInPageZero).take(unitsPerPage)
        }

        var slotIndex = if (page == 0) startSlotInPageZero else startSlot
        for (scope in scopesInPage) {

            val item = when (slotIndex % 9) {
                0 -> Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE
                1 -> Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE
                2 -> Items.WILD_ARMOR_TRIM_SMITHING_TEMPLATE
                3 -> Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE
                4 -> Items.HOST_ARMOR_TRIM_SMITHING_TEMPLATE
                5 -> Items.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE
                6 -> Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE
                7 -> Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                else -> Items.ITEM_FRAME
            }

            addButton(
                slot = slotIndex,
                name = scope.scopeName,
                item = item
            ) { runExecuteScope(playerExecutor, community, scope, geographicFunctionType, playerObject, runBack) }

            slotIndex = incrementSlotIndex(slotIndex)
            if (slotIndex > endSlot) break
        }
    }

    override fun calculateTotalPages(listSize: Int): Int {
        return (listSize + 2 + unitsPerPage - 1) / unitsPerPage
    }

    override fun openNewPage(playerExecutor: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(playerExecutor) { syncId ->
            CommunityRegionScopeMenu(
                syncId = syncId,
                playerExecutor = playerExecutor,
                community = community,
                geographicFunctionType = geographicFunctionType,
                playerObject = playerObject,
                page = newPage,
                runBack = runBack
            )
        }
    }

    companion object {
        fun generateMenuTitle(community: Community, geographicFunctionType: GeographicFunctionType, playerObject: GameProfile?): Text {
            val baseTitle = community.generateCommunityMark() + " - "
            val specificTitle = when (geographicFunctionType) {
                GeographicFunctionType.GEOMETRY_MODIFICATION -> {
                    Translator.tr("ui.community.operation.region.geometry.title.component")?.string
                        ?: "Choose scale modifying geographic shape"
                }
                GeographicFunctionType.SETTING_ADJUSTMENT -> {
                    Translator.tr("ui.community.operation.region.setting.manage.title.component")?.string
                        ?: "Choose scale modifying region settings"
                }
                GeographicFunctionType.TELEPORT_POINT_LOCATING -> {
                    Translator.tr("ui.community.operation.region.teleport_point.component")?.string
                        ?: "Choose scale managing teleport point"
                }
                GeographicFunctionType.TELEPORT_POINT_EXECUTION -> {
                    Translator.tr("ui.community.operation.region.teleport_point.execution.title.component")?.string
                        ?: "Choose scale teleporting to"
                }
            }
            return if (playerObject != null) {
                Text.of("$baseTitle$specificTitle: ${playerObject.name}")
            } else {
                Text.of("$baseTitle$specificTitle")
            }
        }
    }
}