package fr.limpide.sandbox;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Crée un bac à sable vide : chaque test travaille dans le sien et ne voit pas les données des autres.
 */
public final class TestSandboxes {

	private TestSandboxes() {
	}

	public static SandboxId create(JdbcClient jdbc) {
		SandboxId id = new SandboxId(UUID.randomUUID());
		jdbc.sql("INSERT INTO sandbox (id, expires_at) VALUES (:id, now() + interval '1 day')")
			.param("id", id.value())
			.update();
		return id;
	}

}
