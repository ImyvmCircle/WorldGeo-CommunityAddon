package com.imyvm.community.domain.community

enum class AdministrationPermission(val displayKey: String) {
    RENAME_COMMUNITY("ui.community.administration.button.name"),
    MANAGE_MEMBERS("ui.community.administration.button.members"),
    AUDIT_APPLICATIONS("ui.community.administration.button.audit"),
    MANAGE_ANNOUNCEMENTS("ui.community.administration.button.announcement"),
    MANAGE_ADVANCEMENT("ui.community.administration.button.advancement"),
    MANAGE_ASSETS("ui.community.administration.button.assets"),
    MODIFY_REGION_GEOMETRY("ui.community.administration.button.region.geometry"),
    MODIFY_REGION_SETTINGS("ui.community.administration.button.region.setting"),
    MANAGE_TELEPORT_POINTS("ui.community.administration.button.teleport"),
    CHANGE_JOIN_POLICY("ui.community.administration.button.join_policy");

    companion object {
        fun getDefaultEnabledPermissions(): Set<AdministrationPermission> {
            return AdministrationPermission.entries.toSet()
        }
    }
}
