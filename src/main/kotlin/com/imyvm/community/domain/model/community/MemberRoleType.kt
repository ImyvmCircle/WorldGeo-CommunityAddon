package com.imyvm.community.domain.model.community

enum class MemberRoleType(val value: Int) {
    OWNER(0),
    ADMIN(1),
    MEMBER(2),
    APPLICANT(3),
    REFUSED(4);

    companion object {
        fun fromValue(value: Int): MemberRoleType {
            return entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Invalid MemberRole value: $value")
        }
    }
}