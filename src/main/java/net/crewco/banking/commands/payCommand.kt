package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.md_5.bungee.api.ChatColor
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Permission
import java.math.BigDecimal


class payCommand {
	@Command("pay <args>")
	@Permission("banking.command.pay.use")
	fun onExecute(player:Player, @Argument("args") args:Array<String>){

		if (args.size < 2) {
			player.sendMessage("${ChatColor.RED}Usage: /pay <player> <amount> [reason]")
			return
		}

		val targetPlayer = Bukkit.getOfflinePlayer(args[0])
		if (targetPlayer == player) {
			player.sendMessage("${ChatColor.RED}You cannot pay yourself!")
			return
		}

		val amount = try {
			BigDecimal(args[1])
		} catch (e: NumberFormatException) {
			player.sendMessage("${ChatColor.RED}Invalid amount: ${args[1]}")
			return
		}

		if (amount <= BigDecimal.ZERO) {
			player.sendMessage("${ChatColor.RED}Amount must be positive!")
			return
		}

		val reason = if (args.size > 2) args.drop(2).joinToString(" ") else "Payment"

		val playerAccount = accountManager.getOrCreateAccount(player.uniqueId, player.name)
		if (playerAccount.balance < amount) {
			player.sendMessage("${ChatColor.RED}Insufficient funds! You need ${accountManager.formatCurrency(amount - playerAccount.balance)} more.")
			return
		}

		if (transactionManager.transferMoney(player.uniqueId, targetPlayer.uniqueId, amount, reason)) {
			player.sendMessage("${ChatColor.GREEN}Successfully sent ${accountManager.formatCurrency(amount)} to ${targetPlayer.name}!")

			// Notify target player if online
			val onlineTarget = targetPlayer.player
			if (onlineTarget != null && onlineTarget.isOnline) {
				onlineTarget.sendMessage("${ChatColor.GREEN}You received ${accountManager.formatCurrency(amount)} from ${player.name}!")
				if (reason != "Payment") {
					onlineTarget.sendMessage("${ChatColor.GRAY}Reason: $reason")
				}
			}
		} else {
			player.sendMessage("${ChatColor.RED}Payment failed! Please try again later.")
		}
	}
}