package com.imyvm.community.entrypoints.screen.outer_community

import com.imyvm.community.application.interaction.common.filterCommunitiesByType
import com.imyvm.community.application.interaction.screen.CommunityMenuOpener
import com.imyvm.community.application.interaction.screen.outer_community.runSwitchFilterMode
import com.imyvm.community.domain.community.CommunityListFilterType
import com.imyvm.community.domain.community.MemberRoleType
import com.imyvm.community.entrypoints.screen.AbstractListMenu
import com.imyvm.community.entrypoints.screen.component.getPlayerHeadButtonItemStackCommunity
import com.imyvm.community.entrypoints.screen.inner_community.CommunityMenu
import com.imyvm.community.util.Translator
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityListMenu(
    syncId: Int,
    private val mode: CommunityListFilterType = CommunityListFilterType.JOIN_ABLE,
    page: Int = 0,
    val runBack: ((ServerPlayerEntity) -> Unit)
) : AbstractListMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.list.title") ?: Text.literal("Community List"),
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

    override fun openNewPage(player: ServerPlayerEntity, newPage: Int) {
        CommunityMenuOpener.open(player) { syncId ->
            CommunityListMenu(syncId, mode, newPage, runBack)
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