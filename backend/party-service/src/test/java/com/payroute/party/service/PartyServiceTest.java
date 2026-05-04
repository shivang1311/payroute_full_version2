package com.payroute.party.service;

import com.payroute.party.dto.request.PartyRequest;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.dto.response.PartyResponse;
import com.payroute.party.entity.Party;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import com.payroute.party.exception.ResourceNotFoundException;
import com.payroute.party.mapper.PartyMapper;
import com.payroute.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

    @Mock PartyRepository partyRepository;
    @Mock PartyMapper partyMapper;
    @InjectMocks PartyService partyService;

    private Party party;
    private PartyRequest req;

    @BeforeEach
    void setUp() {
        party = Party.builder()
                .id(1L).name("Acme").type(PartyType.CORPORATE)
                .country("IND").riskRating("LOW").status(PartyStatus.ACTIVE).build();

        req = PartyRequest.builder()
                .name("Acme").type(PartyType.CORPORATE).country("IND").riskRating("LOW").build();

        lenient().when(partyMapper.toResponse(any(Party.class)))
                .thenAnswer(inv -> {
                    Party p = inv.getArgument(0);
                    return PartyResponse.builder()
                            .id(p.getId()).name(p.getName()).type(p.getType())
                            .status(p.getStatus()).country(p.getCountry()).build();
                });
        lenient().when(partyMapper.toEntity(any(PartyRequest.class))).thenReturn(party);
        lenient().when(partyMapper.toPagedResponse(any())).thenAnswer(inv -> {
            Page<Party> page = inv.getArgument(0);
            return PagedResponse.<PartyResponse>builder()
                    .content(page.getContent().stream().map(partyMapper::toResponse).toList())
                    .page(page.getNumber()).size(page.getSize())
                    .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                    .last(page.isLast()).build();
        });
    }

    @Nested
    @DisplayName("getAllParties (filter routing)")
    class GetAll {
        @Test
        void noFilters_callsFindAllActive() {
            when(partyRepository.findAllActive(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(party), PageRequest.of(0, 20), 1));
            partyService.getAllParties(null, null, PageRequest.of(0, 20));
            verify(partyRepository).findAllActive(any(Pageable.class));
        }

        @Test
        void statusOnly_callsFindByStatus() {
            when(partyRepository.findByStatus(eq(PartyStatus.ACTIVE), any()))
                    .thenReturn(new PageImpl<>(List.of(party)));
            partyService.getAllParties(PartyStatus.ACTIVE, null, PageRequest.of(0, 20));
            verify(partyRepository).findByStatus(eq(PartyStatus.ACTIVE), any());
        }

        @Test
        void typeOnly_callsFindByType() {
            when(partyRepository.findByType(eq(PartyType.CORPORATE), any()))
                    .thenReturn(new PageImpl<>(List.of(party)));
            partyService.getAllParties(null, PartyType.CORPORATE, PageRequest.of(0, 20));
            verify(partyRepository).findByType(eq(PartyType.CORPORATE), any());
        }

        @Test
        void bothFilters_callsCombined() {
            when(partyRepository.findByStatusAndType(eq(PartyStatus.ACTIVE), eq(PartyType.CORPORATE), any()))
                    .thenReturn(new PageImpl<>(List.of(party)));
            partyService.getAllParties(PartyStatus.ACTIVE, PartyType.CORPORATE, PageRequest.of(0, 20));
            verify(partyRepository).findByStatusAndType(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getPartyById")
    class GetById {
        @Test
        void found() {
            when(partyRepository.findActiveById(1L)).thenReturn(Optional.of(party));
            PartyResponse resp = partyService.getPartyById(1L);
            assertThat(resp.getName()).isEqualTo("Acme");
        }

        @Test
        void notFound() {
            when(partyRepository.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> partyService.getPartyById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("createParty")
    class Create {
        @Test
        @DisplayName("forces status=ACTIVE on creation")
        void forcesActiveStatus() {
            when(partyRepository.save(any(Party.class))).thenAnswer(inv -> {
                Party p = inv.getArgument(0); p.setId(1L); return p;
            });

            partyService.createParty(req);

            ArgumentCaptor<Party> captor = ArgumentCaptor.forClass(Party.class);
            verify(partyRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PartyStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("updateParty")
    class Update {
        @Test
        void updatesExisting() {
            when(partyRepository.findActiveById(1L)).thenReturn(Optional.of(party));
            when(partyRepository.save(any(Party.class))).thenAnswer(inv -> inv.getArgument(0));

            partyService.updateParty(1L, req);

            verify(partyMapper).updateEntity(req, party);
            verify(partyRepository).save(party);
        }

        @Test
        void notFound() {
            when(partyRepository.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> partyService.updateParty(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteParty (soft delete)")
    class Delete {
        @Test
        void marksDeletedAndInactive() {
            when(partyRepository.findActiveById(1L)).thenReturn(Optional.of(party));

            partyService.deleteParty(1L);

            assertThat(party.getDeletedAt()).isNotNull();
            assertThat(party.getStatus()).isEqualTo(PartyStatus.INACTIVE);
            verify(partyRepository).save(party);
        }

        @Test
        void notFound() {
            when(partyRepository.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> partyService.deleteParty(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
