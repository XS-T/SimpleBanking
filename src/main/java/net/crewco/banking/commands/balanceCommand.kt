package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission

class balanceCommand {
	@Command("balance")
	@Permission("banking.command.balance")
	fun onExecute(player:Player){
		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name ?: "Unknown")
		val balance = accountManager.formatCurrency(account.balance)
		player.sendMessage("${ChatColor.GREEN}Your balance is: ${ChatColor.YELLOW}$balance")
		}
	}