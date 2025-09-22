package net.crewco.banking.models

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * Represents a banking transaction
 */
data class BankTransaction(
	val id: Long?,
	val playerId: UUID,
	val targetPlayerId: UUID?, // For transfers
	val amount: BigDecimal,
	val type: TransactionType,
	val description: String,
	val timestamp: LocalDateTime,
	val balanceBefore: BigDecimal,
	val balanceAfter: BigDecimal
)