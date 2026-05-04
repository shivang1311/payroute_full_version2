package com.payroute.party.repository;

import com.payroute.party.entity.AccountDirectory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountDirectoryRepository extends JpaRepository<AccountDirectory, Long> {

    @Query("SELECT a FROM AccountDirectory a WHERE a.deletedAt IS NULL")
    Page<AccountDirectory> findAllActive(Pageable pageable);

    @Query("SELECT a FROM AccountDirectory a WHERE a.id = :id AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findActiveById(@Param("id") Long id);

    @Query("SELECT a FROM AccountDirectory a WHERE a.party.id = :partyId AND a.deletedAt IS NULL")
    List<AccountDirectory> findByPartyId(@Param("partyId") Long partyId);

    @Query("SELECT a FROM AccountDirectory a WHERE a.party.id = :partyId AND a.deletedAt IS NULL")
    Page<AccountDirectory> findByPartyIdPaged(@Param("partyId") Long partyId, Pageable pageable);

    @Query("SELECT a FROM AccountDirectory a WHERE a.accountNumber = :accountNumber AND a.ifscIban = :ifscIban AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findByAccountNumberAndIfscIban(@Param("accountNumber") String accountNumber, @Param("ifscIban") String ifscIban);

    @Query("SELECT a FROM AccountDirectory a WHERE a.alias = :alias AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findByAlias(@Param("alias") String alias);

    @Query("SELECT a FROM AccountDirectory a WHERE a.vpaUpiId = :vpa AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findByVpaUpiId(@Param("vpa") String vpa);

    @Query("SELECT a FROM AccountDirectory a WHERE a.phone = :phone AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findByPhone(@Param("phone") String phone);

    @Query("SELECT a FROM AccountDirectory a WHERE a.email = :email AND a.deletedAt IS NULL")
    Optional<AccountDirectory> findByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.accountNumber = :accountNumber AND a.ifscIban = :ifscIban AND a.deletedAt IS NULL")
    boolean existsByAccountNumberAndIfscIban(@Param("accountNumber") String accountNumber, @Param("ifscIban") String ifscIban);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.accountNumber = :accountNumber AND a.deletedAt IS NULL")
    boolean existsByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.vpaUpiId = :vpa AND a.deletedAt IS NULL")
    boolean existsByVpaUpiId(@Param("vpa") String vpa);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.phone = :phone AND a.deletedAt IS NULL")
    boolean existsByPhone(@Param("phone") String phone);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.email = :email AND a.deletedAt IS NULL")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.accountNumber = :accountNumber AND a.deletedAt IS NULL AND a.id <> :id")
    boolean existsByAccountNumberAndIdNot(@Param("accountNumber") String accountNumber, @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.vpaUpiId = :vpa AND a.deletedAt IS NULL AND a.id <> :id")
    boolean existsByVpaUpiIdAndIdNot(@Param("vpa") String vpa, @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.phone = :phone AND a.deletedAt IS NULL AND a.id <> :id")
    boolean existsByPhoneAndIdNot(@Param("phone") String phone, @Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountDirectory a WHERE a.email = :email AND a.deletedAt IS NULL AND a.id <> :id")
    boolean existsByEmailAndIdNot(@Param("email") String email, @Param("id") Long id);
}
