package com.imyvm.community.application.interaction.command

import com.imyvm.community.application.weekly.CommunityWeeklyReportService
import net.minecraft.server.level.ServerPlayer

fun onWeeklyReportList(player: ServerPlayer): Int = CommunityWeeklyReportService.sendList(player)

fun onWeeklyReportRead(player: ServerPlayer, index: Int): Int = CommunityWeeklyReportService.sendRead(player, index)
