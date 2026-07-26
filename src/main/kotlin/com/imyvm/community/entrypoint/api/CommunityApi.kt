package com.imyvm.community.entrypoint.api

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.MemberAccount
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.community.BuildingRewardLedger
import com.imyvm.community.domain.model.community.CommunityJoinPolicy
import com.imyvm.community.domain.model.community.CommunityPlot
import com.imyvm.community.domain.model.community.CommunityPolicyState
import com.imyvm.community.domain.model.community.CommunityStatus
import com.imyvm.community.domain.model.community.CommunityTitle
import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.model.community.TaxWelfareSettlement
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import com.imyvm.community.domain.model.development.DevelopmentComponents
import com.imyvm.community.domain.model.development.DevelopmentSnapshot
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.PricingConfig
import com.imyvm.economy.EconomyMod
import com.imyvm.iwg.domain.RegionNaturalStatsResult
import com.imyvm.iwg.inter.api.RegionDataApi
import java.util.UUID
import kotlin.math.ln
import kotlin.math.max

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

private data class CommunityMutationSnapshot(
    val member: HashMap<UUID, MemberAccount>,
    val communityIncome: ArrayList<Turnover>,
    val expenditures: ArrayList<Turnover>,
    val buildingRewardLedgers: HashMap<UUID, BuildingRewardLedger>,
    val developmentBlockPlaceTotal: Long,
    val plots: MutableList<CommunityPlot>,
    val titles: MutableList<CommunityTitle>,
    val policy: CommunityPolicyState,
    val taxWelfareSettlements: MutableList<TaxWelfareSettlement>
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
        return saveMutation(community) { refreshDevelopmentFromRegionData(community) }
    }

    fun claimBuildingReward(regionNumberId: Int, playerUUID: UUID, periodId: String): Result<Long> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        val server = WorldGeoCommunityAddon.server
            ?: return Result.failure(IllegalStateException("CommunityApi building reward claims require a running Minecraft server"))
        val player = server.playerList.getPlayer(playerUUID)
            ?: return Result.failure(IllegalStateException("player must be online to claim building reward: $playerUUID"))
        val memberAccount = community.member[playerUUID]
            ?: return Result.failure(IllegalStateException("player must be a member of community regionNumberId=$regionNumberId"))
        val playerAccount = EconomyMod.data.getOrCreate(player)
        val moneySnapshot = playerAccount.money
        val snapshot = captureMutationSnapshot(community)
        return try {
            val amount = claimBuildingRewardState(community, playerUUID, periodId, PricingConfig.BUILDING_REWARD_BLOCK_VALUE.value)
            if (amount <= 0L) return Result.success(0L)
            playerAccount.addMoney(amount)
            val descArgs = listOf(player.name.string, periodId)
            memberAccount.turnover.add(
                Turnover(amount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.building_reward", descArgs)
            )
            community.expenditures.add(
                Turnover(amount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.building_reward", descArgs)
            )
            CommunityDatabase.save()
            Result.success(amount)
        } catch (e: Exception) {
            restoreMutationSnapshot(community, snapshot)
            playerAccount.money = moneySnapshot
            Result.failure(e)
        }
    }

    fun upsertPlot(regionNumberId: Int, subSpaceId: Long, name: String): Result<CommunityPlot> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { upsertPlotRecord(community, subSpaceId, name) }
    }

    fun calculatePlotPrice(regionNumberId: Int, subSpaceId: Long, area: Double? = null): Result<Long> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { calculatePlotPriceSnapshot(community, subSpaceId, area) }
    }

    fun buyTitle(regionNumberId: Int, playerUUID: UUID, slot: Int, titleKey: String, effectKey: String? = null): Result<CommunityTitle> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { buyCommunityTitle(community, playerUUID, slot, titleKey, effectKey) }
    }

    fun schedulePolicy(regionNumberId: Int, newPolicyKey: String, currentPeriodId: String, effectivePeriodId: String): Result<Boolean> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) { schedulePolicyChange(community, newPolicyKey, currentPeriodId, effectivePeriodId) }
    }

    fun settleTaxWelfare(regionNumberId: Int, periodId: String): Result<TaxWelfareSettlement> {
        requireServerThread()?.let { return Result.failure(it) }
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        return saveMutation(community) {
            val settlement = freezeTaxWelfareSettlement(community, periodId)
            applyTaxWelfareSettlement(community, settlement)
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

    private fun refreshDevelopmentFromRegionData(community: Community): Long {
        val regionId = community.regionNumberId ?: return community.developmentBlockPlaceTotal
        val region = RegionDataApi.getRegion(regionId) ?: return community.developmentBlockPlaceTotal
        val current = RegionDataApi.getRegionPlayerStats(region).blockPlaceCount
        if (current > community.developmentBlockPlaceTotal) community.developmentBlockPlaceTotal = current
        return community.developmentBlockPlaceTotal
    }

    private fun claimBuildingRewardState(community: Community, playerUUID: UUID, periodId: String, blockValue: Long): Long {
        val ledger = community.buildingRewardLedgers.getOrPut(playerUUID) { BuildingRewardLedger() }
        val total = refreshDevelopmentFromRegionData(community)
        val unclaimed = (total - ledger.claimedBlockPlaceCount).coerceAtLeast(0L)
        val limit = CommunityConfig.BUILDING_REWARD_DEFAULT_BLOCK_LIMIT.value.toLong()
        val payableBlocks = unclaimed.coerceAtMost(limit)
        val amount = payableBlocks * blockValue
        if (amount <= 0L) return 0L
        ledger.claimedBlockPlaceCount += payableBlocks
        ledger.claimedAmount += amount
        ledger.lastClaimedPeriodId = periodId
        return amount
    }

    private fun upsertPlotRecord(community: Community, subSpaceId: Long, name: String): CommunityPlot {
        val existing = community.plots.firstOrNull { it.subSpaceId == subSpaceId }
        if (existing != null) {
            existing.name = name
            return existing
        }
        val plot = CommunityPlot(subSpaceId = subSpaceId, name = name)
        community.plots.add(plot)
        return plot
    }

    private fun calculatePlotPriceSnapshot(community: Community, subSpaceId: Long, area: Double?, nowMillis: Long = System.currentTimeMillis()): Long {
        val subSpaceTuple = RegionDataApi.getSubSpaceById(subSpaceId)
        val areaValue = area ?: subSpaceTuple?.third?.let { subSpace ->
            RegionDataApi.getSubSpaceSnapshot(subSpaceTuple.first, subSpaceTuple.second, subSpace).area
        } ?: 0.0
        val regionWeight = community.getTotalAssets() / 1000L
        val price = max(0L, (areaValue * PricingConfig.PLOT_AREA_PRICE_PER_BLOCK.value).toLong() + regionWeight)
        val plot = community.plots.firstOrNull { it.subSpaceId == subSpaceId }
        if (plot != null) {
            plot.cachedPrice = price
            plot.lastPriceRefreshMillis = nowMillis
        }
        return price
    }

    private fun buyCommunityTitle(community: Community, playerUUID: UUID, slot: Int, titleKey: String, effectKey: String? = null): CommunityTitle {
        community.titles.removeAll { it.ownerUUID == playerUUID && it.slot == slot }
        val title = CommunityTitle(slot, titleKey, playerUUID, System.currentTimeMillis(), effectKey = effectKey)
        community.titles.add(title)
        return title
    }

    private fun schedulePolicyChange(community: Community, newPolicyKey: String, currentPeriodId: String, effectivePeriodId: String): Boolean {
        if (community.policy.lastChangedPeriodId == currentPeriodId) return false
        community.policy.pendingPolicyKey = newPolicyKey
        community.policy.pendingEffectivePeriodId = effectivePeriodId
        community.policy.lastChangedPeriodId = currentPeriodId
        community.expenditures.add(
            Turnover(PricingConfig.POLICY_SWITCH_COST.value, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.policy_switch", listOf(newPolicyKey))
        )
        return true
    }

    private fun freezeTaxWelfareSettlement(community: Community, periodId: String): TaxWelfareSettlement {
        val existing = community.taxWelfareSettlements.firstOrNull { it.periodId == periodId }
        if (existing != null) return existing
        val total = community.getTotalAssets()
        val tax = (total * CommunityConfig.TAX_WELFARE_TAX_RATE.value).toLong().coerceAtLeast(0L)
        val welfare = (community.member.size * CommunityConfig.TAX_WELFARE_PER_MEMBER.value).coerceAtLeast(0L)
        val settlement = TaxWelfareSettlement(
            settlementId = "${community.regionNumberId ?: 0}:$periodId",
            periodId = periodId,
            createdAt = System.currentTimeMillis(),
            totalAssetsAtFreeze = total,
            taxAmount = tax,
            welfareAmount = welfare
        )
        community.taxWelfareSettlements.add(settlement)
        return settlement
    }

    private fun applyTaxWelfareSettlement(community: Community, settlement: TaxWelfareSettlement): Boolean {
        if (settlement.status == TaxWelfareSettlementStatus.APPLIED) return true
        return try {
            if (settlement.taxAmount > 0L) {
                community.expenditures.add(
                    Turnover(settlement.taxAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.tax", listOf(settlement.periodId))
                )
            }
            if (settlement.welfareAmount > 0L) {
                community.communityIncome.add(
                    Turnover(settlement.welfareAmount, System.currentTimeMillis(), TurnoverSource.SYSTEM, "community.treasury.desc.welfare", listOf(settlement.periodId))
                )
            }
            settlement.status = TaxWelfareSettlementStatus.APPLIED
            settlement.failureReason = null
            true
        } catch (e: Exception) {
            settlement.status = TaxWelfareSettlementStatus.FAILED
            settlement.failureReason = e.message ?: e::class.java.simpleName
            settlement.retryCount++
            settlement.nextRetryAt = System.currentTimeMillis() + CommunityConfig.TAX_WELFARE_RETRY_DELAY_SECONDS.value * 1000L
            false
        }
    }

    private fun <T> saveMutation(community: Community, action: () -> T): Result<T> {
        val snapshot = captureMutationSnapshot(community)
        return try {
            val result = action()
            CommunityDatabase.save()
            Result.success(result)
        } catch (e: Exception) {
            restoreMutationSnapshot(community, snapshot)
            Result.failure(e)
        }
    }

    private fun captureMutationSnapshot(community: Community): CommunityMutationSnapshot = CommunityMutationSnapshot(
        member = copyMembers(community.member),
        communityIncome = ArrayList(community.communityIncome),
        expenditures = ArrayList(community.expenditures),
        buildingRewardLedgers = copyBuildingRewardLedgers(community.buildingRewardLedgers),
        developmentBlockPlaceTotal = community.developmentBlockPlaceTotal,
        plots = copyPlots(community.plots),
        titles = copyTitles(community.titles),
        policy = copyPolicy(community.policy),
        taxWelfareSettlements = copyTaxWelfareSettlements(community.taxWelfareSettlements)
    )

    private fun restoreMutationSnapshot(community: Community, snapshot: CommunityMutationSnapshot) {
        community.member = snapshot.member
        community.communityIncome = snapshot.communityIncome
        community.expenditures = snapshot.expenditures
        community.buildingRewardLedgers = snapshot.buildingRewardLedgers
        community.developmentBlockPlaceTotal = snapshot.developmentBlockPlaceTotal
        community.plots = snapshot.plots
        community.titles = snapshot.titles
        community.policy = snapshot.policy
        community.taxWelfareSettlements = snapshot.taxWelfareSettlements
    }

    private fun copyMembers(source: HashMap<UUID, MemberAccount>): HashMap<UUID, MemberAccount> =
        source.mapValuesTo(HashMap()) { (_, account) ->
            account.copy(
                adminPrivileges = account.adminPrivileges?.let { com.imyvm.community.domain.policy.permission.AdminPrivileges(it.getEnabled().toMutableSet()) },
                mail = ArrayList(account.mail),
                turnover = ArrayList(account.turnover)
            )
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
    pendingSettlementCount = taxWelfareSettlements.count { it.status != TaxWelfareSettlementStatus.APPLIED }
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
        canRepresent = account.basicRoleType != MemberRoleType.APPLICANT &&
            account.basicRoleType != MemberRoleType.REFUSED
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
