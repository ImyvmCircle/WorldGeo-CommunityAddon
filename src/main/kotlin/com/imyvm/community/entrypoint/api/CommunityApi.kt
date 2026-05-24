package com.imyvm.community.entrypoint.api

import com.imyvm.community.domain.model.Community
import com.imyvm.community.domain.model.Turnover
import com.imyvm.community.domain.model.TurnoverSource
import com.imyvm.community.infra.CommunityDatabase

object CommunityApi {

    fun getCommunityByRegion(regionNumberId: Int): Community? {
        return CommunityDatabase.getCommunityById(regionNumberId)
    }

    fun listCommunities(): List<Community> {
        return CommunityDatabase.communities.toList()
    }

    fun deposit(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<Unit> {
        if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        community.communityIncome.add(
            Turnover(amount, System.currentTimeMillis(), source, descriptionKey, descriptionArgs)
        )
        return Result.success(Unit)
    }

    fun withdraw(
        regionNumberId: Int,
        amount: Long,
        source: TurnoverSource,
        descriptionKey: String? = null,
        descriptionArgs: List<String> = emptyList()
    ): Result<Unit> {
        if (amount <= 0L) return Result.failure(IllegalArgumentException("amount must be positive"))
        val community = CommunityDatabase.getCommunityById(regionNumberId)
            ?: return Result.failure(NoSuchElementException("community not found for regionNumberId=$regionNumberId"))
        if (community.getTotalAssets() < amount) {
            return Result.failure(IllegalStateException("insufficient balance for regionNumberId=$regionNumberId"))
        }
        community.expenditures.add(
            Turnover(amount, System.currentTimeMillis(), source, descriptionKey, descriptionArgs)
        )
        return Result.success(Unit)
    }
}
