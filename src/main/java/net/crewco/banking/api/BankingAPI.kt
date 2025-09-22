package net.crewco.banking.api


import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.BankAccount
import net.crewco.banking.models.TransactionType
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.*

/**
 * Main API class for external plugins to interact with the Banking system
 */
class BankingAPI() {

	/**
	 * Get a player's balance
	 */
	fun getBalance(player: OfflinePlayer): BigDecimal {
		return accountManager.getAccount(player.uniqueId)?.balance ?: BigDecimal.ZERO
	}

	/**
	 * Get a player's balance by UUID
	 */
	fun getBalance(playerId: UUID): BigDecimal {
		return accountManager.getAccount(playerId)?.balance ?: BigDecimal.ZERO
	}

	/**
	 * Check if a player has enough money
	 */
	fun hasBalance(player: OfflinePlayer, amount: BigDecimal): Boolean {
		return getBalance(player) >= amount
	}

	/**
	 * Deposit money to a player's account
	 */
	fun deposit(player: OfflinePlayer, amount: BigDecimal, reason: String = "API Deposit"): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name ?: "Unknown")
		return if (accountManager.updateBalance(account.playerId, account.balance + amount)) {
			transactionManager.recordTransaction(
				account.playerId,
				null,
				amount,
				TransactionType.DEPOSIT,
				reason
			)
			true
		} else false
	}

	/**
	 * Withdraw money from a player's account
	 */
	fun withdraw(player: OfflinePlayer, amount: BigDecimal, reason: String = "API Withdrawal"): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val account = accountManager.getAccount(player.uniqueId) ?: return false
		if (account.balance < amount) return false

		return if (accountManager.updateBalance(account.playerId, account.balance - amount)) {
			transactionManager.recordTransaction(
				account.playerId,
				null,
				amount.negate(),
				TransactionType.WITHDRAWAL,
				reason
			)
			true
		} else false
	}

	/**
	 * Transfer money between players
	 */
	fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: BigDecimal, reason: String = "API Transfer"): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val fromAccount = accountManager.getAccount(from.uniqueId) ?: return false
		val toAccount = accountManager.getOrCreateAccount(to.uniqueId, to.name ?: "Unknown")

		if (fromAccount.balance < amount) return false

		return transactionManager.transferMoney(
			fromAccount.playerId,
			toAccount.playerId,
			amount,
			reason
		)
	}

	/**
	 * Set a player's balance
	 */
	fun setBalance(player: OfflinePlayer, amount: BigDecimal, reason: String = "Balance Set"): Boolean {
		if (amount < BigDecimal.ZERO) return false

		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name ?: "Unknown")
		val difference = amount - account.balance

		return if (accountManager.updateBalance(account.playerId, amount)) {
			val transactionType = if (difference >= BigDecimal.ZERO) TransactionType.DEPOSIT else TransactionType.WITHDRAWAL
			transactionManager.recordTransaction(
				account.playerId,
				null,
				difference,
				transactionType,
				reason
			)
			true
		} else false
	}

	/**
	 * Get a player's account information
	 */
	fun getAccount(player: OfflinePlayer): BankAccount? {
		return accountManager.getAccount(player.uniqueId)
	}

	/**
	 * Create an account for a player
	 */
	fun createAccount(player: OfflinePlayer): Boolean {
		return accountManager.createAccount(player.uniqueId, player.name ?: "Unknown")
	}

	/**
	 * Check if a player has an account
	 */
	fun hasAccount(player: OfflinePlayer): Boolean {
		return accountManager.hasAccount(player.uniqueId)
	}

	/**
	 * Get top richest players
	 */
	fun getTopRichestPlayers(limit: Int = 10): List<BankAccount> {
		return accountManager.getTopRichestPlayers(limit)
	}

	/**
	 * Get total money in circulation
	 */
	fun getTotalMoney(): BigDecimal {
		return accountManager.getTotalMoney()
	}

	/**
	 * Format currency amount
	 */
	fun formatCurrency(amount: BigDecimal): String {
		return accountManager.formatCurrency(amount)
	}
}