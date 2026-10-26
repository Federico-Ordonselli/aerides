package dev.ordonselli.raidboard;

import org.springframework.boot.SpringApplication;

import dev.ordonselli.raidboard.support.TestcontainersConfiguration;

public class TestRaidboardApplication {

	public static void main(String[] args) {
		SpringApplication.from(RaidboardApplication::main)
				.with(TestcontainersConfiguration.class)
				.withAdditionalProfiles("dev")
				.run(args);
	}

}
