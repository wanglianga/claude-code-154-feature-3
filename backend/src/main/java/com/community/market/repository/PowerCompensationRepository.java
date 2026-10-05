package com.community.market.repository;

import com.community.market.domain.entity.PowerCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerCompensationRepository extends JpaRepository<PowerCompensation, Long> {
    List<PowerCompensation> findByCaseIdOrderByIdAsc(Long caseId);
    List<PowerCompensation> findByApplicationIdOrderByIdDesc(Long applicationId);
}
