package fr.limpide.ledger;

import java.math.BigDecimal;

/**
 * Un compte lu sous verrou, le temps de contrôler une transaction.
 *
 * @param minBalance plancher du solde, ou {@code null} pour un compte interne sans limite
 */
record LockedAccount(LedgerAccountId id, BigDecimal balance, BigDecimal minBalance) {

	boolean allows(BigDecimal newBalance) {
		return this.minBalance == null || newBalance.compareTo(this.minBalance) >= 0;
	}

}
