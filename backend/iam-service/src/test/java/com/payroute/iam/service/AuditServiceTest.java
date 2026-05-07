package com.payroute.iam.service;

import com.payroute.iam.dto.response.AuditLogResponse;
import com.payroute.iam.dto.response.PagedResponse;
import com.payroute.iam.entity.AuditLog;
import com.payroute.iam.entity.User;
import com.payroute.iam.mapper.AuditLogMapper;
import com.payroute.iam.repository.AuditLogRepository;
import com.payroute.iam.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditLogRepository repo;
    @Mock AuditLogMapper mapper;
    @Mock UserRepository userRepository;
    @InjectMocks AuditService service;

    @DisplayName("logAction persists a new AuditLog with the supplied fields")
    @Test
    void logAction_persistsEntry() {
        service.logAction(7L, "LOGIN", "USER", 7L, "from-detail", "10.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getAction()).isEqualTo("LOGIN");
        assertThat(saved.getEntityType()).isEqualTo("USER");
        assertThat(saved.getEntityId()).isEqualTo("7");
        assertThat(saved.getDetails()).isEqualTo("from-detail");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @DisplayName("logAction handles null entityId gracefully")
    @Test
    void logAction_nullEntityId() {
        service.logAction(7L, "LOGOUT", null, null, null, null);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
    }

    @DisplayName("getAuditLogs returns mapped PagedResponse with username enriched from UserRepository")
    @Test
    void getAuditLogs() {
        AuditLog log = AuditLog.builder().id(1L).userId(7L).action("LOGIN").createdAt(LocalDateTime.now()).build();
        Page<AuditLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
        when(repo.findAll(any(Pageable.class))).thenReturn(page);
        // Username lookup returns the user's display name for the enrichment step.
        when(userRepository.findAllById(any()))
                .thenReturn(List.of(User.builder().id(7L).username("alice").build()));
        lenient().when(mapper.toResponse(any(AuditLog.class)))
                .thenAnswer(inv -> {
                    AuditLog l = inv.getArgument(0);
                    return AuditLogResponse.builder().id(l.getId()).userId(l.getUserId()).action(l.getAction()).build();
                });

        PagedResponse<AuditLogResponse> resp = service.getAuditLogs(PageRequest.of(0, 20));

        assertThat(resp.getContent()).hasSize(1);
        assertThat(resp.getContent().get(0).getAction()).isEqualTo("LOGIN");
        assertThat(resp.getContent().get(0).getUsername()).isEqualTo("alice");
        assertThat(resp.getTotalElements()).isEqualTo(1);
        assertThat(resp.isLast()).isTrue();
    }

    @DisplayName("getAuditLogsByUser delegates to findByUserId")
    @Test
    void getAuditLogsByUser() {
        Page<AuditLog> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repo.findByUserId(eq(7L), any(Pageable.class))).thenReturn(empty);
        // Empty page: still need to stub the username lookup (called with empty Set).
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PagedResponse<AuditLogResponse> resp = service.getAuditLogsByUser(7L, PageRequest.of(0, 20));
        assertThat(resp.getContent()).isEmpty();
        verify(repo).findByUserId(eq(7L), any(Pageable.class));
    }
}
