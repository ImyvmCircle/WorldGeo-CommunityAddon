package com.imyvm.community.domain.policy.permission

class AdminPrivileges(
    private val enabled: MutableSet<AdminPrivilege> = AdminPrivilege.defaultEnabled().toMutableSet()
) {

    fun isEnabled(privilege: AdminPrivilege): Boolean = enabled.contains(privilege)

    fun toggle(privilege: AdminPrivilege): Boolean {
        return if (enabled.contains(privilege)) {
            enabled.remove(privilege)
            false
        } else {
            enabled.add(privilege)
            true
        }
    }

    fun getEnabled(): Set<AdminPrivilege> = enabled.toSet()
}
