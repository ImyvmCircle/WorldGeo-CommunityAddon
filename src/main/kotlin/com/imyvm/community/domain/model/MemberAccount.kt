package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import net.minecraft.network.chat.Component

data class MemberAccount (
    var joinedTime: Long,
    var basicRoleType: MemberRoleType,
    var adminPrivileges: AdminPrivileges? = null,
    var mail: ArrayList<Component> = arrayListOf(),
    var turnover: ArrayList<Turnover> = arrayListOf(),
    var isInvited: Boolean = false,
    var chatHistoryEnabled: Boolean = true
) {
    fun getTotalDonation(): Long {
        return turnover.sumOf { it.amount }
    }
}

enum class TurnoverSource(val value: Int) {
    PLAYER(0),
    COMMUNITY_GRANT(1),
    SYSTEM(2),
    SERVER_ADMIN(3),
    UNKNOWN(4);

    companion object {
        fun fromValue(value: Int): TurnoverSource =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}

data class Turnover(
    val amount: Long,
    val timestamp: Long,
    val source: TurnoverSource = TurnoverSource.UNKNOWN,
    val descriptionKey: String? = null,
    val descriptionArgs: List<String> = emptyList()
)