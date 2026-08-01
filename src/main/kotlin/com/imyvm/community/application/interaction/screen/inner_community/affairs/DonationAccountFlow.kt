package com.imyvm.community.application.interaction.screen.inner_community.affairs

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.domain.model.TreasuryReferenceRecord
import com.imyvm.community.domain.model.account.AccountDirection
import com.imyvm.community.domain.model.account.AccountTransaction
import com.imyvm.community.domain.model.account.AccountTransactionStatus
import com.imyvm.community.domain.model.transaction.CombinationStepFact
import com.imyvm.community.domain.model.transaction.CombinationStepStatus
import com.imyvm.community.domain.model.transaction.MemberLedgerFact
import com.imyvm.community.domain.model.transaction.ResourceDirection
import com.imyvm.community.domain.model.transaction.TreasuryLedgerFact
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.account.AccountSubsystem
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal data class DonationPlan(
    val operationId: UUID,
    val regionId: Int,
    val playerUuid: UUID,
    val playerName: String,
    val amount: Long,
    val createdAtMillis: Long
) {
    fun encode(): String {
        val encodedName = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(playerName.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            DONATION_EVIDENCE_PREFIX, operationId, regionId, playerUuid,
            amount, createdAtMillis, encodedName
        ).joinToString("|")
    }

    companion object {
        fun decode(value: String): DonationPlan? {
            val fields = value.split('|')
            if (fields.size != 7 || fields[0] != DONATION_EVIDENCE_PREFIX) return null
            return runCatching {
                DonationPlan(
                    UUID.fromString(fields[1]),
                    fields[2].toInt(),
                    UUID.fromString(fields[3]),
                    String(Base64.getUrlDecoder().decode(fields[6]), StandardCharsets.UTF_8),
                    fields[4].toLong(),
                    fields[5].toLong()
                )
            }.getOrNull()
        }
    }
}

fun registerDonationAccountRecovery() {
    AccountSubsystem.onReady { runtime -> scanDonationRecovery(runtime, null) }
}

internal fun submitDonation(
    player: ServerPlayer,
    community: Community,
    amount: Long
) {
    val regionId = community.regionNumberId
    if (regionId == null || amount <= 0L || community.member[player.uuid] == null) {
        player.sendSystemMessage(Translator.tr("ui.community.assets.donate.error.not_member"))
        player.closeContainer()
        return
    }
    val runtime = AccountSubsystem.runtimeOrNull()
    if (runtime == null) {
        player.closeContainer()
        return
    }
    val plan = DonationPlan(
        UUID.randomUUID(), regionId, player.uuid, player.gameProfile.name,
        amount, System.currentTimeMillis()
    )
    player.closeContainer()
    persistDonationPlan(runtime, plan) { driveDonation(runtime, plan) }
}

private fun scanDonationRecovery(runtime: AccountSubsystem.Runtime, token: String?) {
    runtime.sharedStore.scanUnresolvedOperations(token, RECOVERY_PAGE_SIZE)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to scan unresolved donation operations", error)
                    return@execute
                }
                page.operationIds.forEach { operationId -> recoverDonation(runtime, operationId) }
                if (page.nextToken != null && page.operationIds.size == RECOVERY_PAGE_SIZE) {
                    scanDonationRecovery(runtime, page.nextToken)
                }
            }
        }
}

private fun recoverDonation(runtime: AccountSubsystem.Runtime, operationId: UUID) {
    runtime.sharedStore.scanOperation(operationId, null, MAX_OPERATION_FACTS)
        .whenComplete { facts, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to read unresolved operation $operationId", error)
                    return@execute
                }
                val plan = facts.items.asSequence()
                    .filterIsInstance<CombinationStepFact>()
                    .mapNotNull { it.evidence?.let(DonationPlan::decode) }
                    .firstOrNull()
                if (plan != null && plan.operationId == operationId) {
                    persistDonationPlan(runtime, plan) { driveDonation(runtime, plan) }
                }
            }
        }
}

private fun persistDonationPlan(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan,
    completed: () -> Unit
) {
    val facts = listOf(
        donationStep(plan, DEBIT_STEP, "money", debitReference(plan), CombinationStepStatus.DETERMINED),
        donationStep(plan, TREASURY_STEP, "treasury", treasuryReference(plan), CombinationStepStatus.DETERMINED),
        donationStep(plan, MEMBER_STEP, "member", memberReference(plan), CombinationStepStatus.DETERMINED)
    )
    appendDonationPlanFact(runtime, facts, 0, completed)
}

private fun appendDonationPlanFact(
    runtime: AccountSubsystem.Runtime,
    facts: List<CombinationStepFact>,
    index: Int,
    completed: () -> Unit
) {
    if (index == facts.size) {
        completed()
        return
    }
    runtime.sharedStore.append(facts[index]).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error(
                    "Failed to persist donation plan ${facts[index].operationId}", error
                )
            } else {
                appendDonationPlanFact(runtime, facts, index + 1, completed)
            }
        }
    }
}

private fun driveDonation(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan
) {
    runtime.sharedStore.scanOperation(plan.operationId, null, MAX_OPERATION_FACTS)
        .whenComplete { page, error ->
            runtime.server.execute {
                if (error != null) {
                    WorldGeoCommunityAddon.logger.error("Failed to inspect donation ${plan.operationId}", error)
                    return@execute
                }
                val latest = LinkedHashMap<String, CombinationStepFact>()
                page.items.filterIsInstance<CombinationStepFact>().forEach { latest[it.stepKey] = it }
                val debit = latest[DEBIT_STEP] ?: return@execute
                val treasury = latest[TREASURY_STEP] ?: return@execute
                val member = latest[MEMBER_STEP] ?: return@execute
                when {
                    debit.status == CombinationStepStatus.COMPENSATED ->
                        compensateDonation(runtime, plan, treasury, member)
                    debit.status != CombinationStepStatus.SUCCEEDED ->
                        submitDonationDebit(runtime, plan)
                    treasury.status != CombinationStepStatus.SUCCEEDED ->
                        appendDonationTreasury(runtime, plan)
                    member.status != CombinationStepStatus.SUCCEEDED ->
                        appendDonationMember(runtime, plan)
                    else -> finishDonation(runtime, plan)
                }
            }
        }
}

private fun submitDonationDebit(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan
) {
    val transaction = donationTransaction(plan)
    runtime.service.submit(transaction) { state ->
        when (state.status) {
            AccountTransactionStatus.SUCCEEDED -> appendDonationStep(
                runtime,
                donationStep(
                    plan, DEBIT_STEP, "money", debitReference(plan),
                    CombinationStepStatus.SUCCEEDED, transaction.shortId
                ),
                plan
            )
            AccountTransactionStatus.RESOLVED -> {
                runtime.server.playerList.getPlayer(plan.playerUuid)?.let { player ->
                    player.sendSystemMessage(
                        Translator.tr("ui.community.assets.donate.error.insufficient_funds")
                    )
                    player.closeContainer()
                }
                appendDonationStep(
                    runtime,
                    donationStep(
                        plan, DEBIT_STEP, "money", debitReference(plan),
                        CombinationStepStatus.COMPENSATED, transaction.shortId
                    ),
                    plan
                )
            }
            else -> Unit
        }
    }.exceptionally { error ->
        WorldGeoCommunityAddon.logger.error("Failed to submit donation debit ${plan.operationId}", error)
        null
    }
}

private fun compensateDonation(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan,
    treasury: CombinationStepFact,
    member: CombinationStepFact
) {
    val outstanding = when {
        !treasury.status.isTerminal() -> donationStep(
            plan, TREASURY_STEP, "treasury", treasuryReference(plan),
            CombinationStepStatus.COMPENSATED
        )
        !member.status.isTerminal() -> donationStep(
            plan, MEMBER_STEP, "member", memberReference(plan),
            CombinationStepStatus.COMPENSATED
        )
        else -> return
    }
    appendDonationStep(runtime, outstanding, plan)
}

private fun appendDonationTreasury(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan
) {
    val ledger = TreasuryLedgerFact(
        stableDonationId(plan, "treasury-ledger"), plan.regionId, plan.createdAtMillis,
        plan.amount, ResourceDirection.CREDIT, "donation", treasuryReference(plan),
        "donation", plan.playerUuid.toString(), "community.treasury.desc.donation",
        donationDescriptionArgs(plan)
    )
    runtime.sharedStore.append(ledger).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error(
                    "Failed to append donation treasury ledger ${plan.operationId}", error
                )
            } else {
                if (!projectDonationTreasuryToLegacyDatabase(runtime, plan)) return@execute
                appendDonationStep(
                    runtime,
                    donationStep(
                        plan, TREASURY_STEP, "treasury", treasuryReference(plan),
                        CombinationStepStatus.SUCCEEDED, ledger.factId.toString()
                    ),
                    plan
                )
            }
        }
    }
}

private fun projectDonationTreasuryToLegacyDatabase(runtime: AccountSubsystem.Runtime, plan: DonationPlan): Boolean {
    val community = CommunityDatabase.getCommunityById(plan.regionId) ?: return true
    val reference = treasuryReference(plan)
    val record = TreasuryReferenceRecord(
        reference, plan.amount, ResourceDirection.CREDIT, "donation", "donation", plan.playerUuid.toString()
    )
    val beforeBalance = community.treasuryBalance
    val beforeReference = community.treasuryReferences[reference]
    return try {
        community.treasuryBalance = runtime.sharedStore.treasuryBalance(plan.regionId).join()
        community.treasuryReferences[reference] = record
        CommunityDatabase.save()
        true
    } catch (error: Exception) {
        community.treasuryBalance = beforeBalance
        if (beforeReference == null) community.treasuryReferences.remove(reference)
        else community.treasuryReferences[reference] = beforeReference
        WorldGeoCommunityAddon.logger.error("Failed to save donation treasury projection ${plan.operationId}", error)
        false
    }
}

private fun appendDonationMember(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan
) {
    val ledger = MemberLedgerFact(
        stableDonationId(plan, "member-ledger"), plan.regionId, plan.createdAtMillis,
        plan.playerUuid, plan.amount, ResourceDirection.CREDIT, "donation",
        memberReference(plan), "community.treasury.desc.donation", donationDescriptionArgs(plan),
        countsAsContribution = true
    )
    runtime.sharedStore.append(ledger).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error(
                    "Failed to append donation member ledger ${plan.operationId}", error
                )
                return@execute
            }
            if (!projectDonationToLegacyDatabase(plan)) return@execute
            appendDonationStep(
                runtime,
                donationStep(
                    plan, MEMBER_STEP, "member", memberReference(plan),
                    CombinationStepStatus.SUCCEEDED, ledger.factId.toString()
                ),
                plan
            )
        }
    }
}

private fun projectDonationToLegacyDatabase(plan: DonationPlan): Boolean {
    val account = CommunityDatabase.getCommunityById(plan.regionId)?.member?.get(plan.playerUuid)
        ?: return true
    val exists = account.turnover.any {
        it.amount == plan.amount &&
            it.timestamp == plan.createdAtMillis &&
            it.source == TurnoverSource.PLAYER &&
            it.descriptionKey == "community.treasury.desc.donation" &&
            it.descriptionArgs == donationDescriptionArgs(plan)
    }
    if (exists) return true
    val turnover = Turnover(
        plan.amount, plan.createdAtMillis, TurnoverSource.PLAYER,
        "community.treasury.desc.donation", donationDescriptionArgs(plan)
    )
    account.turnover += turnover
    return try {
        CommunityDatabase.save()
        true
    } catch (error: Exception) {
        account.turnover.remove(turnover)
        WorldGeoCommunityAddon.logger.error("Failed to save donation projection ${plan.operationId}", error)
        false
    }
}

private fun appendDonationStep(
    runtime: AccountSubsystem.Runtime,
    fact: CombinationStepFact,
    plan: DonationPlan
) {
    runtime.sharedStore.append(fact).whenComplete { _, error ->
        runtime.server.execute {
            if (error != null) {
                WorldGeoCommunityAddon.logger.error("Failed to advance donation ${plan.operationId}", error)
            } else {
                driveDonation(runtime, plan)
            }
        }
    }
}

private fun finishDonation(
    runtime: AccountSubsystem.Runtime,
    plan: DonationPlan
) {
    val player = runtime.server.playerList.getPlayer(plan.playerUuid) ?: return
    val amount = String.format(Locale.ROOT, "%.2f", plan.amount / 100.0)
    player.sendSystemMessage(Translator.tr("ui.community.assets.donate.success", amount))
}

internal fun donationTransaction(plan: DonationPlan): AccountTransaction {
    val id = stableDonationId(plan, "account-debit")
    return AccountTransaction(
        id, id.toString().replace("-", "").take(12), plan.createdAtMillis,
        "donation:${plan.regionId}", plan.playerUuid, plan.playerName, plan.amount,
        AccountDirection.DEBIT, "donation", debitReference(plan)
    )
}

internal fun donationStep(
    plan: DonationPlan,
    stepKey: String,
    resource: String,
    externalReference: String,
    status: CombinationStepStatus,
    evidence: String? = null
) = CombinationStepFact(
    stableDonationId(plan, "step:$stepKey:${status.name}"), plan.regionId,
    plan.createdAtMillis, plan.operationId, stepKey, resource, externalReference, status,
    evidence ?: if (status == CombinationStepStatus.DETERMINED) plan.encode() else null
)

private fun stableDonationId(plan: DonationPlan, purpose: String): UUID =
    UUID.nameUUIDFromBytes("donation:${plan.operationId}:$purpose".toByteArray(StandardCharsets.UTF_8))

private fun debitReference(plan: DonationPlan) = "community:donation:debit:${plan.operationId}"
private fun treasuryReference(plan: DonationPlan) = "community:donation:treasury:${plan.operationId}"
private fun memberReference(plan: DonationPlan) = "community:donation:member:${plan.operationId}"
private fun donationDescriptionArgs(plan: DonationPlan) =
    listOf(plan.playerName, plan.operationId.toString())

private const val DONATION_EVIDENCE_PREFIX = "donation:v1"
private const val DEBIT_STEP = "donation-account-debit"
private const val TREASURY_STEP = "donation-treasury-credit"
private const val MEMBER_STEP = "donation-member-credit"
private const val RECOVERY_PAGE_SIZE = 128
private const val MAX_OPERATION_FACTS = 32
