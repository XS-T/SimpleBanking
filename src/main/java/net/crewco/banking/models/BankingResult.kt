package net.crewco.banking.models

import java.math.BigDecimal

/**
 * Result of a banking operation
 */
data class BankingResult(
	val success: Boolean,
	val message: String,
	val newBalance: BigDecimal? = null
)