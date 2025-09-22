package net.crewco.banking.models

/**
 * Types of banking transactions
 */
enum class TransactionType(val displayName: String) {
	DEPOSIT("Deposit"),
	WITHDRAWAL("Withdrawal"),
	TRANSFER_SENT("Transfer Sent"),
	TRANSFER_RECEIVED("Transfer Received"),
	INTEREST("Interest"),
	PURCHASE("Purchase"),
	SALE("Sale"),
	ADMIN_SET("Admin Set"),
	ADMIN_GIVE("Admin Give"),
	ADMIN_TAKE("Admin Take"),
	BUSINESS_PAYMENT("Business Payment"),
	STOCK_PURCHASE("Stock Purchase"),
	STOCK_SALE("Stock Sale"),
	DIVIDEND("Dividend")
}