package com.imyvm.community.domain.model.account

object AccountBalanceRules {
    fun expected(balanceBefore: Long, direction: AccountDirection, amount: Long): Long {
        require(amount > 0) { "Account amount must be positive" }
        return when (direction) {
            AccountDirection.CREDIT -> Math.addExact(balanceBefore, amount)
            AccountDirection.DEBIT -> {
                require(balanceBefore >= amount) { "Insufficient balance" }
                Math.subtractExact(balanceBefore, amount)
            }
        }
    }
}
