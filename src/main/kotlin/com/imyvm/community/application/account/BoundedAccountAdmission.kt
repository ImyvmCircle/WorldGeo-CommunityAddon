package com.imyvm.community.application.account

import java.util.UUID

enum class AdmissionResult {
    ACQUIRED,
    ALREADY_ACTIVE,
    SATURATED
}

class BoundedAccountAdmission(private val capacity: Int) {
    private val active = HashSet<UUID>()

    init {
        require(capacity > 0)
    }

    fun acquire(subjectUuid: UUID): AdmissionResult = when {
        subjectUuid in active -> AdmissionResult.ALREADY_ACTIVE
        active.size >= capacity -> AdmissionResult.SATURATED
        else -> {
            active.add(subjectUuid)
            AdmissionResult.ACQUIRED
        }
    }

    fun release(subjectUuid: UUID): Boolean = active.remove(subjectUuid)

    fun size(): Int = active.size
}
