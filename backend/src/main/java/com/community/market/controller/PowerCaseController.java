package com.community.market.controller;

import com.community.market.common.ApiResult;
import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.PowerCase;
import com.community.market.domain.entity.Stall;
import com.community.market.domain.enums.UserRole;
import com.community.market.dto.PowerDtos;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.StallRepository;
import com.community.market.security.CurrentUser;
import com.community.market.security.TokenStore;
import com.community.market.service.PowerCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 用电超载处置：跳闸建档、证据、判责、停用/调摊/加电箱、补偿、电工复核解锁。
 */
@RestController
@RequestMapping("/api/onsite/power-cases")
@RequiredArgsConstructor
public class PowerCaseController {

    private final PowerCaseService powerCaseService;
    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final TokenStore tokenStore;

    @PostMapping
    public ApiResult<Object> report(@Valid @RequestBody PowerDtos.PowerCaseRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        PowerCase pc = powerCaseService.report(CurrentUser.userId(), req);
        return ApiResult.ok(view(pc));
    }

    @GetMapping("/events/{eventId}")
    public ApiResult<List<Map<String, Object>>> list(@PathVariable Long eventId) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerCaseService.list(eventId).stream().map(this::view).toList());
    }

    @GetMapping("/{id}")
    public ApiResult<Object> detail(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(view(powerCaseService.detail(id)));
    }

    @PutMapping("/{id}/evidence")
    public ApiResult<Object> evidence(@PathVariable Long id,
                                      @Valid @RequestBody PowerDtos.EvidenceRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(view(powerCaseService.updateEvidence(id, req)));
    }

    @PostMapping("/{id}/judge")
    public ApiResult<Object> judge(@PathVariable Long id, @Valid @RequestBody PowerDtos.JudgeRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(view(powerCaseService.judge(CurrentUser.userId(), id, req)));
    }

    @PostMapping("/{id}/dispose")
    public ApiResult<Object> dispose(@PathVariable Long id, @Valid @RequestBody PowerDtos.DisposeRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(view(powerCaseService.dispose(CurrentUser.userId(), id, req)));
    }

    @PostMapping("/{id}/electrician-review")
    public ApiResult<Object> review(@PathVariable Long id, @Valid @RequestBody PowerDtos.ReviewRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(view(powerCaseService.electricianReview(CurrentUser.userId(), id, req)));
    }

    // ---------- 视图组装：把报名/摊位 ID 解析为可读信息 ----------

    private Map<String, Object> view(PowerCase pc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", pc.getId());
        m.put("eventId", pc.getEventId());
        m.put("incidentId", pc.getIncidentId());
        m.put("boxCode", pc.getBoxCode());
        m.put("boxCapacityWatt", pc.getBoxCapacityWatt());
        m.put("trippedLoadWatt", pc.getTrippedLoadWatt());
        m.put("description", pc.getDescription());
        m.put("devicePhotoUrls", pc.getDevicePhotoUrls() == null ? List.of()
                : Arrays.stream(pc.getDevicePhotoUrls().split(",")).filter(s -> !s.isBlank()).toList());
        m.put("electricianRecord", pc.getElectricianRecord());
        m.put("electricianName", pc.getElectricianName());
        m.put("status", pc.getStatus().name());
        m.put("statusLabel", pc.getStatus().getLabel());
        m.put("involved", pc.getInvolvedApplicationIds().stream().map(this::stallBrief).filter(Objects::nonNull).toList());
        m.put("affected", pc.getAffectedApplicationIds().stream().map(this::stallBrief).filter(Objects::nonNull).toList());
        m.put("responsibility", pc.getResponsibility() == null ? null : pc.getResponsibility().name());
        m.put("responsibilityLabel", pc.getResponsibility() == null ? null : pc.getResponsibility().getLabel());
        m.put("responsibilityNote", pc.getResponsibilityNote());
        m.put("judgedAt", pc.getJudgedAt());
        m.put("measures", pc.getMeasures());
        m.put("measureNote", pc.getMeasureNote());
        m.put("moveToStallId", pc.getMoveToStallId());
        if (pc.getMoveToStallId() != null) {
            stallRepository.findById(pc.getMoveToStallId())
                    .ifPresent(s -> m.put("moveToStall", s.getZone() + " " + s.getCode()));
        }
        m.put("disposedAt", pc.getDisposedAt());
        m.put("compensations", pc.getCompensations().stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("applicationId", c.getApplicationId());
            x.put("shopName", appName(c.getApplicationId()));
            x.put("amount", c.getAmount());
            x.put("reason", c.getReason());
            return x;
        }).toList());
        m.put("reviewedLoadWatt", pc.getReviewedLoadWatt());
        m.put("reviewNote", pc.getReviewNote());
        m.put("reviewedAt", pc.getReviewedAt());
        m.put("createdAt", pc.getCreatedAt());
        return m;
    }

    private Map<String, Object> stallBrief(Long applicationId) {
        Application a = applicationRepository.findById(applicationId).orElse(null);
        if (a == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("applicationId", a.getId());
        m.put("shopName", a.getShopName());
        m.put("powerWanted", a.getPowerWanted());
        m.put("highPowerLocked", a.isHighPowerLocked());
        m.put("stallId", a.getStallId());
        Stall s = a.getStallId() == null ? null : stallRepository.findById(a.getStallId()).orElse(null);
        m.put("stallCode", s == null ? null : s.getZone() + " " + s.getCode());
        return m;
    }

    private String appName(Long applicationId) {
        return applicationRepository.findById(applicationId).map(Application::getShopName).orElse("#" + applicationId);
    }
}
