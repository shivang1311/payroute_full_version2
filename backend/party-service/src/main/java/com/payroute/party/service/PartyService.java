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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PartyService {

    private final PartyRepository partyRepository;
    private final PartyMapper partyMapper;

    public PagedResponse<PartyResponse> getAllParties(PartyStatus status, PartyType type, Pageable pageable) {
        Page<Party> page;

        if (status != null && type != null) {
            page = partyRepository.findByStatusAndType(status, type, pageable);
        } else if (status != null) {
            page = partyRepository.findByStatus(status, pageable);
        } else if (type != null) {
            page = partyRepository.findByType(type, pageable);
        } else {
            page = partyRepository.findAllActive(pageable);
        }

        return partyMapper.toPagedResponse(page);
    }

    public PartyResponse getPartyById(Long id) {
        Party party = partyRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", id));
        return partyMapper.toResponse(party);
    }

    @Transactional
    public PartyResponse createParty(PartyRequest request) {
        Party party = partyMapper.toEntity(request);
        party.setStatus(PartyStatus.ACTIVE);
        party = partyRepository.save(party);
        log.info("Created party with id: {}", party.getId());
        return partyMapper.toResponse(party);
    }

    @Transactional
    public PartyResponse updateParty(Long id, PartyRequest request) {
        Party party = partyRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", id));

        partyMapper.updateEntity(request, party);
        party = partyRepository.save(party);
        log.info("Updated party with id: {}", party.getId());
        return partyMapper.toResponse(party);
    }

    @Transactional
    public void deleteParty(Long id) {
        Party party = partyRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", id));

        party.setDeletedAt(LocalDateTime.now());
        party.setStatus(PartyStatus.INACTIVE);
        partyRepository.save(party);
        log.info("Soft-deleted party with id: {}", id);
    }
}
