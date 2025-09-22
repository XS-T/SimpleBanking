package net.crewco.banking.managers

import com.google.inject.Inject
import net.crewco.banking.BankingPlugin
import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.databaseManager
import net.crewco.banking.BankingPlugin.Companion.plugin
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.InterestConfig
import net.crewco.banking.models.TransactionType
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.logging.Level

class InterestManager {

	private var interestTask: BukkitTask? = null
	private lateinit var interestConfig: InterestConfig

	init {
		loadConfig()
	}

	fun loadConfig() {
		val config = plugin.config
		interestConfig = InterestConfig(
			enabled = config.getBoolean("interest.enabled", true),
			dailyRate = BigDecimal(config.getDouble("interest.daily-rate", 0.01)),
			minimumBalance = BigDecimal(config.getDouble("interest.minimum-balance", 100.0)),
			maximumPayout = BigDecimal(config.getDouble("interest.maximum-payout", 1000.0)),
			payoutInterval = config.getLong("interest.payout-interval-hours", 24) * 60 * 60 * 1000L
		)
	}

	fun startInterestTask() {
		if (!interestConfig.enabled) {
			plugin.logger.info("Interest system is disabled in config")
			return
		}

		stopInterestTask() // Stop any existing task

		// Convert milliseconds to ticks (20 ticks = 1 second)
		val intervalTicks = interestConfig.payoutInterval / 50L // 50ms per tick

		val interestRunnable = object : Runnable {
			override fun run() {
				try {
					processInterestPayouts()
				} catch (e: Exception) {
					plugin.logger.log(Level.SEVERE, "Error processing interest payouts", e)
				}
			}
		}

		interestTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, interestRunnable, intervalTicks, intervalTicks)

		plugin.logger.info("Interest task started with ${interestConfig.payoutInterval / (1000 * 60 * 60)} hour intervals")
	}

	fun stopInterestTask() {
		interestTask?.cancel()
		interestTask = null
	}

	private fun processInterestPayouts() {
		val eligibleAccounts = accountManager.getAccountsForInterest(interestConfig.minimumBalance)
		var totalPayouts = BigDecimal.ZERO
		var accountsProcessed = 0

		plugin.logger.info("Processing interest for ${eligibleAccounts.size} eligible accounts")

		eligibleAccounts.forEach { account ->
			val hoursSinceLastPayout = ChronoUnit.HOURS.between(account.lastInterestPayout, LocalDateTime.now())

			// Only pay interest if enough time has passed
			if (hoursSinceLastPayout >= (interestConfig.payoutInterval / (1000 * 60 * 60))) {
				val interestAmount = calculateInterest(account.balance, account.interestRate, hoursSinceLastPayout)

				if (interestAmount > BigDecimal.ZERO) {
					val cappedInterest = interestAmount.min(interestConfig.maximumPayout)
					val newBalance = account.balance + cappedInterest

					// Update balance synchronously on main thread
					val mainThreadTask = object : Runnable {
						override fun run() {
							if (accountManager.updateBalance(account.playerId, newBalance)) {
								// Record transaction
								transactionManager.recordTransaction(
									account.playerId,
									null,
									cappedInterest,
									TransactionType.INTEREST,
									"Daily interest payout (${account.interestRate.multiply(BigDecimal("100"))}%)",
									account.balance,
									newBalance
								)

								// Update last interest payout
								accountManager.updateLastInterestPayout(account.playerId, LocalDateTime.now())

								// Notify player if online
								val player = Bukkit.getPlayer(account.playerId)
								if (player != null && player.isOnline) {
									val formattedAmount = accountManager.formatCurrency(cappedInterest)
									player.sendMessage("${ChatColor.GREEN}You earned $formattedAmount in interest!")
								}
							}
						}
					}

					Bukkit.getScheduler().runTask(plugin, mainThreadTask)

					totalPayouts += cappedInterest
					accountsProcessed++
				}
			}
		}

		if (accountsProcessed > 0) {
			plugin.logger.info("Processed interest for $accountsProcessed accounts. Total payouts: ${accountManager.formatCurrency(totalPayouts)}")
		}
	}

	private fun calculateInterest(balance: BigDecimal, interestRate: BigDecimal, hoursElapsed: Long): BigDecimal {
		// Calculate compound interest based on hours elapsed
		val dailyInterest = balance * interestRate
		val hourlyRate = dailyInterest / BigDecimal("24")
		return hourlyRate * BigDecimal(hoursElapsed).setScale(2, RoundingMode.HALF_UP)
	}

	fun manualInterestPayout(playerId: java.util.UUID): Boolean {
		val account = accountManager.getAccount(playerId) ?: return false

		if (account.balance < interestConfig.minimumBalance) {
			return false
		}

		val hoursSinceLastPayout = ChronoUnit.HOURS.between(account.lastInterestPayout, LocalDateTime.now())
		val interestAmount = calculateInterest(account.balance, account.interestRate, hoursSinceLastPayout)

		if (interestAmount <= BigDecimal.ZERO) {
			return false
		}

		val cappedInterest = interestAmount.min(interestConfig.maximumPayout)
		val newBalance = account.balance + cappedInterest

		return if (accountManager.updateBalance(playerId, newBalance)) {
			transactionManager.recordTransaction(
				playerId,
				null,
				cappedInterest,
				TransactionType.INTEREST,
				"Manual interest payout",
				account.balance,
				newBalance
			)

			accountManager.updateLastInterestPayout(playerId, LocalDateTime.now())
			true
		} else false
	}

	fun getNextInterestPayout(playerId: java.util.UUID): LocalDateTime? {
		val account = accountManager.getAccount(playerId) ?: return null
		val intervalHours = interestConfig.payoutInterval / (1000 * 60 * 60)
		return account.lastInterestPayout.plusHours(intervalHours)
	}

	fun getInterestConfig(): InterestConfig = interestConfig

	fun setInterestRate(playerId: java.util.UUID, newRate: BigDecimal): Boolean {
		val account = accountManager.getAccount(playerId) ?: return false

		return try {
			databaseManager.getConnection().use { connection ->
				val sql = "UPDATE bank_accounts SET interest_rate = ? WHERE player_id = ?"
				connection.prepareStatement(sql).use { stmt ->
					stmt.setBigDecimal(1, newRate)
					stmt.setString(2, playerId.toString())
					stmt.executeUpdate() > 0
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to update interest rate", e)
			false
		}
	}

	fun calculatePotentialInterest(playerId: java.util.UUID): BigDecimal {
		val account = accountManager.getAccount(playerId) ?: return BigDecimal.ZERO

		if (account.balance < interestConfig.minimumBalance) {
			return BigDecimal.ZERO
		}

		val hoursSinceLastPayout = ChronoUnit.HOURS.between(account.lastInterestPayout, LocalDateTime.now())
		val interestAmount = calculateInterest(account.balance, account.interestRate, hoursSinceLastPayout)

		return interestAmount.min(interestConfig.maximumPayout)
	}

	fun getInterestEligiblePlayers(): List<java.util.UUID> {
		return accountManager.getAccountsForInterest(interestConfig.minimumBalance)
			.map { it.playerId }
	}

	fun getTotalInterestPaid(): BigDecimal {
		return try {
			databaseManager.getConnection().use { connection ->
				val sql = "SELECT SUM(amount) as total FROM bank_transactions WHERE transaction_type = 'INTEREST'"
				connection.prepareStatement(sql).use { stmt ->
					stmt.executeQuery().use { rs ->
						if (rs.next()) rs.getBigDecimal("total") ?: BigDecimal.ZERO
						else BigDecimal.ZERO
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get total interest paid", e)
			BigDecimal.ZERO
		}
	}

	fun getInterestStatistics(): Map<String, Any> {
		val eligibleAccounts = accountManager.getAccountsForInterest(interestConfig.minimumBalance)
		val totalEligibleBalance = eligibleAccounts.sumOf { it.balance }
		val averageBalance = if (eligibleAccounts.isNotEmpty()) {
			totalEligibleBalance / BigDecimal(eligibleAccounts.size)
		} else BigDecimal.ZERO

		return mapOf(
			"eligibleAccounts" to eligibleAccounts.size,
			"totalEligibleBalance" to totalEligibleBalance,
			"averageEligibleBalance" to averageBalance,
			"totalInterestPaid" to getTotalInterestPaid(),
			"interestRate" to interestConfig.dailyRate,
			"minimumBalance" to interestConfig.minimumBalance,
			"maximumPayout" to interestConfig.maximumPayout,
			"payoutIntervalHours" to (interestConfig.payoutInterval / (1000 * 60 * 60)),
			"nextPayoutTime" to getNextGlobalInterestPayout()
		)
	}

	fun getNextGlobalInterestPayout(): LocalDateTime {
		// Calculate when the next interest payout cycle will run
		val intervalHours = interestConfig.payoutInterval / (1000 * 60 * 60)
		return LocalDateTime.now().plusHours(intervalHours)
	}

	fun forceInterestPayout(): Map<String, Any> {
		val eligibleAccounts = accountManager.getAccountsForInterest(interestConfig.minimumBalance)
		var totalPayouts = BigDecimal.ZERO
		var accountsProcessed = 0
		var errors = 0

		plugin.logger.info("Forcing interest payout for ${eligibleAccounts.size} eligible accounts")

		eligibleAccounts.forEach { account ->
			try {
				val hoursSinceLastPayout = ChronoUnit.HOURS.between(account.lastInterestPayout, LocalDateTime.now())
				val interestAmount = calculateInterest(account.balance, account.interestRate, hoursSinceLastPayout)

				if (interestAmount > BigDecimal.ZERO) {
					val cappedInterest = interestAmount.min(interestConfig.maximumPayout)
					val newBalance = account.balance + cappedInterest

					if (accountManager.updateBalance(account.playerId, newBalance)) {
						// Record transaction
						transactionManager.recordTransaction(
							account.playerId,
							null,
							cappedInterest,
							TransactionType.INTEREST,
							"Manual interest payout (${account.interestRate.multiply(BigDecimal("100"))}%)",
							account.balance,
							newBalance
						)

						// Update last interest payout
						accountManager.updateLastInterestPayout(account.playerId, LocalDateTime.now())

						// Notify player if online
						val player = Bukkit.getPlayer(account.playerId)
						if (player != null && player.isOnline) {
							val formattedAmount = accountManager.formatCurrency(cappedInterest)
							player.sendMessage("${ChatColor.GREEN}You earned $formattedAmount in interest!")
						}

						totalPayouts += cappedInterest
						accountsProcessed++
					} else {
						errors++
					}
				}
			} catch (e: Exception) {
				plugin.logger.log(Level.WARNING, "Error processing interest for player ${account.playerId}", e)
				errors++
			}
		}

		return mapOf(
			"totalEligibleAccounts" to eligibleAccounts.size,
			"accountsProcessed" to accountsProcessed,
			"totalPayouts" to totalPayouts,
			"errors" to errors,
			"success" to (errors == 0)
		)
	}

	fun enableInterest() {
		interestConfig = interestConfig.copy(enabled = true)
		plugin.config.set("interest.enabled", true)
		plugin.saveConfig()

		if (interestTask == null) {
			startInterestTask()
		}

		plugin.logger.info("Interest system enabled")
	}

	fun disableInterest() {
		interestConfig = interestConfig.copy(enabled = false)
		plugin.config.set("interest.enabled", false)
		plugin.saveConfig()

		stopInterestTask()
		plugin.logger.info("Interest system disabled")
	}

	fun updateInterestConfig(newConfig: InterestConfig) {
		val oldConfig = interestConfig
		interestConfig = newConfig

		// Update config file
		plugin.config.set("interest.enabled", newConfig.enabled)
		plugin.config.set("interest.daily-rate", newConfig.dailyRate.toDouble())
		plugin.config.set("interest.minimum-balance", newConfig.minimumBalance.toDouble())
		plugin.config.set("interest.maximum-payout", newConfig.maximumPayout.toDouble())
		plugin.config.set("interest.payout-interval-hours", newConfig.payoutInterval / (1000 * 60 * 60))
		plugin.saveConfig()

		// Restart task if interval changed
		if (oldConfig.payoutInterval != newConfig.payoutInterval) {
			stopInterestTask()
			if (newConfig.enabled) {
				startInterestTask()
			}
		}

		plugin.logger.info("Interest configuration updated")
	}

	fun getPlayerInterestInfo(playerId: java.util.UUID): Map<String, Any>? {
		val account = accountManager.getAccount(playerId) ?: return null

		val hoursSinceLastPayout = ChronoUnit.HOURS.between(account.lastInterestPayout, LocalDateTime.now())
		val potentialInterest = calculatePotentialInterest(playerId)
		val nextPayout = getNextInterestPayout(playerId)
		val isEligible = account.balance >= interestConfig.minimumBalance

		return mapOf<String, Any>(
			"currentBalance" to account.balance,
			"interestRate" to account.interestRate,
			"isEligible" to isEligible,
			"lastInterestPayout" to account.lastInterestPayout,
			"hoursSinceLastPayout" to hoursSinceLastPayout,
			"potentialInterest" to potentialInterest,
			"nextPayout" to (nextPayout ?: "N/A"),
			"minimumBalanceRequired" to interestConfig.minimumBalance,
			"maximumDailyPayout" to interestConfig.maximumPayout
		)
	}
}