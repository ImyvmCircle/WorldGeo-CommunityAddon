package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.domain.model.Community
import com.imyvm.iwg.domain.WorldGeoBiomeCategory
import com.imyvm.iwg.domain.WorldGeoGeographicProfile
import com.imyvm.iwg.domain.WorldGeoGeographicProfileResult
import com.imyvm.iwg.domain.WorldGeoSpaceSnapshot
import com.imyvm.iwg.inter.api.RegionDataApi
import net.minecraft.server.level.ServerPlayer

internal data class AdministrativeAreaRegionView(
    val region: WorldGeoSpaceSnapshot,
    val regionDominantBiomeDisplay: String?,
    val scopes: List<AdministrativeAreaScopeView>
)

internal data class AdministrativeAreaScopeView(
    val snapshot: WorldGeoSpaceSnapshot,
    val dominantBiomeDisplay: String?
)

internal data class AdministrativeAreaScopeDetailsView(
    val scope: AdministrativeAreaScopeView,
    val subSpaces: List<AdministrativeAreaSubSpaceView>
)

internal data class AdministrativeAreaSubSpaceView(
    val snapshot: WorldGeoSpaceSnapshot,
    val dominantBiomeDisplay: String?
)

internal object AdministrativeAreaReadService {
    fun readRegionView(player: ServerPlayer, community: Community): AdministrativeAreaRegionView? {
        val region = community.getRegion() ?: return null
        val server = player.level().server
        val regionSnapshot = RegionDataApi.getRegionSpaceSnapshot(server, region)
        val scopes = RegionDataApi.getRegionScopes(region).map { scope ->
            AdministrativeAreaScopeView(
                snapshot = RegionDataApi.getScopeSpaceSnapshot(server, region, scope),
                dominantBiomeDisplay = RegionDataApi.getScopeGeographicProfileSnapshot(server, region, scope)
                    .result
                    .toDominantBiomeDisplay()
            )
        }
        return AdministrativeAreaRegionView(
            region = regionSnapshot,
            regionDominantBiomeDisplay = RegionDataApi.getRegionGeographicProfileSnapshot(server, region)
                .result
                .toDominantBiomeDisplay(),
            scopes = scopes
        )
    }

    fun readScopeDetailsView(player: ServerPlayer, community: Community, scopeSnapshot: WorldGeoSpaceSnapshot): AdministrativeAreaScopeDetailsView? {
        val region = community.getRegion() ?: return null
        val scope = RegionDataApi.getRegionScopePair(region, scopeSnapshot.name).second ?: return null
        val server = player.level().server
        val scopeView = AdministrativeAreaScopeView(
            snapshot = RegionDataApi.getScopeSpaceSnapshot(server, region, scope),
            dominantBiomeDisplay = RegionDataApi.getScopeGeographicProfileSnapshot(server, region, scope)
                .result
                .toDominantBiomeDisplay()
        )
        val subSpaces = RegionDataApi.listSubSpaceSnapshots(scopeSnapshot.id).map { snapshot ->
            RegionDataApi.getSubSpaceById(snapshot.id)?.let { (snapshotRegion, snapshotScope, subSpace) ->
                AdministrativeAreaSubSpaceView(
                    snapshot = RegionDataApi.getSubSpaceSnapshot(server, snapshotRegion, snapshotScope, subSpace),
                    dominantBiomeDisplay = RegionDataApi.getSubSpaceGeographicProfileSnapshot(server, snapshotRegion, snapshotScope, subSpace)
                        .result
                        .toDominantBiomeDisplay()
                )
            } ?: AdministrativeAreaSubSpaceView(snapshot, null)
        }
        return AdministrativeAreaScopeDetailsView(scopeView, subSpaces)
    }
}

internal fun WorldGeoGeographicProfileResult.toDominantBiomeDisplay(): String? {
    val profile = (this as? WorldGeoGeographicProfileResult.Success)?.profile ?: return null
    return profile.toGeographyDisplay()
}

private fun WorldGeoGeographicProfile.toGeographyDisplay(): String =
    dominantCategories.joinToString("+") { it.toReadableCategoryName() }.ifBlank { attributeKey }

private fun WorldGeoBiomeCategory.toReadableCategoryName(): String =
    key.replace('_', ' ').split(' ').joinToString(" ") { token ->
        token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
