package net.crewco.banking

import net.crewco.banking.api.BankingAPI
import net.crewco.banking.commands.TransActionHistoryCommand
import net.crewco.banking.commands.balanceCommand
import net.crewco.banking.commands.balanceTopCommand
import net.crewco.banking.commands.bankAdminCommand
import net.crewco.banking.commands.depositCommand
import net.crewco.banking.commands.payCommand
import net.crewco.banking.commands.withdrawCommand
import net.crewco.banking.database.DatabaseManager
import net.crewco.banking.economy.BankingEconomyProvider
import net.crewco.banking.listeners.AccountHandlerListener
import net.crewco.banking.managers.AccountManager
import net.crewco.banking.managers.InterestManager
import net.crewco.banking.managers.TransactionManager
import net.crewco.common.CrewCoPlugin
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicePriority

class BankingPlugin : CrewCoPlugin() {
	companion object {
		lateinit var plugin: BankingPlugin
			private set

		lateinit var api: BankingAPI
		var economyProvider: Economy? = null
		lateinit var databaseManager: DatabaseManager
		lateinit var accountManager: AccountManager
		lateinit var transactionManager: TransactionManager
		lateinit var interestManager: InterestManager
	}

	override suspend fun onEnableAsync() {
		super.onEnableAsync()

		//Inits
		plugin = this

		//Config
		plugin.config.options().copyDefaults()
		plugin.saveDefaultConfig()

		// Initialize database
		databaseManager = DatabaseManager(this)
		if (!databaseManager.initialize()) {
			logger.severe("Failed to initialize database! Disabling plugin...")
			server.pluginManager.disablePlugin(this)
			return
		}

		// Initialize managers
		accountManager = AccountManager(this)
		transactionManager = TransactionManager()
		interestManager = InterestManager()

		// Initialize API
		api = BankingAPI()

		// Register Banking API as a service for other plugins
		try {
			server.servicesManager.register(
				BankingAPI::class.java,
				api,
				this,
				ServicePriority.Normal
			)
			logger.info("Banking API registered as service successfully!")
		} catch (e: Exception) {
			logger.severe("Failed to register Banking API service: ${e.message}")
			e.printStackTrace()
		}
		// Setup Vault economy provider
		if (!setupVaultEconomy()) {
			logger.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().name));
			server.pluginManager.disablePlugin(this);
			return;
		}

		// Register commands
		registerCommands(balanceCommand::class,balanceTopCommand::class,bankAdminCommand::class,depositCommand::class,payCommand::class,TransActionHistoryCommand::class,withdrawCommand::class)

		// Register listeners
		registerListeners(AccountHandlerListener::class)

		// Start interest task
		interestManager.startInterestTask()


		logger.info("Banking Plugin has been enabled!")
		logger.info("Banking API registered - other plugins can now depend on this")


	}

	override suspend fun onDisableAsync() {
		super.onDisableAsync()

		databaseManager.close()
	}

	private fun setupVaultEconomy(): Boolean {
		if (server.pluginManager.getPlugin("Vault") == null) {
			logger.warning("Vault not found! Economy integration disabled.")
			return false
		}

		try {
			// CREATE and REGISTER your economy provider (don't get existing one)
			economyProvider = BankingEconomyProvider()
			server.servicesManager.register(Economy::class.java, economyProvider as BankingEconomyProvider, this, ServicePriority.Highest)
			logger.info("Banking Plugin registered as Vault economy provider!")
			return true
		} catch (e: Exception) {
			logger.severe("Failed to register Vault economy provider: ${e.message}")
			return false
		}
	}

	// Optional: Function to get OTHER economy providers (if you need to check existing ones)
	private fun getExistingEconomy(): Economy? {
		if (server.pluginManager.getPlugin("Vault") == null) {
			return null
		}
		val rsp: RegisteredServiceProvider<Economy>? = server.servicesManager.getRegistration(Economy::class.java)
		return rsp?.provider
	}

}