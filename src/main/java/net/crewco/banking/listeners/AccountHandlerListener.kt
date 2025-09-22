package net.crewco.banking.listeners

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.plugin
import net.md_5.bungee.api.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class AccountHandlerListener:Listener {
	@EventHandler
	fun onPlayerJoin(event: PlayerJoinEvent) {
		val player = event.player

		// Create account if it doesn't exist
		if (!accountManager.hasAccount(player.uniqueId)) {
			accountManager.createAccount(player.uniqueId, player.name)
			player.sendMessage("${ChatColor.GREEN}Welcome! A bank account has been created for you.")
		} else {
			// Load account into cache
			accountManager.getOrCreateAccount(player.uniqueId, player.name)
		}

		// Show balance on join if enabled in config
		if (plugin.config.getBoolean("show-balance-on-join", true)) {
			val account = accountManager.getAccount(player.uniqueId)
			if (account != null) {
				val balance = accountManager.formatCurrency(account.balance)
				player.sendMessage("${ChatColor.YELLOW}Current balance: ${ChatColor.GREEN}$balance")
			}
		}
	}

	@EventHandler
	fun onPlayerQuit(event: PlayerQuitEvent) {
		val player = event.player

		// Save account data when player leaves
		val account = accountManager.getAccount(player.uniqueId)
		if (account != null) {
			// The account is automatically saved due to caching system
			// This could be extended to force-save critical data
		}
	}
}