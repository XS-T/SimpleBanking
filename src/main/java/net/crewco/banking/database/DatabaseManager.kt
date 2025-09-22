package net.crewco.banking.database


import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.crewco.banking.models.BankAccount
import net.crewco.banking.models.BankTransaction
import net.crewco.banking.models.TransactionType
import org.bukkit.plugin.java.JavaPlugin
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*
import java.util.logging.Level

class DatabaseManager(private val plugin: JavaPlugin) {

	private lateinit var dataSource: HikariDataSource
	private val config = plugin.config

	fun initialize(): Boolean {
		return try {
			setupDatabase()
			createTables()
			true
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to initialize database", e)
			false
		}
	}

	private fun setupDatabase() {
		val dbConfig = HikariConfig()

		when (config.getString("database.type", "sqlite")?.lowercase()) {
			"mysql" -> {
				dbConfig.jdbcUrl = "jdbc:mysql://${config.getString("database.host", "localhost")}:${
					config.getInt(
						"database.port",
						3306
					)
				}/${config.getString("database.name", "banking")}"
				dbConfig.username = config.getString("database.username", "root")
				dbConfig.password = config.getString("database.password", "")
				dbConfig.driverClassName = "com.mysql.cj.jdbc.Driver"
			}

			"postgresql" -> {
				dbConfig.jdbcUrl = "jdbc:postgresql://${
					config.getString(
						"database.host",
						"localhost"
					)
				}:${config.getInt("database.port", 5432)}/${config.getString("database.name", "banking")}"
				dbConfig.username = config.getString("database.username", "postgres")
				dbConfig.password = config.getString("database.password", "")
				dbConfig.driverClassName = "org.postgresql.Driver"
			}

			else -> {
				// Default to SQLite
				dbConfig.jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/banking.db"
				dbConfig.driverClassName = "org.sqlite.JDBC"
			}
		}

		dbConfig.maximumPoolSize = config.getInt("database.pool-size", 10)
		dbConfig.connectionTimeout = config.getLong("database.connection-timeout", 30000)
		dbConfig.leakDetectionThreshold = config.getLong("database.leak-detection-threshold", 60000)

		dataSource = HikariDataSource(dbConfig)
	}

	private fun createTables() {
		getConnection().use { connection ->
			val isMySQL = config.getString("database.type", "sqlite")?.lowercase() == "mysql"
			val isPostgreSQL = config.getString("database.type", "sqlite")?.lowercase() == "postgresql"

			// Create accounts table
			val accountsTableSQL = if (isMySQL || isPostgreSQL) {
				"""
                CREATE TABLE IF NOT EXISTS bank_accounts (
                    player_id VARCHAR(36) PRIMARY KEY,
                    player_name VARCHAR(16) NOT NULL,
                    balance DECIMAL(15,2) DEFAULT 0.00,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT true,
                    interest_rate DECIMAL(5,4) DEFAULT 0.0100,
                    last_interest_payout TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent()
			} else {
				// SQLite version
				"""
                CREATE TABLE IF NOT EXISTS bank_accounts (
                    player_id TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    balance REAL DEFAULT 0.00,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
                    is_active INTEGER DEFAULT 1,
                    interest_rate REAL DEFAULT 0.0100,
                    last_interest_payout DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """.trimIndent()
			}

			connection.prepareStatement(accountsTableSQL).execute()

			// Create transactions table
			val transactionsTableSQL = if (isMySQL) {
				"""
                CREATE TABLE IF NOT EXISTS bank_transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    target_player_id VARCHAR(36),
                    amount DECIMAL(15,2) NOT NULL,
                    transaction_type VARCHAR(20) NOT NULL,
                    description TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    balance_before DECIMAL(15,2),
                    balance_after DECIMAL(15,2)
                )
                """.trimIndent()
			} else if (isPostgreSQL) {
				"""
                CREATE TABLE IF NOT EXISTS bank_transactions (
                    id BIGSERIAL PRIMARY KEY,
                    player_id VARCHAR(36) NOT NULL,
                    target_player_id VARCHAR(36),
                    amount DECIMAL(15,2) NOT NULL,
                    transaction_type VARCHAR(20) NOT NULL,
                    description TEXT,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    balance_before DECIMAL(15,2),
                    balance_after DECIMAL(15,2)
                )
                """.trimIndent()
			} else {
				// SQLite version
				"""
                CREATE TABLE IF NOT EXISTS bank_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id TEXT NOT NULL,
                    target_player_id TEXT,
                    amount REAL NOT NULL,
                    transaction_type TEXT NOT NULL,
                    description TEXT,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    balance_before REAL,
                    balance_after REAL
                )
                """.trimIndent()
			}

			connection.prepareStatement(transactionsTableSQL).execute()

			// Create indexes separately for SQLite compatibility
			createIndexes(connection)

			plugin.logger.info("Database tables created successfully!")
		}
	}

	private fun createIndexes(connection: Connection) {
		val indexes = listOf(
			"CREATE INDEX IF NOT EXISTS idx_player_transactions ON bank_transactions(player_id)",
			"CREATE INDEX IF NOT EXISTS idx_transaction_type ON bank_transactions(transaction_type)",
			"CREATE INDEX IF NOT EXISTS idx_timestamp ON bank_transactions(timestamp)"
		)

		indexes.forEach { indexSQL ->
			try {
				connection.prepareStatement(indexSQL).execute()
			} catch (e: Exception) {
				plugin.logger.warning("Failed to create index: $indexSQL - ${e.message}")
			}
		}
	}

	fun getConnection(): Connection = dataSource.connection

	fun loadAccount(playerId: UUID): BankAccount? {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT * FROM bank_accounts WHERE player_id = ?"
				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, playerId.toString())
					stmt.executeQuery().use { rs ->
						if (rs.next()) {
							mapResultSetToAccount(rs)
						} else null
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to load account", e)
			null
		}
	}

	fun updateBalance(playerId: UUID, newBalance: BigDecimal, connection: Connection? = null): Boolean {
		return try {
			val conn = connection ?: getConnection()
			val shouldClose = connection == null

			try {
				val sql = "UPDATE bank_accounts SET balance = ?, last_updated = ? WHERE player_id = ?"
				conn.prepareStatement(sql).use { stmt ->
					stmt.setBigDecimal(1, newBalance)
					stmt.setObject(2, LocalDateTime.now())
					stmt.setString(3, playerId.toString())
					stmt.executeUpdate() > 0
				}
			} finally {
				if (shouldClose) conn.close()
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to update balance", e)
			false
		}
	}

	fun saveTransaction(transaction: BankTransaction, connection: Connection? = null): Boolean {
		return try {
			val conn = connection ?: getConnection()
			val shouldClose = connection == null

			try {
				val sql = """
                    INSERT INTO bank_transactions 
                    (player_id, target_player_id, amount, transaction_type, description, timestamp, balance_before, balance_after)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

				conn.prepareStatement(sql).use { stmt ->
					stmt.setString(1, transaction.playerId.toString())
					stmt.setString(2, transaction.targetPlayerId?.toString())
					stmt.setBigDecimal(3, transaction.amount)
					stmt.setString(4, transaction.type.name)
					stmt.setString(5, transaction.description)
					stmt.setObject(6, transaction.timestamp)
					stmt.setBigDecimal(7, transaction.balanceBefore)
					stmt.setBigDecimal(8, transaction.balanceAfter)

					stmt.executeUpdate() > 0
				}
			} finally {
				if (shouldClose) conn.close()
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to save transaction", e)
			false
		}
	}

	fun getTransactionHistory(playerId: UUID, limit: Int, offset: Int): List<BankTransaction> {
		return try {
			getConnection().use { connection ->
				val sql = """
                    SELECT * FROM bank_transactions 
                    WHERE player_id = ? 
                    ORDER BY timestamp DESC 
                    LIMIT ? OFFSET ?
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, playerId.toString())
					stmt.setInt(2, limit)
					stmt.setInt(3, offset)

					stmt.executeQuery().use { rs ->
						val transactions = mutableListOf<BankTransaction>()
						while (rs.next()) {
							transactions.add(mapResultSetToTransaction(rs))
						}
						transactions
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transaction history", e)
			emptyList()
		}
	}

	fun getTopRichestPlayers(limit: Int): List<BankAccount> {
		return try {
			getConnection().use { connection ->
				val sql = """
                    SELECT * FROM bank_accounts 
                    WHERE is_active = true 
                    ORDER BY balance DESC 
                    LIMIT ?
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setInt(1, limit)
					stmt.executeQuery().use { rs ->
						val accounts = mutableListOf<BankAccount>()
						while (rs.next()) {
							accounts.add(mapResultSetToAccount(rs))
						}
						accounts
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get top richest players", e)
			emptyList()
		}
	}

	fun getTotalMoney(): BigDecimal {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT SUM(balance) as total FROM bank_accounts WHERE is_active = true"
				connection.prepareStatement(sql).use { stmt ->
					stmt.executeQuery().use { rs ->
						if (rs.next()) rs.getBigDecimal("total") ?: BigDecimal.ZERO
						else BigDecimal.ZERO
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get total money", e)
			BigDecimal.ZERO
		}
	}

	fun getAccountsForInterest(minimumBalance: BigDecimal): List<BankAccount> {
		return try {
			getConnection().use { connection ->
				val sql = """
                    SELECT * FROM bank_accounts 
                    WHERE is_active = true AND balance >= ?
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setBigDecimal(1, minimumBalance)
					stmt.executeQuery().use { rs ->
						val accounts = mutableListOf<BankAccount>()
						while (rs.next()) {
							accounts.add(mapResultSetToAccount(rs))
						}
						accounts
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get accounts for interest", e)
			emptyList()
		}
	}

	fun updateLastInterestPayout(playerId: UUID, timestamp: LocalDateTime): Boolean {
		return try {
			getConnection().use { connection ->
				val sql = "UPDATE bank_accounts SET last_interest_payout = ? WHERE player_id = ?"
				connection.prepareStatement(sql).use { stmt ->
					stmt.setObject(1, timestamp)
					stmt.setString(2, playerId.toString())
					stmt.executeUpdate() > 0
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to update last interest payout", e)
			false
		}
	}

	fun getAccountByName(playerName: String): BankAccount? {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT * FROM bank_accounts WHERE LOWER(player_name) = LOWER(?) AND is_active = true"
				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, playerName)
					stmt.executeQuery().use { rs ->
						if (rs.next()) mapResultSetToAccount(rs) else null
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get account by name", e)
			null
		}
	}

	fun getTransactionsBetweenDates(
		playerId: UUID,
		startDate: LocalDateTime,
		endDate: LocalDateTime
	): List<BankTransaction> {
		return try {
			getConnection().use { connection ->
				val sql = """
                    SELECT * FROM bank_transactions 
                    WHERE player_id = ? AND timestamp BETWEEN ? AND ?
                    ORDER BY timestamp DESC
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, playerId.toString())
					stmt.setObject(2, startDate)
					stmt.setObject(3, endDate)

					stmt.executeQuery().use { rs ->
						val transactions = mutableListOf<BankTransaction>()
						while (rs.next()) {
							transactions.add(mapResultSetToTransaction(rs))
						}
						transactions
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transactions between dates", e)
			emptyList()
		}
	}

	fun getTransactionsByType(playerId: UUID, type: TransactionType, limit: Int): List<BankTransaction> {
		return try {
			getConnection().use { connection ->
				val sql = """
                    SELECT * FROM bank_transactions 
                    WHERE player_id = ? AND transaction_type = ?
                    ORDER BY timestamp DESC 
                    LIMIT ?
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, playerId.toString())
					stmt.setString(2, type.name)
					stmt.setInt(3, limit)

					stmt.executeQuery().use { rs ->
						val transactions = mutableListOf<BankTransaction>()
						while (rs.next()) {
							transactions.add(mapResultSetToTransaction(rs))
						}
						transactions
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transactions by type", e)
			emptyList()
		}
	}

	fun getTotalTransactionVolume(playerId: UUID? = null): BigDecimal {
		return try {
			getConnection().use { connection ->
				val sql = if (playerId != null) {
					"SELECT SUM(ABS(amount)) as volume FROM bank_transactions WHERE player_id = ?"
				} else {
					"SELECT SUM(ABS(amount)) as volume FROM bank_transactions"
				}

				connection.prepareStatement(sql).use { stmt ->
					if (playerId != null) {
						stmt.setString(1, playerId.toString())
					}
					stmt.executeQuery().use { rs ->
						if (rs.next()) rs.getBigDecimal("volume") ?: BigDecimal.ZERO
						else BigDecimal.ZERO
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transaction volume", e)
			BigDecimal.ZERO
		}
	}

	fun getTotalTransactionCount(): Long {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT COUNT(*) as count FROM bank_transactions"
				connection.prepareStatement(sql).use { stmt ->
					stmt.executeQuery().use { rs ->
						if (rs.next()) rs.getLong("count") else 0L
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transaction count", e)
			0L
		}
	}

	fun getAverageTransactionSize(): BigDecimal {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT AVG(ABS(amount)) as average FROM bank_transactions"
				connection.prepareStatement(sql).use { stmt ->
					stmt.executeQuery().use { rs ->
						if (rs.next()) rs.getBigDecimal("average") ?: BigDecimal.ZERO
						else BigDecimal.ZERO
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get average transaction size", e)
			BigDecimal.ZERO
		}
	}

	fun getTransactionCountsByType(): Map<TransactionType, Long> {
		return try {
			getConnection().use { connection ->
				val sql = "SELECT transaction_type, COUNT(*) as count FROM bank_transactions GROUP BY transaction_type"
				connection.prepareStatement(sql).use { stmt ->
					stmt.executeQuery().use { rs ->
						val counts = mutableMapOf<TransactionType, Long>()
						while (rs.next()) {
							val type = TransactionType.valueOf(rs.getString("transaction_type"))
							val count = rs.getLong("count")
							counts[type] = count
						}
						counts
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get transaction counts by type", e)
			emptyMap()
		}
	}

	fun <T> executeInTransaction(block: (Connection) -> T): T {
		getConnection().use { connection ->
			connection.autoCommit = false
			try {
				val result = block(connection)
				connection.commit()
				return result
			} catch (e: Exception) {
				connection.rollback()
				throw e
			} finally {
				connection.autoCommit = true
			}
		}
	}

	private fun mapResultSetToAccount(rs: ResultSet): BankAccount {
		val isSQLite = config.getString("database.type", "sqlite")?.lowercase() == "sqlite"
		return BankAccount(
			playerId = UUID.fromString(rs.getString("player_id")),
			playerName = rs.getString("player_name"),
			balance = rs.getBigDecimal("balance"),
			createdAt = if (isSQLite) {
				parseTimestamp(rs.getString("created_at"))
			} else {
				rs.getTimestamp("created_at").toLocalDateTime()
			},
			lastUpdated = if (isSQLite) {
				parseTimestamp(rs.getString("last_updated"))
			} else {
				rs.getTimestamp("last_updated").toLocalDateTime()
			},
			isActive = if (isSQLite) {
				rs.getInt("is_active") == 1
			} else {
				rs.getBoolean("is_active")
			},
			interestRate = rs.getBigDecimal("interest_rate"),
			lastInterestPayout = if (isSQLite) {
				parseTimestamp(rs.getString("last_interest_payout"))
			} else {
				rs.getTimestamp("last_interest_payout").toLocalDateTime()
			}
		)
	}
	fun getAccountByNumber(accountNumber: String): BankAccount? {
		return try {
			getConnection().use { connection ->
				// We need to search by the generated account number
				// Since account numbers are generated from UUID, we need to check all accounts
				val sql = "SELECT * FROM bank_accounts WHERE is_active = ? OR is_active = 1"
				connection.prepareStatement(sql).use { stmt ->
					if (isSQLite()) {
						stmt.setInt(1, 1)
					} else {
						stmt.setBoolean(1, true)
					}
					stmt.executeQuery().use { rs ->
						while (rs.next()) {
							val account = mapResultSetToAccount(rs)
							if (account.accountNumber == accountNumber) {
								return account
							}
						}
						null
					}
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to get account by number", e)
			null
		}
	}



	private fun isSQLite(): Boolean {
		return config.getString("database.type", "sqlite")?.lowercase() == "sqlite"
	}

	private fun parseTimestamp(timestampString: String?): LocalDateTime {
		return if (timestampString != null) {
			try {
				// Try parsing ISO format first (from SQLite string storage)
				LocalDateTime.parse(timestampString)
			} catch (e: Exception) {
				try {
					// Try parsing SQL timestamp format
					LocalDateTime.parse(timestampString.replace(" ", "T"))
				} catch (e2: Exception) {
					// Fallback to current time
					plugin.logger.warning("Failed to parse timestamp: $timestampString, using current time")
					LocalDateTime.now()
				}
			}
		} else {
			LocalDateTime.now()
		}
	}

	private fun mapResultSetToTransaction(rs: ResultSet): BankTransaction {
		val isSQLite = config.getString("database.type", "sqlite")?.lowercase() == "sqlite"

		return BankTransaction(
			id = rs.getLong("id"),
			playerId = UUID.fromString(rs.getString("player_id")),
			targetPlayerId = rs.getString("target_player_id")?.let { UUID.fromString(it) },
			amount = rs.getBigDecimal("amount"),
			type = TransactionType.valueOf(rs.getString("transaction_type")),
			description = rs.getString("description"),
			timestamp = if (isSQLite) {
				LocalDateTime.parse(rs.getString("timestamp"))
			} else {
				rs.getTimestamp("timestamp").toLocalDateTime()
			},
			balanceBefore = rs.getBigDecimal("balance_before"),
			balanceAfter = rs.getBigDecimal("balance_after")
		)
	}

	fun close() {
		if (::dataSource.isInitialized) {
			dataSource.close()
		}
	}

	fun saveAccount(account: BankAccount): Boolean {
		return try {
			getConnection().use { connection ->
				val sql = """
                    INSERT OR REPLACE INTO bank_accounts 
                    (player_id, player_name, balance, created_at, last_updated, is_active, interest_rate, last_interest_payout)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()

				connection.prepareStatement(sql).use { stmt ->
					stmt.setString(1, account.playerId.toString())
					stmt.setString(2, account.playerName)
					stmt.setBigDecimal(3, account.balance)
					stmt.setObject(4, account.createdAt)
					stmt.setObject(5, account.lastUpdated)
					stmt.setBoolean(6, account.isActive)
					stmt.setBigDecimal(7, account.interestRate)
					stmt.setObject(8, account.lastInterestPayout)

					stmt.executeUpdate() > 0
				}
			}
		} catch (e: Exception) {
			plugin.logger.log(Level.SEVERE, "Failed to save account", e)
			false
		}
	}


}