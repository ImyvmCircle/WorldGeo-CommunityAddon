package com.imyvm.community

import com.imyvm.community.domain.model.PendingOperationStore
import com.imyvm.community.entrypoint.command.register
import com.imyvm.community.entrypoint.command.registerCh
import com.imyvm.community.entrypoint.command.registerCommun
import com.imyvm.community.entrypoint.event.registerAccountIdentityCapture
import com.imyvm.community.entrypoint.event.registerAccountIssueNotification
import com.imyvm.community.entrypoint.event.registerAnnouncementNotification
import com.imyvm.community.entrypoint.event.registerChatInterceptor
import com.imyvm.community.application.interaction.common.registerCommunityCreationAccountRecovery
import com.imyvm.community.application.interaction.common.registerApplicationRefundRecovery
import com.imyvm.community.application.interaction.screen.inner_community.affairs.registerDonationAccountRecovery
import com.imyvm.community.entrypoint.event.registerExpireCheck
import com.imyvm.community.entrypoint.event.registerMailCheck
import com.imyvm.community.entrypoint.event.registerPendingRefundCheck
import com.imyvm.community.entrypoint.event.registerSelectionContextCleanup
import com.imyvm.community.entrypoint.registerDataLoadAndSave
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WorldGeoCommunityAddon : ModInitializer {

    override fun onInitialize() {
        registerDataLoadAndSave()
        registerExpireCheck()
        registerMailCheck()
        registerPendingRefundCheck()
        registerSelectionContextCleanup()
        registerAnnouncementNotification()
        registerAccountIdentityCapture()
        registerAccountIssueNotification()
        registerCommunityCreationAccountRecovery()
        registerApplicationRefundRecovery()
        registerDonationAccountRecovery()
        registerChatInterceptor()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerCh(dispatcher) }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> registerCommun(dispatcher) }
        logger.info("$MOD_ID initialized successfully.")
    }

    companion object {
        const val MOD_ID = "community"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)

        val pendingOperations = PendingOperationStore()
        var server: MinecraftServer? = null
    }
}
