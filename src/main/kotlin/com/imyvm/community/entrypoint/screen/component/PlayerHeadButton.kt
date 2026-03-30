package com.imyvm.community.entrypoint.screen.component

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.util.Translator
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.chat.Component
import java.util.*

fun getPlayerHeadButtonItemStackCommunity(community: Community): ItemStack{
    val ownerUuid = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }?.key ?: return ItemStack.EMPTY
    val displayName = community.generateCommunityMark()
    val itemStack = createPlayerHeadItemStack(displayName, ownerUuid)

    val loreLines = mutableListOf<Component>()
    fun addEntry(key: String, value: Any?) =
        Translator.tr(key, value)?.let { loreLines.add(it) }
    addEntry("ui.list.button.lore.id", community.regionNumberId)
    addEntry("ui.list.button.lore.status", community.status.name)
    addEntry("ui.list.button.lore.founding_time", community.getFormattedFoundingTime())
    addEntry("ui.list.button.lore.member_size", community.member.size)
    addEntry("ui.list.button.lore.join_policy", community.joinPolicy.name)
    addEntry("ui.list.button.lore.like_count", community.likeCount)

    return getLoreButton(itemStack, loreLines)
}

fun createPlayerHeadItemStack(name: String, uuid: UUID): ItemStack {
    val headStack = ItemStack(Items.PLAYER_HEAD)
    headStack.set(DataComponents.CUSTOM_NAME, Component.literal(name))

    val profileComponent = ResolvableProfile.createUnresolved(uuid)
    headStack.set(DataComponents.PROFILE, profileComponent)

    return headStack
}
