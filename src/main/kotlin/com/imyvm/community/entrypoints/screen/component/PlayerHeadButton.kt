package com.imyvm.community.entrypoints.screen.component

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.util.Translator
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text
import java.util.*

fun getPlayerHeadButtonItemStackCommunity(community: Community): ItemStack{
    val ownerUuid = community.member.entries.find { community.getMemberRole(it.key) == MemberRoleType.OWNER }?.key ?: return ItemStack.EMPTY
    val displayName = community.generateCommunityMark()
    val itemStack = createPlayerHeadItemStack(displayName, ownerUuid)

    val loreLines = mutableListOf<Text>()
    fun addEntry(key: String, value: Any?) =
        Translator.tr(key, value)?.let { loreLines.add(it) }
    addEntry("ui.list.button.lore.id", community.regionNumberId)
    addEntry("ui.list.button.lore.status", community.status.name)
    addEntry("ui.list.button.lore.founding_time", community.getFormattedFoundingTime())
    addEntry("ui.list.button.lore.member_size", community.member.size)
    addEntry("ui.list.button.lore.join_policy", community.joinPolicy.name)

    return getLoreButton(itemStack, loreLines)
}

fun createPlayerHeadItemStack(name: String, uuid: UUID): ItemStack {
    val headStack = ItemStack(Items.PLAYER_HEAD)
    headStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))

    val profileComponent = ProfileComponent(Optional.empty(), Optional.of(uuid), PropertyMap())
    profileComponent.future.thenAccept { fullProfile ->
        headStack.set(DataComponentTypes.PROFILE, fullProfile)
    }

    return headStack
}
