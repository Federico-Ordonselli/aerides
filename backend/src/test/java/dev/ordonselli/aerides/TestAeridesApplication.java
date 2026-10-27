package dev.ordonselli.aerides;

import org.springframework.boot.SpringApplication;

import dev.ordonselli.aerides.support.TestcontainersConfiguration;

public class TestAeridesApplication {

	public static void main(String[] args) {
		SpringApplication.from(AeridesApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("dev")
				.run(args);
	}

}
