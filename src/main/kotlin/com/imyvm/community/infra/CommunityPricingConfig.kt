package com.imyvm.community.infra

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option

class CommunityPricingConfig : HokiConfig("CommunityPricing.conf") {
    companion object {
        @JvmField
        @ConfigOption
        val PERMISSION_BUILD_BREAK_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.build_break.coefficient_per_unit",
            20000L,
            "the pricing coefficient for BUILD_BREAK permission per unit area (Long, = display price * 100 per unit)."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_CONTAINER_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.container.coefficient_per_unit",
            40000L,
            "the pricing coefficient for CONTAINER permission per unit area (Long, = display price * 100 per unit)."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_COEFFICIENT_UNIT_SIZE = Option(
            "economy.permission.coefficient_unit_size",
            10000,
            "the unit area size (m²) corresponding to each coefficient unit."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BASE_COST_MANOR_REGION = Option(
            "economy.permission.base_cost.manor.region",
            20000L,
            "base cost for a Region-level permission change in a Manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BASE_COST_REALM_REGION = Option(
            "economy.permission.base_cost.realm.region",
            10000L,
            "base cost for a Region-level permission change in a Realm community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BASE_COST_MANOR_SCOPE = Option(
            "economy.permission.base_cost.manor.scope",
            10000L,
            "base cost for a Scope-level permission change in a Manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BASE_COST_REALM_SCOPE = Option(
            "economy.permission.base_cost.realm.scope",
            5000L,
            "base cost for a Scope-level permission change in a Realm community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_TARGET_PLAYER_DENOMINATOR = Option(
            "economy.permission.target_player_denominator",
            5L,
            "denominator for the player-targeting coefficient (coefficient = 1 / denominator, default 0.2)."
        ) { obj, path ->
            obj.getLong(path)
        }
    }
}
