package com.imyvm.community.application.testcycle

import com.imyvm.community.application.interaction.common.CommunityV4Service
import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.community.TaxWelfareSettlementStatus
import com.imyvm.community.domain.model.testcycle.CommunityV4TestCycleRun
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.infra.testcycle.CommunityV4TestCycleDatabase

object CommunityV4TestCycleService {
    const val TEST_PERIOD_MILLIS = 300000L
    const val TEST_MAX_CYCLES = 2

    fun start(community: Community, nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true): CommunityV4TestCycleRun {
        val regionId = community.regionNumberId ?: throw IllegalArgumentException("community has no region")
        CommunityV4TestCycleDatabase.runs.filter { it.regionNumberId == regionId && it.active }.forEach { it.active = false }
        val run = CommunityV4TestCycleRun(
            runId = "$regionId:$nowMillis",
            regionNumberId = regionId,
            startedAt = nowMillis,
            nextCycleAt = nowMillis + TEST_PERIOD_MILLIS,
            maxCycles = TEST_MAX_CYCLES,
            periodMillis = TEST_PERIOD_MILLIS
        )
        CommunityV4TestCycleDatabase.runs.add(run)
        if (persist) CommunityV4TestCycleDatabase.save()
        return run
    }

    fun processDue(nowMillis: Long = System.currentTimeMillis(), persist: Boolean = true): Int {
        var processed = 0
        for (run in CommunityV4TestCycleDatabase.runs.filter { it.active }) {
            val community = CommunityDatabase.getCommunityById(run.regionNumberId)
            if (community == null) {
                run.active = false
                continue
            }
            while (run.active && nowMillis >= run.nextCycleAt && run.completedCycles < run.maxCycles) {
                val cycleNumber = run.completedCycles + 1
                val periodId = "test-${run.startedAt}-$cycleNumber"
                val settlement = CommunityV4Service.createTaxWelfareSettlement(community, periodId, "${run.runId}:$cycleNumber")
                settlement.status = TaxWelfareSettlementStatus.APPLIED
                run.settlements.add(settlement)
                run.completedCycles = cycleNumber
                run.nextCycleAt += run.periodMillis
                if (run.completedCycles >= run.maxCycles) run.active = false
                processed++
            }
        }
        if (processed > 0 && persist) CommunityV4TestCycleDatabase.save()
        return processed
    }

    fun latestRun(regionNumberId: Int): CommunityV4TestCycleRun? =
        CommunityV4TestCycleDatabase.runs.lastOrNull { it.regionNumberId == regionNumberId }
}
