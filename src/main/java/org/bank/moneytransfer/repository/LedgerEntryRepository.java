package org.bank.moneytransfer.repository;

import org.bank.moneytransfer.domain.Account;
import org.bank.moneytransfer.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    Page<LedgerEntry> findByAccount(Account account, Pageable pageable);
}
