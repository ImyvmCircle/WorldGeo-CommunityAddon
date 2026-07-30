package com.imyvm.community.domain.policy.territory

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerritoryPricingTest {
    @Test
    fun areasRoundToIntegerSquareMetresWithoutUsingTheDefaultLocale() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(
                mapOf(TerritoryPricing.DIMENSION_OVERWORLD to 11.0),
                TerritoryPricing.buildAreaMap(TerritoryPricing.DIMENSION_OVERWORLD, 10.5)
            )
            assertEquals(20000L, TerritoryPricing.calculateLandCostTotal(19999.5, true))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun invalidAreasAndNonAdvancingTiersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            TerritoryPricing.buildAreaMap(TerritoryPricing.DIMENSION_OVERWORLD, Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            TerritoryPricing.buildAreaMap(TerritoryPricing.DIMENSION_OVERWORLD, -1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            TerritoryPricing.forEachSettingBracket(0.0, 1.0, 1L, 1.0, 0.0) { _, _, _, _, _, _ -> }
        }
    }

    @Test
    fun fixedDimensionPriceOverflowIsRejected() {
        assertFailsWith<ArithmeticException> {
            TerritoryPricing.applyGeoscopePriceMultiplier(Long.MAX_VALUE, TerritoryPricing.DIMENSION_NETHER)
        }
    }

    @Test
    fun refundsAndScopeSurchargesUseExactCheckedAmounts() {
        assertEquals(-50L, TerritoryPricing.calculateRefund(101L))
        assertEquals(227L, TerritoryPricing.applySoftLimitMultiplier(101L, 1.5, 2))
        assertFailsWith<ArithmeticException> {
            TerritoryPricing.applySoftLimitMultiplier(Long.MAX_VALUE, 1.5, 1)
        }
    }
}
