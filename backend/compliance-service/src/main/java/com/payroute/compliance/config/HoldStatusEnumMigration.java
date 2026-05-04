package com.payroute.compliance.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time DDL migration: the original {@code hold.status} column was created
 * as {@code ENUM('ACTIVE','RELEASED','ESCALATED')}. We've added a fourth value
 * (REJECTED) at the Java side, but MySQL ENUM columns don't auto-update via
 * Hibernate's {@code ddl-auto: update}. This runner widens the column to
 * include REJECTED on every startup. Idempotent — MySQL's MODIFY COLUMN is a
 * no-op if the definition already matches.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class HoldStatusEnumMigration {

    private static final String ALTER_SQL =
            "ALTER TABLE hold MODIFY COLUMN status " +
                    "ENUM('ACTIVE','RELEASED','ESCALATED','REJECTED') NOT NULL DEFAULT 'ACTIVE'";

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner widenHoldStatusEnum() {
        return args -> doAlter();
    }

    @Transactional
    public void doAlter() {
        try {
            jdbcTemplate.execute(ALTER_SQL);
            log.info("[HoldStatusEnumMigration] hold.status column widened to include REJECTED");
        } catch (Exception ex) {
            // Don't crash the service if this fails (e.g. wrong DB engine, no permissions).
            // The first reject attempt will surface the underlying error in logs.
            log.warn("[HoldStatusEnumMigration] Could not alter hold.status column: {}", ex.getMessage());
        }
    }
}
