package com.imyvm.community.entrypoint.screen.outer_community

import com.imyvm.community.application.interaction.common.filterCommunitiesByType
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchFilterMode
import com.imyvm.community.domain.model.community.CommunityListFilterType
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.entrypoint.screen.AbstractListMenu
import com.imyvm.community.entrypoint.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.entrypoint.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer

class CommunityListMenu(
    syncId: Int,
    private val mode: CommunityListFilterType = CommunityListFilterType.ALL,
    page: Int = 0,
    val runBack: ((ServerPlayer) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.trOrFallback("ui.list.title", "Community List"),
    page = page,
    runBack = runBack
) {

    private val communitiesPerPage = 26
    private val startSlot = 10

    init {
        val communities = filterCommunitiesByType(mode)
        renderList(communities, communitiesPerPage, startSlot) { community, slot, _ ->
            addButton(
                slot = slot,
                name = community.generateCommunityMark(),
                itemStack = getPlayerHeadButtonItemStackCommunity(community)
            ) { player ->
                val memberRole = community.getMemberRole(player.uuid)
                
                when (memberRole) {
                    null -> {
                        CommunityMenuOpener.open(player) { newSyncId ->
                            NonMemberCommunityMenu(newSyncId, player, community) {
                                CommunityMenuOpener.open(player) { returnSyncId ->
                                    CommunityListMenu(
                                        syncId = returnSyncId,
                                        mode = mode,
                                        runBack = runBack
                                    )
                                }
                            }
                        }
                    }
                    MemberRoleType.APPLICANT -> {
                        CommunityMenuOpener.open(player) { newSyncId ->
                            ApplicantStatusMenu(newSyncId, player, community) {
                                CommunityMenuOpener.open(player) { returnSyncId ->
                                    CommunityListMenu(
                                        syncId = returnSyncId,
                                        mode = mode,
                                        runBack = runBack
                                    )
                                }
                            }
                        }
                    }
                    MemberRoleType.REFUSED -> {
                        CommunityMenuOpener.open(player) { newSyncId ->
                            RefusedStatusMenu(newSyncId, player, community) {
                                CommunityMenuOpener.open(player) { returnSyncId ->
                                    CommunityListMenu(
                                        syncId = returnSyncId,
                                        mode = mode,
                                        runBack = runBack
                                    )
                                }
                            }
                        }
                    }
                    MemberRoleType.OWNER, MemberRoleType.ADMIN, MemberRoleType.MEMBER -> {
                        CommunityMenuOpener.open(player) { newSyncId ->
                            CommunityMenu(newSyncId, player, community) {
                                CommunityMenuOpener.open(player) { returnSyncId ->
                                    CommunityListMenu(
                                        syncId = returnSyncId,
                                        mode = mode,
                                        runBack = runBack
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        handlePageWithSize(communities.size, communitiesPerPage)
        addModeButtons()
    }

    override fun openNewPage(player: ServerPlayer, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityListMenu(syncId, mode, newPage, runBack)
        }
    }

    private fun addModeButtons() {
        val modeColorMap = mapOf(
            CommunityListFilterType.ALL to Items.WOOL.orange(),
            CommunityListFilterType.JOIN_ABLE to Items.WOOL.green(),
            CommunityListFilterType.RECRUITING to Items.WOOL.lime(),
            CommunityListFilterType.AUDITING to Items.WOOL.yellow(),
            CommunityListFilterType.ACTIVE to Items.WOOL.cyan(),
            CommunityListFilterType.REVOKED to Items.WOOL.red()
        )

        val selectedItem = modeColorMap[mode] ?: Items.WOOL.white()

        addButton(
            slot = 45,
            name = Translator.trStringOrFallback("ui.list.button.${mode.name.lowercase()}", mode.name),
            item = selectedItem
        ) {}

        CommunityListFilterType.entries.forEachIndexed { index, filterType ->
            addButton(
                slot = 47 + index,
                name = Translator.trStringOrFallback("ui.list.button.${filterType.name.lowercase()}", filterType.name),
                item = modeColorMap[filterType] ?: Items.WOOL.white()
            ) { runSwitchFilterMode(it, filterType, runBack) }
        }
    }
}