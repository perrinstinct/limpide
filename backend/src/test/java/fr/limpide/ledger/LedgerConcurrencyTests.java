package fr.limpide.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import fr.limpide.TestcontainersConfiguration;
import fr.limpide.sandbox.SandboxId;
import fr.limpide.sandbox.TestSandboxes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerConcurrencyTests {

	@Autowired
	private Ledger ledger;

	@Autowired
	private JdbcClient jdbc;

	private SandboxId sandbox;

	private LedgerAccountId outside;

	@BeforeEach
	void createSandbox() {
		this.sandbox = TestSandboxes.create(this.jdbc);
		this.outside = this.ledger.openInternalAccount(this.sandbox, "Contrepartie externe");
	}

	@Test
	void hundredSimultaneousDebitsNeverOverdraw() throws Exception {
		LedgerAccountId alice = openFundedAccount("Alice", "100");
		LedgerAccountId shop = openFundedAccount("Commerçant", "0");
		AtomicInteger accepted = new AtomicInteger();
		AtomicInteger refused = new AtomicInteger();

		runAllAtOnce(100, (i) -> () -> {
			try {
				this.ledger.post(Posting.transfer(this.sandbox, "Achat", alice, shop, new BigDecimal("10")));
				accepted.incrementAndGet();
			}
			catch (InsufficientFundsException ex) {
				refused.incrementAndGet();
			}
			return null;
		});

		assertThat(accepted).hasValue(10);
		assertThat(refused).hasValue(90);
		assertThat(this.ledger.balanceOf(this.sandbox, alice)).isEqualByComparingTo("0");
		assertThat(this.ledger.balanceOf(this.sandbox, shop)).isEqualByComparingTo("100");
		assertThat(this.ledger.checkIntegrity().isSound()).isTrue();
	}

	@Test
	void crossedTransfersDoNotDeadlock() throws Exception {
		LedgerAccountId alice = openFundedAccount("Alice", "1000");
		LedgerAccountId bob = openFundedAccount("Bob", "1000");

		runAllAtOnce(100, (i) -> () -> {
			boolean aliceToBob = i % 2 == 0;
			this.ledger.post(Posting.transfer(this.sandbox, "Remboursement", aliceToBob ? alice : bob,
					aliceToBob ? bob : alice, BigDecimal.ONE));
			return null;
		});

		assertThat(this.ledger.balanceOf(this.sandbox, alice)).isEqualByComparingTo("1000");
		assertThat(this.ledger.balanceOf(this.sandbox, bob)).isEqualByComparingTo("1000");
	}

	private LedgerAccountId openFundedAccount(String label, String initialBalance) {
		LedgerAccountId account = this.ledger.openAccount(this.sandbox, label, BigDecimal.ZERO);
		BigDecimal amount = new BigDecimal(initialBalance);
		if (amount.signum() > 0) {
			this.ledger.post(Posting.transfer(this.sandbox, "Dépôt initial", this.outside, account, amount));
		}
		return account;
	}

	/**
	 * Lance toutes les tâches sur des threads virtuels, les libère au même instant, puis propage la première erreur
	 * inattendue (un interblocage, par exemple).
	 */
	private void runAllAtOnce(int count, TaskFactory tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Void>> futures = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				Callable<Void> task = tasks.create(i);
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			for (Future<Void> future : futures) {
				future.get();
			}
		}
	}

	@FunctionalInterface
	private interface TaskFactory {

		Callable<Void> create(int index);

	}

}
