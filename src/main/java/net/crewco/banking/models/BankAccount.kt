package net.crewco.banking.models

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*


/**
 * Represents a player's bank account
 */
data class BankAccount(
	val playerId: UUID,
	val playerName: String,
	var balance: BigDecimal,
	val createdAt: LocalDateTime,
	var lastUpdated: LocalDateTime,
	var isActive: Boolean = true,
	var interestRate: BigDecimal = BigDecimal("0.01"), // 1% daily interest
	var lastInterestPayout: LocalDateTime = LocalDateTime.now(),
	val accountNumber: String = generateAccountNumber(playerId) // Add account number
) {
	companion object {
		/**
		 * Generate a formatted account number from UUID
		 */
		fun generateAccountNumber(playerId: UUID): String {
			// Take first 12 characters of UUID (without dashes) and format as account number
			val uuidString = playerId.toString().replace("-", "").uppercase()
			val accountBase = uuidString.substring(0, 12)
			// Format as XXX-XXX-XXX-XXX
			return "${accountBase.substring(0, 3)}-${accountBase.substring(3, 6)}-${accountBase.substring(6, 9)}-${accountBase.substring(9, 12)}"
		}
	}
}