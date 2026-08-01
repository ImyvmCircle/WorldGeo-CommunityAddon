package com.imyvm.community.domain.model.development

data class DevelopmentSnapshot(
    val regionNumberId: Int,
    val tick: Long,
    val version: Long,
    val aCommunity: Double,
    val components: DevelopmentComponents
)

data class DevelopmentComponents(
    val memberCount: Int,
    val totalAssets: Long,
    val avgRegionDifficulty: Double?,
    val blockPlaceCount: Long,
    val weekActiveMemberCount: Int = 0,
    val totalTheoreticalBuildingIncome: Long = 0L,
    val weekTheoreticalBuildingIncome: Long = 0L,
    val totalHabitationMillis: Long = 0L,
    val averageHabitationMillis: Long = 0L,
    val buildingScore: Double = 0.0,
    val populationScore: Double = 0.0,
    val habitationScore: Double = 0.0,
    val habitationModifier: Double = 0.4
)
