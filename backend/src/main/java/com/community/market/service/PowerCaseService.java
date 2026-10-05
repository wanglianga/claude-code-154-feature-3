package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.Incident;
import com.community.market.domain.entity.PowerCase;
import com.community.market.domain.entity.Stall;
import com.community.market.domain.enums.IncidentStatus;
import com.community.market.domain.enums.PowerCaseStatus;
import com.community.market.domain.enums.PowerResponsibility;
import com.community.market.dto.PowerDtos;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.IncidentRepository;
import com.community.market.repository.PowerCaseRepository;
import com.community.market.repository.StallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用电超载处置：跳闸建档 → 证据补录（申报用电/设备照片/电工记录）→ 平台判责 →
 * 下发停用设备/调整摊位/增加电箱（相关摊位锁定大功率设备）→ 停电补偿 →
 * 电工复核线路负载通过后解锁并闭环异常档案。
 */
@Service
@RequiredArgsConstructor
public class PowerCaseService {

    /** 大功率设备认定阈值（瓦）：锁定期间巡检实测达到该值即被拦截 */
    public static final int HIGH_POWER_WATT = 800;

    private final PowerCaseRepository powerCaseRepository;
    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService;
    private final ApplicationService applicationService;
    private final CreditService creditService;

    // ==================== 跳闸建档 ====================

    @Transactional
    public PowerCase report(Long staffId, PowerDtos.PowerCaseRequest req) {
        PowerCase pc = new PowerCase();
        pc.setEventId(req.eventId());
        pc.setBoxCode(req.boxCode());
        pc.setBoxCapacityWatt(req.boxCapacityWatt());
        pc.setTrippedLoadWatt(req.trippedLoadWatt());
        pc.setDescription(req.description());
        pc.setDevicePhotoUrls(joinPhotos(req.devicePhotoUrls()));
        pc.setElectricianRecord(req.electricianRecord());
        pc.setElectricianName(req.electricianName());
        pc.setInvolvedApplicationIds(safe(req.involvedApplicationIds()));
        pc.setAffectedApplicationIds(safe(req.affectedApplicationIds()));
        pc.setStatus(PowerCaseStatus.REPORTED);
        pc.setRaisedBy(staffId);
        powerCaseRepository.save(pc);

        // 同步进入现场异常档案（用电超载），复核通过后自动闭环
        Long firstApp = pc.getInvolvedApplicationIds().stream().findFirst().orElse(null);
        String desc = "临时电箱" + (pc.getBoxCode() == null ? "" : "「" + pc.getBoxCode() + "」")
                + "跳闸：" + req.description()
                + (pc.getTrippedLoadWatt() != null ? "（跳闸时叠加约 " + pc.getTrippedLoadWatt() + "W"
                + (pc.getBoxCapacityWatt() != null ? " / 额定 " + pc.getBoxCapacityWatt() + "W" : "") + "）" : "");
        Incident inc = incidentService.raise(req.eventId(), firstApp,
                com.community.market.domain.enums.IncidentType.POWER_OVERLOAD, desc, 3, staffId);
        pc.setIncidentId(inc.getId());
        return powerCaseRepository.save(pc);
    }

    /** 补录/更新判责证据与涉及、受影响摊位 */
    @Transactional
    public PowerCase updateEvidence(Long id, PowerDtos.EvidenceRequest req) {
        PowerCase pc = mustGet(id);
        if (req.devicePhotoUrls() != null) {
            pc.setDevicePhotoUrls(joinPhotos(req.devicePhotoUrls()));
        }
        if (req.electricianRecord() != null) {
            pc.setElectricianRecord(req.electricianRecord());
        }
        if (req.electricianName() != null) {
            pc.setElectricianName(req.electricianName());
        }
        if (req.involvedApplicationIds() != null) {
            pc.setInvolvedApplicationIds(safe(req.involvedApplicationIds()));
        }
        if (req.affectedApplicationIds() != null) {
            pc.setAffectedApplicationIds(safe(req.affectedApplicationIds()));
        }
        return powerCaseRepository.save(pc);
    }

    // ==================== 判责 ====================

    @Transactional
    public PowerCase judge(Long staffId, Long id, PowerDtos.JudgeRequest req) {
        PowerCase pc = mustGet(id);
        if (pc.getInvolvedApplicationIds().isEmpty()
                && (req.responsibility() == PowerResponsibility.VENDOR_FAULT
                || req.responsibility() == PowerResponsibility.JOINT)) {
            throw new BizException("请先在证据中勾选导致超载的责任摊位，再进行摊主侧判责");
        }
        pc.setResponsibility(req.responsibility());
        pc.setResponsibilityNote(req.responsibilityNote());
        pc.setJudgedBy(staffId);
        pc.setJudgedAt(LocalDateTime.now());
        powerCaseRepository.save(pc);

        // 责任与信用挂钩：单一摊主违规重扣，多摊叠加各摊轻扣；平台/设备/外部原因不扣摊主
        if (req.responsibility() == PowerResponsibility.VENDOR_FAULT) {
            pc.getInvolvedApplicationIds().forEach(aid -> credit(aid, -10, "用电超载致电箱跳闸，判定摊主担责"));
        } else if (req.responsibility() == PowerResponsibility.JOINT) {
            pc.getInvolvedApplicationIds().forEach(aid -> credit(aid, -5, "多摊叠加超载致跳闸，分担责任"));
        }
        return pc;
    }

    private void credit(Long applicationId, int delta, String reason) {
        applicationRepository.findById(applicationId).ifPresent(a ->
                creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), delta, reason));
    }

    // ==================== 处置措施 ====================

    @Transactional
    public PowerCase dispose(Long staffId, Long id, PowerDtos.DisposeRequest req) {
        PowerCase pc = mustGet(id);
        if (pc.getResponsibility() == null) {
            throw new BizException("请先完成责任判定，再下发处置措施");
        }
        if (req.measures() == null || req.measures().isEmpty()) {
            throw new BizException("请至少选择一项处置措施（停用设备/调整摊位/增加电箱）");
        }
        for (String m : req.measures()) {
            if (!m.equals("DEVICE_STOP") && !m.equals("STALL_MOVE") && !m.equals("BOX_ADD")) {
                throw new BizException("处置措施不合法：" + m);
            }
        }

        // 调整摊位：把主要责任摊位改派到目标空闲摊位
        if (req.measures().contains("STALL_MOVE")) {
            if (req.moveToStallId() == null) {
                throw new BizException("选择「调整摊位」时需指定目标摊位");
            }
            Long appId = pc.getInvolvedApplicationIds().stream().findFirst()
                    .orElseThrow(() -> new BizException("没有可调整的责任摊位"));
            Stall target = stallRepository.findById(req.moveToStallId())
                    .orElseThrow(() -> new BizException("目标摊位不存在"));
            if (!Objects.equals(target.getEventId(), pc.getEventId())) {
                throw new BizException("目标摊位与本活动不符");
            }
            applicationService.reassign(appId, req.moveToStallId());
            pc.setMoveToStallId(req.moveToStallId());
        }

        pc.setMeasures(new ArrayList<>(req.measures()));
        pc.setMeasureNote(req.measureNote());
        pc.setDisposedBy(staffId);
        pc.setDisposedAt(LocalDateTime.now());
        pc.setStatus(PowerCaseStatus.DISPOSED);

        // 停电波及补偿登记（进入活动复盘）
        List<PowerCase.Compensation> comps = new ArrayList<>();
        if (req.compensations() != null) {
            for (PowerDtos.CompensationItem c : req.compensations()) {
                if (c.applicationId() == null || c.amount() == null) {
                    continue;
                }
                comps.add(new PowerCase.Compensation(c.applicationId(), c.amount(), c.reason()));
            }
        }
        pc.setCompensations(comps);
        powerCaseRepository.save(pc);

        // 处置即刻锁定相关摊位：电工复核前不得继续使用大功率设备
        for (Long aid : pc.getInvolvedApplicationIds()) {
            applicationRepository.findById(aid).ifPresent(a -> {
                a.setHighPowerLocked(true);
                applicationRepository.save(a);
            });
        }
        return pc;
    }

    // ==================== 电工复核 ====================

    @Transactional
    public PowerCase electricianReview(Long staffId, Long id, PowerDtos.ReviewRequest req) {
        PowerCase pc = mustGet(id);
        if (pc.getStatus() != PowerCaseStatus.DISPOSED) {
            throw new BizException("仅已下发处置措施、待复核的处置单可进行电工复核");
        }
        pc.setReviewedLoadWatt(req.reviewedLoadWatt());
        pc.setReviewNote(req.reviewNote());
        pc.setReviewedBy(staffId);
        pc.setReviewedAt(LocalDateTime.now());
        pc.setStatus(PowerCaseStatus.REVIEWED);
        powerCaseRepository.save(pc);

        // 复核通过：解除相关摊位大功率设备锁定
        for (Long aid : pc.getInvolvedApplicationIds()) {
            applicationRepository.findById(aid).ifPresent(a -> {
                a.setHighPowerLocked(false);
                applicationRepository.save(a);
            });
        }

        // 自动闭环对应的用电超载异常档案
        if (pc.getIncidentId() != null) {
            String note = "电工已复核线路负载（实测 " + req.reviewedLoadWatt() + "W），恢复供电并解除大功率设备限制。"
                    + (req.reviewNote() == null ? "" : req.reviewNote());
            incidentService.resolve(pc.getIncidentId(), note, staffId);
        }
        return pc;
    }

    public List<PowerCase> list(Long eventId) {
        return powerCaseRepository.findByEventIdOrderByIdDesc(eventId);
    }

    public PowerCase detail(Long id) {
        return mustGet(id);
    }

    private PowerCase mustGet(Long id) {
        return powerCaseRepository.findById(id).orElseThrow(() -> new BizException("用电处置单不存在"));
    }

    private List<Long> safe(List<Long> in) {
        return in == null ? new ArrayList<>() : new ArrayList<>(in);
    }

    private String joinPhotos(List<String> urls) {
        if (urls == null) {
            return null;
        }
        return String.join(",", urls.stream().filter(u -> u != null && !u.isBlank()).toList());
    }
}
