package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.TransactionType
import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.stream.Stream

class bankAdminCommand {
	@Command("bankAdmin <args>")
	@Permission("banking.admin.command.use")
	fun onExecute(player: Player, @Argument("args", suggestions = "args") args:Array<String>){
		if (args.isEmpty()) {
			sendHelpMessage(player)
			return
		}

		when (args[0].lowercase()) {
			"set" -> handleSetCommand(player, args)
			"give" -> handleGiveCommand(player, args)
			"take" -> handleTakeCommand(player, args)
			"info" -> handleInfoCommand(player, args)
			"stats" -> handleStatsCommand(player)
			"reload" -> handleReloadCommand(player)
			else -> sendHelpMessage(player)
		}

		return
	}

	private fun handleSetCommand(player: Player, args: Array<String>) {
		if (args.size < 3) {
			player.sendMessage("${ChatColor.RED}Usage: /bankadmin set <player> <amount>")
			return
		}

		val targetPlayer = Bukkit.getOfflinePlayer(args[1])
		val amount = try {
			BigDecimal(args[2])
		} catch (e: NumberFormatException) {
			player.sendMessage("${ChatColor.RED}Invalid amount: ${args[2]}")
			return
		}

		if (amount < BigDecimal.ZERO) {
			player.sendMessage("${ChatColor.RED}Amount cannot be negative!")
			return
		}

		val account = accountManager.getOrCreateAccount(targetPlayer.uniqueId, targetPlayer.name ?: "Unknown")
		val oldBalance = account.balance

		if (accountManager.updateBalance(targetPlayer.uniqueId, amount)) {
			transactionManager.recordTransaction(
				targetPlayer.uniqueId,
				null,
				amount - oldBalance,
				TransactionType.ADMIN_SET,
				oldBalance,
				amount,
				"Balance set by ${player.name}"
			)

			player.sendMessage("${ChatColor.GREEN}Set ${targetPlayer.name}'s balance to ${accountManager.formatCurrency(amount)}")
		} else {
			player.sendMessage("${ChatColor.RED}Failed to set balance!")
		}
	}

	private fun handleGiveCommand(player: Player, args: Array<String>) {
		if (args.size < 3) {
			player.sendMessage("${ChatColor.RED}Usage: /bankadmin give <player> <amount>")
			return
		}

		val targetPlayer = Bukkit.getOfflinePlayer(args[1])
		val amount = try {
			BigDecimal(args[2])
		} catch (e: NumberFormatException) {
			player.sendMessage("${ChatColor.RED}Invalid amount: ${args[2]}")
			return
		}

		if (amount <= BigDecimal.ZERO) {
			player.sendMessage("${ChatColor.RED}Amount must be positive!")
			return
		}

		val account = accountManager.getOrCreateAccount(targetPlayer.uniqueId, targetPlayer.name ?: "Unknown")
		val newBalance = account.balance + amount

		if (accountManager.updateBalance(targetPlayer.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				targetPlayer.uniqueId,
				null,
				amount,
				TransactionType.ADMIN_GIVE,
				account.balance,
				newBalance,
				"Money given by ${player.name}"
			)

			player.sendMessage("${ChatColor.GREEN}Gave ${accountManager.formatCurrency(amount)} to ${targetPlayer.name}")

			// Notify target player if online
			val onlineTarget = targetPlayer.player
			if (onlineTarget != null && onlineTarget.isOnline) {
				onlineTarget.sendMessage("${ChatColor.GREEN}You received ${accountManager.formatCurrency(amount)} from an administrator!")
			}
		} else {
			player.sendMessage("${ChatColor.RED}Failed to give money!")
		}
	}

	private fun handleTakeCommand(player: Player, args: Array<String>) {
		if (args.size < 3) {
			player.sendMessage("${ChatColor.RED}Usage: /bankadmin take <player> <amount>")
			return
		}

		val targetPlayer = Bukkit.getOfflinePlayer(args[1])
		val amount = try {
			BigDecimal(args[2])
		} catch (e: NumberFormatException) {
			player.sendMessage("${ChatColor.RED}Invalid amount: ${args[2]}")
			return
		}

		if (amount <= BigDecimal.ZERO) {
			player.sendMessage("${ChatColor.RED}Amount must be positive!")
			return
		}

		val account = accountManager.getAccount(targetPlayer.uniqueId)
		if (account == null) {
			player.sendMessage("${ChatColor.RED}Player ${targetPlayer.name} doesn't have a bank account!")
			return
		}

		val newBalance = (account.balance - amount).max(BigDecimal.ZERO)
		val actualTaken = account.balance - newBalance

		if (accountManager.updateBalance(targetPlayer.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				targetPlayer.uniqueId,
				null,
				actualTaken.negate(),
				TransactionType.ADMIN_TAKE,
				account.balance,
				newBalance,
				"Money taken by ${player.name}"
			)

			player.sendMessage("${ChatColor.GREEN}Took ${accountManager.formatCurrency(actualTaken)} from ${targetPlayer.name}")
			if (actualTaken < amount) {
				player.sendMessage("${ChatColor.YELLOW}Note: Could only take ${accountManager.formatCurrency(actualTaken)} (insufficient funds)")
			}

			// Notify target player if online
			val onlineTarget = targetPlayer.player
			if (onlineTarget != null && onlineTarget.isOnline) {
				onlineTarget.sendMessage("${ChatColor.RED}${accountManager.formatCurrency(actualTaken)} was taken from your account by an administrator!")
			}
		} else {
			player.sendMessage("${ChatColor.RED}Failed to take money!")
		}
	}

	private fun handleInfoCommand(player: Player, args: Array<String>) {
		if (args.size < 2) {
			player.sendMessage("${ChatColor.RED}Usage: /bankadmin info <player>")
			return
		}

		val targetPlayer = Bukkit.getOfflinePlayer(args[1])
		val account = accountManager.getAccount(targetPlayer.uniqueId)

		if (account == null) {
			player.sendMessage("${ChatColor.RED}Player ${targetPlayer.name} doesn't have a bank account!")
			return
		}

		val recentTransactions = transactionManager.getTransactionHistory(targetPlayer.uniqueId, 5)

		player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Account Info for ${targetPlayer.name}:")
		player.sendMessage("${ChatColor.GRAY}${"-".repeat(40)}")
		player.sendMessage("${ChatColor.YELLOW}Balance: ${ChatColor.WHITE}${accountManager.formatCurrency(account.balance)}")
		player.sendMessage("${ChatColor.YELLOW}Account Created: ${ChatColor.WHITE}${account.createdAt.format(
			DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))}")
		player.sendMessage("${ChatColor.YELLOW}Last Updated: ${ChatColor.WHITE}${account.lastUpdated.format(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))}")
		player.sendMessage("${ChatColor.YELLOW}Interest Rate: ${ChatColor.WHITE}${account.interestRate.multiply(BigDecimal("100"))}% daily")
		player.sendMessage("${ChatColor.YELLOW}Status: ${if (account.isActive) "${ChatColor.GREEN}Active" else "${ChatColor.RED}Inactive"}")

		if (recentTransactions.isNotEmpty()) {
			player.sendMessage("${ChatColor.YELLOW}Recent Transactions:")
			recentTransactions.take(3).forEach { transaction ->
				val color = if (transaction.amount >= BigDecimal.ZERO) ChatColor.GREEN else ChatColor.RED
				val sign = if (transaction.amount >= BigDecimal.ZERO) "+" else ""
				player.sendMessage("${ChatColor.GRAY}  • $color$sign${transaction.amount} ${ChatColor.GRAY}(${transaction.type.displayName})")
			}
		}
	}

	private fun handleStatsCommand(player: Player) {
		val stats = transactionManager.getServerTransactionStats()
		val totalMoney = accountManager.getTotalMoney()

		player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Banking System Statistics:")
		player.sendMessage("${ChatColor.GRAY}${"-".repeat(40)}")
		player.sendMessage("${ChatColor.YELLOW}Total Money in Circulation: ${ChatColor.WHITE}${accountManager.formatCurrency(totalMoney)}")
		player.sendMessage("${ChatColor.YELLOW}Total Transactions: ${ChatColor.WHITE}${stats["totalTransactions"]}")
		player.sendMessage("${ChatColor.YELLOW}Total Transaction Volume: ${ChatColor.WHITE}${accountManager.formatCurrency(stats["totalVolume"] as BigDecimal)}")
		player.sendMessage("${ChatColor.YELLOW}Average Transaction Size: ${ChatColor.WHITE}${accountManager.formatCurrency(stats["averageTransactionSize"] as BigDecimal)}")

		val transactionsByType = stats["transactionsByType"] as Map<*, *>
		player.sendMessage("${ChatColor.YELLOW}Transactions by Type:")
		transactionsByType.forEach { (type, count) ->
			player.sendMessage("${ChatColor.GRAY}  • ${type}: ${ChatColor.WHITE}$count")
		}
	}

	private fun handleReloadCommand(player: Player) {
		accountManager.clearCache()
		player.sendMessage("${ChatColor.GREEN}Banking plugin cache cleared and configuration reloaded!")
	}

	private fun sendHelpMessage(player: Player) {
		player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Banking Admin Commands:")
		player.sendMessage("${ChatColor.GRAY}${"-".repeat(40)}")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin set <player> <amount> ${ChatColor.GRAY}- Set a player's balance")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin give <player> <amount> ${ChatColor.GRAY}- Give money to a player")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin take <player> <amount> ${ChatColor.GRAY}- Take money from a player")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin info <player> ${ChatColor.GRAY}- View account information")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin stats ${ChatColor.GRAY}- View server banking statistics")
		player.sendMessage("${ChatColor.YELLOW}/bankadmin reload ${ChatColor.GRAY}- Reload plugin configuration")
	}

	@Suggestions("args")
	fun containerSuggestions(
		context: CommandContext<Player>,
		input: String
	): Stream<String> {
		val CommandList = mutableListOf<String>()
		CommandList.add("set")
		CommandList.add("give")
		CommandList.add("take")
		CommandList.add("info")
		CommandList.add("stats")
		CommandList.add("reload")
		return CommandList.stream()
	}
}