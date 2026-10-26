package fr.limpide.ledger;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Passe l'appli en échec si le grand livre n'est plus équilibré ou si un solde en cache a dérivé.
 */
@Component
class LedgerHealthIndicator implements HealthIndicator {

	private final Ledger ledger;

	LedgerHealthIndicator(Ledger ledger) {
		this.ledger = ledger;
	}

	@Override
	public Health health() {
		IntegrityReport report = this.ledger.checkIntegrity();
		if (report.isSound()) {
			return Health.up().build();
		}
		return Health.down()
			.withDetail("unbalancedSandboxes", report.unbalancedSandboxes())
			.withDetail("inconsistentAccounts", report.inconsistentAccounts())
			.build();
	}

}
