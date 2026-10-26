package fr.limpide.ledger;

import java.util.Objects;
import java.util.UUID;

public record TransactionId(UUID value) {

	public TransactionId {
		Objects.requireNonNull(value, "value");
	}

	static TransactionId generate() {
		return new TransactionId(UUID.randomUUID());
	}

}
