package fr.limpide;

import org.springframework.boot.SpringApplication;

public class TestLimpideApplication {

	public static void main(String[] args) {
		SpringApplication.from(LimpideApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
