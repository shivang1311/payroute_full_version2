package com.payroute.iam.repository;

import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests using the full Spring context (H2 in-memory), so the
 * existing JpaAuditing configuration / Eureka stubs / etc. all wire up the
 * same way as in the real service. Each test is wrapped in a transaction
 * so committed state is rolled back between methods.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    private User customer;

    @BeforeEach
    void seed() {
        userRepository.deleteAll();

        customer = userRepository.save(User.builder()
                .username("alice")
                .email("alice@example.com")
                .phone("9000000001")
                .passwordHash("hashed")
                .role(Role.CUSTOMER)
                .active(true)
                .partyId(101L)
                .build());

        userRepository.save(User.builder()
                .username("ops.bob")
                .email("ops.bob@example.com")
                .phone("9000000002")
                .passwordHash("hashed")
                .role(Role.OPERATIONS)
                .active(true)
                .build());
    }

    // ---------- findByUsername ----------

    @Nested
    @DisplayName("findByUsername")
    class FindByUsername {

        @Test
        @DisplayName("returns the user when the username exists")
        void found() {
            Optional<User> result = userRepository.findByUsername("alice");
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(customer.getId());
            assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
        }

        @Test
        @DisplayName("returns empty when no such username")
        void notFound() {
            assertThat(userRepository.findByUsername("nope")).isEmpty();
        }
    }

    // ---------- findByEmail ----------

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        void foundReturnsUser() {
            Optional<User> result = userRepository.findByEmail("alice@example.com");
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo("alice");
        }

        @Test
        void missingReturnsEmpty() {
            assertThat(userRepository.findByEmail("ghost@example.com")).isEmpty();
        }
    }

    // ---------- existsBy* ----------

    @Nested
    @DisplayName("existsBy*")
    class ExistsBy {

        @Test
        void existsByUsername_trueAndFalse() {
            assertThat(userRepository.existsByUsername("alice")).isTrue();
            assertThat(userRepository.existsByUsername("does-not-exist")).isFalse();
        }

        @Test
        void existsByEmail_trueAndFalse() {
            assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
            assertThat(userRepository.existsByEmail("nope@example.com")).isFalse();
        }

        @Test
        void existsByPhone_trueAndFalse() {
            assertThat(userRepository.existsByPhone("9000000001")).isTrue();
            assertThat(userRepository.existsByPhone("0000000000")).isFalse();
        }
    }

    // ---------- findByRoleAndTransactionPinHashIsNull ----------

    @Nested
    @DisplayName("findByRoleAndTransactionPinHashIsNull (PIN backfill query)")
    class PinBackfillQuery {

        @Test
        @DisplayName("returns customers whose PIN hash is null only")
        void onlyMissingPins() {
            // Both seeded users have null transactionPinHash by default.
            List<User> missing = userRepository.findByRoleAndTransactionPinHashIsNull(Role.CUSTOMER);
            assertThat(missing).extracting(User::getUsername).containsExactly("alice");
        }

        @Test
        @DisplayName("excludes customers who already have a PIN")
        void excludesUsersWithPin() {
            customer.setTransactionPinHash("$2a$mock-hash");
            userRepository.save(customer);

            List<User> missing = userRepository.findByRoleAndTransactionPinHashIsNull(Role.CUSTOMER);
            assertThat(missing).isEmpty();
        }

        @Test
        @DisplayName("filters strictly by role — staff are not returned")
        void staffNotIncluded() {
            List<User> missing = userRepository.findByRoleAndTransactionPinHashIsNull(Role.CUSTOMER);
            assertThat(missing).extracting(User::getRole).containsOnly(Role.CUSTOMER);
        }
    }
}
