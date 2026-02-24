package com.imyvm.community.domain.policy.permission

import com.imyvm.community.util.Translator
import net.minecraft.server.network.ServerPlayerEntity

sealed class PermissionResult {
    data object Allowed : PermissionResult()
    data class Denied(val reasonKey: String, val args: Array<out Any> = emptyArray()) : PermissionResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Denied
            if (reasonKey != other.reasonKey) return false
            if (!args.contentEquals(other.args)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = reasonKey.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    fun isAllowed(): Boolean = this is Allowed
    fun isDenied(): Boolean = this is Denied

    fun sendFeedback(player: ServerPlayerEntity) {
        if (this is Denied) {
            player.sendMessage(Translator.tr(reasonKey, *args))
        }
    }
}
