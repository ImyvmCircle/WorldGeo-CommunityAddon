package com.imyvm.community.util

import com.imyvm.community.WorldGeoCommunityAddon.Companion.MOD_ID
import com.imyvm.community.infra.CommunityConfig
import com.imyvm.hoki.i18n.HokiLanguage
import com.imyvm.hoki.i18n.HokiTranslator
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component

object Translator : HokiTranslator() {
    private var languageInstance = createLanguage(CommunityConfig.LANGUAGE.value)

    init {
        CommunityConfig.LANGUAGE.changeEvents.register { option, _, _ ->
            languageInstance = createLanguage(option.value)
        }
    }

    fun trMenu(
        playerExecutor: ServerPlayer,
        key: String,
        vararg args: Any
    ) {
        tr(key, *args).let {
            playerExecutor.closeContainer()
            playerExecutor.sendSystemMessage(it)
        }
    }

    fun tr(key: String?, vararg args: Any?): Component {
        return format(key, args)?.let { TextParser.parse(it) } ?: Component.empty()
    }

    fun trOrFallback(key: String?, fallback: String, vararg args: Any?): Component {
        return format(key, args)?.let { TextParser.parse(it) } ?: Component.literal(fallback)
    }

    fun trStringOrFallback(key: String?, fallback: String, vararg args: Any?): String {
        return format(key, args)?.let { TextParser.parse(it).string } ?: fallback
    }

    private fun format(key: String?, args: Array<out Any?>): String? {
        val raw = key?.let { languageInstance.get(it) }
        return if (args.isNotEmpty()) {
            raw?.let { java.text.MessageFormat.format(it, *args) }
        } else {
            raw
        }
    }

    private fun createLanguage(languageId: String) = HokiLanguage.create(
        HokiLanguage.getResourcePath(MOD_ID, languageId)
            .let { Translator::class.java.getResourceAsStream(it) }
    )
}
