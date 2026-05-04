package com.payroute.routing.repository;

import com.payroute.routing.entity.RailType;
import com.payroute.routing.entity.SlaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlaConfigRepository extends JpaRepository<SlaConfig, Long> {
    Optional<SlaConfig> findByRail(RailType rail);
    List<SlaConfig> findByActiveTrue();
}
