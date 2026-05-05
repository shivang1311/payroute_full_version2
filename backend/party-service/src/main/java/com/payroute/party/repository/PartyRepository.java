package com.payroute.party.repository;

import com.payroute.party.entity.Party;
import com.payroute.party.entity.PartyStatus;
import com.payroute.party.entity.PartyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository over {@link Party}. Soft-delete-aware: queries filter
 * {@code deletedAt IS NULL} so deleted parties are invisible to the API
 * without a hard DELETE.
 */
@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {

    @Query("SELECT p FROM Party p WHERE p.deletedAt IS NULL")
    Page<Party> findAllActive(Pageable pageable);

    @Query("SELECT p FROM Party p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Party> findActiveById(@Param("id") Long id);

    @Query("SELECT p FROM Party p WHERE p.status = :status AND p.deletedAt IS NULL")
    Page<Party> findByStatus(@Param("status") PartyStatus status, Pageable pageable);

    @Query("SELECT p FROM Party p WHERE p.type = :type AND p.deletedAt IS NULL")
    Page<Party> findByType(@Param("type") PartyType type, Pageable pageable);

    @Query("SELECT p FROM Party p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.deletedAt IS NULL")
    Page<Party> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    @Query("SELECT p FROM Party p WHERE p.status = :status AND p.type = :type AND p.deletedAt IS NULL")
    Page<Party> findByStatusAndType(@Param("status") PartyStatus status, @Param("type") PartyType type, Pageable pageable);
}
