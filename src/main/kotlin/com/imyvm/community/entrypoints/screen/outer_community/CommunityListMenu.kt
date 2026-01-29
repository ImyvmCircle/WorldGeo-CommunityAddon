package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.application.interaction.common.filterCommunitiesByType
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchFilterMode
import com.imyvm.community.domain.Community
import com.imyvm.community.domain.community.CommunityListFilterType
import com.imyvm.community.entrypoints.screen.AbstractCommunityListMenu
import com.imyvm.community.entrypoints.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity

class CommunityListMenu(
    syncId: Int,
    private val mode: CommunityListFilterType = CommunityListFilterType.JOIN_ABLE,
    page: Int = 0,
    override val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractCommunityListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.list.title"),
    page = page,
    runBack = runBack
) {

    init {
        addCommunityButtons()
        handlePage(getCommunities().size)
        addModeButtons()
    }

    override fun createNewMenu(syncId: Int, newPage: Int): AbstractCommunityListMenu {
        return CommunityListMenu(syncId, mode, newPage, runBack)
    }

    override fun getCommunities(): List<Community> = filterCommunitiesByType(mode)

    override fun onCommunityButtonClick(
        player: ServerPlayerEntity,
        community: Community,
        runBack: (ServerPlayerEntity) -> Unit
    ) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityMenu(syncId, player, community) {
                CommunityMenuOpener.open(player) { newSyncId ->
                    CommunityListMenu(
                        syncId = newSyncId,
                        mode = mode,
                        runBack = runBack
                    )
                }
            }
        }
    }

    private fun addModeButtons() {
        val modeColorMap = mapOf(
            CommunityListFilterType.ALL to Items.ORANGE_WOOL,
            CommunityListFilterType.JOIN_ABLE to Items.GREEN_WOOL,
            CommunityListFilterType.RECRUITING to Items.LIME_WOOL,
            CommunityListFilterType.AUDITING to Items.YELLOW_WOOL,
            CommunityListFilterType.ACTIVE to Items.CYAN_WOOL,
            CommunityListFilterType.REVOKED to Items.RED_WOOL
        )

        val selectedItem = modeColorMap[mode] ?: Items.WHITE_WOOL

        addButton(
            slot = 45,
            name = Translator.tr("ui.list.button.${mode.name.lowercase()}")?.string ?: mode.name,
            item = selectedItem
        ) {}

        CommunityListFilterType.entries.forEachIndexed { index, filterType ->
            addButton(
                slot = 47 + index,
                name = filterType.name,
                item = modeColorMap[filterType] ?: Items.WHITE_WOOL
            ) { runSwitchFilterMode(it, filterType, runBack) }
        }
    }
}