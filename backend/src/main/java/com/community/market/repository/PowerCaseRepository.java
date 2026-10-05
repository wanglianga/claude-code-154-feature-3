package com.community.market.repository;

import com.community.market.domain.entity.PowerCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerCaseRepository extends JpaRepository<PowerCase, Long> {
    List<PowerCase> findByEventIdOrderByIdDesc(Long eventId);
}
