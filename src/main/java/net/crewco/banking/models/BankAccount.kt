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
	var lastInterestPayout: LocalDateTime = LocalDateTime.now()
)