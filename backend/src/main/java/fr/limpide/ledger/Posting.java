package fr.limpide.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import fr.limpide.sandbox.SandboxId;

/**
 * Une demande de comptabilisation : au moins deux écritures dont la somme fait zéro.
 * <p>
 * Les montants sont signés : positifs pour un crédit, négatifs pour un débit.
 */
public record Posting(SandboxId sandbox, String description, List<Entry> entries) {

	public static final int MAX_DESCRIPTION_LENGTH = 140;

	public Posting {
		Objects.requireNonNull(sandbox, "sandbox");
		if (description == null || description.isBlank() || description.length() > MAX_DESCRIPTION_LENGTH) {
			throw new IllegalArgumentException(
					"Le libellé est obligatoire et limité à " + MAX_DESCRIPTION_LENGTH + " caractères");
		}
		entries = List.copyOf(entries);
		if (entries.size() < 2) {
			throw new IllegalArgumentException("Une transaction comporte au moins deux écritures");
		}
		BigDecimal sum = entries.stream().map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		if (sum.signum() != 0) {
			throw new IllegalArgumentException("Transaction déséquilibrée : la somme des écritures vaut " + sum);
		}
	}

	/**
	 * Virement d'un compte à un autre : la source est débitée, la destination créditée.
	 */
	public static Posting transfer(SandboxId sandbox, String description, LedgerAccountId from, LedgerAccountId to,
			BigDecimal amount) {
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("Le montant d'un virement doit être positif : " + amount);
		}
		return new Posting(sandbox, description, List.of(new Entry(from, amount.negate()), new Entry(to, amount)));
	}

	public record Entry(LedgerAccountId account, BigDecimal amount) {

		public Entry {
			Objects.requireNonNull(account, "account");
			Objects.requireNonNull(amount, "amount");
			if (amount.signum() == 0) {
				throw new IllegalArgumentException("Une écriture ne peut pas être nulle");
			}
			if (amount.stripTrailingZeros().scale() > 2) {
				throw new IllegalArgumentException("Pas plus de deux décimales : " + amount);
			}
			amount = amount.setScale(2);
		}

		Entry negated() {
			return new Entry(account, amount.negate());
		}

	}

}
