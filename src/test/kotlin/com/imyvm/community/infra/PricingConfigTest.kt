package com.imyvm.community.infra

import kotlin.test.Test
import kotlin.test.assertEquals

class PricingConfigTest {
    @Test
    fun invalidValuesRetainThePreviousValidValue() {
        val originalPrice = PricingConfig.PRICE_MANOR.value
        val originalFreeArea = PricingConfig.MANOR_FREE_AREA.value
        val originalMultiplier = PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.value
        try {
            PricingConfig.PRICE_MANOR.setValue(12345L)
            PricingConfig.PRICE_MANOR.setValue(-1L)
            assertEquals(12345L, PricingConfig.PRICE_MANOR.value)

            PricingConfig.MANOR_FREE_AREA.setValue(1000.0)
            PricingConfig.MANOR_FREE_AREA.setValue(Double.NaN)
            assertEquals(1000.0, PricingConfig.MANOR_FREE_AREA.value)

            PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.setValue(4L)
            PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.setValue(0L)
            assertEquals(4L, PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.value)
            PricingConfig.validateValues()
        } finally {
            PricingConfig.PRICE_MANOR.setValue(originalPrice)
            PricingConfig.MANOR_FREE_AREA.setValue(originalFreeArea)
            PricingConfig.DIMENSION_PRICE_MULTIPLIER_NETHER.setValue(originalMultiplier)
        }
    }
}
