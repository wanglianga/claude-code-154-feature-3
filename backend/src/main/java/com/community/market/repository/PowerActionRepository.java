package com.community.market.repository;

import com.community.market.domain.entity.PowerAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerActionRepository extends JpaRepository<PowerAction, Long> {
    List<PowerAction> findByCaseIdOrderByIdAsc(Long caseId);
    List<PowerAction> findByApplicationId(Long applicationId);
}
