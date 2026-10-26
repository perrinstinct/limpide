package fr.limpide.ledger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import fr.limpide.sandbox.SandboxId;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point d'entrée unique du grand livre : seul composant autorisé à faire bouger de l'argent.
 */
@Service
public class Ledger {

	private final LedgerRepository repository;

	Ledger(LedgerRepository repository) {
		this.repository = repository;
	}

	/**
	 * Ouvre un compte dont le solde ne pourra pas descendre sous {@code minBalance} (0, ou négatif pour un
	 * découvert autorisé).
	 */
	public LedgerAccountId openAccount(SandboxId sandbox, String label, BigDecimal minBalance) {
		Objects.requireNonNull(minBalance, "minBalance");
		return this.repository.insertAccount(sandbox, label, minBalance);
	}

	/**
	 * Ouvre un compte interne de la banque, sans plancher.
	 */
	public LedgerAccountId openInternalAccount(SandboxId sandbox, String label) {
		return this.repository.insertAccount(sandbox, label, null);
	}

	/**
	 * Comptabilise une transaction, ou la refuse en bloc si un compte passait sous son plancher.
	 */
	@Transactional
	public TransactionId post(Posting posting) {
		return record(posting, null);
	}

	/**
	 * Annule une transaction en comptabilisant son inverse. L'original reste intact.
	 */
	@Transactional
	public TransactionId reverse(SandboxId sandbox, TransactionId original, String description) {
		List<Posting.Entry> entries = this.repository.findEntries(sandbox, original);
		if (entries.isEmpty()) {
			throw new IllegalArgumentException("Transaction inconnue dans ce bac à sable : " + original.value());
		}
		Posting reversal = new Posting(sandbox, description, entries.stream().map(Posting.Entry::negated).toList());
		try {
			return record(reversal, original);
		}
		catch (DuplicateKeyException ex) {
			// Filet de sécurité : la contrainte d'unicité sur reverses_id a le dernier mot.
			throw new AlreadyReversedException(original);
		}
	}

	@Transactional(readOnly = true)
	public BigDecimal balanceOf(SandboxId sandbox, LedgerAccountId account) {
		return this.repository.findBalance(sandbox, account)
			.orElseThrow(() -> new IllegalArgumentException("Compte inconnu dans ce bac à sable : " + account.value()));
	}

	@Transactional(readOnly = true)
	public IntegrityReport checkIntegrity() {
		return new IntegrityReport(this.repository.findUnbalancedSandboxes(),
				this.repository.findInconsistentAccounts());
	}

	private TransactionId record(Posting posting, TransactionId reverses) {
		Map<LedgerAccountId, BigDecimal> deltas = new LinkedHashMap<>();
		posting.entries().forEach((entry) -> deltas.merge(entry.account(), entry.amount(), BigDecimal::add));

		List<LockedAccount> accounts = this.repository.lockAccounts(posting.sandbox(), deltas.keySet());
		if (accounts.size() != deltas.size()) {
			throw new IllegalArgumentException("Compte inconnu dans ce bac à sable");
		}
		// Vérifié sous verrou : une contre-passation concurrente de la même transaction touche les mêmes comptes,
		// elle a donc forcément fini avant nous. Le motif « déjà contre-passée » passe avant tout refus de solde.
		if (reverses != null && this.repository.isReversed(posting.sandbox(), reverses)) {
			throw new AlreadyReversedException(reverses);
		}

		// Tous les contrôles passent avant la première écriture : la transaction est acceptée ou refusée en bloc.
		Map<LedgerAccountId, BigDecimal> newBalances = new LinkedHashMap<>();
		for (LockedAccount account : accounts) {
			BigDecimal newBalance = account.balance().add(deltas.get(account.id()));
			if (!account.allows(newBalance)) {
				throw new InsufficientFundsException(account.id(), newBalance, account.minBalance());
			}
			newBalances.put(account.id(), newBalance);
		}

		TransactionId id = TransactionId.generate();
		this.repository.insertTransaction(id, posting.sandbox(), posting.description(), reverses);
		this.repository.insertEntries(id, posting);
		newBalances.forEach(this.repository::updateBalance);
		return id;
	}

}
