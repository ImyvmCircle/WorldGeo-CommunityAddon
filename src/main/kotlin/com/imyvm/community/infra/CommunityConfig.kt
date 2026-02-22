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
        val INVITATION_RESPONSE_TIMEOUT_MINUTES = Option(
            "community.invitation_response_timeout_minutes",
            5,
            "the time limit in minutes for a player to respond to a community invitation."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_FREE_USES_FORMAL_MEMBER = Option(
            "teleport.free_uses_formal_member_per_day",
            10,
            "free teleport uses per day in the same community for owner/admin/member."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_FREE_USES_NON_FORMAL = Option(
            "teleport.free_uses_non_formal_per_day",
            1,
            "free teleport uses per day in the same community for non-formal players."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_PAID_BASE_DELAY_SECONDS = Option(
            "teleport.paid_base_delay_seconds",
            2,
            "base teleport delay in seconds after free uses are exhausted."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val TELEPORT_POST_EFFECT_TICKS = Option(
            "teleport.post_effect_ticks",
            40,
            "status effect duration in ticks after teleport completes."
        ) { obj, path ->
            obj.getInt(path)
        }
    }
}
