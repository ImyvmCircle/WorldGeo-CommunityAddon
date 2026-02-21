package com.imyvm.community

import com.imyvm.community.domain.model.PendingOperation
import com.imyvm.community.entrypoint.command.register
import com.imyvm.community.entrypoint.event.registerAnnouncementNotification
import com.imyvm.community.entrypoint.event.registerChatInterceptor
import com.imyvm.community.entrypoint.event.registerExpireCheck
import com.imyvm.community.entrypoint.event.registerMailCheck
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