package com.payroute.iam.repository;

import com.payroute.iam.entity.Role;
import com.payroute.iam.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<User> findByRoleAndTransactionPinHashIsNull(Role role);

    /** All active users with the given role — used by notification broadcasts. */
    List<User> findByRoleAndActiveTrue(Role role);
}
