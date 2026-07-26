package com.imyvm.community.entrypoint.screen.inner_community.affairs

import com.imyvm.community.application.interaction.screen.inner_community.affairs.AdministrativeAreaReadService
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenCommunitySpaceInfoMenu
import com.imyvm.community.application.interaction.screen.inner_community.affairs.runOpenScopeSubSpaceInfoMenu
import com.imyvm.community.domain.model.Community
import com.imyvm.community.entrypoint.screen.AbstractMenu
import com.imyvm.community.entrypoint.screen.component.getLoreButton
import com.imyvm.community.util.Translator
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import com.imyvm.iwg.domain.WorldGeoSpaceType
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class CommunitySpaceInfoMenu(
    syncId: Int,
    private val player: ServerPlayer,
    private val community: Community,
    private val page: Int = 0,
    private val runBack: (ServerPlayer) -> Unit
) : AbstractMenu(
    syncId = syncId,
    menuTitle = Translator.tr("ui.community.space.title") ?: Component.literal("行政区信息"),
    runBack = runBack
) {
    private val regionView = AdministrativeAreaReadService.readRegionView(player, community)
    private val scopeSlots = listOf(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40)
    private val pageSize = scopeSlots.size

    init {
        addRegionSummary()
        addScopeEntries()
        addPageControls()
    }

    private fun addRegionSummary() {
        val snapshot = regionView?.region
        addButton(
            slot = 10,
            itemStack = getLoreButton(
                ItemStack(Items.FILLED_MAP),
                snapshot?.let { buildSpaceLore(it, regionView?.regionDominantBiomeDisplay) } ?: listOf(
                    Translator.tr("ui.community.space.unavailable.region") ?: Component.literal("区域信息不可用")
                )
            ),
            name = snapshot?.displayName ?: (Translator.tr("ui.community.space.region")?.string ?: "区域")
        ) {}
    }

    private fun addScopeEntries() {
        val scopeEntries = regionView?.scopes ?: emptyList()
        if (scopeEntries.isEmpty()) {
            addButton(
                slot = 31,
                itemStack = getLoreButton(
                    ItemStack(Items.BARRIER),
                    listOf(Translator.tr("ui.community.space.empty.scopes") ?: Component.literal("暂无辖区"))
                ),
                name = Translator.tr("ui.community.space.empty.scopes")?.string ?: "暂无辖区"
            ) {}
            return
        }

        val start = page * pageSize
        val current = scopeEntries.drop(start).take(pageSize)
        current.forEachIndexed { index, entry ->
            addButton(
                slot = scopeSlots[index],
                itemStack = getLoreButton(ItemStack(Items.COMPASS), buildSpaceLore(entry.snapshot, entry.dominantBiomeDisplay)),
                name = entry.snapshot.displayName
            ) {
                runOpenScopeSubSpaceInfoMenu(player, community, entry.snapshot, runBackToCurrentPage())
            }
        }
    }

    private fun addPageControls() {
        val scopeEntries = regionView?.scopes ?: emptyList()
        val totalPages = if (scopeEntries.isEmpty()) 1 else (scopeEntries.size + pageSize - 1) / pageSize
        if (page > 0) {
            addButton(0, Translator.tr("ui.common.button.previous")?.string ?: "上一页", Items.ARROW) {
                runOpenCommunitySpaceInfoMenu(it, community, page - 1, runBack)
            }
        }
        if (page < totalPages - 1) {
            addButton(8, Translator.tr("ui.common.button.next")?.string ?: "下一页", Items.ARROW) {
                runOpenCommunitySpaceInfoMenu(it, community, page + 1, runBack)
            }
        }
    }

    private fun runBackToCurrentPage(): (ServerPlayer) -> Unit = {
        runOpenCommunitySpaceInfoMenu(it, community, page, runBack)
    }
}

internal fun buildSpaceLore(snapshot: WorldGeoSpaceSnapshot, dominantBiomeOverride: String? = null): List<Component> {
    val lore = mutableListOf<Component>()

    if (snapshot.type != WorldGeoSpaceType.GEOSCOPE) {
        lore += translatedLore("ui.community.space.lore.type", translatedSpaceType(snapshot.type))
    }

    lore += translatedLore("ui.community.space.lore.dimension", snapshot.dimensionId?.toString() ?: "-")
    lore += translatedLore("ui.community.space.lore.area", snapshot.area?.let { "%.2f".format(it) } ?: "-")
    lore += translatedLore("ui.community.space.lore.biome", dominantBiomeOverride ?: formatBiomeName(snapshot))

    when (snapshot.type) {
        WorldGeoSpaceType.REGION -> {
            lore += translatedLore("ui.community.space.lore.child_scopes", snapshot.childScopeCount.toString())
            lore += translatedLore("ui.community.space.lore.child_subspaces", snapshot.childSubSpaceCount.toString())
        }
        WorldGeoSpaceType.GEOSCOPE -> {
            lore += translatedLore("ui.community.space.lore.parent_region", snapshot.parentRegionName ?: "-")
            lore += translatedLore("ui.community.space.lore.child_subspaces", snapshot.childSubSpaceCount.toString())
        }
        WorldGeoSpaceType.SUBSPACE -> {
            lore += translatedLore("ui.community.space.lore.parent_region", snapshot.parentRegionName ?: "-")
            lore += translatedLore("ui.community.space.lore.parent_scope", snapshot.parentScopeName ?: "-")
        }
    }

    lore += translatedLore(
        "ui.community.space.lore.entry_message",
        Translator.tr(
            if (snapshot.entryMessageEnabled) "ui.community.space.lore.entry_message.enabled"
            else "ui.community.space.lore.entry_message.disabled"
        )?.string ?: if (snapshot.entryMessageEnabled) "已启用" else "已关闭"
    )
    return lore
}

private fun translatedSpaceType(type: WorldGeoSpaceType): String = when (type) {
    WorldGeoSpaceType.REGION -> Translator.tr("ui.community.space.type.region")?.string ?: "区域"
    WorldGeoSpaceType.GEOSCOPE -> Translator.tr("ui.community.space.type.scope")?.string ?: "辖区"
    WorldGeoSpaceType.SUBSPACE -> Translator.tr("ui.community.space.type.subspace")?.string ?: "子空间"
}

private fun formatBiomeName(snapshot: WorldGeoSpaceSnapshot): String {
    val biomeId = snapshot.dominantBiomeId
        ?: snapshot.keyedTags["worldgeo:dominant_biome"]?.let { Identifier.tryParse(it) }
        ?: snapshot.keyedTags["dominant_biome"]?.let { Identifier.tryParse(it) }
    return biomeId?.path?.replace('_', ' ')?.split(' ')?.joinToString(" ") { token ->
        token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } ?: (Translator.tr("ui.community.space.lore.biome.unknown")?.string ?: "未记录")
}

private fun translatedLore(key: String, value: String): Component =
    Translator.tr(key, value) ?: Component.literal(value)
