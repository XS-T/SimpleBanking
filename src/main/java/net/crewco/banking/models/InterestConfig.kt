package net.crewco.banking.models

import java.math.BigDecimal

/**
 * Configuration for interest rates
 */
data class InterestConfig(
	val enabled: Boolean = true,
	val dailyRate: BigDecimal = BigDecimal("0.01"), // 1% per day
	val minimumBalance: BigDecimal = BigDecimal("100.00"), // Min balance to earn interest
	val maximumPayout: BigDecimal = BigDecimal("1000.00"), // Max interest per payout
	val payoutInterval: Long = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
)