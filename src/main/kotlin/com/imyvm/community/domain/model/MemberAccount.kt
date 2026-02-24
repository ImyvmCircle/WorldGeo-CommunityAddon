package com.imyvm.community.domain.model

import com.imyvm.community.domain.model.community.MemberRoleType
import com.imyvm.community.domain.policy.permission.AdminPrivileges
import net.minecraft.text.Text

data class MemberAccount (
    var joinedTime: Long,
    var basicRoleType: MemberRoleType,
    var adminPrivileges: AdminPrivileges? = null,
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