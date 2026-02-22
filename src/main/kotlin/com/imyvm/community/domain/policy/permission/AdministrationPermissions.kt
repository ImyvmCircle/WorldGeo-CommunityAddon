package com.imyvm.community.domain.policy.permission

class AdministrationPermissions(
    private val enabledForAdmin: MutableSet<AdministrationPermission> = AdministrationPermission.getDefaultEnabledPermissions().toMutableSet()
) {

    fun isEnabledForAdmin(permission: AdministrationPermission): Boolean {
        return enabledForAdmin.contains(permission)
    }

    fun enableForAdmin(permission: AdministrationPermission) {
        enabledForAdmin.add(permission)
    }

    fun disableForAdmin(permission: AdministrationPermission) {
        enabledForAdmin.remove(permission)
    }

    fun toggleForAdmin(permission: AdministrationPermission): Boolean {
        return if (enabledForAdmin.contains(permission)) {
            enabledForAdmin.remove(permission)
            false
        } else {
            enabledForAdmin.add(permission)
            true
        }
    }

    fun getEnabledForAdmin(): Set<AdministrationPermission> {
        return enabledForAdmin.toSet()
    }
}
