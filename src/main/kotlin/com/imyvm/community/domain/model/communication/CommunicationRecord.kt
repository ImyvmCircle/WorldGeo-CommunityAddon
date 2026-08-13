package com.imyvm.community.domain.model.communication

data class CommunicationRecord(
    val regionId: Int,
    val recordedAtMillis: Long,
    val senderUuid: String?,
    val senderName: String?,
    val type: CommunicationRecordType,
    val legacyText: String? = null,
    val localizationKey: String? = null,
    val localizationArgs: List<String> = emptyList(),
    val visibility: CommunicationVisibility = CommunicationVisibility.MEMBER
)

enum class CommunicationRecordType { CHAT, SYSTEM, ANNOUNCEMENT, OP_EXCEPTION }

enum class CommunicationVisibility { MEMBER, ALL, OP }

enum class CommunicationCategory(val filePrefix: String, val retentionDays: Int?) {
    CHAT("CHAT", null),
    SYSTEM("SYS", 180),
    OP_EXCEPTION_UNCLOSED("OPX", 365)
}
