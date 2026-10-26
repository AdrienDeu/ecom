package org.ecom;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application against Testcontainers instead of compose.yaml.
 */
public class TestEcomApplication {

    public static void main(String[] args) {
        SpringApplication.from(EcomApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
