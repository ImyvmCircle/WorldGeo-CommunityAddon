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
    val blockPlaceCount: Long
)
