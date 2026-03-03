package com.imyvm.community.domain.policy.permission

enum class AdminPrivilege(val displayKey: String) {
    RENAME_COMMUNITY("ui.admin.button.name"),
    MANAGE_MEMBERS("ui.admin.button.members"),
    AUDIT_APPLICATIONS("ui.admin.button.audit"),
    MANAGE_ANNOUNCEMENTS("ui.admin.button.announcement"),
    MANAGE_ADVANCEMENT("ui.admin.button.advancement"),
    MODIFY_REGION_GEOMETRY("ui.admin.button.region.geometry"),
    MODIFY_REGION_SETTINGS("ui.admin.button.region.setting"),
    MANAGE_TELEPORT_POINTS("ui.admin.button.teleport"),
    CHANGE_JOIN_POLICY("ui.admin.button.join_policy");

    companion object {
        fun defaultEnabled(): Set<AdminPrivilege> = entries.toSet()
    }
}
