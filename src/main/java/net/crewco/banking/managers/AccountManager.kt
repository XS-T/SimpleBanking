package net.crewco.banking.managers

import com.google.inject.Inject
import net.crewco.banking.BankingPlugin
import net.crewco.banking.BankingPlugin.Companion.databaseManager
import net.crewco.banking.models.BankAccount
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class AccountManager @Inject constructor(private val plugin:BankingPlugin) {

	private val accountCache = ConcurrentHashMap<UUID, BankAccount>()
	private val currencyFormat = DecimalFormat("#,##0.00")

	/**
	 * Get or create a player's account
	 */
	fun getOrCreateAccount(playerId: UUID, playerName: String): BankAccount {
		// Check cache first
		accountCache[playerId]?.let { return it }

		// Try to load from database
		var account = databaseManager.loadAccount(playerId)

		// Create new account if doesn't exist
		if (account == null) {
			createAccount(playerId, playerName)
			account = databaseManager.loadAccount(playerId)!!
		}

		// Update cache
		accountCache[playerId] = account
		return account
	}

	/**
	 * Get a player's account (null if doesn't exist)
	 */
	fun getAccount(playerId: UUID): BankAccount? {
		// Check cache first
		accountCache[playerId]?.let { return it }

		// Load from database
		val account = databaseManager.loadAccount(playerId)
		account?.let { accountCache[playerId] = it }
		return account
	}

	/**
	 * Create a new account for a player
	 */
	fun createAccount(playerId: UUID, playerName: String): Boolean {
		val startingBalance = BigDecimal(plugin.config.getDouble("starting-balance", 100.0))
		val account = BankAccount(
			playerId = playerId,
			playerName = playerName,
			balance = startingBalance,
			createdAt = LocalDateTime.now(),
			lastUpdated = LocalDateTime.now()
		)

		return if (databaseManager.saveAccount(account)) {
			accountCache[playerId] = account
			true
		} else false
	}

	/**
	 * Check if a player has an account
	 */
	fun hasAccount(playerId: UUID): Boolean {
		return getAccount(playerId) != null
	}

	/**
	 * Update a player's balance
	 */
	fun updateBalance(playerId: UUID, newBalance: BigDecimal): Boolean {
		val account = getAccount(playerId) ?: return false

		account.balance = newBalance
		account.lastUpdated = LocalDateTime.now()

		return if (databaseManager.updateBalance(playerId, newBalance)) {
			accountCache[playerId] = account
			true
		} else false
	}

	/**
	 * Get top richest players
	 */
	fun getTopRichestPlayers(limit: Int): List<BankAccount> {
		return databaseManager.getTopRichestPlayers(limit)
	}

	/**
	 * Get total money in circulation
	 */
	fun getTotalMoney(): BigDecimal {
		return databaseManager.getTotalMoney()
	}


	/**
	 * Get account by account number
	 */
	fun getAccountByNumber(accountNumber: String): BankAccount? {
		return databaseManager.getAccountByNumber(accountNumber)
	}

	/**
	 * Format currency for display
	 */
	fun formatCurrency(amount: BigDecimal): String {
		val config = plugin.config
		val symbol = config.getString("currency.symbol", "$")
		val suffix = config.getString("currency.suffix", "")

		return "$symbol${currencyFormat.format(amount)}$suffix"
	}

	/**
	 * Get all accounts with balance above minimum for interest
	 */
	fun getAccountsForInterest(minimumBalance: BigDecimal): List<BankAccount> {
		return databaseManager.getAccountsForInterest(minimumBalance)
	}

	/**
	 * Update last interest payout time
	 */
	fun updateLastInterestPayout(playerId: UUID, timestamp: LocalDateTime): Boolean {
		val account = getAccount(playerId) ?: return false
		account.lastInterestPayout = timestamp
		return databaseManager.updateLastInterestPayout(playerId, timestamp)
	}

	/**
	 * Get account by player name
	 */
	fun getAccountByName(playerName: String): BankAccount? {
		return databaseManager.getAccountByName(playerName)
	}

	/**
	 * Clear account cache (useful for reloading)
	 */
	fun clearCache() {
		accountCache.clear()
	}

	/**
	 * Save all cached accounts to database
	 */
	fun saveAllCachedAccounts() {
		accountCache.values.forEach { account ->
			databaseManager.saveAccount(account)
		}
	}
}