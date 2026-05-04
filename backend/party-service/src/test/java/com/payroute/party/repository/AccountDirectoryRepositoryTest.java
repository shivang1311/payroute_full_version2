package com.payroute.party.repository;

import com.payroute.party.entity.AccountDirectory;
import com.payroute.party.entity.Party;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountDirectoryRepositoryTest {

    @Autowired AccountDirectoryRepository accountRepo;
    @Autowired PartyRepository partyRepo;

    private Party alice;
    private Party bob;
    private AccountDirectory aliceMain;
    private AccountDirectory deletedRow;

    @BeforeEach
    void seed() {
        accountRepo.deleteAll();
        partyRepo.deleteAll();

        alice = partyRepo.save(Party.builder()
                .name("Alice").type(PartyType.INDIVIDUAL).country("IND")
                .riskRating("LOW").status(PartyStatus.ACTIVE).build());
        bob = partyRepo.save(Party.builder()
                .name("Bob").type(PartyType.INDIVIDUAL).country("IND")
                .riskRating("LOW").status(PartyStatus.ACTIVE).build());

        aliceMain = accountRepo.save(AccountDirectory.builder()
                .party(alice).accountNumber("11112222").ifscIban("HDFC0001234")
                .alias("alice-main").currency("INR").accountType("SAVINGS")
                .vpaUpiId("alice@upi").phone("9000000001").email("alice@x.com")
                .active(true).build());

        accountRepo.save(AccountDirectory.builder()
                .party(alice).accountNumber("33334444").ifscIban("ICIC0009876")
                .alias("alice-secondary").currency("INR").accountType("CURRENT")
                .active(true).build());

        accountRepo.save(AccountDirectory.builder()
                .party(bob).accountNumber("55556666").ifscIban("HDFC0001234")
                .alias("bob-main").currency("INR").accountType("SAVINGS")
                .vpaUpiId("bob@upi").phone("9000000002").email("bob@x.com")
                .active(true).build());

        deletedRow = accountRepo.save(AccountDirectory.builder()
                .party(alice).accountNumber("99990000").ifscIban("HDFC0001234")
                .alias("ghost").currency("INR").accountType("SAVINGS")
                .active(false).deletedAt(LocalDateTime.now()).build());
    }

    @Nested
    @DisplayName("findByPartyId / findByPartyIdPaged (excludes deleted)")
    class FindByParty {
        @Test
        void aliceHasTwoActive() {
            List<AccountDirectory> rows = accountRepo.findByPartyId(alice.getId());
            assertThat(rows).hasSize(2);
        }

        @Test
        void deletedRowsAreFiltered() {
            assertThat(accountRepo.findByPartyId(alice.getId()))
                    .extracting(AccountDirectory::getId)
                    .doesNotContain(deletedRow.getId());
        }
    }

    @Nested
    @DisplayName("findByAccountNumberAndIfscIban")
    class FindByAccount {
        @Test
        void exactMatch() {
            assertThat(accountRepo.findByAccountNumberAndIfscIban("11112222", "HDFC0001234"))
                    .isPresent()
                    .get().extracting(AccountDirectory::getId).isEqualTo(aliceMain.getId());
        }

        @Test
        void wrongIfscReturnsEmpty() {
            assertThat(accountRepo.findByAccountNumberAndIfscIban("11112222", "OTHER0009999"))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("alias-based lookups")
    class AliasLookup {
        @Test
        void byAlias() {
            assertThat(accountRepo.findByAlias("alice-main")).isPresent();
        }
        @Test
        void byVpa() {
            assertThat(accountRepo.findByVpaUpiId("alice@upi")).isPresent();
        }
        @Test
        void byPhone() {
            assertThat(accountRepo.findByPhone("9000000002"))
                    .isPresent().get().extracting(AccountDirectory::getAlias).isEqualTo("bob-main");
        }
        @Test
        void byEmail() {
            assertThat(accountRepo.findByEmail("alice@x.com")).isPresent();
        }
        @Test
        void unknownAliasReturnsEmpty() {
            assertThat(accountRepo.findByAlias("nope")).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsBy* uniqueness checks")
    class ExistsBy {
        @Test
        void existsByAccountNumber() {
            assertThat(accountRepo.existsByAccountNumber("11112222")).isTrue();
            assertThat(accountRepo.existsByAccountNumber("00000000")).isFalse();
        }

        @Test
        void existsByVpa_phone_email() {
            assertThat(accountRepo.existsByVpaUpiId("alice@upi")).isTrue();
            assertThat(accountRepo.existsByPhone("9000000001")).isTrue();
            assertThat(accountRepo.existsByEmail("alice@x.com")).isTrue();
        }

        @Test
        void existsByAccountNumberAndIfscIban_compositeUniqueness() {
            assertThat(accountRepo.existsByAccountNumberAndIfscIban("11112222", "HDFC0001234")).isTrue();
            assertThat(accountRepo.existsByAccountNumberAndIfscIban("11112222", "OTHER0009999")).isFalse();
        }

        @Test
        void existsByAccountNumberAndIdNot_excludesSelf() {
            // Same account number, but excluding aliceMain's own id → false
            assertThat(accountRepo.existsByAccountNumberAndIdNot("11112222", aliceMain.getId())).isFalse();
            // Same account number with a different id-exclusion → true
            assertThat(accountRepo.existsByAccountNumberAndIdNot("11112222", 999_999L)).isTrue();
        }

        @Test
        void existsByVpaUpiIdAndIdNot_excludesSelf() {
            assertThat(accountRepo.existsByVpaUpiIdAndIdNot("alice@upi", aliceMain.getId())).isFalse();
            assertThat(accountRepo.existsByVpaUpiIdAndIdNot("alice@upi", 999_999L)).isTrue();
        }
    }

    @Nested
    @DisplayName("findActiveById ignores soft-deleted rows")
    class ActiveOnlyFilter {
        @Test
        void deletedRowIsHidden() {
            assertThat(accountRepo.findActiveById(deletedRow.getId())).isEmpty();
        }

        @Test
        void liveRowVisible() {
            assertThat(accountRepo.findActiveById(aliceMain.getId())).isPresent();
        }
    }
}
