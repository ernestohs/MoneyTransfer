package org.bank.moneytransfer.repository;

import java.util.Optional;
import org.bank.moneytransfer.domain.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Optional<Transfer> findByOwnerIdAndPublicId(String ownerId, String publicId);
}
