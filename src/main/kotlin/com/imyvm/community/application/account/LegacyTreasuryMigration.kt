package com.imyvm.community.application.account

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.transaction.CommunityFact
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Volatile private var legacyTreasuryMigrationReady = false

fun isLegacyTreasuryMigrationReady(): Boolean = legacyTreasuryMigrationReady

fun registerLegacyTreasuryMigration() {
    AccountSubsystem.onReady { runtime -> runLegacyTreasuryMigration(runtime) }
}

private fun runLegacyTreasuryMigration(runtime: AccountSubsystem.Runtime) {
    legacyTreasuryMigrationReady = false
    val communities = runCatching { CommunityDatabase.communities.toList() }
        .onFailure { WorldGeoCommunityAddon.logger.error("Failed to freeze legacy treasury migration", it) }
        .getOrNull() ?: return
    val facts = legacyTreasuryFacts(communities)
    appendLegacyTreasuryFact(runtime, facts, 0)
}

private fun appendLegacyTreasuryFact(
    runtime: AccountSubsystem.Runtime,
    facts: List<CommunityFact>,
    index: Int
) {
    if (index == facts.size) {
        syncLegacyTreasuryBalances(runtime, facts.size)
        return
    }
    runtime.sharedStore.append(facts[index]).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                legacyTreasuryMigrationReady = false
                WorldGeoCommunityAddon.logger.error("Failed to migrate legacy treasury fact ${facts[index].factId}", error)
                runtime.scheduler.schedule(
                    { runtime.server.execute { runLegacyTreasuryMigration(runtime) } },
                    MIGRATION_RETRY_SECONDS,
                    TimeUnit.SECONDS
                )
            } else {
                appendLegacyTreasuryFact(runtime, facts, index + 1)
            }
        }
    }
}

private fun syncLegacyTreasuryBalances(runtime: AccountSubsystem.Runtime, migratedFactCount: Int) {
    val communities = CommunityDatabase.communities.filter { it.regionNumberId != null }
    if (communities.isEmpty()) {
        legacyTreasuryMigrationReady = true
        WorldGeoCommunityAddon.logger.info("Legacy treasury migration ready with {} facts", migratedFactCount)
        return
    }
    val futures = communities.map { community ->
        val regionId = community.regionNumberId!!
        runtime.sharedStore.treasuryBalance(regionId).thenApply { balance -> regionId to balance }
    }
    CompletableFuture.allOf(*futures.toTypedArray()).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                legacyTreasuryMigrationReady = false
                WorldGeoCommunityAddon.logger.error("Failed to synchronize treasury aggregates", error)
                runtime.scheduler.schedule(
                    { runtime.server.execute { runLegacyTreasuryMigration(runtime) } },
                    MIGRATION_RETRY_SECONDS,
                    TimeUnit.SECONDS
                )
                return@execute
            }
            var changed = false
            futures.forEach { future ->
                val (regionId, balance) = future.join()
                val community = CommunityDatabase.getCommunityById(regionId) ?: return@forEach
                if (community.treasuryBalance != balance) {
                    community.treasuryBalance = balance
                    changed = true
                }
            }
            try {
                if (changed) CommunityDatabase.save()
                legacyTreasuryMigrationReady = true
                WorldGeoCommunityAddon.logger.info("Legacy treasury migration ready with {} facts", migratedFactCount)
            } catch (saveError: Exception) {
                legacyTreasuryMigrationReady = false
                WorldGeoCommunityAddon.logger.error("Failed to save synchronized treasury aggregates", saveError)
                runtime.scheduler.schedule(
                    { runtime.server.execute { runLegacyTreasuryMigration(runtime) } },
                    MIGRATION_RETRY_SECONDS,
                    TimeUnit.SECONDS
                )
            }
        }
    }
}

internal fun legacyTreasuryFacts(communities: List<Community>): List<CommunityFact> {
    val memberCredits = mutableListOf<CommunityFact>()
    val incomeCredits = mutableListOf<CommunityFact>()
    val expenditureDebits = mutableListOf<CommunityFact>()
    communities.forEach { community ->
        val regionId = community.regionNumberId ?: return@forEach
        community.member.entries.sortedBy { it.key.toString() }.forEach { (memberUuid, account) ->
            account.turnover.forEachIndexed { index, turnover ->
                if (turnover.amount <= 0L || isCurrentDonationProjection(turnover.descriptionKey, turnover.descriptionArgs)) {
                    return@forEachIndexed
                }
                val treasuryReference = "legacy:member-turnover:treasury:$regionId:$memberUuid:$index"
                val memberReference = "legacy:member-turnover:member:$regionId:$memberUuid:$index"
                memberCredits += TreasuryLedgerFact(
                    legacyFactId(treasuryReference), regionId, turnover.timestamp, turnover.amount,
                    ResourceDirection.CREDIT, "legacy-migration", treasuryReference,
                    "legacy-member-turnover", memberUuid.toString(), turnover.descriptionKey,
                    turnover.descriptionArgs
                )
                memberCredits += MemberLedgerFact(
                    legacyFactId(memberReference), regionId, turnover.timestamp, memberUuid,
                    turnover.amount, ResourceDirection.CREDIT, "legacy-migration", memberReference,
                    turnover.descriptionKey, turnover.descriptionArgs, countsAsContribution = true
                )
            }
        }
        community.communityIncome.forEachIndexed { index, turnover ->
            if (turnover.amount <= 0L) return@forEachIndexed
            val reference = "legacy:community-income:$regionId:$index"
            incomeCredits += TreasuryLedgerFact(
                legacyFactId(reference), regionId, turnover.timestamp, turnover.amount,
                ResourceDirection.CREDIT, "legacy-migration", reference,
                "legacy-community-income", regionId.toString(), turnover.descriptionKey,
                turnover.descriptionArgs
            )
        }
        community.expenditures.forEachIndexed { index, turnover ->
            if (turnover.amount <= 0L) return@forEachIndexed
            val reference = "legacy:community-expenditure:$regionId:$index"
            expenditureDebits += TreasuryLedgerFact(
                legacyFactId(reference), regionId, turnover.timestamp, turnover.amount,
                ResourceDirection.DEBIT, "legacy-migration", reference,
                "legacy-community-expenditure", regionId.toString(), turnover.descriptionKey,
                turnover.descriptionArgs
            )
        }
    }
    return memberCredits + incomeCredits + expenditureDebits
}

internal fun isCurrentDonationProjection(descriptionKey: String?, descriptionArgs: List<String>): Boolean =
    descriptionKey == "community.treasury.desc.donation" &&
        descriptionArgs.size >= 2 &&
        runCatching { UUID.fromString(descriptionArgs.last()) }.isSuccess

private fun legacyFactId(reference: String): UUID =
    UUID.nameUUIDFromBytes(reference.toByteArray(StandardCharsets.UTF_8))

private const val MIGRATION_RETRY_SECONDS = 5L
