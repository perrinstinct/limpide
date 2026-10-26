package fr.limpide.ledger;

import java.math.BigDecimal;

/**
 * Refus métier : l'opération ferait passer un compte sous son plancher.
 */
public class InsufficientFundsException extends RuntimeException {

	private final LedgerAccountId account;

	InsufficientFundsException(LedgerAccountId account, BigDecimal attemptedBalance, BigDecimal minBalance) {
		super("Solde insuffisant sur le compte %s : il passerait à %s, sous son plancher de %s"
			.formatted(account.value(), attemptedBalance, minBalance));
		this.account = account;
	}

	public LedgerAccountId account() {
		return this.account;
	}

}
