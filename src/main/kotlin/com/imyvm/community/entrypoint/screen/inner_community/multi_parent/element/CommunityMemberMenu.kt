package com.imyvm.community.entrypoint.screen.inner_community.multi_parent.element

import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runNotifyMember
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runOpenPlayerRegionScopeChoice
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runPromoteMember
import com.imyvm.community.application.interaction.screen.inner_community.multi_parent.element.runRemoveMember
import com.imyvm.community.domain.policy.permission.CommunityPermissionPolicy
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.createPlayerHeadItemStack
import com.imyvm.community.util.Translator
import com.mojang.authlib.GameProfile
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class CommunityMemberMenu(
    syncId: Int,
    val community: Community,
    private val playerObject: GameProfile,
    private val playerExecutor: ServerPlayerEntity,
    private val runBack: (ServerPlayerEntity) -> Unit
) : AbstractMenu(
    syncId,
    menuTitle = generateCommunityMemberListMemberMenuTitle(community, playerObject),
    runBack = runBack
) {

    init {
        addDescriptionButtons()
        if (CommunityPermissionPolicy.canManageMember(playerExecutor, community, playerObject.id).isAllowed()) {
            addManageButtons()
        }
    }

    private fun addDescriptionButtons() {
        addButton(
            slot = 10,
            name = playerObject.name,
            itemStack = createPlayerHeadItemStack(playerObject.name, playerObject.id)
        ) {}
    }

    private fun addManageButtons() {
        addButton(
            slot = 19,
            name = Translator.tr("ui.community.administration.member.member_page.button.setting")?.string ?: "Setting",
            item = Items.MAP
        ) {
            runOpenPlayerRegionScopeChoice(
                community = community,
                playerExecutor = playerExecutor,
                playerObject = playerObject,
                runBackGrandfather = runBack
            )
        }

        addButton(
            slot = 21,
            name = Translator.tr("ui.community.administration.member.member_page.button.remove")?.string ?: "Remove Member",
            item = Items.ZOMBIE_VILLAGER_SPAWN_EGG
        ) { runRemoveMember(community, playerExecutor, playerObject) }

        addButton(
            slot = 23,
            name = Translator.tr("ui.community.administration.member.member_page.button.message")?.string ?: "Send Message",
            item = Items.PAPER
        ) { runNotifyMember(community, playerExecutor, playerObject) }

        if (community.getMemberRole(playerExecutor.uuid) == MemberRoleType.OWNER) {
            addButton(
                slot = 25,
                name = Translator.tr("ui.community.administration.member.member_page.button.promote.admin")?.string ?: "Promote to Admin",
                item = Items.COMMAND_BLOCK
            ) { runPromoteMember(community, playerExecutor, playerObject) }
        }
    }

    companion object {

        fun generateCommunityMemberListMemberMenuTitle(community: Community, playerObject: GameProfile): Text {
            return Text.of(
                "${community.getRegion()?.name}" +
                        " - ${playerObject.name} " +
                        Translator.tr("ui.community.administration.member.title.component")!!.string
            )
        }
    }
}