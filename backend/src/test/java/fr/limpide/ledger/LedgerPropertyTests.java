package fr.limpide.ledger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import fr.limpide.TestcontainersConfiguration;
import fr.limpide.sandbox.SandboxId;
import fr.limpide.sandbox.TestSandboxes;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.spring.JqwikSpringSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rejoue des séquences d'opérations aléatoires contre le vrai grand livre et les compare à un modèle en mémoire,
 * volontairement naïf : chaque opération doit être acceptée ou refusée exactement comme le modèle le prévoit.
 */
@JqwikSpringSupport
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class LedgerPropertyTests {

	@Autowired
	private Ledger ledger;

	@Autowired
	private JdbcClient jdbc;

	@Property(tries = 200)
	void ledgerBehavesLikeTheModel(@ForAll("floors") List<BigDecimal> floors,
			@ForAll("operations") List<Operation> operations) {
		SandboxId sandbox = TestSandboxes.create(this.jdbc);
		Model model = new Model();
		List<LedgerAccountId> accounts = new ArrayList<>();
		// Le compte 0 représente l'extérieur : sans plancher, c'est lui qui fait entrer l'argent.
		accounts.add(this.ledger.openInternalAccount(sandbox, "Contrepartie externe"));
		model.open(accounts.get(0), null);
		for (BigDecimal floor : floors) {
			LedgerAccountId account = (floor != null) ? this.ledger.openAccount(sandbox, "Client", floor)
					: this.ledger.openInternalAccount(sandbox, "Interne");
			accounts.add(account);
			model.open(account, floor);
		}

		List<TransactionId> posted = new ArrayList<>();
		for (Operation operation : operations) {
			switch (operation) {
				case Transfer transfer -> {
					Posting posting = Posting.transfer(sandbox, "Virement", pick(accounts, transfer.from()),
							pick(accounts, transfer.to()), transfer.amount());
					boolean expected = model.accepts(posting.entries());
					Optional<TransactionId> id = attempt(() -> this.ledger.post(posting));
					assertThat(id.isPresent()).as("acceptation de %s", posting).isEqualTo(expected);
					Statistics.collect(id.isPresent() ? "virement accepté" : "virement refusé");
					id.ifPresent((tx) -> {
						model.apply(tx, posting.entries());
						posted.add(tx);
					});
				}
				case Reversal reversal when !posted.isEmpty() -> {
					TransactionId original = pick(posted, reversal.which());
					List<Posting.Entry> entries = model.reversalOf(original);
					boolean expected = !model.isReversed(original) && model.accepts(entries);
					Optional<TransactionId> id = attempt(() -> this.ledger.reverse(sandbox, original, "Annulation"));
					assertThat(id.isPresent()).as("contre-passation de %s", original).isEqualTo(expected);
					Statistics.collect(id.isPresent() ? "contre-passation acceptée" : "contre-passation refusée");
					id.ifPresent((tx) -> {
						model.apply(tx, entries);
						model.markReversed(original);
						posted.add(tx);
					});
				}
				case Reversal reversal -> {
					// Rien à contre-passer pour l'instant.
				}
			}
		}

		for (LedgerAccountId account : accounts) {
			assertThat(this.ledger.balanceOf(sandbox, account)).isEqualByComparingTo(model.balanceOf(account));
		}
		assertThat(this.ledger.checkIntegrity().isSound()).isTrue();
	}

	@Provide
	Arbitrary<List<BigDecimal>> floors() {
		// null : un compte interne sans plancher.
		return Arbitraries.of(new BigDecimal("0.00"), new BigDecimal("-100.00"), new BigDecimal("-500.00"))
			.injectNull(0.2)
			.list()
			.ofMinSize(1)
			.ofMaxSize(4);
	}

	@Provide
	Arbitrary<List<Operation>> operations() {
		Arbitrary<Integer> index = Arbitraries.integers().between(0, 99);
		Arbitrary<BigDecimal> amount = Arbitraries.bigDecimals()
			.between(new BigDecimal("0.01"), new BigDecimal("1000.00"))
			.ofScale(2);
		Arbitrary<Operation> transfer = Combinators.combine(index, index, amount).as(Transfer::new);
		Arbitrary<Operation> reversal = index.map(Reversal::new);
		return Arbitraries.frequencyOf(Tuple.of(4, transfer), Tuple.of(1, reversal))
			.list()
			.ofMinSize(5)
			.ofMaxSize(40);
	}

	private static <T> T pick(List<T> items, int index) {
		return items.get(index % items.size());
	}

	private static Optional<TransactionId> attempt(Supplier<TransactionId> operation) {
		try {
			return Optional.of(operation.get());
		}
		catch (InsufficientFundsException | AlreadyReversedException ex) {
			return Optional.empty();
		}
	}

	sealed interface Operation permits Transfer, Reversal {

	}

	record Transfer(int from, int to, BigDecimal amount) implements Operation {

	}

	record Reversal(int which) implements Operation {

	}

	/**
	 * Ce que le grand livre devrait faire, sans base de données, sans verrou, sans cache.
	 */
	private static final class Model {

		private final Map<LedgerAccountId, BigDecimal> balances = new HashMap<>();

		private final Map<LedgerAccountId, BigDecimal> floors = new HashMap<>();

		private final Map<TransactionId, List<Posting.Entry>> entriesByTransaction = new HashMap<>();

		private final Set<TransactionId> reversed = new HashSet<>();

		void open(LedgerAccountId account, BigDecimal floor) {
			this.balances.put(account, BigDecimal.ZERO);
			this.floors.put(account, floor);
		}

		boolean accepts(List<Posting.Entry> entries) {
			Map<LedgerAccountId, BigDecimal> next = new HashMap<>(this.balances);
			entries.forEach((entry) -> next.merge(entry.account(), entry.amount(), BigDecimal::add));
			return next.entrySet()
				.stream()
				.allMatch((e) -> this.floors.get(e.getKey()) == null
						|| e.getValue().compareTo(this.floors.get(e.getKey())) >= 0);
		}

		void apply(TransactionId transaction, List<Posting.Entry> entries) {
			entries.forEach((entry) -> this.balances.merge(entry.account(), entry.amount(), BigDecimal::add));
			this.entriesByTransaction.put(transaction, entries);
		}

		List<Posting.Entry> reversalOf(TransactionId transaction) {
			return this.entriesByTransaction.get(transaction).stream().map(Posting.Entry::negated).toList();
		}

		boolean isReversed(TransactionId transaction) {
			return this.reversed.contains(transaction);
		}

		void markReversed(TransactionId transaction) {
			this.reversed.add(transaction);
		}

		BigDecimal balanceOf(LedgerAccountId account) {
			return this.balances.get(account);
		}

	}

}
