package fr.limpide.ledger;

import java.util.List;

import fr.limpide.sandbox.SandboxId;

/**
 * Résultat du contrôle d'intégrité du grand livre.
 *
 * @param unbalancedSandboxes bacs à sable dont la somme des écritures ne vaut pas zéro
 * @param inconsistentAccounts comptes dont le solde en cache diffère de la somme de leurs écritures
 */
public record IntegrityReport(List<SandboxId> unbalancedSandboxes, List<LedgerAccountId> inconsistentAccounts) {

	public boolean isSound() {
		return this.unbalancedSandboxes.isEmpty() && this.inconsistentAccounts.isEmpty();
	}

}
