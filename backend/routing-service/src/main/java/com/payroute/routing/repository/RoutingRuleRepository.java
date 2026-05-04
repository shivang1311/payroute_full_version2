package com.payroute.routing.repository;

import com.payroute.routing.entity.RailType;
import com.payroute.routing.entity.RoutingRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {

    @Query("SELECT r FROM RoutingRule r WHERE r.active = true AND r.deletedAt IS NULL ORDER BY r.priority ASC")
    List<RoutingRule> findByActiveOrderByPriorityAsc();

    @Query("SELECT r FROM RoutingRule r WHERE r.preferredRail = :rail AND r.deletedAt IS NULL")
    List<RoutingRule> findByPreferredRail(RailType rail);

    @Query("SELECT r FROM RoutingRule r WHERE r.deletedAt IS NULL")
    Page<RoutingRule> findAllActive(Pageable pageable);

    @Query("SELECT r FROM RoutingRule r WHERE r.id = :id AND r.deletedAt IS NULL")
    Optional<RoutingRule> findByIdAndNotDeleted(Long id);
}
