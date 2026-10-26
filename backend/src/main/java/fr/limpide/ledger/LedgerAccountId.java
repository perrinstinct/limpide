package fr.limpide.ledger;

import java.util.Objects;
import java.util.UUID;

public record LedgerAccountId(UUID value) {

	public LedgerAccountId {
		Objects.requireNonNull(value, "value");
	}

	static LedgerAccountId generate() {
		return new LedgerAccountId(UUID.randomUUID());
	}

}
