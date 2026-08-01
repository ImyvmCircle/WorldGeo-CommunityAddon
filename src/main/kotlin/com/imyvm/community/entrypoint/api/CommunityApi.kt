package com.imyvm.community.entrypoint.api

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.application.account.mutateTreasury
import com.imyvm.community.application.title.CommunityTitleService
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.TreasuryMutationResult
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.development.DevelopmentComponents
import com.imyvm.community.domain.model.development.DevelopmentSnapshot
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.inter.api.RegionDataApi
import java.util.UUID
import kotlin.math.ln

data class CommunitySnapshot(
    val regionNumberId: Int?,
    val joinPolicy: CommunityJoinPolicy,
    val status: CommunityStatus,
    val memberRoles: Map<UUID, MemberRoleType>,
    val ownerUUID: UUID?,
    val adminUUIDs: List<UUID>,
    val memberUUIDs: List<UUID>,
    val totalAssets: Long,
    val creationCost: Long,
    val likeCount: Int,
    val activeAnnouncementCount: Int,
    val messageCount: Int,
    val isManor: Boolean,
    val titleSummary: CommunityTitleSummary
)

data class CommunityTitleSummary(
    val foremanSlotLimit: Int,
    val purchasedForemanSlots: Int,
    val foremanHolders: List<UUID>,
    val selectedDisplays: Map<UUID, String>
)

object CommunityApi {

    fun getCommunityByRegion(regionNumberId: Int): CommunitySnapshot? {
        return CommunityDatabase.getCommunityById(regionNumberId)?.toSnapshot()
    }

    fun listCommunities(): List<CommunitySnapshot> {
        return CommunityDatabase.communities.map { it.toSnapshot() }
    }

    fun deposit(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<Unit> {
        requireServerThread()?.let { return Result.failure(it) }
        if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return mutateTreasury(
            community = community,
            amount = amount,
            direction = ResourceDirection.CREDIT,
            source = source.name.lowercase(),
            externalReference = "community:external-deposit:$regionNumberId:${source.name.lowercase()}:${descriptionKey.orEmpty()}:${descriptionArgs.joinToString("|")}:${System.currentTimeMillis()}",
            operationType = "external-deposit",
            objectReference = regionNumberId.toString(),
            descriptionKey = descriptionKey,
            descriptionArgs = descriptionArgs
        ).map { Unit }
    }

    fun withdraw(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<Unit> {
        requireServerThread()?.let { return Result.failure(it) }
        if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return mutateTreasury(
            community = community,
            amount = amount,
            direction = ResourceDirection.DEBIT,
            source = source.name.lowercase(),
            externalReference = "community:external-withdrawal:$regionNumberId:${source.name.lowercase()}:${descriptionKey.orEmpty()}:${descriptionArgs.joinToString("|")}:${System.currentTimeMillis()}",
            operationType = "external-withdrawal",
            objectReference = regionNumberId.toString(),
            descriptionKey = descriptionKey,
            descriptionArgs = descriptionArgs
        ).map { Unit }
    }


    fun controlledDeposit(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        externalReference: String,
        operationType: String,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<TreasuryMutationResult> = controlledTreasury(
        regionNumberId, amount, source, externalReference, operationType,
        ResourceDirection.CREDIT, descriptionKey, descriptionArgs
    )

    fun controlledWithdraw(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        externalReference: String,
        operationType: String,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<TreasuryMutationResult> = controlledTreasury(
        regionNumberId, amount, source, externalReference, operationType,
        ResourceDirection.DEBIT, descriptionKey, descriptionArgs
    )

    private fun controlledTreasury(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        externalReference: String,
        operationType: String,
        direction: ResourceDirection,
        descriptionKey: String?,
        descriptionArgs: List<String>
    ): Result<TreasuryMutationResult> {
        requireServerThread()?.let { return Result.failure(it) }
        if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
        if (externalReference.isBlank()) return Result.failure(IllegalArgumentException("externalReference must not be blank"))
        if (operationType.isBlank()) return Result.failure(IllegalArgumentException("operationType must not be blank"))
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return mutateTreasury(
            community = community,
            amount = amount,
            direction = direction,
            source = source.name.lowercase(),
            externalReference = externalReference,
            operationType = operationType,
            objectReference = regionNumberId.toString(),
            descriptionKey = descriptionKey,
            descriptionArgs = descriptionArgs
        )
    }

    fun snapshotDevelopment(regionNumberId: Int, tick: Long): DevelopmentSnapshot? {
        val community = CommunityDatabase.getCommunityById(regionNumberId) ?: return null
        val region = RegionDataApi.getRegion(regionNumberId) ?: return null
        val server = WorldGeoCommunityAddon.server

        val memberCount = community.getMemberUUIDs().size + community.getAdminUUIDs().size +
            (if (community.getOwnerUUID() != null) 1 else 0)
        val totalAssets = community.getTotalAssets()

        var avgRegionDifficulty: Double? = null
        if (server != null) {
            val statsResult = RegionDataApi.getRegionNaturalStats(server, region)
            if (statsResult is RegionNaturalStatsResult.Success) {
                avgRegionDifficulty = statsResult.stats.averageLocalDifficulty
            }
        }
        val blockPlaceCount = RegionDataApi.getRegionPlayerStats(region).blockPlaceCount

        val aCommunity =
            (ln((memberCount + 1).toDouble()) / ln(2.0)) +
            ln((totalAssets / 100L + 1L).toDouble()) +
            (avgRegionDifficulty ?: 0.0) +
            ln((blockPlaceCount / 2000L + 1L).toDouble())

        val version = (memberCount.toLong() shl 40) or ((totalAssets / 100L) and 0xFFFFFFFFFFL)

        return DevelopmentSnapshot(
            regionNumberId = regionNumberId,
            tick = tick,
            version = version,
            aCommunity = aCommunity,
            components = DevelopmentComponents(
                memberCount = memberCount,
                totalAssets = totalAssets,
                avgRegionDifficulty = avgRegionDifficulty,
                blockPlaceCount = blockPlaceCount
            )
        )
    }

    private fun requireServerThread(): IllegalStateException? {
        val server = WorldGeoCommunityAddon.server
            ?: return IllegalStateException("CommunityApi mutating calls require a running Minecraft server")
        if (!server.isSameThread) {
            return IllegalStateException("CommunityApi mutating calls must run on the Minecraft server thread")
        }
        return null
    }

private fun Community.toSnapshot(): CommunitySnapshot = CommunitySnapshot(
    regionNumberId = regionNumberId,
    joinPolicy = joinPolicy,
    status = status,
    memberRoles = member.mapValues { it.value.basicRoleType },
    ownerUUID = getOwnerUUID(),
    adminUUIDs = getAdminUUIDs(),
    memberUUIDs = getMemberUUIDs(),
    totalAssets = getTotalAssets(),
    creationCost = creationCost,
    likeCount = likeCount,
    activeAnnouncementCount = getActiveAnnouncements().size,
    messageCount = messages.count { !it.isDeleted },
    isManor = isManor(),
    titleSummary = CommunityTitleSummary(
        foremanSlotLimit = CommunityTitleService.foremanSlotLimit(this),
        purchasedForemanSlots = titleState.normalized().foremanSlots.size,
        foremanHolders = titleState.activeForemen().toList(),
        selectedDisplays = titleState.selectedDisplay.mapNotNull { uuid ->
            CommunityTitleService.displayLabel(this, uuid)?.let { uuid to it }
        }.toMap()
    )
)
}
