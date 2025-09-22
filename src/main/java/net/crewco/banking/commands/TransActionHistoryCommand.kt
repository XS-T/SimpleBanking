package net.crewco.banking.commands

import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

class TransActionHistoryCommand {
	private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")

	@Command("transActionHistory <args>")
	@Permission("banking.command.history.use")
	fun onExecute(player:Player, @Argument("args") args:Array<String>){
		val page = if (args.isNotEmpty()) {
			try {
				args[0].toInt().coerceAtLeast(1)
			} catch (e: NumberFormatException) {
				1
			}
		} else 1

		val limit = 10
		val offset = (page - 1) * limit

		val transactions = transactionManager.getTransactionHistory(player.uniqueId, limit, offset)

		if (transactions.isEmpty()) {
			player.sendMessage("${ChatColor.YELLOW}No transactions found.")
			return
		}

		player.sendMessage("${ChatColor.GOLD}${ChatColor.BOLD}Transaction History - Page $page:")
		player.sendMessage("${ChatColor.GRAY}${"-".repeat(50)}")

		transactions.forEach { transaction ->
			val color = if (transaction.amount >= BigDecimal.ZERO) ChatColor.GREEN else ChatColor.RED
			val sign = if (transaction.amount >= BigDecimal.ZERO) "+" else ""
			val formattedAmount = "$sign${transaction.amount}"
			val date = transaction.timestamp.format(dateFormatter)

			val message = TextComponent("$color$formattedAmount ${ChatColor.GRAY}| ${transaction.type.displayName}")
			message.hoverEvent = HoverEvent(
				HoverEvent.Action.SHOW_TEXT,
				ComponentBuilder("${ChatColor.WHITE}${transaction.description}\n")
					.append("${ChatColor.GRAY}Date: $date\n")
					.append("Balance After: ${ChatColor.GREEN}${transaction.balanceAfter}")
					.create()
			)

			player.spigot().sendMessage(message)
		}

		// Navigation
		if (page > 1 || transactions.size == limit) {
			val navigation = TextComponent("${ChatColor.GRAY}[")

			if (page > 1) {
				val prevButton = TextComponent("${ChatColor.YELLOW}<<Previous")
				prevButton.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/banktransactions ${page - 1}")
				navigation.addExtra(prevButton)
			}

			if (page > 1 && transactions.size == limit) {
				navigation.addExtra(TextComponent("${ChatColor.GRAY} | "))
			}

			if (transactions.size == limit) {
				val nextButton = TextComponent("${ChatColor.YELLOW}Next>>")
				nextButton.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/banktransactions ${page + 1}")
				navigation.addExtra(nextButton)
			}

			navigation.addExtra(TextComponent("${ChatColor.GRAY}]"))
			player.spigot().sendMessage(navigation)
		}

		return
	}
}