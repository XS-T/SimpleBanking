package net.crewco.banking.api


import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.BankAccount
import net.crewco.banking.models.BankTransaction
import net.crewco.banking.models.TransactionType
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.*

/**
 * Main API class for external plugins to interact with the Banking system
 */
class BankingAPI{

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
				account.balance+amount,account.balance,
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
				account.balance+amount,account.balance,
				reason
			)
			true
		} else false
	}

	/**
	 * Transfer money between players
	 */
	fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: BigDecimal, reason: String = "System Transfer"): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val fromAccount = accountManager.getAccount(from.uniqueId) ?: return false
		val toAccount = accountManager.getOrCreateAccount(to.uniqueId, to.name ?: "Unknown")

		if (fromAccount.balance < amount) return false

		return if (transactionManager.transferMoney(fromAccount.playerId, toAccount.playerId, amount, reason)){
			// Sender Record
			transactionManager.recordTransaction(
				from.uniqueId,
				to.uniqueId,amount,
				TransactionType.TRANSFER_SENT,
				fromAccount.balance+amount,
				fromAccount.balance)
			// Receiver Record
			transactionManager.recordTransaction(
				to.uniqueId,
				from.uniqueId,amount,
				TransactionType.TRANSFER_RECEIVED,
				toAccount.balance-amount,
				toAccount.balance)
		}else false
	}

	/**
	 * Set a player's balance
	 */
	fun setBalance(player: OfflinePlayer, amount: BigDecimal, reason: String = "Balance Set"): Boolean {
		if (amount < BigDecimal.ZERO) return false

		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name ?: "Unknown")
		val difference = amount - account.balance

		return if (accountManager.updateBalance(account.playerId, amount)) {
			transactionManager.recordTransaction(
				account.playerId,
				null,
				difference,
				TransactionType.ADMIN_SET,
				account.balance+amount,
				account.balance,
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
	 * Get account balance by account number
	 * @param accountNumber The account number (format: XXX-XXX-XXX-XXX)
	 * @return Balance of the account or null if account not found
	 */
	fun getAccountBalance(accountNumber: String): BigDecimal? {
		val account = getAccountByNumber(accountNumber)
		return account?.balance
	}

	/**
	 * Get account by account number
	 * @param accountNumber The account number (format: XXX-XXX-XXX-XXX)
	 * @return BankAccount or null if not found
	 */
	fun getAccountByNumber(accountNumber: String): BankAccount? {
		return accountManager.getAccountByNumber(accountNumber)
	}

	/**
	 * Check if account exists by account number
	 * @param accountNumber The account number
	 * @return true if account exists
	 */
	fun hasAccountByNumber(accountNumber: String): Boolean {
		return getAccountByNumber(accountNumber) != null
	}

	/**
	 * Transfer money using account numbers
	 * @param fromAccountNumber Source account number
	 * @param toAccountNumber Destination account number
	 * @param amount Amount to transfer
	 * @param reason Reason for transfer
	 * @return true if transfer was successful
	 */
	fun transferByAccountNumber(
		fromAccountNumber: String,
		toAccountNumber: String,
		amount: BigDecimal,
		reason: String = "Account Transfer"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val fromAccount = getAccountByNumber(fromAccountNumber) ?: return false
		val toAccount = getAccountByNumber(toAccountNumber) ?: return false

		val fromAccountUUID = UUID.fromString(fromAccountNumber)
		val toAccountUUID = UUID.fromString(toAccountNumber)

		if (fromAccount.balance < amount) return false

		return if (transactionManager.transferMoney(fromAccount.playerId, toAccount.playerId, amount, reason)){
			// Sender Record
			transactionManager.recordTransaction(
				fromAccountUUID,
				toAccountUUID,
				amount,
				TransactionType.TRANSFER_SENT,
				fromAccount.balance+amount,
				fromAccount.balance)
			// Receiver Record
			transactionManager.recordTransaction(
				toAccountUUID,
				fromAccountUUID,
				amount,
				TransactionType.TRANSFER_RECEIVED,
				toAccount.balance-amount,
				toAccount.balance)
		}else false
	}

	/**
	 * Format currency amount
	 */
	fun formatCurrency(amount: BigDecimal): String {
		return accountManager.formatCurrency(amount)
	}

	// ===== BUSINESS PLUGIN HOOKS =====

	/**
	 * Create a business account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param businessType The type of business (e.g., "Restaurant", "Shop")
	 * @param initialDeposit Initial money to deposit into the business account
	 * @return true if business account was created successfully
	 */
	fun createBusinessAccount(
		businessOwner: OfflinePlayer,
		businessName: String,
		initialDeposit: BigDecimal = BigDecimal.ZERO
	): Boolean {
		val businessId = generateBusinessAccountId(businessOwner.uniqueId, businessName)

		// Check if business account already exists
		if (accountManager.hasAccount(businessId)) {
			return false
		}

		// Create the business account
		if (!accountManager.createAccount(businessId, businessName)) {
			return false
		}

		// If there's an initial deposit, transfer from owner's personal account
		if (initialDeposit > BigDecimal.ZERO) {
			if (!hasBalance(businessOwner, initialDeposit)) {
				// Delete the account if owner can't afford initial deposit
				// Note: You might want to add a deleteAccount method to AccountManager
				return false
			}

			// Transfer from personal to business account
			val ownerAccount = accountManager.getAccount(businessOwner.uniqueId) ?: return false
			val businessAccount = accountManager.getAccount(businessId) ?: return false

			if (accountManager.updateBalance(businessOwner.uniqueId, ownerAccount.balance - initialDeposit) &&
				accountManager.updateBalance(businessId, businessAccount.balance + initialDeposit)) {

				// Record transactions
				transactionManager.recordTransaction(
					businessOwner.uniqueId,
					businessId,
					initialDeposit.negate(),
					TransactionType.TRANSFER_SENT,
					null,
					null,
					"Initial deposit to business: $businessName"
				)

				transactionManager.recordTransaction(
					businessId,
					businessOwner.uniqueId,
					initialDeposit,
					TransactionType.TRANSFER_RECEIVED,
					null,
					null,
					"Initial deposit from owner: ${businessOwner.name}"
				)
			}
		}

		return true
	}

	/**
	 * Get a business account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @return BankAccount for the business or null if not found
	 */
	fun getBusinessAccount(businessOwner: OfflinePlayer, businessName: String): BankAccount? {
		val businessId = generateBusinessAccountId(businessOwner.uniqueId, businessName)
		return accountManager.getAccount(businessId)
	}

	/**
	 * Get business account by business ID
	 * @param businessId The unique business account ID
	 * @return BankAccount for the business or null if not found
	 */
	fun getBusinessAccountById(businessId: UUID): BankAccount? {
		return accountManager.getAccount(businessId)
	}

	/**
	 * Get business account balance
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @return Balance of the business account
	 */
	fun getBusinessBalance(businessOwner: OfflinePlayer, businessName: String): BigDecimal {
		val businessAccount = getBusinessAccount(businessOwner, businessName)
		return businessAccount?.balance ?: BigDecimal.ZERO
	}


	/**
	 * Set Business Account Balance
	 * @param businessOwner
	 * @param businessName
	 * @return sets account balance of a business
	 */
	fun setBusinessCapital(
		businessOwner: OfflinePlayer,
		businessName: String,
		amount: BigDecimal,
		reason: String = "ADMIN SET Business account to $amount"
	): Boolean {
		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false

		return if (accountManager.updateBalance(businessAccount.playerId,amount)) {
			transactionManager.recordTransaction(
				businessAccount.playerId,
				null,
				amount,
				TransactionType.ADMIN_SET,
				businessAccount.balance,
				amount,
				"$reason ($businessName)"
			)
			true
		} else false
	}

	/**
	 * Check if a business account exists
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @return true if business account exists
	 */
	fun hasBusinessAccount(businessOwner: OfflinePlayer, businessName: String): Boolean {
		val businessId = generateBusinessAccountId(businessOwner.uniqueId, businessName)
		return accountManager.hasAccount(businessId)
	}

	/**
	 * Delete a business account (transfers remaining balance to owner)
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @return true if business account was deleted successfully
	 */
	fun deleteBusinessAccount(businessOwner: OfflinePlayer, businessName: String): Boolean {
		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false
		val ownerAccount = accountManager.getOrCreateAccount(businessOwner.uniqueId, businessOwner.name ?: "Unknown")

		// Transfer remaining balance to owner
		if (businessAccount.balance > BigDecimal.ZERO) {
			if (!transferFromBusiness(businessOwner, businessName, businessOwner, businessAccount.balance, "Business closure - final balance transfer")) {
				return false
			}
		}
		accountManager.getAccount(businessAccount.playerId)?.isActive =false

		// Note: You'll need to add a deleteAccount method to AccountManager
		// For now, we can set the account as inactive
		// accountManager.deleteAccount(businessAccount.playerId)

		return true
	}

	/**
	 * Transfer money from business account to personal account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param recipient The recipient of the transfer
	 * @param amount The amount to transfer
	 * @param reason The reason for the transfer
	 * @return true if transfer was successful
	 */
	fun transferFromBusiness(
		businessOwner: OfflinePlayer,
		businessName: String,
		recipient: OfflinePlayer,
		amount: BigDecimal,
		reason: String = "Business Transfer"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false
		val recipientAccount = accountManager.getOrCreateAccount(recipient.uniqueId, recipient.name ?: "Unknown")

		if (businessAccount.balance < amount) return false

		return transactionManager.transferMoney(
			businessAccount.playerId,
			recipientAccount.playerId,
			amount,
			"$reason (from $businessName)"
		)
	}

	/**
	 * Transfer money to business account from personal account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param sender The sender of the transfer
	 * @param amount The amount to transfer
	 * @param reason The reason for the transfer
	 * @return true if transfer was successful
	 */
	fun transferToBusiness(
		businessOwner: OfflinePlayer,
		businessName: String,
		sender: OfflinePlayer,
		amount: BigDecimal,
		reason: String = "Business Transfer"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false
		val senderAccount = accountManager.getAccount(sender.uniqueId) ?: return false

		if (senderAccount.balance < amount) return false

		return transactionManager.transferMoney(
			senderAccount.playerId,
			businessAccount.playerId,
			amount,
			"$reason (to $businessName)"
		)
	}

	/**
	 * Deposit money directly into business account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param amount The amount to deposit
	 * @param reason The reason for the deposit
	 * @return true if deposit was successful
	 */
	fun depositToBusiness(
		businessOwner: OfflinePlayer,
		businessName: String,
		amount: BigDecimal,
		reason: String = "Business Deposit"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false

		return if (accountManager.updateBalance(businessAccount.playerId, businessAccount.balance + amount)) {
			transactionManager.recordTransaction(
				businessAccount.playerId,
				null,
				amount,
				TransactionType.DEPOSIT,
				businessAccount.balance+amount,
				businessAccount.balance,
				"$reason ($businessName)"
			)
			true
		} else false
	}

	/**
	 * Withdraw money directly from business account
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param amount The amount to withdraw
	 * @param reason The reason for the withdrawal
	 * @return true if withdrawal was successful
	 */
	fun withdrawFromBusiness(
		businessOwner: OfflinePlayer,
		businessName: String,
		amount: BigDecimal,
		reason: String = "Business Withdrawal"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val businessAccount = getBusinessAccount(businessOwner, businessName) ?: return false

		if (businessAccount.balance < amount) return false

		return if (accountManager.updateBalance(businessAccount.playerId, businessAccount.balance - amount)) {
			transactionManager.recordTransaction(
				businessAccount.playerId,
				null,
				amount.negate(),
				TransactionType.WITHDRAWAL,
				businessAccount.balance+amount,
				businessAccount.balance,
				"$reason ($businessName)"
			)
			true
		} else false
	}

	/**
	 * Get all business accounts owned by a player
	 * @param businessOwner The owner of the businesses
	 * @return List of business accounts
	 */
	fun getPlayerBusinessAccounts(businessOwner: OfflinePlayer): List<BankAccount> {
		// This would require storing business account mappings
		// For now, return empty list - you'd need to implement business account tracking
		return emptyList()
	}

	/**
	 * Generate a unique business account ID
	 * @param ownerId The UUID of the business owner
	 * @param businessName The name of the business
	 * @return UUID for the business account
	 */
	private fun generateBusinessAccountId(ownerId: UUID, businessName: String): UUID {
		// Create a deterministic UUID based on owner ID and business name
		val input = "$ownerId-$businessName".lowercase().replace(" ", "_")
		return UUID.nameUUIDFromBytes(input.toByteArray())
	}

	/**
	 * Check if a business account has sufficient funds
	 * @param businessOwner The owner of the business
	 * @param businessName The name of the business
	 * @param amount The amount to check
	 * @return true if business account has sufficient funds
	 */
	fun businessHasBalance(businessOwner: OfflinePlayer, businessName: String, amount: BigDecimal): Boolean {
		return getBusinessBalance(businessOwner, businessName) >= amount
	}

	/**
	 * Process a business payment transaction
	 * @param customer The customer making the payment
	 * @param businessOwner The owner of the business receiving payment
	 * @param amount The amount to transfer
	 * @param businessName The name of the business
	 * @param itemDescription Description of what was purchased
	 * @return true if transaction was successful
	 */
	fun processBusinessPayment(
		customer: OfflinePlayer,
		businessOwner: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		itemDescription: String = ""
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val customerAccount = accountManager.getAccount(customer.uniqueId) ?: return false
		val ownerAccount = accountManager.getOrCreateAccount(businessOwner.uniqueId, businessOwner.name ?: "Unknown")

		if (customerAccount.balance < amount) return false

		val description = if (itemDescription.isNotEmpty()) {
			"Business payment to $businessName: $itemDescription"
		} else {
			"Business payment to $businessName"
		}

		return transactionManager.transferMoney(
			customerAccount.playerId,
			ownerAccount.playerId,
			amount,
			description
		)
	}

	/**
	 * Process a business employee salary payment
	 * @param businessOwner The business owner paying the salary
	 * @param employee The employee receiving the salary
	 * @param amount The salary amount
	 * @param businessName The name of the business
	 * @param salaryPeriod The salary period (e.g., "Weekly", "Monthly")
	 * @return true if payment was successful
	 */
	fun processEmployeeSalary(
		businessOwner: OfflinePlayer,
		employee: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		salaryPeriod: String = "Salary"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		return transfer(
			businessOwner,
			employee,
			amount,
			"$salaryPeriod salary from $businessName"
		)
	}

	/**
	 * Process business expenses (rent, utilities, etc.)
	 * @param businessOwner The business owner paying the expense
	 * @param amount The expense amount
	 * @param businessName The name of the business
	 * @param expenseType The type of expense (e.g., "Rent", "Utilities")
	 * @return true if payment was successful
	 */
	fun processBusinessExpense(
		businessOwner: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		expenseType: String
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		return withdraw(
			businessOwner,
			amount,
			"$expenseType expense for $businessName"
		)
	}

	/**
	 * Process business revenue deposit
	 * @param businessOwner The business owner receiving revenue
	 * @param amount The revenue amount
	 * @param businessName The name of the business
	 * @param source The source of revenue
	 * @return true if deposit was successful
	 */
	fun processBusinessRevenue(
		businessOwner: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		source: String = "Business Revenue"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		return deposit(
			businessOwner,
			amount,
			"$source from $businessName"
		)
	}

	/**
	 * Process business loan payment
	 * @param businessOwner The business owner making the loan payment
	 * @param amount The loan payment amount
	 * @param businessName The name of the business
	 * @param loanId Optional loan identifier
	 * @return true if payment was successful
	 */
	fun processBusinessLoanPayment(
		businessOwner: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		loanId: String? = null
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		val description = if (loanId != null) {
			"Loan payment for $businessName (ID: $loanId)"
		} else {
			"Loan payment for $businessName"
		}

		return withdraw(businessOwner, amount, description)
	}

	/**
	 * Process business investment
	 * @param investor The player making the investment
	 * @param businessOwner The business owner receiving the investment
	 * @param amount The investment amount
	 * @param businessName The name of the business
	 * @param investmentType The type of investment (e.g., "Startup", "Expansion")
	 * @return true if investment was successful
	 */
	fun processBusinessInvestment(
		investor: OfflinePlayer,
		businessOwner: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		investmentType: String = "Investment"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		return transfer(
			investor,
			businessOwner,
			amount,
			"$investmentType in $businessName"
		)
	}

	/**
	 * Process business refund
	 * @param businessOwner The business owner issuing the refund
	 * @param customer The customer receiving the refund
	 * @param amount The refund amount
	 * @param businessName The name of the business
	 * @param reason The reason for the refund
	 * @return true if refund was successful
	 */
	fun processBusinessRefund(
		businessOwner: OfflinePlayer,
		customer: OfflinePlayer,
		amount: BigDecimal,
		businessName: String,
		reason: String = "Refund"
	): Boolean {
		if (amount <= BigDecimal.ZERO) return false

		return transfer(
			businessOwner,
			customer,
			amount,
			"$reason from $businessName"
		)
	}

	/**
	 * Check if a business owner can afford a specific expense
	 * @param businessOwner The business owner
	 * @param amount The expense amount
	 * @return true if the owner can afford the expense
	 */
	fun canAffordBusinessExpense(businessOwner: OfflinePlayer, amount: BigDecimal): Boolean {
		return hasBalance(businessOwner, amount)
	}

	/**
	 * Get business transaction history for a player
	 * @param player The player to get business transactions for
	 * @param limit Maximum number of transactions to return
	 * @return List of business-related transactions
	 */
	fun getBusinessTransactions(player: OfflinePlayer, limit: Int = 50): List<BankTransaction> {
		return transactionManager.getTransactionHistory(player.uniqueId, limit)
			.filter { transaction ->
				transaction.description.contains("business", ignoreCase = true) ||
						transaction.type == TransactionType.BUSINESS_PAYMENT
			}
	}

	/**
	 * Calculate total business expenses for a player over a period
	 * @param businessOwner The business owner
	 * @param days Number of days to look back
	 * @return Total amount spent on business expenses
	 */
	fun getTotalBusinessExpenses(businessOwner: OfflinePlayer, days: Int = 30): BigDecimal {
		val transactions = transactionManager.getTransactionHistory(businessOwner.uniqueId, 1000)
		val cutoffDate = java.time.LocalDateTime.now().minusDays(days.toLong())

		return transactions
			.filter { it.timestamp.isAfter(cutoffDate) }
			.filter {
				it.amount < BigDecimal.ZERO &&
						(it.description.contains("expense", ignoreCase = true) ||
								it.description.contains("salary", ignoreCase = true) ||
								it.description.contains("rent", ignoreCase = true))
			}
			.sumOf { it.amount.abs() }
	}

	/**
	 * Calculate total business revenue for a player over a period
	 * @param businessOwner The business owner
	 * @param days Number of days to look back
	 * @return Total business revenue received
	 */
	fun getTotalBusinessRevenue(businessOwner: OfflinePlayer, days: Int = 30): BigDecimal {
		val transactions = transactionManager.getTransactionHistory(businessOwner.uniqueId, 1000)
		val cutoffDate = java.time.LocalDateTime.now().minusDays(days.toLong())

		return transactions
			.filter { it.timestamp.isAfter(cutoffDate) }
			.filter {
				it.amount > BigDecimal.ZERO &&
						(it.description.contains("business payment", ignoreCase = true) ||
								it.description.contains("revenue", ignoreCase = true))
			}
			.sumOf { it.amount }
	}
}