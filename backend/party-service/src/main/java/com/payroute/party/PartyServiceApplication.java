package com.payroute.party;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Party Service — owns customers/corporates ({@code Party}) and the
 * {@code AccountDirectory} (the customer's bank accounts plus saved
 * beneficiaries). Spring Boot entrypoint.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaAuditing
public class PartyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartyServiceApplication.class, args);
    }
}
