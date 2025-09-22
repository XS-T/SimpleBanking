package net.crewco.banking.managers

import net.crewco.banking.BankingPlugin.Companion.databaseManager
import net.crewco.banking.database.DatabaseManager
import net.crewco.banking.models.BankTransaction
import net.crewco.banking.models.TransactionType
import org.bukkit.Bukkit
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class TransactionManager {

	/**
	 * Record a transaction
	 */
	fun recordTransaction(
		playerId: UUID,
		targetPlayerId: UUID?,
		amount: BigDecimal,
		type: TransactionType,
		description: String,
		balanceBefore: BigDecimal? = null,
		balanceAfter: BigDecimal? = null
	): Boolean {
		val account = databaseManager.loadAccount(playerId) ?: return false

		val transaction = BankTransaction(
			id = null,
			playerId = playerId,
			targetPlayerId = targetPlayerId,
			amount = amount,
			type = type,
			description = description,
			timestamp = LocalDateTime.now(),
			balanceBefore = balanceBefore ?: account.balance,
			balanceAfter = balanceAfter ?: account.balance
		)

		return databaseManager.saveTransaction(transaction)
	}

	/**
	 * Transfer money between two players
	 */
	fun transferMoney(fromPlayerId: UUID, toPlayerId: UUID, amount: BigDecimal, reason: String): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val fromAccount = databaseManager.loadAccount(fromPlayerId) ?: return false
		val toAccount = databaseManager.loadAccount(toPlayerId) ?: return false

		if (fromAccount.balance < amount) return false

		val newFromBalance = fromAccount.balance - amount
		val newToBalance = toAccount.balance + amount

		return databaseManager.executeInTransaction { connection ->
			// Update sender's balance
			databaseManager.updateBalance(fromPlayerId, newFromBalance, connection)

			// Update receiver's balance
			databaseManager.updateBalance(toPlayerId, newToBalance, connection)

			// Record sender's transaction
			val senderTransaction = BankTransaction(
				id = null,
				playerId = fromPlayerId,
				targetPlayerId = toPlayerId,
				amount = amount.negate(),
				type = TransactionType.TRANSFER_SENT,
				description = "Transfer to ${toAccount.playerName}: $reason",
				timestamp = LocalDateTime.now(),
				balanceBefore = fromAccount.balance,
				balanceAfter = newFromBalance
			)
			databaseManager.saveTransaction(senderTransaction, connection)

			// Record receiver's transaction
			val receiverTransaction = BankTransaction(
				id = null,
				playerId = toPlayerId,
				targetPlayerId = fromPlayerId,
				amount = amount,
				type = TransactionType.TRANSFER_RECEIVED,
				description = "Transfer from ${fromAccount.playerName}: $reason",
				timestamp = LocalDateTime.now(),
				balanceBefore = toAccount.balance,
				balanceAfter = newToBalance
			)
			databaseManager.saveTransaction(receiverTransaction, connection)

			true
		}
	}

	/**
	 * Get transaction history for a player
	 */
	fun getTransactionHistory(playerId: UUID, limit: Int = 50, offset: Int = 0): List<BankTransaction> {
		return databaseManager.getTransactionHistory(playerId, limit, offset)
	}

	/**
	 * Get all transactions between two dates
	 */
	fun getTransactionsBetweenDates(
		playerId: UUID,
		startDate: LocalDateTime,
		endDate: LocalDateTime
	): List<BankTransaction> {
		return databaseManager.getTransactionsBetweenDates(playerId, startDate, endDate)
	}

	/**
	 * Get transactions by type
	 */
	fun getTransactionsByType(playerId: UUID, type: TransactionType, limit: Int = 50): List<BankTransaction> {
		return databaseManager.getTransactionsByType(playerId, type, limit)
	}

	/**
	 * Get total transaction volume for a player
	 */
	fun getTotalTransactionVolume(playerId: UUID): BigDecimal {
		return databaseManager.getTotalTransactionVolume(playerId)
	}

	/**
	 * Process business payment (for Business plugin integration)
	 */
	fun processBusinessPayment(
		payerId: UUID,
		businessOwnerId: UUID,
		amount: BigDecimal,
		businessName: String,
		description: String
	): Boolean {
		return transferMoney(payerId, businessOwnerId, amount, "Business payment to $businessName: $description")
	}

	/**
	 * Process stock transaction (for StockMarket plugin integration)
	 */
	fun processStockTransaction(
		playerId: UUID,
		amount: BigDecimal,
		stockSymbol: String,
		shares: Int,
		isBuying: Boolean
	): Boolean {
		val account = databaseManager.loadAccount(playerId) ?: return false

		return if (isBuying) {
			// Stock purchase
			if (account.balance < amount) return false

			val newBalance = account.balance - amount
			if (databaseManager.updateBalance(playerId, newBalance)) {
				recordTransaction(
					playerId,
					null,
					amount.negate(),
					TransactionType.STOCK_PURCHASE,
					"Purchased $shares shares of $stockSymbol",
					account.balance,
					newBalance
				)
			} else false
		} else {
			// Stock sale
			val newBalance = account.balance + amount
			if (databaseManager.updateBalance(playerId, newBalance)) {
				recordTransaction(
					playerId,
					null,
					amount,
					TransactionType.STOCK_SALE,
					"Sold $shares shares of $stockSymbol",
					account.balance,
					newBalance
				)
			} else false
		}
	}

	/**
	 * Process dividend payment
	 */
	fun processDividend(playerId: UUID, amount: BigDecimal, stockSymbol: String): Boolean {
		val account = databaseManager.loadAccount(playerId) ?: return false
		val newBalance = account.balance + amount

		return if (databaseManager.updateBalance(playerId, newBalance)) {
			recordTransaction(
				playerId,
				null,
				amount,
				TransactionType.DIVIDEND,
				"Dividend payment from $stockSymbol",
				account.balance,
				newBalance
			)
		} else false
	}

	/**
	 * Get server-wide transaction statistics
	 */
	fun getServerTransactionStats(): Map<String, Any> {
		return mapOf(
			"totalTransactions" to databaseManager.getTotalTransactionCount(),
			"totalVolume" to databaseManager.getTotalTransactionVolume(),
			"averageTransactionSize" to databaseManager.getAverageTransactionSize(),
			"transactionsByType" to databaseManager.getTransactionCountsByType()
		)
	}
}