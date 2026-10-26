package fr.limpide;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(LimpideApplication.class);

	@Test
	void respectsModuleBoundaries() {
		modules.verify();
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}

}
