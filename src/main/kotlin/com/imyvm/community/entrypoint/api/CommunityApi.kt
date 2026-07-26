package com.imyvm.community.entrypoint.api

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.application.interaction.common.CommunityV4Service
import com.imyvm.community.domain.model.community.BuildingRewardLedger
import com.imyvm.community.domain.model.community.CommunityPlot
import com.imyvm.community.domain.model.community.CommunityPolicyState
import com.imyvm.community.domain.model.community.CommunityTitle
import com.imyvm.community.domain.model.community.TaxWelfareSettlement
import com.imyvm.community.domain.model.development.DevelopmentComponents
import com.imyvm.community.domain.model.development.DevelopmentSnapshot
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.inter.api.RegionDataApi
import java.util.UUID
import kotlin.math.ln

data class CommunitySnapshot(
    val regionNumberId: Int?,
    val communityName: String?,
    val organizationType: CommunityOrganizationType,
    val joinPolicy: CommunityJoinPolicy,
    val status: CommunityStatus,
    val memberRoles: Map<UUID, MemberRoleType>,
    val ownerUUID: UUID?,
    val adminUUIDs: List<UUID>,
    val memberUUIDs: List<UUID>,
    val memberCount: Int,
    val totalAssets: Long,
    val creationCost: Long,
    val likeCount: Int,
    val activeAnnouncementCount: Int,
    val messageCount: Int,
    val isManor: Boolean,
    val developmentBlockPlaceTotal: Long,
    val plotCount: Int,
    val titleCount: Int,
    val displayTitleKeys: List<String>,
    val activePolicyKey: String,
    val publicPolicy: CommunityPolicySnapshot,
    val pendingSettlementCount: Int
)

enum class CommunityOrganizationType {
    MANOR,
    REALM
}

data class CommunityPolicySnapshot(
    val activePolicyKey: String,
    val pendingPolicyKey: String?,
    val pendingEffectivePeriodId: String?,
    val lastChangedPeriodId: String?
)

data class PlayerOrganizationSnapshot(
    val regionNumberId: Int?,
    val communityName: String?,
    val organizationType: CommunityOrganizationType,
    val memberRole: MemberRoleType,
    val displayTitleKeys: List<String>,
    val administrationPrivileges: List<String>,
    val canRepresent: Boolean
)

object CommunityApi {

    fun getCommunityByRegion(regionNumberId: Int): CommunitySnapshot? {
        return CommunityDatabase.getCommunityById(regionNumberId)?.toSnapshot()
    }

    fun listCommunities(): List<CommunitySnapshot> {
        return CommunityDatabase.communities.map { it.toSnapshot() }
    }

    fun listPlayerOrganizations(playerUUID: UUID): List<PlayerOrganizationSnapshot> {
        return CommunityDatabase.communities
            .mapNotNull { it.toPlayerOrganizationSnapshot(playerUUID) }
            .sortedWith(compareBy<PlayerOrganizationSnapshot> { it.regionNumberId ?: Int.MAX_VALUE }.thenBy { it.communityName ?: "" })
    }

    fun getTreasuryBalance(regionNumberId: Int): Long? {
        return CommunityDatabase.getCommunityById(regionNumberId)?.getTotalAssets()
    }

    fun getCommunityPolicies(regionNumberId: Int): CommunityPolicySnapshot? {
        return CommunityDatabase.getCommunityById(regionNumberId)?.toPolicySnapshot()
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
        val turnover = Turnover(amount, System.currentTimeMillis(), source, descriptionKey, descriptionArgs)
        return try {
            community.communityIncome.add(turnover)
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (e: Exception) {
            community.communityIncome.remove(turnover)
            Result.failure(e)
        }
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
        if (community.getTotalAssets() < amount) {
            return Result.failure(IllegalStateException("insufficient balance for regionNumberId=$regionNumberId"))
        }
        val turnover = Turnover(amount, System.currentTimeMillis(), source, descriptionKey, descriptionArgs)
        return try {
            community.expenditures.add(turnover)
            CommunityDatabase.save()
            Result.success(Unit)
        } catch (e: Exception) {
            community.expenditures.remove(turnover)
            Result.failure(e)
        }
    }


    fun refreshDevelopment(regionNumberId: Int): Result<Long> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community, { CommunityV4Service.refreshDevelopmentFromWorldGeo(community) })
    }

    fun claimBuildingReward(regionNumberId: Int, playerUUID: UUID, periodId: String): Result<Long> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) {
            val amount = CommunityV4Service.claimBuildingReward(
                community,
                playerUUID,
                periodId,
                com.imyvm.community.infra.PricingConfig.BUILDING_REWARD_BLOCK_VALUE.value
            )
            if (amount <= 0L) return@saveMutation 0L
            val server = WorldGeoCommunityAddon.server
                ?: throw IllegalStateException("CommunityApi building reward claims require a running Minecraft server")
            val player = server.playerList.getPlayer(playerUUID)
                ?: throw IllegalStateException("player must be online to claim building reward: $playerUUID")
            EconomyMod.data.getOrCreate(player).addMoney(amount)
            amount
        }
    }

    fun upsertPlot(regionNumberId: Int, subSpaceId: Long, name: String): Result<CommunityPlot> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { CommunityV4Service.upsertPlot(community, subSpaceId, name) }
    }

    fun calculatePlotPrice(regionNumberId: Int, subSpaceId: Long, area: Double? = null): Result<Long> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { CommunityV4Service.calculatePlotPrice(community, subSpaceId, area) }
    }

    fun buyTitle(regionNumberId: Int, playerUUID: UUID, slot: Int, titleKey: String, effectKey: String? = null): Result<CommunityTitle> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { CommunityV4Service.buyTitle(community, playerUUID, slot, titleKey, effectKey) }
    }

    fun schedulePolicy(regionNumberId: Int, newPolicyKey: String, currentPeriodId: String, effectivePeriodId: String): Result<Boolean> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { CommunityV4Service.schedulePolicy(community, newPolicyKey, currentPeriodId, effectivePeriodId) }
    }

    fun settleTaxWelfare(regionNumberId: Int, periodId: String): Result<TaxWelfareSettlement> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) {
            val settlement = CommunityV4Service.freezeTaxWelfare(community, periodId)
            CommunityV4Service.applyTaxWelfare(community, settlement)
            settlement
        }
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

    private fun <T> saveMutation(community: Community, action: () -> T): Result<T> {
        val memberSnapshot = HashMap(community.member)
        val incomeSnapshot = ArrayList(community.communityIncome)
        val expenditureSnapshot = ArrayList(community.expenditures)
        val buildingRewardSnapshot = copyBuildingRewardLedgers(community.buildingRewardLedgers)
        val developmentSnapshot = community.developmentBlockPlaceTotal
        val plotSnapshot = copyPlots(community.plots)
        val titleSnapshot = copyTitles(community.titles)
        val policySnapshot = copyPolicy(community.policy)
        val settlementSnapshot = copyTaxWelfareSettlements(community.taxWelfareSettlements)
        return try {
            val result = action()
            CommunityDatabase.save()
            Result.success(result)
        } catch (e: Exception) {
            community.member = memberSnapshot
            community.communityIncome = incomeSnapshot
            community.expenditures = expenditureSnapshot
            community.buildingRewardLedgers = buildingRewardSnapshot
            community.developmentBlockPlaceTotal = developmentSnapshot
            community.plots = plotSnapshot
            community.titles = titleSnapshot
            community.policy = policySnapshot
            community.taxWelfareSettlements = settlementSnapshot
            Result.failure(e)
        }
    }

    private fun copyBuildingRewardLedgers(source: HashMap<UUID, BuildingRewardLedger>): HashMap<UUID, BuildingRewardLedger> =
        source.mapValuesTo(HashMap()) { (_, ledger) -> ledger.copy() }

    private fun copyPlots(source: MutableList<CommunityPlot>): MutableList<CommunityPlot> =
        source.map { it.copy() }.toMutableList()

    private fun copyTitles(source: MutableList<CommunityTitle>): MutableList<CommunityTitle> =
        source.map { it.copy() }.toMutableList()

    private fun copyTaxWelfareSettlements(source: MutableList<TaxWelfareSettlement>): MutableList<TaxWelfareSettlement> =
        source.map { it.copy() }.toMutableList()

    private fun copyPolicy(source: CommunityPolicyState): CommunityPolicyState = source.copy()

    private fun requireServerThread(): IllegalStateException? {
        val server = WorldGeoCommunityAddon.server
            ?: return IllegalStateException("CommunityApi mutating calls require a running Minecraft server")
        if (!server.isSameThread) {
            return IllegalStateException("CommunityApi mutating calls must run on the Minecraft server thread")
        }
        return null
    }

}

private fun Community.toSnapshot(): CommunitySnapshot = CommunitySnapshot(
    regionNumberId = regionNumberId,
    communityName = regionNumberId?.let { RegionDataApi.getRegion(it)?.name },
    organizationType = getOrganizationType(),
    joinPolicy = joinPolicy,
    status = status,
    memberRoles = member.mapValues { it.value.basicRoleType },
    ownerUUID = getOwnerUUID(),
    adminUUIDs = getAdminUUIDs(),
    memberUUIDs = getMemberUUIDs(),
    memberCount = getOwnerAdminMemberCount(),
    totalAssets = getTotalAssets(),
    creationCost = creationCost,
    likeCount = likeCount,
    activeAnnouncementCount = getActiveAnnouncements().size,
    messageCount = messages.count { !it.isDeleted },
    isManor = isManor(),
    developmentBlockPlaceTotal = developmentBlockPlaceTotal,
    plotCount = plots.size,
    titleCount = titles.size,
    displayTitleKeys = getDisplayTitleKeys(),
    activePolicyKey = policy.activePolicyKey,
    publicPolicy = toPolicySnapshot(),
    pendingSettlementCount = taxWelfareSettlements.count { it.status != com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus.APPLIED }
)

private fun Community.toPlayerOrganizationSnapshot(playerUUID: UUID): PlayerOrganizationSnapshot? {
    val account = member[playerUUID] ?: return null
    return PlayerOrganizationSnapshot(
        regionNumberId = regionNumberId,
        communityName = regionNumberId?.let { RegionDataApi.getRegion(it)?.name },
        organizationType = getOrganizationType(),
        memberRole = account.basicRoleType,
        displayTitleKeys = titles.filter { it.ownerUUID == playerUUID && it.active }.map { it.titleKey }.distinct().sorted(),
        administrationPrivileges = account.adminPrivileges?.getEnabled()?.map { it.name }?.sorted() ?: emptyList(),
        canRepresent = account.basicRoleType != com.imyvm.community.domain.model.community.MemberRoleType.APPLICANT &&
            account.basicRoleType != com.imyvm.community.domain.model.community.MemberRoleType.REFUSED
    )
}

private fun Community.toPolicySnapshot(): CommunityPolicySnapshot = CommunityPolicySnapshot(
    activePolicyKey = policy.activePolicyKey,
    pendingPolicyKey = policy.pendingPolicyKey,
    pendingEffectivePeriodId = policy.pendingEffectivePeriodId,
    lastChangedPeriodId = policy.lastChangedPeriodId
)

private fun Community.getOrganizationType(): CommunityOrganizationType =
    if (isManor()) CommunityOrganizationType.MANOR else CommunityOrganizationType.REALM

private fun Community.getOwnerAdminMemberCount(): Int =
    getMemberUUIDs().size + getAdminUUIDs().size + if (getOwnerUUID() != null) 1 else 0

private fun Community.getDisplayTitleKeys(): List<String> =
    titles.filter { it.active }.map { it.titleKey }.distinct().sorted()
