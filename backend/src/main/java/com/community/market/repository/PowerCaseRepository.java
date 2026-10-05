package com.community.market.repository;

import com.community.market.domain.entity.PowerCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PowerCaseRepository extends JpaRepository<PowerCase, Long> {
    List<PowerCase> findByEventIdOrderByIdDesc(Long eventId);

    /** 某摊位参与（责任方/受影响方）的未闭环工单 */
    List<PowerCase> findByEventIdAndTriggeredByApplicationIdAndStatusNot(Long eventId, Long applicationId, String status);
}
