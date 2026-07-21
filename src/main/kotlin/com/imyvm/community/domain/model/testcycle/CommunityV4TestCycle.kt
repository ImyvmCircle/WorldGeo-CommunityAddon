package com.imyvm.community.domain.model.testcycle

import com.imyvm.community.domain.model.community.TaxWelfareSettlement

data class CommunityV4TestCycleRun(
    val runId: String,
    val regionNumberId: Int,
    val startedAt: Long,
    var nextCycleAt: Long,
    var completedCycles: Int = 0,
    val maxCycles: Int = 2,
    val periodMillis: Long = 300000L,
    var active: Boolean = true,
    val settlements: MutableList<TaxWelfareSettlement> = mutableListOf()
)
