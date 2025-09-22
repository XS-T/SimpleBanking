package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.md_5.bungee.api.ChatColor
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission

class balanceTopCommand {
	@Command("balTop")
	@Permission("banking.command.baltop.use")
	fun onExecute(player:Player){
		val limit = 10

		val topPlayers = accountManager.getTopRichestPlayers(limit)

		player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Top $limit Richest Players:")
		player.sendMessage("${ChatColor.GRAY}${"-".repeat(40)}")

		topPlayers.forEachIndexed { index, account ->
			val position = index + 1
			val balance = accountManager.formatCurrency(account.balance)
			val color = when (position) {
				1 -> ChatColor.YELLOW
				2 -> ChatColor.GRAY
				3 -> ChatColor.GOLD
				else -> ChatColor.WHITE
			}

			player.sendMessage("$color$position. ${account.playerName}: ${ChatColor.GREEN}$balance")
		}
	}
}
