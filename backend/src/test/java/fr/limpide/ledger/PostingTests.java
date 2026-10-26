package fr.limpide.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import fr.limpide.ledger.Posting.Entry;
import fr.limpide.sandbox.SandboxId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PostingTests {

	private final SandboxId sandbox = new SandboxId(UUID.randomUUID());

	private final LedgerAccountId alice = LedgerAccountId.generate();

	private final LedgerAccountId bob = LedgerAccountId.generate();

	@Test
	void transferDebitsSourceAndCreditsDestination() {
		Posting posting = Posting.transfer(this.sandbox, "Loyer", this.alice, this.bob, new BigDecimal("650"));

		assertThat(posting.entries()).containsExactly(new Entry(this.alice, new BigDecimal("-650.00")),
				new Entry(this.bob, new BigDecimal("650.00")));
	}

	@Test
	void refusesUnbalancedEntries() {
		List<Entry> entries = List.of(new Entry(this.alice, new BigDecimal("-100")),
				new Entry(this.bob, new BigDecimal("99.99")));

		assertThatIllegalArgumentException().isThrownBy(() -> new Posting(this.sandbox, "Erreur", entries))
			.withMessageContaining("déséquilibrée");
	}

	@Test
	void refusesSingleEntry() {
		List<Entry> entries = List.of(new Entry(this.alice, new BigDecimal("100")));

		assertThatIllegalArgumentException().isThrownBy(() -> new Posting(this.sandbox, "Erreur", entries));
	}

	@Test
	void refusesZeroAndSubCentAmounts() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Entry(this.alice, BigDecimal.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new Entry(this.alice, new BigDecimal("10.005")));
	}

	@Test
	void refusesNonPositiveTransfer() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Posting.transfer(this.sandbox, "Erreur", this.alice, this.bob, new BigDecimal("-5")));
	}

	@Test
	void refusesMissingOrTooLongDescription() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Posting.transfer(this.sandbox, " ", this.alice, this.bob, BigDecimal.TEN));
		assertThatIllegalArgumentException().isThrownBy(
				() -> Posting.transfer(this.sandbox, "x".repeat(141), this.alice, this.bob, BigDecimal.TEN));
	}

}
