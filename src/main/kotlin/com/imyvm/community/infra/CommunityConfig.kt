package com.imyvm.community.infra

import com.imyvm.hoki.config.ConfigOption
import com.imyvm.hoki.config.HokiConfig
import com.imyvm.hoki.config.Option
import java.time.ZoneId

class CommunityConfig : HokiConfig("Community.conf") {
    companion object {
        @JvmField
        @ConfigOption
        val LANGUAGE = Option(
            "language",
            "zh_cn",
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

        @JvmField
        @ConfigOption
        val BUILDING_REWARD_DEFAULT_BLOCK_LIMIT = Option(
            "building.reward_default_block_limit",
            12,
            "default block count limit paid per building reward claim."
        ) { obj, path ->
            obj.getInt(path)
        }

        @JvmField
        @ConfigOption
        val TAX_WELFARE_TAX_RATE = Option(
            "tax_welfare.tax_rate",
            0.02,
            "community asset tax rate applied during tax welfare settlement."
        ) { obj, path ->
            obj.getDouble(path)
        }

        @JvmField
        @ConfigOption
        val TAX_WELFARE_PER_MEMBER = Option(
            "tax_welfare.per_member",
            1000L,
            "welfare amount per member for each settlement."
        ) { obj, path ->
            obj.getLong(path)
        }

        @JvmField
        @ConfigOption
        val TAX_WELFARE_RETRY_DELAY_SECONDS = Option(
            "tax_welfare.retry_delay_seconds",
            300,
            "delay before retrying failed tax welfare settlement."
        ) { obj, path ->
            obj.getInt(path)
        }


        fun validateValues() {
            require(LANGUAGE.value.isNotBlank()) { "language must not be blank" }
            ZoneId.of(TIMEZONE.value)
            require(PENDING_CHECK_INTERVAL_SECONDS.value > 0) { "pending.check_interval_ticks must be positive" }
            require(MAX_MANOR_AREA.value > 0) { "region.max_manor_area must be positive" }
            require(MIN_NUMBER_MEMBER_REALM.value > 0) { "community.min_number_member_realm must be positive" }
            require(REALM_REQUEST_EXPIRE_HOURS.value > 0) { "community.realm_request_expire_hours must be positive" }
            require(AUDITING_EXPIRE_HOURS.value > 0) { "community.auditing_expire_hours must be positive" }
            require(MAX_MEMBER_MANOR.value > 0) { "community.max_member_manor must be positive" }
            require(MAX_NUMBER_ADMIN.value >= 0) { "community.max_number_admin must not be negative" }
            require(INVITATION_RESPONSE_TIMEOUT_MINUTES.value > 0) { "community.invitation_response_timeout_minutes must be positive" }
            require(TELEPORT_FREE_USES_FORMAL_MEMBER.value >= 0) { "teleport.free_uses_formal_member_per_day must not be negative" }
            require(TELEPORT_FREE_USES_NON_FORMAL.value >= 0) { "teleport.free_uses_non_formal_per_day must not be negative" }
            require(TELEPORT_PAID_BASE_DELAY_SECONDS.value >= 0) { "teleport.paid_base_delay_seconds must not be negative" }
            require(TELEPORT_POST_EFFECT_TICKS.value >= 0) { "teleport.post_effect_ticks must not be negative" }
            require(BUILDING_REWARD_DEFAULT_BLOCK_LIMIT.value >= 0) { "building.reward_default_block_limit must not be negative" }
            require(TAX_WELFARE_TAX_RATE.value in 0.0..1.0) { "tax_welfare.tax_rate must be between 0.0 and 1.0" }
            require(TAX_WELFARE_PER_MEMBER.value >= 0L) { "tax_welfare.per_member must not be negative" }
            require(TAX_WELFARE_RETRY_DELAY_SECONDS.value > 0) { "tax_welfare.retry_delay_seconds must be positive" }
        }
    }
}
