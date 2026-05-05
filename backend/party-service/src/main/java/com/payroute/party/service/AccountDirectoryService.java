package com.payroute.party.service;

import com.payroute.party.dto.request.AccountRequest;
import com.payroute.party.dto.response.AccountResponse;
import com.payroute.party.dto.response.AccountValidationResponse;
import com.payroute.party.dto.response.PagedResponse;
import com.payroute.party.entity.AccountDirectory;
import com.payroute.party.entity.Party;
import com.payroute.party.exception.DuplicateResourceException;
import com.payroute.party.exception.ResourceNotFoundException;
import com.payroute.party.client.LedgerServiceClient;
import com.payroute.party.client.NotificationServiceClient;
import com.payroute.party.dto.client.LedgerPostRequest;
import com.payroute.party.dto.client.NotificationRequest;
import com.payroute.party.mapper.AccountMapper;
import com.payroute.party.repository.AccountDirectoryRepository;
import com.payroute.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountDirectoryService {

    private final AccountDirectoryRepository accountDirectoryRepository;
    private final PartyRepository partyRepository;
    private final AccountMapper accountMapper;
    private final NotificationServiceClient notificationServiceClient;
    private final LedgerServiceClient ledgerServiceClient;

    /** Seed credit applied to every newly created INR account so customers can transact. */
    private static final BigDecimal OPENING_BALANCE_INR = new BigDecimal("1000000.00");

    /**
     * Post a CREDIT ledger entry of {@link #OPENING_BALANCE_INR} so the new account
     * has a usable balance for transactions. Only applied to INR accounts.
     *
     * <p>Uses {@code paymentId = 0} as a sentinel — real payments start at id 1
     * so this never collides. Failure is logged but does not block account creation.
     */
    private void seedOpeningBalance(Long accountId, String currency) {
        if (!"INR".equalsIgnoreCase(currency)) return;
        try {
            ledgerServiceClient.postEntry(LedgerPostRequest.builder()
                    .paymentId(0L) // sentinel — opening balance has no payment
                    .accountId(accountId)
                    .entryType("CREDIT")
                    .amount(OPENING_BALANCE_INR)
                    .currency("INR")
                    .narrative("Opening balance — account onboarded")
                    .build());
            log.info("Seeded opening balance of INR {} for account {}", OPENING_BALANCE_INR, accountId);
        } catch (Exception ex) {
            log.warn("Failed to seed opening balance for account {}: {}", accountId, ex.getMessage());
        }
    }

    private void sendNotificationSafe(String userId, String title, String message,
                                       String severity, Long accountId) {
        if (userId == null || userId.isBlank()) return;
        try {
            notificationServiceClient.sendNotification(NotificationRequest.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .category("ACCOUNT")
                    .severity(severity)
                    .referenceType("ACCOUNT")
                    .referenceId(accountId)
                    .build());
        } catch (Exception ex) {
            log.warn("Failed to send account notification to user={}: {}", userId, ex.getMessage());
        }
    }

    public PagedResponse<AccountResponse> getAccounts(Long partyId, Pageable pageable) {
        Page<AccountDirectory> page;
        if (partyId != null) {
            page = accountDirectoryRepository.findByPartyIdPaged(partyId, pageable);
        } else {
            page = accountDirectoryRepository.findAllActive(pageable);
        }
        return accountMapper.toPagedResponse(page);
    }

    public List<AccountResponse> getAccountsByPartyId(Long partyId) {
        List<AccountDirectory> accounts = accountDirectoryRepository.findByPartyId(partyId);
        return accountMapper.toResponseList(accounts);
    }

    public AccountResponse getAccountById(Long id) {
        AccountDirectory account = accountDirectoryRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        return accountMapper.toResponse(account);
    }

    /** IFSC (Indian) or IBAN / SWIFT (international) — relaxed format check. */
    private static final java.util.regex.Pattern IFSC_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    private static final java.util.regex.Pattern IBAN_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{10,30}$");

    private static final int MAX_ACCOUNTS_PER_PARTY = 10;

    private void validateIfscIban(String value) {
        if (value == null || value.isBlank()) return;
        String v = value.trim().toUpperCase();
        if (!IFSC_PATTERN.matcher(v).matches() && !IBAN_PATTERN.matcher(v).matches()) {
            throw new IllegalArgumentException(
                    "Invalid IFSC/IBAN format: '" + value + "' (expected IFSC like HDFC0001234 or IBAN)");
        }
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        return createAccount(request, null);
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request, String actingUser) {
        // IFSC / IBAN format
        validateIfscIban(request.getIfscIban());

        // Per-customer cap
        if (request.getPartyId() != null) {
            long active = accountDirectoryRepository.findByPartyId(request.getPartyId()).size();
            if (active >= MAX_ACCOUNTS_PER_PARTY) {
                throw new IllegalStateException(
                        "Party has reached the maximum of " + MAX_ACCOUNTS_PER_PARTY + " active accounts");
            }
        }

        // Duplicate (accountNumber + IFSC) — same account at same bank
        if (request.getIfscIban() != null &&
                accountDirectoryRepository.existsByAccountNumberAndIfscIban(
                        request.getAccountNumber(), request.getIfscIban())) {
            throw new DuplicateResourceException("Account", "accountNumber/ifscIban",
                    request.getAccountNumber() + "/" + request.getIfscIban());
        }
        // Duplicate account number across the directory (defensive — catches typos where IFSC varies)
        if (request.getAccountNumber() != null &&
                accountDirectoryRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new DuplicateResourceException("Account", "accountNumber", request.getAccountNumber());
        }
        // Duplicate VPA / UPI ID — globally unique
        if (request.getVpaUpiId() != null && !request.getVpaUpiId().isBlank() &&
                accountDirectoryRepository.existsByVpaUpiId(request.getVpaUpiId())) {
            throw new DuplicateResourceException("Account", "vpaUpiId", request.getVpaUpiId());
        }
        // Duplicate phone alias
        if (request.getPhone() != null && !request.getPhone().isBlank() &&
                accountDirectoryRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("Account", "phone", request.getPhone());
        }
        // Duplicate email alias
        if (request.getEmail() != null && !request.getEmail().isBlank() &&
                accountDirectoryRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Account", "email", request.getEmail());
        }

        Party party = partyRepository.findActiveById(request.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", request.getPartyId()));

        AccountDirectory account = accountMapper.toEntity(request);
        account.setParty(party);
        account.setActive(true);
        account = accountDirectoryRepository.save(account);
        log.info("Created account with id: {} for party: {}", account.getId(), party.getId());

        // Seed an opening balance so customers can transact immediately on a new INR account.
        seedOpeningBalance(account.getId(), account.getCurrency());

        sendNotificationSafe(actingUser,
                "Account added",
                "Your account " + account.getAccountNumber() + " (" + account.getIfscIban() + ") has been successfully added.",
                "INFO",
                account.getId());

        return accountMapper.toResponse(account);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, AccountRequest request) {
        return updateAccount(id, request, null);
    }

    @Transactional
    public AccountResponse updateAccount(Long id, AccountRequest request, String actingUser) {
        AccountDirectory account = accountDirectoryRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));

        // IFSC / IBAN format
        validateIfscIban(request.getIfscIban());

        // Duplicate checks that exclude the current row
        if (request.getAccountNumber() != null &&
                accountDirectoryRepository.existsByAccountNumberAndIdNot(request.getAccountNumber(), id)) {
            throw new DuplicateResourceException("Account", "accountNumber", request.getAccountNumber());
        }
        if (request.getVpaUpiId() != null && !request.getVpaUpiId().isBlank() &&
                accountDirectoryRepository.existsByVpaUpiIdAndIdNot(request.getVpaUpiId(), id)) {
            throw new DuplicateResourceException("Account", "vpaUpiId", request.getVpaUpiId());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() &&
                accountDirectoryRepository.existsByPhoneAndIdNot(request.getPhone(), id)) {
            throw new DuplicateResourceException("Account", "phone", request.getPhone());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank() &&
                accountDirectoryRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateResourceException("Account", "email", request.getEmail());
        }

        Party party = partyRepository.findActiveById(request.getPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Party", "id", request.getPartyId()));

        accountMapper.updateEntity(request, account);
        account.setParty(party);
        if (request.getActive() != null) {
            account.setActive(request.getActive());
        }
        account = accountDirectoryRepository.save(account);
        log.info("Updated account with id: {}", account.getId());

        sendNotificationSafe(actingUser,
                "Account updated",
                "Your account " + account.getAccountNumber() + " has been updated.",
                "INFO",
                account.getId());

        return accountMapper.toResponse(account);
    }

    @Transactional
    public void deleteAccount(Long id) {
        deleteAccount(id, null);
    }

    @Transactional
    public void deleteAccount(Long id, String actingUser) {
        AccountDirectory account = accountDirectoryRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));

        account.setDeletedAt(LocalDateTime.now());
        account.setActive(false);
        accountDirectoryRepository.save(account);
        log.info("Soft-deleted account with id: {}", id);

        sendNotificationSafe(actingUser,
                "Account removed",
                "Your account " + account.getAccountNumber() + " has been removed.",
                "WARN",
                account.getId());
    }

    public AccountValidationResponse validateAccount(String accountNumber, String ifscIban) {
        return accountDirectoryRepository.findByAccountNumberAndIfscIban(accountNumber, ifscIban)
                .map(account -> AccountValidationResponse.builder()
                        .exists(true)
                        .active(account.isActive())
                        .currency(account.getCurrency())
                        .partyName(account.getParty().getName())
                        .accountId(account.getId())
                        .build())
                .orElse(AccountValidationResponse.builder()
                        .exists(false)
                        .active(false)
                        .build());
    }

    public AccountValidationResponse validateById(Long accountId) {
        return accountDirectoryRepository.findById(accountId)
                .map(account -> AccountValidationResponse.builder()
                        .exists(true)
                        .active(account.isActive())
                        .currency(account.getCurrency())
                        .partyName(account.getParty() != null ? account.getParty().getName() : null)
                        .partyId(account.getParty() != null ? account.getParty().getId() : null)
                        .accountId(account.getId())
                        .build())
                .orElse(AccountValidationResponse.builder()
                        .exists(false)
                        .active(false)
                        .build());
    }

    public AccountResponse findByAlias(String alias) {
        AccountDirectory account = accountDirectoryRepository.findByAlias(alias)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "alias", alias));
        return accountMapper.toResponse(account);
    }

    /**
     * Resolve an account by a typed alias (VPA / PHONE / EMAIL / NAME).
     * Implements the §2.5 Account Directory alias routing requirement.
     */
    public AccountResponse resolveByAlias(String aliasType, String value) {
        if (aliasType == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("aliasType and value are required");
        }
        String type = aliasType.trim().toUpperCase();
        AccountDirectory account = switch (type) {
            case "VPA", "UPI" -> accountDirectoryRepository.findByVpaUpiId(value)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "vpa", value));
            case "PHONE", "MSISDN" -> accountDirectoryRepository.findByPhone(value)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "phone", value));
            case "EMAIL" -> accountDirectoryRepository.findByEmail(value)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "email", value));
            case "NAME", "ALIAS" -> accountDirectoryRepository.findByAlias(value)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "alias", value));
            default -> throw new IllegalArgumentException(
                    "Unsupported aliasType: " + aliasType + " (expected VPA | PHONE | EMAIL | NAME)");
        };
        return accountMapper.toResponse(account);
    }
}
