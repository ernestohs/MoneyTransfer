package org.bank.moneytransfer.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.bank.moneytransfer.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByOwnerIdAndPublicId(String ownerId, String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id asc")
    List<Account> lockAllByIdsOrdered(@Param("ids") List<Long> ids);
}
