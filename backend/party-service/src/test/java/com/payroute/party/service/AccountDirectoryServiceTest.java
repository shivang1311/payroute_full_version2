package com.payroute.party.service;

import com.payroute.party.client.NotificationServiceClient;
import com.payroute.party.dto.request.AccountRequest;
import com.payroute.party.dto.response.AccountResponse;
import com.payroute.party.dto.response.AccountValidationResponse;
import com.payroute.party.entity.AccountDirectory;
import com.payroute.party.entity.Party;
import com.payroute.party.entity.PartyType;
import com.payroute.party.exception.DuplicateResourceException;
import com.payroute.party.exception.ResourceNotFoundException;
import com.payroute.party.mapper.AccountMapper;
import com.payroute.party.repository.AccountDirectoryRepository;
import com.payroute.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDirectoryServiceTest {

    @Mock AccountDirectoryRepository accountRepo;
    @Mock PartyRepository partyRepo;
    @Mock AccountMapper accountMapper;
    @Mock NotificationServiceClient notificationServiceClient;
    @InjectMocks AccountDirectoryService service;

    private Party party;
    private AccountDirectory account;
    private AccountRequest req;

    @BeforeEach
    void setUp() {
        party = Party.builder().id(1L).name("Alice").type(PartyType.INDIVIDUAL).build();
        account = AccountDirectory.builder()
                .id(10L).party(party).accountNumber("1111222233334444").ifscIban("HDFC0001234")
                .alias("alice-main").currency("INR").accountType("SAVINGS").active(true).build();

        req = AccountRequest.builder()
                .partyId(1L).accountNumber("1111222233334444").ifscIban("HDFC0001234")
                .alias("alice-main").currency("INR").accountType("SAVINGS").build();

        lenient().when(accountMapper.toEntity(any(AccountRequest.class))).thenReturn(account);
        lenient().when(accountMapper.toResponse(any(AccountDirectory.class)))
                .thenAnswer(inv -> {
                    AccountDirectory a = inv.getArgument(0);
                    return AccountResponse.builder().id(a.getId())
                            .accountNumber(a.getAccountNumber()).ifscIban(a.getIfscIban())
                            .currency(a.getCurrency()).accountType(a.getAccountType()).build();
                });
    }

    // -------------------- createAccount --------------------

    @Nested
    @DisplayName("createAccount")
    class Create {
        @Test
        void happyPath() {
            when(accountRepo.findByPartyId(1L)).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(partyRepo.findActiveById(1L)).thenReturn(Optional.of(party));
            when(accountRepo.save(any(AccountDirectory.class))).thenReturn(account);

            AccountResponse resp = service.createAccount(req);

            assertThat(resp.getId()).isEqualTo(10L);
            verify(accountRepo).save(any(AccountDirectory.class));
        }

        @Test
        @DisplayName("rejects malformed IFSC/IBAN")
        void invalidIfsc() {
            req.setIfscIban("not-an-ifsc");
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid IFSC/IBAN");
        }

        @Test
        @DisplayName("accepts valid IBAN format")
        void validIban() {
            req.setIfscIban("DE89370400440532013000"); // German IBAN
            when(accountRepo.findByPartyId(1L)).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(partyRepo.findActiveById(1L)).thenReturn(Optional.of(party));
            when(accountRepo.save(any(AccountDirectory.class))).thenReturn(account);

            assertThat(service.createAccount(req)).isNotNull();
        }

        @Test
        @DisplayName("rejects when party already has 10 accounts (cap)")
        void exceedsCap() {
            when(accountRepo.findByPartyId(1L))
                    .thenReturn(java.util.stream.IntStream.range(0, 10)
                            .mapToObj(i -> AccountDirectory.builder().id((long) i).build())
                            .toList());
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("rejects duplicate accountNumber+ifsc")
        void dupAccountIfsc() {
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(true);
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects duplicate accountNumber across directory")
        void dupAccountNumber() {
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(true);
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects duplicate VPA")
        void dupVpa() {
            req.setVpaUpiId("alice@upi");
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(accountRepo.existsByVpaUpiId("alice@upi")).thenReturn(true);
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects duplicate phone alias")
        void dupPhone() {
            req.setPhone("9000000001");
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(accountRepo.existsByPhone("9000000001")).thenReturn(true);
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects duplicate email alias")
        void dupEmail() {
            req.setEmail("alice@x.com");
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(accountRepo.existsByEmail("alice@x.com")).thenReturn(true);
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("rejects when party not found / inactive")
        void partyMissing() {
            when(accountRepo.findByPartyId(anyLong())).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(partyRepo.findActiveById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.createAccount(req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("notification failure does not roll back account creation")
        void notificationBestEffort() {
            when(accountRepo.findByPartyId(1L)).thenReturn(Collections.emptyList());
            when(accountRepo.existsByAccountNumberAndIfscIban(anyString(), anyString())).thenReturn(false);
            when(accountRepo.existsByAccountNumber(anyString())).thenReturn(false);
            when(partyRepo.findActiveById(1L)).thenReturn(Optional.of(party));
            when(accountRepo.save(any(AccountDirectory.class))).thenReturn(account);
            org.mockito.Mockito.doThrow(new RuntimeException("notif down"))
                    .when(notificationServiceClient).sendNotification(any());

            // Should NOT throw — notif is best-effort.
            AccountResponse resp = service.createAccount(req, "user-7");
            assertThat(resp.getId()).isEqualTo(10L);
        }
    }

    // -------------------- updateAccount --------------------

    @Nested
    @DisplayName("updateAccount (excludes self in dup checks)")
    class Update {
        @Test
        void successWithoutDups() {
            when(accountRepo.findActiveById(10L)).thenReturn(Optional.of(account));
            when(accountRepo.existsByAccountNumberAndIdNot(anyString(), anyLong())).thenReturn(false);
            when(partyRepo.findActiveById(1L)).thenReturn(Optional.of(party));
            when(accountRepo.save(any(AccountDirectory.class))).thenReturn(account);

            AccountResponse resp = service.updateAccount(10L, req);
            assertThat(resp).isNotNull();
            verify(accountMapper).updateEntity(req, account);
        }

        @Test
        void rejectsAccountDup() {
            when(accountRepo.findActiveById(10L)).thenReturn(Optional.of(account));
            when(accountRepo.existsByAccountNumberAndIdNot(anyString(), anyLong())).thenReturn(true);
            assertThatThrownBy(() -> service.updateAccount(10L, req))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        void rejectsWhenAccountMissing() {
            when(accountRepo.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateAccount(99L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- deleteAccount --------------------

    @Nested
    @DisplayName("deleteAccount (soft delete)")
    class Delete {
        @Test
        void marksDeletedAndInactive() {
            when(accountRepo.findActiveById(10L)).thenReturn(Optional.of(account));
            service.deleteAccount(10L);
            assertThat(account.getDeletedAt()).isNotNull();
            assertThat(account.isActive()).isFalse();
            verify(accountRepo).save(account);
        }

        @Test
        void notFound() {
            when(accountRepo.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteAccount(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- getAccountById --------------------

    @Nested
    @DisplayName("getAccountById")
    class GetById {
        @Test
        void found() {
            when(accountRepo.findActiveById(10L)).thenReturn(Optional.of(account));
            AccountResponse resp = service.getAccountById(10L);
            assertThat(resp.getId()).isEqualTo(10L);
        }

        @Test
        void notFound() {
            when(accountRepo.findActiveById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getAccountById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- validateAccount / validateById --------------------

    @Nested
    @DisplayName("validateAccount + validateById")
    class Validate {
        @Test
        @DisplayName("validateAccount returns exists=true with metadata for live account")
        void found() {
            when(accountRepo.findByAccountNumberAndIfscIban("1111222233334444", "HDFC0001234"))
                    .thenReturn(Optional.of(account));

            AccountValidationResponse resp = service.validateAccount("1111222233334444", "HDFC0001234");
            assertThat(resp.isExists()).isTrue();
            assertThat(resp.isActive()).isTrue();
            assertThat(resp.getCurrency()).isEqualTo("INR");
            assertThat(resp.getAccountId()).isEqualTo(10L);
            assertThat(resp.getPartyName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("validateAccount returns exists=false for unknown")
        void notFound() {
            when(accountRepo.findByAccountNumberAndIfscIban(anyString(), anyString()))
                    .thenReturn(Optional.empty());

            AccountValidationResponse resp = service.validateAccount("0000", "X");
            assertThat(resp.isExists()).isFalse();
            assertThat(resp.isActive()).isFalse();
            assertThat(resp.getAccountId()).isNull();
        }

        @Test
        @DisplayName("validateById includes partyId when present")
        void byIdIncludesPartyId() {
            when(accountRepo.findById(10L)).thenReturn(Optional.of(account));
            AccountValidationResponse resp = service.validateById(10L);
            assertThat(resp.isExists()).isTrue();
            assertThat(resp.getPartyId()).isEqualTo(1L);
        }
    }

    // -------------------- resolveByAlias --------------------

    @Nested
    @DisplayName("resolveByAlias (alias-type routing)")
    class Resolve {
        @Test
        void byVpa() {
            when(accountRepo.findByVpaUpiId("alice@upi")).thenReturn(Optional.of(account));
            AccountResponse resp = service.resolveByAlias("VPA", "alice@upi");
            assertThat(resp.getId()).isEqualTo(10L);
        }

        @Test
        void byPhone() {
            when(accountRepo.findByPhone("9000000001")).thenReturn(Optional.of(account));
            assertThat(service.resolveByAlias("PHONE", "9000000001")).isNotNull();
        }

        @Test
        void byEmail() {
            when(accountRepo.findByEmail("alice@x.com")).thenReturn(Optional.of(account));
            assertThat(service.resolveByAlias("EMAIL", "alice@x.com")).isNotNull();
        }

        @Test
        void byNameAlias() {
            when(accountRepo.findByAlias("alice-main")).thenReturn(Optional.of(account));
            assertThat(service.resolveByAlias("NAME", "alice-main")).isNotNull();
        }

        @Test
        void unsupportedTypeThrows() {
            assertThatThrownBy(() -> service.resolveByAlias("BANK_CODE", "x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported aliasType");
        }

        @Test
        void nullValueThrows() {
            assertThatThrownBy(() -> service.resolveByAlias("VPA", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void notFoundThrows() {
            when(accountRepo.findByVpaUpiId("nope@upi")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.resolveByAlias("VPA", "nope@upi"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // -------------------- getAccounts (filter by party) --------------------

    @Nested
    @DisplayName("getAccounts pagination")
    class List_ {
        @Test
        void byPartyId() {
            org.springframework.data.domain.Page<AccountDirectory> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(account));
            when(accountRepo.findByPartyIdPaged(any(), any())).thenReturn(page);
            lenient().when(accountMapper.toPagedResponse(any())).thenReturn(null);

            service.getAccounts(1L, org.springframework.data.domain.PageRequest.of(0, 20));
            verify(accountRepo).findByPartyIdPaged(any(), any());
            verify(accountRepo, never()).findAllActive(any());
        }

        @Test
        void noFilter() {
            org.springframework.data.domain.Page<AccountDirectory> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(account));
            when(accountRepo.findAllActive(any())).thenReturn(page);
            lenient().when(accountMapper.toPagedResponse(any())).thenReturn(null);

            service.getAccounts(null, org.springframework.data.domain.PageRequest.of(0, 20));
            verify(accountRepo).findAllActive(any());
        }
    }
}
