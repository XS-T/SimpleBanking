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

class withdrawCommand {
	@Command("withdraw <args>")
	@Permission("banking.command.withdraw.use")
	fun onExecute(player:Player, @Argument("args") args:Array<String>){
		if (args.isEmpty()) {
			player.sendMessage("${ChatColor.RED}Usage: /withdraw <amount>")
			return
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

		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name)
		if (account.balance < amount) {
			player.sendMessage("${ChatColor.RED}Insufficient funds! You need ${accountManager.formatCurrency(amount - account.balance)} more.")
			return
		}

		val newBalance = account.balance - amount
		if (accountManager.updateBalance(player.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				player.uniqueId,
				null,
				amount.negate(),
				TransactionType.WITHDRAWAL,
				account.balance,
				newBalance,
				"Cash withdrawal"
			)

			// Here you would give the player physical money items
			// For now, we'll just show a success message
			player.sendMessage("${ChatColor.GREEN}Successfully withdrew ${accountManager.formatCurrency(amount)}!")
			player.sendMessage("${ChatColor.GREEN}New balance: ${accountManager.formatCurrency(newBalance)}")
		} else {
			player.sendMessage("${ChatColor.RED}Withdrawal failed! Please try again later.")
		}
	}
}