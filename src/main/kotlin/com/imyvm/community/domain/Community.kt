package com.imyvm.community.domain

import com.imyvm.community.domain.community.*
import com.imyvm.community.util.Translator
import com.imyvm.community.util.getFormattedMillsHour
import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.inter.api.PlayerInteractionApi
import com.imyvm.iwg.inter.api.RegionDataApi
import com.imyvm.iwg.inter.api.UtilApi
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

class Community(
    val regionNumberId: Int?,
    var member: HashMap<UUID, MemberAccount>,
    var joinPolicy: CommunityJoinPolicy,
    var status: CommunityStatus,
    var council: Council = Council(),
    var announcements: MutableList<Announcement> = mutableListOf(),
    var administrationPermissions: AdministrationPermissions = AdministrationPermissions(),
    var expenditures: ArrayList<Turnover> = arrayListOf(),
    var messages: MutableList<CommunityMessage> = mutableListOf()
) {
    fun isManor(): Boolean {
        return status == CommunityStatus.PENDING_MANOR || status == CommunityStatus.ACTIVE_MANOR || status == CommunityStatus.REVOKED_MANOR
    }

    fun generateCommunityMark(): String {
        return RegionDataApi.getRegion(this.regionNumberId!!)?.name ?: "Community #${this.regionNumberId}"
    }

    fun getFormattedFoundingTime(): String {
        val foundingTimeMillis = this.regionNumberId?.let { UtilApi.parseRegionFoundingTime(it) }
        return getFormattedMillsHour(foundingTimeMillis ?: 0L)
    }

    fun getRegion(): Region? {
        if (regionNumberId == null) return null
        return RegionDataApi.getRegion(regionNumberId)
    }

    fun sendCommunityRegionDescription(player: ServerPlayerEntity) {
        val region = getRegion()
        if(region != null){
            PlayerInteractionApi.queryRegionInfo(player, region)
        } else {
            player.sendMessage(Translator.tr("community.description.no_region", regionNumberId))
        }
    }

    fun getOwnerUUID(): UUID? {
        return this.member.entries.filter { it.value.basicRoleType.name == "OWNER" }
            .map { it.key }
            .firstOrNull()
    }

    fun getAdminUUIDs(): List<UUID> {
        return this.member.entries.filter { it.value.basicRoleType.name == "ADMIN" }.map { it.key }
    }

    fun getMemberUUIDs(): List<UUID> {
        return this.member.entries
            .filter { it.value.basicRoleType.name == "MEMBER" }
            .map { it.key }
    }

    fun getMemberRole(playerUuid: UUID): MemberRoleType? {
        return member[playerUuid]?.basicRoleType
    }

    @Deprecated("Use PermissionCheck.canManageMember instead", ReplaceWith("PermissionCheck.canManageMember(playerExecutor, this, targetPlayerUuid)"))
    fun isManageable(playerExecutor: ServerPlayerEntity, targetPlayerUuid: UUID): Boolean {
        return com.imyvm.community.application.permission.PermissionCheck
            .canManageMember(playerExecutor, this, targetPlayerUuid).isAllowed()
    }

    @Deprecated("Use PermissionCheck.canExecuteAdministration instead", ReplaceWith("PermissionCheck.canExecuteAdministration(playerExecutor, this)"))
    fun isManageable(playerExecutor: ServerPlayerEntity): Boolean {
        return com.imyvm.community.application.permission.PermissionCheck
            .canExecuteAdministration(playerExecutor, this).isAllowed()
    }

    fun getTotalAssets(): Long {
        val totalIncome = member.values.sumOf { it.getTotalDonation() }
        val totalExpenditure = expenditures.sumOf { it.amount }
        return totalIncome - totalExpenditure
    }

    fun getDonorList(): List<UUID> {
        return member.entries
            .filter { it.value.turnover.isNotEmpty() }
            .sortedByDescending { it.value.getTotalDonation() }
            .map { it.key }
    }

    fun addAnnouncement(announcement: Announcement) {
        announcements.add(announcement)
    }

    fun getActiveAnnouncements(): List<Announcement> {
        return announcements.filter { !it.isDeleted }
    }

    fun getAllAnnouncements(): List<Announcement> {
        return announcements
    }

    fun getAnnouncementById(id: UUID): Announcement? {
        return announcements.find { it.id == id }
    }

    fun softDeleteAnnouncement(id: UUID): Boolean {
        val announcement = getAnnouncementById(id) ?: return false
        announcement.isDeleted = true
        return true
    }

    fun getUnreadAnnouncementsFor(playerUUID: UUID): List<Announcement> {
        return getActiveAnnouncements().filter { !it.isReadBy(playerUUID) }
    }

    fun transferOwnership(newOwnerUUID: UUID): Boolean {
        val currentOwnerUUID = getOwnerUUID() ?: return false
        val newOwnerAccount = member[newOwnerUUID] ?: return false
        val currentOwnerAccount = member[currentOwnerUUID] ?: return false

        if (newOwnerAccount.basicRoleType == MemberRoleType.APPLICANT || 
            newOwnerAccount.basicRoleType == MemberRoleType.REFUSED) {
            return false
        }

        currentOwnerAccount.basicRoleType = MemberRoleType.ADMIN

        newOwnerAccount.basicRoleType = MemberRoleType.OWNER

        return true
    }

    fun addMessage(message: CommunityMessage) {
        messages.add(message)
    }

    fun getChatMessages(): List<CommunityMessage> {
        return messages.filter { it.type == MessageType.CHAT && !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }

    fun getMailsFor(playerUUID: UUID): List<CommunityMessage> {
        return messages.filter { 
            it.type == MessageType.MAIL && 
            !it.isDeleted && 
            it.recipientUUID == playerUUID 
        }.sortedByDescending { it.timestamp }
    }

    fun getAnnouncementsAsMessages(): List<CommunityMessage> {
        return messages.filter { it.type == MessageType.ANNOUNCEMENT && !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }
}