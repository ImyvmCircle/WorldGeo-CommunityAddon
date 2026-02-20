package com.imyvm.community.infra

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option

class CommunityConfig : HokiConfig("Community.conf") {
    companion object {
        @JvmField
        @ConfigOption
        val LANGUAGE = Option(
            "language",
            "en_us",
            "the language of the mod."
        ) { obj, path ->
            obj.getString(path)
        }

        @JvmField
        @ConfigOption
        val TIMEZONE = Option(
            "timezone",
            "Asia/Hong_Kong",
            "the time zone of the mod."
        ) { obj, path ->
            obj.getString(path)
        }

        @JvmField
        @ConfigOption
        val PENDING_CHECK_INTERVAL_SECONDS = Option(
            "pending.check_interval_ticks",
            10,
            "the interval in seconds to check for expired pending operations."
        ) { obj, path ->
            obj.getInt(path)
        }

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

        @JvmField
        @ConfigOption
        val IS_CHECKING_REGION_AREA = Option(
            "region.is_checking_area",
            true,
            "whether to check the area of the selected region when creating a community."
        ) { obj, path ->
            obj.getBoolean(path)
        }

        @JvmField
        @ConfigOption
        val MAX_MANOR_AREA = Option(
            "region.max_manor_area",
            50000,
            "the maximum area of a manor region."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val IS_CHECKING_DEVELOPMENT = Option(
            "development.is_checking",
            false,
            "whether to enable development checking."
        ) { obj, path ->
            obj.getBoolean(path)
        }

        @JvmField
        @ConfigOption
        val MIN_NUMBER_MEMBER_REALM = Option(
            "community.min_number_member_realm",
            4,
            "the minimum number of members required to create a realm."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val REALM_REQUEST_EXPIRE_HOURS = Option(
            "community.realm_request_expire_hours",
            48,
            "the number of hours after which a community request of realm type expires."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val AUDITING_EXPIRE_HOURS = Option(
            "community.auditing_expire_hours",
            8760,
            "the number of hours after which a community auditing expires."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val IS_CHECKING_MANOR_MEMBER_SIZE = Option(
            "community.is_checking_manor_member_size",
            true,
            "whether to check the number of members in a manor ."
        ) { obj, path ->
            obj.getBoolean(path)
        }

        @JvmField
        @ConfigOption
        val MAX_MEMBER_MANOR = Option(
            "community.max_member_manor",
            4,
            "the maximum number of members in a manor."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val MAX_NUMBER_ADMIN = Option(
            "community.max_number_admin",
            3,
            "the maximum number of admins in a community."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val COUNCIL_MAX_VOTES_PER_DAY = Option(
            "council.max_votes_per_day",
            2,
            "the maximum number of votes that can be created per day in council."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val COUNCIL_VOTE_CREATION_COST = Option(
            "council.vote_creation_cost",
            20000L,
            "the cost in community assets to create a council vote."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val COUNCIL_VOTE_DURATION_HOURS = Option(
            "council.vote_duration_hours",
            48,
            "the duration in hours for a council vote to remain active."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val COUNCIL_MIN_PARTICIPATION_PERCENTAGE = Option(
            "council.min_participation_percentage",
            0.0,
            "the minimum percentage of council members that must vote for a vote to be valid (0.0 - 1.0)."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val COMMUNITY_JOIN_COST_REALM = Option(
            "economy.community_join_cost",
            50000L,
            "the cost in community assets to join a community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val COMMUNITY_JOIN_COST_MANOR = Option(
            "economy.community_join_cost_manor",
            150000L,
            "the cost in community assets to join a manor community."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val INVITATION_RESPONSE_TIMEOUT_MINUTES = Option(
            "community.invitation_response_timeout_minutes",
            5,
            "the time limit in minutes for a player to respond to a community invitation."
        ) { obj, path ->
            obj.getInt(path)
        }

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
    }
}
