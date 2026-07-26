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
            500000L,
            "the price to create a manor."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PRICE_REALM = Option(
            "economy.price_realm",
            1000000L,
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
            45000L,
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
            20000L,
            "the price per unit area for manor above free area (Long, = display price * 100 per unit)."
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
            60000L,
            "the price per unit area for realm above free area (Long, = display price * 100 per unit)."
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

        @JvmField
        @ConfigOption
        val DIMENSION_PRICE_MULTIPLIER_NETHER = Option(
            "economy.dimension_price_multiplier_nether",
            8L,
            "the price multiplier applied to geoscope-related prices in the nether."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val DIMENSION_PRICE_MULTIPLIER_END = Option(
            "economy.dimension_price_multiplier_end",
            2L,
            "the price multiplier applied to geoscope-related prices in the end."
        ) { obj, path ->
            obj.getLong(path)
        }

        // Scope addition

        @JvmField
        @ConfigOption
        val SCOPE_ADDITION_BASE_COST_MANOR = Option(
            "economy.scope_addition_base_cost_manor",
            50000L,
            "the fixed base cost to add a new scope in a manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val SCOPE_ADDITION_BASE_COST_REALM = Option(
            "economy.scope_addition_base_cost_realm",
            100000L,
            "the fixed base cost to add a new scope in a realm community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val SCOPE_ADDITION_SOFT_LIMIT_MULTIPLIER = Option(
            "economy.scope_addition_soft_limit_multiplier",
            1.5,
            "cost multiplier applied per excess scope above the member-based soft limit (fixedCost *= multiplier^excessCount)."
        ) { obj, path ->
            obj.getDouble(path)
        }

        // Community rename

        @JvmField
        @ConfigOption
        val RENAME_GLOBAL_COST = Option(
            "economy.rename_global_cost",
            200000L,
            "the cost to rename the community (global name, display price * 100)."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RENAME_SCOPE_COST = Option(
            "economy.rename_scope_cost",
            10000L,
            "the cost to rename a single geoscope (display price * 100)."
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
            100000L,
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

        // Permission settings

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
            10000L,
            "the pricing coefficient for CONTAINER permission per unit area (Long, = display price * 100 per unit)."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BUILD_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.build.coefficient_per_unit",
            12500L,
            "the pricing coefficient for BUILD permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BREAK_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.break.coefficient_per_unit",
            12500L,
            "the pricing coefficient for BREAK permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BUCKET_BUILD_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.bucket_build.coefficient_per_unit",
            2000L,
            "the pricing coefficient for BUCKET_BUILD permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_BUCKET_SCOOP_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.bucket_scoop.coefficient_per_unit",
            2000L,
            "the pricing coefficient for BUCKET_SCOOP permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_INTERACTION_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.interaction.coefficient_per_unit",
            15000L,
            "the pricing coefficient for INTERACTION permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_REDSTONE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.redstone.coefficient_per_unit",
            10000L,
            "the pricing coefficient for REDSTONE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_TRADE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.trade.coefficient_per_unit",
            3000L,
            "the pricing coefficient for TRADE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_PVP_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.pvp.coefficient_per_unit",
            12500L,
            "the pricing coefficient for PVP permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_ANIMAL_KILLING_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.animal_killing.coefficient_per_unit",
            7000L,
            "the pricing coefficient for ANIMAL_KILLING permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_VILLAGER_KILLING_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.villager_killing.coefficient_per_unit",
            10000L,
            "the pricing coefficient for VILLAGER_KILLING permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_THROWABLE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.throwable.coefficient_per_unit",
            2000L,
            "the pricing coefficient for THROWABLE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_EGG_USE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.egg_use.coefficient_per_unit",
            500L,
            "the pricing coefficient for EGG_USE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_SNOWBALL_USE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.snowball_use.coefficient_per_unit",
            500L,
            "the pricing coefficient for SNOWBALL_USE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_POTION_USE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.potion_use.coefficient_per_unit",
            1000L,
            "the pricing coefficient for POTION_USE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_FARMING_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.farming.coefficient_per_unit",
            3500L,
            "the pricing coefficient for FARMING permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_IGNITE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.ignite.coefficient_per_unit",
            2000L,
            "the pricing coefficient for IGNITE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_ARMOR_STAND_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.armor_stand.coefficient_per_unit",
            3500L,
            "the pricing coefficient for ARMOR_STAND permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_ITEM_FRAME_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.item_frame.coefficient_per_unit",
            3500L,
            "the pricing coefficient for ITEM_FRAME permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val PERMISSION_WIND_CHARGE_USE_COEFFICIENT_PER_UNIT = Option(
            "economy.permission.wind_charge_use.coefficient_per_unit",
            800L,
            "the pricing coefficient for WIND_CHARGE_USE permission per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_SPAWN_MONSTERS_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.spawn_monsters.coefficient_per_unit",
            45000L,
            "the pricing coefficient for SPAWN_MONSTERS rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_SPAWN_PHANTOMS_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.spawn_phantoms.coefficient_per_unit",
            7000L,
            "the pricing coefficient for SPAWN_PHANTOMS rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_TNT_BLOCK_PROTECTION_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.tnt_block_protection.coefficient_per_unit",
            7000L,
            "the pricing coefficient for TNT_BLOCK_PROTECTION rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_ENDERMAN_BLOCK_PICKUP_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.enderman_block_pickup.coefficient_per_unit",
            7000L,
            "the pricing coefficient for ENDERMAN_BLOCK_PICKUP rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_SCULK_SPREAD_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.sculk_spread.coefficient_per_unit",
            7000L,
            "the pricing coefficient for SCULK_SPREAD rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_SNOW_GOLEM_TRAIL_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.snow_golem_trail.coefficient_per_unit",
            7000L,
            "the pricing coefficient for SNOW_GOLEM_TRAIL rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_DISPENSER_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.dispenser.coefficient_per_unit",
            7000L,
            "the pricing coefficient for DISPENSER rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_PRESSURE_PLATE_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.pressure_plate.coefficient_per_unit",
            7000L,
            "the pricing coefficient for PRESSURE_PLATE rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_PISTON_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.piston.coefficient_per_unit",
            7000L,
            "the pricing coefficient for PISTON rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_RPG_NATURAL_REGEN_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.rpg_natural_regen.coefficient_per_unit",
            7000L,
            "the pricing coefficient for RPG_NATURAL_REGEN rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_RPG_FIRE_SPREAD_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.rpg_fire_spread.coefficient_per_unit",
            7000L,
            "the pricing coefficient for RPG_FIRE_SPREAD rule per unit area."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val RULE_RPG_HUNGER_COEFFICIENT_PER_UNIT = Option(
            "economy.rule.rpg_hunger.coefficient_per_unit",
            7000L,
            "the pricing coefficient for RPG_HUNGER rule per unit area."
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


        fun validateValues() {
            val nonNegativeLongOptions = listOf(
                "economy.price_manor" to PRICE_MANOR.value,
                "economy.price_realm" to PRICE_REALM.value,
                "economy.community_join_cost" to COMMUNITY_JOIN_COST_REALM.value,
                "economy.community_join_cost_manor" to COMMUNITY_JOIN_COST_MANOR.value,
                "economy.manor_area_price_per_unit" to MANOR_AREA_PRICE_PER_UNIT.value,
                "economy.realm_area_price_per_unit" to REALM_AREA_PRICE_PER_UNIT.value,
                "economy.scope_addition_base_cost_manor" to SCOPE_ADDITION_BASE_COST_MANOR.value,
                "economy.scope_addition_base_cost_realm" to SCOPE_ADDITION_BASE_COST_REALM.value,
                "economy.rename_global_cost" to RENAME_GLOBAL_COST.value,
                "economy.rename_scope_cost" to RENAME_SCOPE_COST.value,
                "economy.teleport_point_second_point_base_cost" to TELEPORT_POINT_SECOND_POINT_BASE_COST.value,
                "economy.teleport_point_modify_cost" to TELEPORT_POINT_MODIFY_COST.value,
                "economy.teleport_paid_base_cost" to TELEPORT_PAID_BASE_COST.value,
                "economy.permission.build_break.coefficient_per_unit" to PERMISSION_BUILD_BREAK_COEFFICIENT_PER_UNIT.value,
                "economy.permission.container.coefficient_per_unit" to PERMISSION_CONTAINER_COEFFICIENT_PER_UNIT.value,
                "economy.permission.build.coefficient_per_unit" to PERMISSION_BUILD_COEFFICIENT_PER_UNIT.value,
                "economy.permission.break.coefficient_per_unit" to PERMISSION_BREAK_COEFFICIENT_PER_UNIT.value,
                "economy.permission.bucket_build.coefficient_per_unit" to PERMISSION_BUCKET_BUILD_COEFFICIENT_PER_UNIT.value,
                "economy.permission.bucket_scoop.coefficient_per_unit" to PERMISSION_BUCKET_SCOOP_COEFFICIENT_PER_UNIT.value,
                "economy.permission.interaction.coefficient_per_unit" to PERMISSION_INTERACTION_COEFFICIENT_PER_UNIT.value,
                "economy.permission.container.coefficient_per_unit" to PERMISSION_CONTAINER_COEFFICIENT_PER_UNIT.value,
                "economy.permission.redstone.coefficient_per_unit" to PERMISSION_REDSTONE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.trade.coefficient_per_unit" to PERMISSION_TRADE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.pvp.coefficient_per_unit" to PERMISSION_PVP_COEFFICIENT_PER_UNIT.value,
                "economy.permission.animal_killing.coefficient_per_unit" to PERMISSION_ANIMAL_KILLING_COEFFICIENT_PER_UNIT.value,
                "economy.permission.villager_killing.coefficient_per_unit" to PERMISSION_VILLAGER_KILLING_COEFFICIENT_PER_UNIT.value,
                "economy.permission.throwable.coefficient_per_unit" to PERMISSION_THROWABLE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.egg_use.coefficient_per_unit" to PERMISSION_EGG_USE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.snowball_use.coefficient_per_unit" to PERMISSION_SNOWBALL_USE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.potion_use.coefficient_per_unit" to PERMISSION_POTION_USE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.farming.coefficient_per_unit" to PERMISSION_FARMING_COEFFICIENT_PER_UNIT.value,
                "economy.permission.ignite.coefficient_per_unit" to PERMISSION_IGNITE_COEFFICIENT_PER_UNIT.value,
                "economy.permission.armor_stand.coefficient_per_unit" to PERMISSION_ARMOR_STAND_COEFFICIENT_PER_UNIT.value,
                "economy.permission.item_frame.coefficient_per_unit" to PERMISSION_ITEM_FRAME_COEFFICIENT_PER_UNIT.value,
                "economy.permission.wind_charge_use.coefficient_per_unit" to PERMISSION_WIND_CHARGE_USE_COEFFICIENT_PER_UNIT.value,
                "economy.rule.spawn_monsters.coefficient_per_unit" to RULE_SPAWN_MONSTERS_COEFFICIENT_PER_UNIT.value,
                "economy.rule.spawn_phantoms.coefficient_per_unit" to RULE_SPAWN_PHANTOMS_COEFFICIENT_PER_UNIT.value,
                "economy.rule.tnt_block_protection.coefficient_per_unit" to RULE_TNT_BLOCK_PROTECTION_COEFFICIENT_PER_UNIT.value,
                "economy.rule.enderman_block_pickup.coefficient_per_unit" to RULE_ENDERMAN_BLOCK_PICKUP_COEFFICIENT_PER_UNIT.value,
                "economy.rule.sculk_spread.coefficient_per_unit" to RULE_SCULK_SPREAD_COEFFICIENT_PER_UNIT.value,
                "economy.rule.snow_golem_trail.coefficient_per_unit" to RULE_SNOW_GOLEM_TRAIL_COEFFICIENT_PER_UNIT.value,
                "economy.rule.dispenser.coefficient_per_unit" to RULE_DISPENSER_COEFFICIENT_PER_UNIT.value,
                "economy.rule.pressure_plate.coefficient_per_unit" to RULE_PRESSURE_PLATE_COEFFICIENT_PER_UNIT.value,
                "economy.rule.piston.coefficient_per_unit" to RULE_PISTON_COEFFICIENT_PER_UNIT.value,
                "economy.rule.rpg_natural_regen.coefficient_per_unit" to RULE_RPG_NATURAL_REGEN_COEFFICIENT_PER_UNIT.value,
                "economy.rule.rpg_fire_spread.coefficient_per_unit" to RULE_RPG_FIRE_SPREAD_COEFFICIENT_PER_UNIT.value,
                "economy.rule.rpg_hunger.coefficient_per_unit" to RULE_RPG_HUNGER_COEFFICIENT_PER_UNIT.value
            )
            for ((path, value) in nonNegativeLongOptions) {
                require(value >= 0L) { "$path must not be negative" }
            }
            require(MANOR_FREE_AREA.value >= 0.0) { "economy.manor_free_area must not be negative" }
            require(REALM_FREE_AREA.value >= 0.0) { "economy.realm_free_area must not be negative" }
            require(MANOR_AREA_UNIT_SIZE.value > 0.0) { "economy.manor_area_unit_size must be positive" }
            require(REALM_AREA_UNIT_SIZE.value > 0.0) { "economy.realm_area_unit_size must be positive" }
            require(AREA_REFUND_RATE.value in 0.0..1.0) { "economy.area_refund_rate must be between 0.0 and 1.0" }
            require(DIMENSION_PRICE_MULTIPLIER_NETHER.value > 0L) { "economy.dimension_price_multiplier_nether must be positive" }
            require(DIMENSION_PRICE_MULTIPLIER_END.value > 0L) { "economy.dimension_price_multiplier_end must be positive" }
            require(SCOPE_ADDITION_SOFT_LIMIT_MULTIPLIER.value >= 1.0) { "economy.scope_addition_soft_limit_multiplier must be at least 1.0" }
            require(PERMISSION_COEFFICIENT_UNIT_SIZE.value > 0) { "economy.permission.coefficient_unit_size must be positive" }
            require(PERMISSION_TARGET_PLAYER_DENOMINATOR.value > 0L) { "economy.permission.target_player_denominator must be positive" }
        }
    }
}
