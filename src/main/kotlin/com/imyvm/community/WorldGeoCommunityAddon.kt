package com.imyvm.community

import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.entrypoints.command.register
import com.imyvm.community.entrypoints.event.registerAnnouncementNotification
import com.imyvm.community.entrypoints.event.registerChatInterceptor
import com.imyvm.community.entrypoints.event.registerExpireCheck
import com.imyvm.community.entrypoints.event.registerMailCheck
import com.imyvm.community.entrypoints.registerDataLoadAndSave
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
		registerAnnouncementNotification()
		registerChatInterceptor()

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> register(dispatcher) }
		logger.info("$MOD_ID initialized successfully.")
	}

	companion object {
		const val MOD_ID = "community"
		val logger: Logger = LoggerFactory.getLogger(MOD_ID)

		val pendingOperations: MutableMap<Int, PendingOperation> = mutableMapOf()
		var server: MinecraftServer? = null
	}
}