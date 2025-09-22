package net.crewco.banking.economy

import net.crewco.banking.BankingPlugin.Companion.accountManager
import net.crewco.banking.BankingPlugin.Companion.transactionManager
import net.crewco.banking.models.TransactionType
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.text.DecimalFormat

class BankingEconomyProvider(
) : Economy {

	private val currencyFormat = DecimalFormat("#,##0.00")

	override fun isEnabled(): Boolean = true

	override fun getName(): String = "Banking Economy"

	override fun hasBankSupport(): Boolean = true

	override fun fractionalDigits(): Int = 2

	override fun format(amount: Double): String {
		return accountManager.formatCurrency(BigDecimal.valueOf(amount))
	}

	override fun currencyNamePlural(): String {
		return Bukkit.getPluginManager().getPlugin("BankingPlugin")!!.config.getString("currency.plural", "Dollars") ?: "Dollars"
	}

	override fun currencyNameSingular(): String {
		return Bukkit.getPluginManager().getPlugin("BankingPlugin")!!.config.getString("currency.singular", "Dollar") ?: "Dollar"
	}

	@Deprecated("Deprecated in Java")
	override fun hasAccount(playerName: String): Boolean {
		val player = Bukkit.getOfflinePlayer(playerName)
		return accountManager.hasAccount(player.uniqueId)
	}

	override fun hasAccount(player: OfflinePlayer): Boolean {
		return accountManager.hasAccount(player.uniqueId)
	}

	@Deprecated("Deprecated in Java", ReplaceWith("hasAccount(playerName)"))
	override fun hasAccount(playerName: String, worldName: String): Boolean {
		return hasAccount(playerName) // World-independent
	}

	override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean {
		return hasAccount(player) // World-independent
	}

	@Deprecated("Deprecated in Java")
	override fun getBalance(playerName: String): Double {
		val player = Bukkit.getOfflinePlayer(playerName)
		return accountManager.getAccount(player.uniqueId)?.balance?.toDouble() ?: 0.0
	}

	override fun getBalance(player: OfflinePlayer): Double {
		return accountManager.getAccount(player.uniqueId)?.balance?.toDouble() ?: 0.0
	}

	@Deprecated("Deprecated in Java", ReplaceWith("getBalance(playerName)"))
	override fun getBalance(playerName: String, world: String): Double {
		return getBalance(playerName) // World-independent
	}

	override fun getBalance(player: OfflinePlayer, world: String): Double {
		return getBalance(player) // World-independent
	}

	@Deprecated("Deprecated in Java", ReplaceWith("getBalance(playerName) >= amount"))
	override fun has(playerName: String, amount: Double): Boolean {
		return getBalance(playerName) >= amount
	}

	override fun has(player: OfflinePlayer, amount: Double): Boolean {
		return getBalance(player) >= amount
	}

	@Deprecated("Deprecated in Java", ReplaceWith("has(playerName, amount)"))
	override fun has(playerName: String, worldName: String, amount: Double): Boolean {
		return has(playerName, amount) // World-independent
	}

	override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean {
		return has(player, amount) // World-independent
	}

	@Deprecated("Deprecated in Java")
	override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse {
		val player = Bukkit.getOfflinePlayer(playerName)
		return withdrawPlayer(player, amount)
	}

	override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
		if (amount < 0) {
			return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amounts")
		}

		val account = accountManager.getAccount(player.uniqueId)
			?: return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "Account does not exist")

		val withdrawAmount = BigDecimal.valueOf(amount)
		if (account.balance < withdrawAmount) {
			return EconomyResponse(0.0, account.balance.toDouble(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds")
		}

		val newBalance = account.balance - withdrawAmount
		return if (accountManager.updateBalance(player.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				player.uniqueId,
				null,
				withdrawAmount.negate(),
				TransactionType.WITHDRAWAL,
				"Vault economy withdrawal",
				account.balance,
				newBalance
			)
			EconomyResponse(amount, newBalance.toDouble(), EconomyResponse.ResponseType.SUCCESS, null)
		} else {
			EconomyResponse(0.0, account.balance.toDouble(), EconomyResponse.ResponseType.FAILURE, "Transaction failed")
		}
	}

	@Deprecated("Deprecated in Java", ReplaceWith("withdrawPlayer(playerName, amount)"))
	override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
		return withdrawPlayer(playerName, amount) // World-independent
	}

	override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
		return withdrawPlayer(player, amount) // World-independent
	}

	@Deprecated("Deprecated in Java")
	override fun depositPlayer(playerName: String, amount: Double): EconomyResponse {
		val player = Bukkit.getOfflinePlayer(playerName)
		return depositPlayer(player, amount)
	}

	override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
		if (amount < 0) {
			return EconomyResponse(0.0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amounts")
		}

		val account = accountManager.getOrCreateAccount(player.uniqueId, player.name ?: "Unknown")
		val depositAmount = BigDecimal.valueOf(amount)
		val newBalance = account.balance + depositAmount

		return if (accountManager.updateBalance(player.uniqueId, newBalance)) {
			transactionManager.recordTransaction(
				player.uniqueId,
				null,
				depositAmount,
				TransactionType.DEPOSIT,
				"Vault economy deposit",
				account.balance,
				newBalance
			)
			EconomyResponse(amount, newBalance.toDouble(), EconomyResponse.ResponseType.SUCCESS, null)
		} else {
			EconomyResponse(0.0, account.balance.toDouble(), EconomyResponse.ResponseType.FAILURE, "Transaction failed")
		}
	}

	@Deprecated("Deprecated in Java", ReplaceWith("depositPlayer(playerName, amount)"))
	override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
		return depositPlayer(playerName, amount) // World-independent
	}

	override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
		return depositPlayer(player, amount) // World-independent
	}

	@Deprecated("Deprecated in Java")
	override fun createBank(name: String, player: String): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun createBank(name: String, player: OfflinePlayer): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun deleteBank(name: String): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun bankBalance(name: String): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun bankHas(name: String, amount: Double): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun bankWithdraw(name: String, amount: Double): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun bankDeposit(name: String, amount: Double): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	@Deprecated("Deprecated in Java")
	override fun isBankOwner(name: String, playerName: String): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	@Deprecated("Deprecated in Java")
	override fun isBankMember(name: String, playerName: String): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse {
		return EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Bank accounts not implemented in this version")
	}

	override fun getBanks(): List<String> {
		return emptyList() // No bank support in this implementation
	}

	@Deprecated("Deprecated in Java")
	override fun createPlayerAccount(playerName: String): Boolean {
		val player = Bukkit.getOfflinePlayer(playerName)
		return createPlayerAccount(player)
	}

	override fun createPlayerAccount(player: OfflinePlayer): Boolean {
		return accountManager.createAccount(player.uniqueId, player.name ?: "Unknown")
	}

	@Deprecated("Deprecated in Java", ReplaceWith("createPlayerAccount(playerName)"))
	override fun createPlayerAccount(playerName: String, worldName: String): Boolean {
		return createPlayerAccount(playerName) // World-independent
	}

	override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean {
		return createPlayerAccount(player) // World-independent
	}
}