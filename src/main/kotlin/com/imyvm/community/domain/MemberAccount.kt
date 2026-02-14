package com.imyvm.community.domain

import com.imyvm.community.domain.community.MemberRoleType
import net.minecraft.text.Text

data class MemberAccount (
    var joinedTime: Long,
    var basicRoleType: MemberRoleType,
    val isCouncilMember: Boolean = false,
    var governorship: Int = -1,
    var mail: ArrayList<Text> = arrayListOf(),
    var turnover: ArrayList<Turnover> = arrayListOf(),
    var isInvited: Boolean = false,
    var chatHistoryEnabled: Boolean = true
) {
    fun getTotalDonation(): Long {
        return turnover.sumOf { it.amount }
    }
}

data class Turnover(
    val amount: Long,
    val timestamp: Long
)