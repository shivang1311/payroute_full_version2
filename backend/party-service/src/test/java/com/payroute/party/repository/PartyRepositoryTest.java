package com.payroute.party.repository;

import com.payroute.party.entity.Party;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PartyRepositoryTest {

    @Autowired PartyRepository partyRepository;

    private Party active;
    private Party deleted;
    private Party suspended;

    @BeforeEach
    void seed() {
        partyRepository.deleteAll();

        active = partyRepository.save(Party.builder()
                .name("Acme Corp").type(PartyType.CORPORATE).country("IND")
                .riskRating("LOW").status(PartyStatus.ACTIVE).build());

        suspended = partyRepository.save(Party.builder()
                .name("Beta Ltd").type(PartyType.CORPORATE).country("IND")
                .riskRating("HIGH").status(PartyStatus.SUSPENDED).build());

        Party individual = partyRepository.save(Party.builder()
                .name("John Doe").type(PartyType.INDIVIDUAL).country("USA")
                .riskRating("STANDARD").status(PartyStatus.ACTIVE).build());

        deleted = partyRepository.save(Party.builder()
                .name("Ghost Inc").type(PartyType.CORPORATE).country("IND")
                .riskRating("STANDARD").status(PartyStatus.INACTIVE)
                .deletedAt(LocalDateTime.now()).build());
    }

    @Nested
    @DisplayName("findAllActive (excludes soft-deleted)")
    class FindAllActive {
        @Test
        void onlyReturnsNonDeleted() {
            Page<Party> page = partyRepository.findAllActive(PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName)
                    .containsExactlyInAnyOrder("Acme Corp", "Beta Ltd", "John Doe");
        }
    }

    @Nested
    @DisplayName("findActiveById")
    class FindActiveById {
        @Test
        void returnsLiveRecord() {
            assertThat(partyRepository.findActiveById(active.getId())).isPresent();
        }

        @Test
        void returnsEmptyForDeleted() {
            assertThat(partyRepository.findActiveById(deleted.getId())).isEmpty();
        }

        @Test
        void returnsEmptyForUnknownId() {
            assertThat(partyRepository.findActiveById(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {
        @Test
        void filtersByActive() {
            Page<Party> page = partyRepository.findByStatus(PartyStatus.ACTIVE, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName)
                    .containsExactlyInAnyOrder("Acme Corp", "John Doe");
        }

        @Test
        void filtersBySuspended() {
            Page<Party> page = partyRepository.findByStatus(PartyStatus.SUSPENDED, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName).containsExactly("Beta Ltd");
        }
    }

    @Nested
    @DisplayName("findByType")
    class FindByType {
        @Test
        void filtersIndividual() {
            Page<Party> page = partyRepository.findByType(PartyType.INDIVIDUAL, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName).containsExactly("John Doe");
        }

        @Test
        void filtersCorporate() {
            Page<Party> page = partyRepository.findByType(PartyType.CORPORATE, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName)
                    .containsExactlyInAnyOrder("Acme Corp", "Beta Ltd");
        }
    }

    @Nested
    @DisplayName("findByStatusAndType (combined)")
    class CombinedFilter {
        @Test
        void activeCorporate() {
            Page<Party> page = partyRepository.findByStatusAndType(
                    PartyStatus.ACTIVE, PartyType.CORPORATE, PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName).containsExactly("Acme Corp");
        }
    }

    @Nested
    @DisplayName("findByNameContainingIgnoreCase")
    class NameSearch {
        @Test
        void caseInsensitivePartialMatch() {
            Page<Party> page = partyRepository.findByNameContainingIgnoreCase("acme", PageRequest.of(0, 20));
            assertThat(page.getContent()).extracting(Party::getName).containsExactly("Acme Corp");
        }

        @Test
        void noMatchReturnsEmpty() {
            Page<Party> page = partyRepository.findByNameContainingIgnoreCase("zeta", PageRequest.of(0, 20));
            assertThat(page.getContent()).isEmpty();
        }
    }
}
