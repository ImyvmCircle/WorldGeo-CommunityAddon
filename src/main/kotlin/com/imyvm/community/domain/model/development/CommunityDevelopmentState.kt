package com.imyvm.community.domain.model.development

data class CommunityDevelopmentInputs(
    val memberCount: Int,
    val weekActiveMemberCount: Int,
    val totalTheoreticalBuildingIncome: Long,
    val weekTheoreticalBuildingIncome: Long,
    val totalHabitationMillis: Long,
    val averageHabitationMillis: Long
)

data class CommunityDevelopmentBreakdown(
    val building: Double,
    val population: Double,
    val habitation: Double,
    val habitationModifier: Double
)

data class CommunityLandPriceSnapshot(
    val area: Long,
    val total25HabitationMillis: Long,
    val theoreticalBuildingIncome: Long,
    val activePrice: Long,
    val buildingPrice: Long,
    val totalPrice: Long
)

data class CommunityDevelopmentState(
    var weekKey: String = "",
    var updatedAtMillis: Long = 0L,
    var development: Double = 0.0,
    var inputs: CommunityDevelopmentInputs = CommunityDevelopmentInputs(0, 0, 0L, 0L, 0L, 0L),
    var breakdown: CommunityDevelopmentBreakdown = CommunityDevelopmentBreakdown(0.0, 0.0, 0.0, 0.4),
    var landPrice: CommunityLandPriceSnapshot? = null
)
