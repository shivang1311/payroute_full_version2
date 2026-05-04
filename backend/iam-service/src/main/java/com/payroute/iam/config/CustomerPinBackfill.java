package com.payroute.iam.config;

import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import com.payroute.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time backfill: existing CUSTOMER rows that pre-date the transaction-PIN
 * feature have a NULL {@code transaction_pin_hash}. Without a PIN they would
 * be locked out of payments after the upgrade. We seed every such customer
 * with a default PIN of {@code 1234}.
 *
 * <p>Idempotent — only touches rows where the column is null. Once a customer
 * has any PIN set (default or custom), they're skipped on subsequent restarts.
 *
 * <p>Customers can change their PIN at any time from the Profile screen.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CustomerPinBackfill {

    private static final String DEFAULT_PIN = "1234";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner backfillCustomerTransactionPins() {
        return args -> doBackfill();
    }

    @Transactional
    public void doBackfill() {
        List<User> missing = userRepository.findByRoleAndTransactionPinHashIsNull(Role.CUSTOMER);
        if (missing.isEmpty()) {
            log.info("[CustomerPinBackfill] No customers with missing transaction PIN — nothing to do");
            return;
        }
        String hash = passwordEncoder.encode(DEFAULT_PIN);
        for (User u : missing) {
            u.setTransactionPinHash(hash);
        }
        userRepository.saveAll(missing);
        log.info("[CustomerPinBackfill] Seeded default transaction PIN '{}' for {} existing customer(s)",
                DEFAULT_PIN, missing.size());
    }
}
