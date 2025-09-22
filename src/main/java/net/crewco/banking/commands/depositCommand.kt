package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.TransactionType
import net.md_5.bungee.api.ChatColor
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import java.math.BigDecimal

class depositCommand {
	@Command("deposit <args>")
	@Permission("banking.command.deposit.use")
	fun onExecute(player:Player, @Argument("args") args:Array<String>){
		if (args.isEmpty()) {
			player.sendMessage("${ChatColor.RED}Usage: /deposit <amount>")

		}

		val amount = try {
			BigDecimal(args[0])
		} catch (e: NumberFormatException) {
			player.sendMessage("${ChatColor.RED}Invalid amount: ${args[0]}")
			return
		}

		if (amount <= BigDecimal.ZERO) {
			player.sendMessage("${ChatColor.RED}Amount must be positive!")
			return

		}

		// Here you would integrate with your item-based economy or other deposit method
		// For now, we'll just add the money (you can modify this based on your server's economy)
		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name)
		val newBalance = account.balance + amount

		if (accountManager.updateBalance(player.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				player.uniqueId,
				null,
				amount,
				TransactionType.DEPOSIT,
				"Cash deposit",
				account.balance,
				newBalance
			)
			player.sendMessage("${ChatColor.GREEN}Successfully deposited ${accountManager.formatCurrency(amount)}!")
			player.sendMessage("${ChatColor.GREEN}New balance: ${accountManager.formatCurrency(newBalance)}")
		} else {
			player.sendMessage("${ChatColor.RED}Deposit failed! Please try again later.")
		}
	}
}