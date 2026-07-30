package com.imyvm.community.domain.model.account

import java.math.BigDecimal

object MoneyAmount {
    fun parseCents(input: String): Long {
        val value = input.toBigDecimalOrNull() ?: throw IllegalArgumentException("Invalid amount")
        require(value > BigDecimal.ZERO) { "Amount must be positive" }
        return value.movePointRight(2).longValueExact()
    }
}
