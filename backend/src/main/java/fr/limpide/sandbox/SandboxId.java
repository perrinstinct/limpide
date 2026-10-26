package fr.limpide.sandbox;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un bac à sable : toute donnée métier lui appartient (ADR-0003).
 */
public record SandboxId(UUID value) {

	public SandboxId {
		Objects.requireNonNull(value, "value");
	}

}
