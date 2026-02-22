package com.imyvm.community.infra

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option

class PricingConfig : HokiConfig("Pricing.conf") {
    companion object {

        // Community creation

        @JvmField
        @ConfigOption
        val PRICE_MANOR = Option(
            "economy.price_manor",
            1500000L,
            "the price to create a manor."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PRICE_REALM = Option(
            "economy.price_realm",
            3000000L,
            "the price to create a realm."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Community joining

        @JvmField
        @ConfigOption
        val COMMUNITY_JOIN_COST_REALM = Option(
            "economy.community_join_cost",
            50000L,
            "the cost to join a realm community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val COMMUNITY_JOIN_COST_MANOR = Option(
            "economy.community_join_cost_manor",
            150000L,
            "the cost to join a manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Territory area (creation & modification)

        @JvmField
        @ConfigOption
        val MANOR_FREE_AREA = Option(
            "economy.manor_free_area",
            10000.0,
            "the free area for manor (m²) that does not incur additional cost."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val MANOR_AREA_PRICE_PER_UNIT = Option(
            "economy.manor_area_price_per_unit",
            1000L,
            "the price per unit area for manor above free area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val MANOR_AREA_UNIT_SIZE = Option(
            "economy.manor_area_unit_size",
            10000.0,
            "the unit size (m²) for manor area pricing."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val REALM_FREE_AREA = Option(
            "economy.realm_free_area",
            40000.0,
            "the free area for realm (m²) that does not incur additional cost."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val REALM_AREA_PRICE_PER_UNIT = Option(
            "economy.realm_area_price_per_unit",
            3000L,
            "the price per unit area for realm above free area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val REALM_AREA_UNIT_SIZE = Option(
            "economy.realm_area_unit_size",
            40000.0,
            "the unit size (m²) for realm area pricing."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val AREA_REFUND_RATE = Option(
            "economy.area_refund_rate",
            0.5,
            "the refund rate (0.0 - 1.0) when reducing community area."
        ) { obj, path ->
            obj.getDouble(path)
        }

        // Scope addition

        @JvmField
        @ConfigOption
        val SCOPE_ADDITION_BASE_COST_MANOR = Option(
            "economy.scope_addition_base_cost_manor",
            500000L,
            "the fixed base cost to add a new scope in a manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val SCOPE_ADDITION_BASE_COST_REALM = Option(
            "economy.scope_addition_base_cost_realm",
            250000L,
            "the fixed base cost to add a new scope in a realm community."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Teleport point management

        @JvmField
        @ConfigOption
        val TELEPORT_POINT_SECOND_POINT_BASE_COST = Option(
            "economy.teleport_point_second_point_base_cost",
            200000L,
            "the base cost for creating the second active teleport point in a community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_POINT_MODIFY_COST = Option(
            "economy.teleport_point_modify_cost",
            300000L,
            "the fixed cost for modifying an existing teleport point."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_PAID_BASE_COST = Option(
            "economy.teleport_paid_base_cost",
            1000L,
            "base teleport fee after free uses are exhausted."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Council operations

        @JvmField
        @ConfigOption
        val COUNCIL_VOTE_CREATION_COST = Option(
            "council.vote_creation_cost",
            20000L,
            "the cost in community assets to create a council vote."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Permission settings

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
        val PERMISSION_TARGET_PLAYER_DENOMINATOR = Option(
            "economy.permission.target_player_denominator",
            5L,
            "denominator for the player-targeting coefficient (coefficient = 1 / denominator, default 0.2)."
        ) { obj, path ->
            obj.getLong(path)
        }
    }
}
