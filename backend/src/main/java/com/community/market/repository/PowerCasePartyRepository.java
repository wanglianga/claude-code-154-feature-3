package com.community.market.repository;

import com.community.market.domain.entity.PowerCaseParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerCasePartyRepository extends JpaRepository<PowerCaseParty, Long> {
    List<PowerCaseParty> findByCaseIdOrderByIdAsc(Long caseId);
    List<PowerCaseParty> findByApplicationId(Long applicationId);
}
