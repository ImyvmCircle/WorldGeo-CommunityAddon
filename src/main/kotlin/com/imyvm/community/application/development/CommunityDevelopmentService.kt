package com.imyvm.community.application.development

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.development.CommunityDevelopmentBreakdown
import com.imyvm.community.domain.model.development.CommunityDevelopmentInputs
import com.imyvm.community.domain.model.development.CommunityDevelopmentState
import com.imyvm.community.domain.model.development.CommunityLandPriceSnapshot
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.ln
import kotlin.math.sqrt

object CommunityDevelopmentService {
    private const val HOUR_MILLIS = 3_600_000L
    private const val ACTIVE_PRICE_FACTOR = 3L
    private const val ACTIVE_PRICE_CAP_MILLIS = 25L * HOUR_MILLIS

    fun calculateDevelopment(inputs: CommunityDevelopmentInputs): Pair<Double, CommunityDevelopmentBreakdown> {
        require(inputs.memberCount >= 0) { "memberCount must be non-negative" }
        require(inputs.weekActiveMemberCount >= 0) { "weekActiveMemberCount must be non-negative" }
        require(inputs.totalTheoreticalBuildingIncome >= 0L) { "totalTheoreticalBuildingIncome must be non-negative" }
        require(inputs.weekTheoreticalBuildingIncome >= 0L) { "weekTheoreticalBuildingIncome must be non-negative" }
        require(inputs.totalHabitationMillis >= 0L) { "totalHabitationMillis must be non-negative" }
        require(inputs.averageHabitationMillis >= 0L) { "averageHabitationMillis must be non-negative" }

        val totalBuildingYuan = inputs.totalTheoreticalBuildingIncome / 100.0
        val weekBuildingYuan = inputs.weekTheoreticalBuildingIncome / 100.0
        val building = sqrt(totalBuildingYuan + 2.0 * weekBuildingYuan)
        val population = 0.4 * sqrt(inputs.memberCount.toDouble()) + 0.4 * sqrt(inputs.weekActiveMemberCount.toDouble())
        val habitation = ln(1.0 + inputs.totalHabitationMillis.toDouble() / HOUR_MILLIS)
        val averageRatio = (inputs.averageHabitationMillis.toDouble() / HOUR_MILLIS).coerceIn(0.0, 1.0)
        val modifier = 0.4 + 0.6 * averageRatio
        val development = (building + population + habitation) * modifier
        require(development.isFinite() && development >= 0.0) { "invalid development result" }
        return development to CommunityDevelopmentBreakdown(building, population, habitation, modifier)
    }

    fun updateDevelopment(
        community: Community,
        weekKey: String,
        inputs: CommunityDevelopmentInputs,
        updatedAtMillis: Long = System.currentTimeMillis()
    ): CommunityDevelopmentState {
        val (development, breakdown) = calculateDevelopment(inputs)
        val state = CommunityDevelopmentState(weekKey, updatedAtMillis, development, inputs, breakdown, community.developmentState.landPrice)
        community.developmentState = state
        return state
    }

    fun calculateLandPrice(
        area: BigDecimal,
        total25HabitationMillis: Long,
        theoreticalBuildingIncome: Long
    ): CommunityLandPriceSnapshot {
        require(total25HabitationMillis >= 0L) { "total25HabitationMillis must be non-negative" }
        require(theoreticalBuildingIncome >= 0L) { "theoreticalBuildingIncome must be non-negative" }
        val areaUnits = area.setScale(0, RoundingMode.HALF_UP).longValueExact()
        require(areaUnits >= 1L) { "area must be at least 1" }
        val cappedMillis = total25HabitationMillis.coerceAtMost(ACTIVE_PRICE_CAP_MILLIS)
        val activePrice = BigInteger.valueOf(areaUnits)
            .multiply(BigInteger.valueOf(ACTIVE_PRICE_FACTOR))
            .multiply(BigInteger.valueOf(cappedMillis))
            .divide(BigInteger.valueOf(ACTIVE_PRICE_CAP_MILLIS))
            .longValueExact()
        val buildingPrice = theoreticalBuildingIncome / 10L
        val totalPrice = Math.addExact(Math.addExact(areaUnits, activePrice), buildingPrice)
        return CommunityLandPriceSnapshot(areaUnits, total25HabitationMillis, theoreticalBuildingIncome, activePrice, buildingPrice, totalPrice)
    }

    fun updateLandPrice(
        community: Community,
        area: BigDecimal,
        total25HabitationMillis: Long,
        theoreticalBuildingIncome: Long
    ): CommunityLandPriceSnapshot {
        val snapshot = calculateLandPrice(area, total25HabitationMillis, theoreticalBuildingIncome)
        community.developmentState.landPrice = snapshot
        return snapshot
    }
}
