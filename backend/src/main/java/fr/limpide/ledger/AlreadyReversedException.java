package fr.limpide.ledger;

/**
 * Une transaction ne peut être contre-passée qu'une seule fois.
 */
public class AlreadyReversedException extends RuntimeException {

	AlreadyReversedException(TransactionId transaction) {
		super("La transaction %s a déjà été contre-passée".formatted(transaction.value()));
	}

}
