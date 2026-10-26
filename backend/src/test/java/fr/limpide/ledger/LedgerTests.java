package fr.limpide.ledger;

import java.math.BigDecimal;

import fr.limpide.TestcontainersConfiguration;
import fr.limpide.sandbox.SandboxId;
import fr.limpide.sandbox.TestSandboxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerTests {

	@Autowired
	private Ledger ledger;

	@Autowired
	private LedgerHealthIndicator health;

	@Autowired
	private JdbcClient jdbc;

	private SandboxId sandbox;

	private LedgerAccountId outside;

	private LedgerAccountId alice;

	private LedgerAccountId bob;

	@BeforeEach
	void openAccountsAndPaySalary() {
		this.sandbox = TestSandboxes.create(this.jdbc);
		this.outside = this.ledger.openInternalAccount(this.sandbox, "Contrepartie externe");
		this.alice = this.ledger.openAccount(this.sandbox, "Alice", euros("0"));
		this.bob = this.ledger.openAccount(this.sandbox, "Bob", euros("-500"));
		this.ledger.post(transfer(this.outside, this.alice, "2000"));
	}

	@Test
	void movesMoneyBetweenAccounts() {
		this.ledger.post(transfer(this.alice, this.bob, "300"));

		assertBalance(this.alice, "1700");
		assertBalance(this.bob, "300");
		assertBalance(this.outside, "-2000");
	}

	@Test
	void refusesTransferBelowFloorAndWritesNothing() {
		long entriesBefore = countEntries();

		assertThatExceptionOfType(InsufficientFundsException.class)
			.isThrownBy(() -> this.ledger.post(transfer(this.alice, this.bob, "2000.01")));

		assertBalance(this.alice, "2000");
		assertBalance(this.bob, "0");
		assertThat(countEntries()).isEqualTo(entriesBefore);
	}

	@Test
	void allowsOverdraftDownToFloorButNotBeyond() {
		this.ledger.post(transfer(this.bob, this.alice, "500"));
		assertBalance(this.bob, "-500");

		assertThatExceptionOfType(InsufficientFundsException.class)
			.isThrownBy(() -> this.ledger.post(transfer(this.bob, this.alice, "0.01")));
	}

	@Test
	void refusesAccountFromAnotherSandbox() {
		SandboxId otherSandbox = TestSandboxes.create(this.jdbc);
		LedgerAccountId stranger = this.ledger.openAccount(otherSandbox, "Inconnu", euros("0"));

		assertThatIllegalArgumentException().isThrownBy(() -> this.ledger.post(transfer(this.alice, stranger, "10")));
	}

	@Test
	void databaseRefusesToRewriteHistory() {
		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.jdbc.sql("UPDATE ledger_entry SET amount = amount * 2 WHERE sandbox_id = :sandbox")
				.param("sandbox", this.sandbox.value())
				.update())
			.withMessageContaining("ajout seul");
		assertThatExceptionOfType(DataAccessException.class)
			.isThrownBy(() -> this.jdbc.sql("DELETE FROM ledger_transaction WHERE sandbox_id = :sandbox")
				.param("sandbox", this.sandbox.value())
				.update())
			.withMessageContaining("ajout seul");
	}

	@Test
	void reversalRestoresBalancesOnlyOnce() {
		TransactionId rent = this.ledger.post(transfer(this.alice, this.bob, "650"));

		this.ledger.reverse(this.sandbox, rent, "Annulation du loyer");

		assertBalance(this.alice, "2000");
		assertBalance(this.bob, "0");
		assertThatExceptionOfType(AlreadyReversedException.class)
			.isThrownBy(() -> this.ledger.reverse(this.sandbox, rent, "Annulation du loyer"));
	}

	@Test
	void reversalRespectsFloors() {
		TransactionId gift = this.ledger.post(transfer(this.alice, this.bob, "300"));
		this.ledger.post(transfer(this.bob, this.alice, "800"));

		// Bob est à -500 : reprendre les 300 € le ferait passer à -800.
		assertThatExceptionOfType(InsufficientFundsException.class)
			.isThrownBy(() -> this.ledger.reverse(this.sandbox, gift, "Annulation du cadeau"));
	}

	@Test
	void healthGoesDownWhenCachedBalanceDrifts() {
		assertThat(this.health.health().getStatus()).isEqualTo(Status.UP);

		setCachedBalance(this.alice, "1999");
		try {
			assertThat(this.ledger.checkIntegrity().inconsistentAccounts()).containsExactly(this.alice);
			assertThat(this.health.health().getStatus()).isEqualTo(Status.DOWN);
		}
		finally {
			setCachedBalance(this.alice, "2000");
		}
	}

	private Posting transfer(LedgerAccountId from, LedgerAccountId to, String amount) {
		return Posting.transfer(this.sandbox, "Virement", from, to, euros(amount));
	}

	private void assertBalance(LedgerAccountId account, String expected) {
		assertThat(this.ledger.balanceOf(this.sandbox, account)).isEqualByComparingTo(expected);
	}

	private long countEntries() {
		return this.jdbc.sql("SELECT count(*) FROM ledger_entry WHERE sandbox_id = :sandbox")
			.param("sandbox", this.sandbox.value())
			.query(Long.class)
			.single();
	}

	private void setCachedBalance(LedgerAccountId account, String balance) {
		this.jdbc.sql("UPDATE ledger_account SET balance = :balance WHERE id = :id")
			.param("balance", euros(balance))
			.param("id", account.value())
			.update();
	}

	private static BigDecimal euros(String amount) {
		return new BigDecimal(amount);
	}

}
