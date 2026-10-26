package fr.limpide.ledger;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.limpide.sandbox.SandboxId;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Accès SQL explicite au grand livre : chaque requête filtre par bac à sable.
 */
@Repository
class LedgerRepository {

	private final JdbcClient jdbc;

	LedgerRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	LedgerAccountId insertAccount(SandboxId sandbox, String label, BigDecimal minBalance) {
		LedgerAccountId id = LedgerAccountId.generate();
		this.jdbc.sql("""
				INSERT INTO ledger_account (id, sandbox_id, label, min_balance)
				VALUES (:id, :sandbox, :label, :minBalance)""")
			.param("id", id.value())
			.param("sandbox", sandbox.value())
			.param("label", label)
			.param("minBalance", minBalance, Types.NUMERIC)
			.update();
		return id;
	}

	/**
	 * Verrouille les comptes dans l'ordre de leurs identifiants : deux transactions croisées (A vers B et B vers A)
	 * prennent leurs verrous dans le même ordre et ne peuvent donc pas s'attendre mutuellement.
	 */
	List<LockedAccount> lockAccounts(SandboxId sandbox, Collection<LedgerAccountId> ids) {
		return this.jdbc.sql("""
				SELECT id, balance, min_balance
				FROM ledger_account
				WHERE sandbox_id = :sandbox AND id IN (:ids)
				ORDER BY id
				FOR UPDATE""")
			.param("sandbox", sandbox.value())
			.param("ids", ids.stream().map(LedgerAccountId::value).toList())
			.query((rs, rowNum) -> new LockedAccount(new LedgerAccountId(rs.getObject("id", UUID.class)),
					rs.getBigDecimal("balance"), rs.getBigDecimal("min_balance")))
			.list();
	}

	void insertTransaction(TransactionId id, SandboxId sandbox, String description, TransactionId reverses) {
		this.jdbc.sql("""
				INSERT INTO ledger_transaction (id, sandbox_id, description, reverses_id)
				VALUES (:id, :sandbox, :description, :reverses)""")
			.param("id", id.value())
			.param("sandbox", sandbox.value())
			.param("description", description)
			.param("reverses", (reverses != null) ? reverses.value() : null, Types.OTHER)
			.update();
	}

	void insertEntries(TransactionId transaction, Posting posting) {
		for (Posting.Entry entry : posting.entries()) {
			this.jdbc.sql("""
					INSERT INTO ledger_entry (sandbox_id, transaction_id, account_id, amount)
					VALUES (:sandbox, :transaction, :account, :amount)""")
				.param("sandbox", posting.sandbox().value())
				.param("transaction", transaction.value())
				.param("account", entry.account().value())
				.param("amount", entry.amount())
				.update();
		}
	}

	void updateBalance(LedgerAccountId account, BigDecimal balance) {
		this.jdbc.sql("UPDATE ledger_account SET balance = :balance WHERE id = :id")
			.param("balance", balance)
			.param("id", account.value())
			.update();
	}

	boolean isReversed(SandboxId sandbox, TransactionId transaction) {
		return this.jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM ledger_transaction WHERE sandbox_id = :sandbox AND reverses_id = :transaction)""")
			.param("sandbox", sandbox.value())
			.param("transaction", transaction.value())
			.query(Boolean.class)
			.single();
	}

	List<Posting.Entry> findEntries(SandboxId sandbox, TransactionId transaction) {
		return this.jdbc.sql("""
				SELECT account_id, amount
				FROM ledger_entry
				WHERE sandbox_id = :sandbox AND transaction_id = :transaction
				ORDER BY id""")
			.param("sandbox", sandbox.value())
			.param("transaction", transaction.value())
			.query((rs, rowNum) -> new Posting.Entry(new LedgerAccountId(rs.getObject("account_id", UUID.class)),
					rs.getBigDecimal("amount")))
			.list();
	}

	Optional<BigDecimal> findBalance(SandboxId sandbox, LedgerAccountId account) {
		return this.jdbc.sql("SELECT balance FROM ledger_account WHERE sandbox_id = :sandbox AND id = :id")
			.param("sandbox", sandbox.value())
			.param("id", account.value())
			.query(BigDecimal.class)
			.optional();
	}

	List<SandboxId> findUnbalancedSandboxes() {
		return this.jdbc.sql("SELECT sandbox_id FROM ledger_entry GROUP BY sandbox_id HAVING SUM(amount) <> 0")
			.query((rs, rowNum) -> new SandboxId(rs.getObject("sandbox_id", UUID.class)))
			.list();
	}

	List<LedgerAccountId> findInconsistentAccounts() {
		return this.jdbc.sql("""
				SELECT a.id
				FROM ledger_account a
				LEFT JOIN (SELECT account_id, SUM(amount) AS total FROM ledger_entry GROUP BY account_id) e
					ON e.account_id = a.id
				WHERE a.balance <> COALESCE(e.total, 0)""")
			.query((rs, rowNum) -> new LedgerAccountId(rs.getObject("id", UUID.class)))
			.list();
	}

}
