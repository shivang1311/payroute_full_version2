package com.payroute.ledger.service;

import com.payroute.ledger.dto.request.LedgerPostRequest;
import com.payroute.ledger.dto.response.AccountSummaryResponse;
import com.payroute.ledger.dto.response.LedgerEntryResponse;
import com.payroute.ledger.dto.response.PagedResponse;
import com.payroute.ledger.entity.EntryType;
import com.payroute.ledger.entity.LedgerEntry;
import com.payroute.ledger.entity.RailType;
import com.payroute.ledger.exception.ResourceNotFoundException;
import com.payroute.ledger.mapper.LedgerEntryMapper;
import com.payroute.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerEntryMapper ledgerEntryMapper;
    private final FeeService feeService;

    /**
     * Post a single ledger entry with SERIALIZABLE isolation to prevent
     * concurrent balance inconsistencies.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public LedgerEntryResponse postEntry(LedgerPostRequest request) {
        LedgerEntry entry = ledgerEntryMapper.toEntity(request);
        entry.setEntryDate(LocalDate.now());
        entry.setCreatedBy("SYSTEM");

        LedgerEntry saved = ledgerEntryRepository.save(entry);
        log.info("Posted ledger entry id={}, paymentId={}, type={}, amount={}",
                saved.getId(), saved.getPaymentId(), saved.getEntryType(), saved.getAmount());
        return ledgerEntryMapper.toResponse(saved);
    }

    /**
     * Post the full set of payment entries: DEBIT for debtor, CREDIT for creditor,
     * and FEE entry computed via FeeService. All within a single SERIALIZABLE transaction.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<LedgerEntryResponse> postPaymentEntries(Long paymentId, Long debtorAccountId,
                                                         Long creditorAccountId, BigDecimal amount,
                                                         String currency, RailType rail) {
        return postPaymentEntries(paymentId, debtorAccountId, creditorAccountId, amount, currency, rail, null);
    }

    /**
     * Same as the 6-arg form but accepts a {@code paymentMethod} hint.
     * When the caller's payment method is UPI, the fee leg is skipped — UPI
     * transactions are zero-fee per product policy.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<LedgerEntryResponse> postPaymentEntries(Long paymentId, Long debtorAccountId,
                                                         Long creditorAccountId, BigDecimal amount,
                                                         String currency, RailType rail,
                                                         String paymentMethod) {
        List<LedgerEntry> entries = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // DEBIT entry for debtor
        LedgerEntry debitEntry = LedgerEntry.builder()
                .paymentId(paymentId)
                .accountId(debtorAccountId)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .narrative("Payment debit - PaymentId: " + paymentId)
                .entryDate(today)
                .createdBy("SYSTEM")
                .build();
        entries.add(ledgerEntryRepository.save(debitEntry));

        // CREDIT entry for creditor
        LedgerEntry creditEntry = LedgerEntry.builder()
                .paymentId(paymentId)
                .accountId(creditorAccountId)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .narrative("Payment credit - PaymentId: " + paymentId)
                .entryDate(today)
                .createdBy("SYSTEM")
                .build();
        entries.add(ledgerEntryRepository.save(creditEntry));

        // UPI is zero-fee per product policy — skip the FEE leg entirely
        if ("UPI".equalsIgnoreCase(paymentMethod)) {
            log.info("Skipping fee entry for UPI payment id={}", paymentId);
            log.info("Posted {} ledger entries for paymentId={}", entries.size(), paymentId);
            return ledgerEntryMapper.toResponseList(entries);
        }

        // Compute and post FEE entry (charged to debtor)
        try {
            BigDecimal fee = feeService.computeFee("PAYMENT", rail, amount, today);
            if (fee.compareTo(BigDecimal.ZERO) > 0) {
                LedgerEntry feeEntry = LedgerEntry.builder()
                        .paymentId(paymentId)
                        .accountId(debtorAccountId)
                        .entryType(EntryType.FEE)
                        .amount(fee)
                        .currency(currency)
                        .narrative("Processing fee - Rail: " + rail + ", PaymentId: " + paymentId)
                        .entryDate(today)
                        .createdBy("SYSTEM")
                        .build();
                entries.add(ledgerEntryRepository.save(feeEntry));
            }
        } catch (ResourceNotFoundException ex) {
            log.warn("No fee schedule found for payment {}. Skipping fee entry. Reason: {}",
                    paymentId, ex.getMessage());
        }

        log.info("Posted {} ledger entries for paymentId={}", entries.size(), paymentId);
        return ledgerEntryMapper.toResponseList(entries);
    }

    /**
     * Reverse all entries for a given payment by creating mirror REVERSAL entries.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<LedgerEntryResponse> reversePayment(Long paymentId) {
        List<LedgerEntry> originalEntries = ledgerEntryRepository.findByPaymentId(paymentId);
        if (originalEntries.isEmpty()) {
            throw new ResourceNotFoundException("LedgerEntry", "paymentId", paymentId);
        }

        // Check if already reversed
        boolean alreadyReversed = originalEntries.stream()
                .anyMatch(e -> e.getEntryType() == EntryType.REVERSAL);
        if (alreadyReversed) {
            throw new IllegalStateException("Payment " + paymentId + " has already been reversed");
        }

        List<LedgerEntry> reversalEntries = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (LedgerEntry original : originalEntries) {
            LedgerEntry reversal = LedgerEntry.builder()
                    .paymentId(paymentId)
                    .accountId(original.getAccountId())
                    .entryType(EntryType.REVERSAL)
                    .amount(original.getAmount())
                    .currency(original.getCurrency())
                    .narrative("Reversal of " + original.getEntryType() + " entry (id=" + original.getId() + ")")
                    .entryDate(today)
                    .createdBy("SYSTEM")
                    .build();
            reversalEntries.add(ledgerEntryRepository.save(reversal));
        }

        log.info("Reversed {} entries for paymentId={}", reversalEntries.size(), paymentId);
        return ledgerEntryMapper.toResponseList(reversalEntries);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getEntriesByPayment(Long paymentId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByPaymentId(paymentId);
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("LedgerEntry", "paymentId", paymentId);
        }
        return ledgerEntryMapper.toResponseList(entries);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LedgerEntryResponse> getEntriesByAccount(Long accountId, Pageable pageable) {
        Page<LedgerEntry> page = ledgerEntryRepository.findByAccountId(accountId, pageable);
        List<LedgerEntryResponse> content = ledgerEntryMapper.toResponseList(page.getContent());
        return PagedResponse.of(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public LedgerEntryResponse getEntryById(Long id) {
        LedgerEntry entry = ledgerEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LedgerEntry", "id", id));
        return ledgerEntryMapper.toResponse(entry);
    }

    @Transactional(readOnly = true)
    public PagedResponse<LedgerEntryResponse> getEntries(Long paymentId, Long accountId,
                                                          LocalDate startDate, LocalDate endDate,
                                                          Pageable pageable) {
        Page<LedgerEntry> page;

        if (paymentId != null && accountId != null) {
            page = ledgerEntryRepository.findByPaymentIdAndAccountId(paymentId, accountId, pageable);
        } else if (paymentId != null) {
            page = ledgerEntryRepository.findByPaymentId(paymentId, pageable);
        } else if (accountId != null) {
            page = ledgerEntryRepository.findByAccountId(accountId, pageable);
        } else if (startDate != null && endDate != null) {
            page = ledgerEntryRepository.findByEntryDateBetween(startDate, endDate, pageable);
        } else {
            page = ledgerEntryRepository.findAll(pageable);
        }

        List<LedgerEntryResponse> content = ledgerEntryMapper.toResponseList(page.getContent());
        return PagedResponse.of(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Aggregate debits, credits, and fees for an account to produce a summary.
     */
    @Transactional(readOnly = true)
    public AccountSummaryResponse getAccountSummary(Long accountId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(accountId);
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("LedgerEntry", "accountId", accountId);
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        String currency = entries.get(0).getCurrency();

        for (LedgerEntry entry : entries) {
            switch (entry.getEntryType()) {
                case DEBIT -> totalDebits = totalDebits.add(entry.getAmount());
                case CREDIT -> totalCredits = totalCredits.add(entry.getAmount());
                case FEE -> totalFees = totalFees.add(entry.getAmount());
                case REVERSAL -> {
                    // Reversals offset the original; treat as credit for summary
                    totalCredits = totalCredits.add(entry.getAmount());
                }
                case TAX -> totalFees = totalFees.add(entry.getAmount());
            }
        }

        BigDecimal netBalance = totalCredits.subtract(totalDebits).subtract(totalFees);

        return AccountSummaryResponse.builder()
                .accountId(accountId)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .totalFees(totalFees)
                .netBalance(netBalance)
                .currency(currency)
                .entryCount((long) entries.size())
                .build();
    }
}
