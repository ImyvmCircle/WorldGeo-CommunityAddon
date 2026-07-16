package com.imyvm.community.application.interaction.common

import com.imyvm.community.WorldGeoCommunityAddon
import com.imyvm.community.infra.CommunityDatabase
import com.imyvm.community.util.Translator
import net.minecraft.server.level.ServerPlayer

internal fun saveCommunityDatabaseOrRollback(
    player: ServerPlayer,
    operationName: String,
    restoreCommunityState: () -> Unit,
    rollbackCoreState: () -> Unit
): Boolean = saveCommunityStateOrRollback(
    operationName = operationName,
    saveCommunityState = { CommunityDatabase.save() },
    restoreCommunityState = restoreCommunityState,
    rollbackCoreState = rollbackCoreState,
    notifyFailure = { player.sendSystemMessage(Translator.tr("community.operation.save_failed", operationName)) }
)

internal fun saveCommunityStateOrRollback(
    operationName: String,
    saveCommunityState: () -> Unit,
    restoreCommunityState: () -> Unit,
    rollbackCoreState: () -> Unit,
    notifyFailure: () -> Unit
): Boolean {
    return try {
        saveCommunityState()
        true
    } catch (error: Exception) {
        runCatching { restoreCommunityState() }
            .onFailure { restoreError ->
                WorldGeoCommunityAddon.logger.error("Failed to restore CommunityAddon state after $operationName save failure", restoreError)
            }
        runCatching { rollbackCoreState() }
            .onFailure { rollbackError ->
                WorldGeoCommunityAddon.logger.error("Failed to rollback WorldGeo Core state after $operationName save failure", rollbackError)
            }
        WorldGeoCommunityAddon.logger.error("Failed to save CommunityAddon database after $operationName", error)
        notifyFailure()
        false
    }
}
