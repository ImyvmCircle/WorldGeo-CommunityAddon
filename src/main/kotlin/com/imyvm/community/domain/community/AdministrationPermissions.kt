package com.imyvm.community.domain.community

class AdministrationPermissions(
    private val enabledForAdmin: MutableSet<AdministrationPermission> = AdministrationPermission.getDefaultEnabledPermissions().toMutableSet(),
    private val enabledForCouncil: MutableSet<AdministrationPermission> = AdministrationPermission.getDefaultEnabledPermissions().toMutableSet()
) {

    fun isEnabledForAdmin(permission: AdministrationPermission): Boolean {
        return enabledForAdmin.contains(permission)
    }

    fun isEnabledForCouncil(permission: AdministrationPermission): Boolean {
        return enabledForCouncil.contains(permission)
    }

    fun enableForAdmin(permission: AdministrationPermission) {
        enabledForAdmin.add(permission)
    }

    fun disableForAdmin(permission: AdministrationPermission) {
        enabledForAdmin.remove(permission)
    }

    fun enableForCouncil(permission: AdministrationPermission) {
        enabledForCouncil.add(permission)
    }

    fun disableForCouncil(permission: AdministrationPermission) {
        enabledForCouncil.remove(permission)
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

    fun toggleForCouncil(permission: AdministrationPermission): Boolean {
        return if (enabledForCouncil.contains(permission)) {
            enabledForCouncil.remove(permission)
            false
        } else {
            enabledForCouncil.add(permission)
            true
        }
    }

    fun getEnabledForAdmin(): Set<AdministrationPermission> {
        return enabledForAdmin.toSet()
    }

    fun getEnabledForCouncil(): Set<AdministrationPermission> {
        return enabledForCouncil.toSet()
    }
}
