package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.*;
import com.community.market.domain.enums.*;
import com.community.market.dto.PowerDtos;
import com.community.market.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 用电超载/跳闸处置：
 * 摊主申报用电 + 现场设备照片 + 电工处理记录三方比对判定责任；
 * 可下达停用设备/调摊/增加电箱措施；停电波及其他摊主时登记补偿（进入活动复盘）；
 * 整改后电工复核线路负载，未复核通过前责任摊位大功率设备一律封锁。
 */
@Service
@RequiredArgsConstructor
public class PowerService {

    /** 封锁期间禁止使用的大功率设备功率门槛（瓦）：照明、手机充电等小功率不受影响 */
    public static final int HIGH_POWER_WATT = 500;

    private final PowerCaseRepository caseRepository;
    private final PowerCasePartyRepository partyRepository;
    private final PowerActionRepository actionRepository;
    private final PowerCompensationRepository compensationRepository;
    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final EventRepository eventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentService incidentService;
    private final CreditService creditService;
    private final ApplicationService applicationService;

    // ==================== 登记跳闸工单 ====================

    @Transactional
    public PowerCase reportCase(Long staffId, PowerDtos.CreateCaseRequest req) {
        eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("活动不存在"));

        PowerCase c = new PowerCase();
        c.setEventId(req.eventId());
        c.setBoxCode(req.boxCode());
        c.setTitle(req.title());
        c.setDescription(req.description());
        c.setDevicePhotoUrls(req.devicePhotoUrls());
        c.setBoxCapacityWatt(req.boxCapacityWatt());
        c.setTotalDeclaredWatt(req.totalDeclaredWatt());
        c.setTotalActualWatt(req.totalActualWatt());
        c.setTriggeredByApplicationId(req.triggeredByApplicationId());
        c.setStatus(PowerCaseStatus.REPORTED);
        c.setRaisedBy(staffId);
        caseRepository.save(c);

        // 同步进入现场异常档案（复核通过后自动闭环）
        StringBuilder desc = new StringBuilder("临时电箱跳闸处置工单 #" + c.getId() + "：" + req.title());
        if (req.boxCode() != null) {
            desc.append("（电箱 ").append(req.boxCode()).append("）");
        }
        if (req.boxCapacityWatt() != null) {
            desc.append("，电箱核定 ").append(req.boxCapacityWatt()).append("W");
        }
        if (req.totalActualWatt() != null) {
            desc.append("，现场实测合计 ").append(req.totalActualWatt()).append("W");
        }
        desc.append("。已收集摊主申报用电、现场设备照片与电工处理记录用于责任判定。");
        Incident inc = incidentService.raise(req.eventId(), req.triggeredByApplicationId(),
                IncidentType.POWER_OVERLOAD, desc.toString(), 3, staffId);
        c.setIncidentId(inc.getId());

        // 涉及摊位：快照申报功率；初步认定有责任的摊位先封锁大功率设备
        if (req.parties() != null) {
            for (PowerDtos.PartyItem p : req.parties()) {
                Application a = applicationRepository.findById(p.applicationId())
                        .orElseThrow(() -> new BizException("涉及摊位报名不存在：" + p.applicationId()));
                if (!Objects.equals(a.getEventId(), req.eventId())) {
                    throw new BizException("涉及摊位不属于该活动");
                }
                PowerCaseParty party = new PowerCaseParty();
                party.setCaseId(c.getId());
                party.setApplicationId(a.getId());
                party.setShopName(a.getShopName());
                party.setDeclaredWatt(p.declaredWatt() != null ? p.declaredWatt() : a.getPowerWanted());
                party.setActualWatt(p.actualWatt());
                party.setLiable(p.liable());
                party.setAffectedByOutage(p.affectedByOutage());
                party.setNote(p.note());
                partyRepository.save(party);
                if (p.liable()) {
                    lockApplication(a, c, "跳闸工单 #" + c.getId() + " 初步认定超载，电工复核前禁止使用大功率设备");
                }
            }
        }
        return caseRepository.save(c);
    }

    // ==================== 责任判定 ====================

    @Transactional
    public PowerCase judgeLiability(Long caseId, PowerDtos.LiabilityRequest req) {
        PowerCase c = must(caseId);
        c.setLiability(req.liability());
        c.setLiabilityNote(req.liabilityNote());
        if (c.getStatus() == PowerCaseStatus.REPORTED) {
            c.setStatus(PowerCaseStatus.HANDLING);
        }
        touch(c);

        // 责任摊主信用扣分（只扣一次）
        for (PowerCaseParty p : partyRepository.findByCaseIdOrderByIdAsc(caseId)) {
            Application a = applicationRepository.findById(p.getApplicationId()).orElse(null);
            if (a == null) {
                continue;
            }
            int delta = c.getLiability() == PowerLiability.JOINT_OVERLOAD ? -5 : -8;
            if (p.isLiable() && !p.isCreditDeducted()) {
                creditService.adjust(a.getVendorId(), c.getEventId(), a.getId(), delta,
                        "用电跳闸责任认定：" + c.getLiability().getLabel());
                p.setCreditDeducted(true);
                partyRepository.save(p);
                lockApplication(a, c, "跳闸工单 #" + c.getId() + " 责任认定："
                        + c.getLiability().getLabel() + "，复核前禁止使用大功率设备");
            }
            if (!p.isLiable() && p.isCreditDeducted()) {
                // 允许改判：已扣分但改判无责，按原扣分值返还分数并解除本工单的封锁
                creditService.adjust(a.getVendorId(), c.getEventId(), a.getId(), -delta,
                        "跳闸工单 #" + c.getId() + " 改判无责，返还用电扣分");
                p.setCreditDeducted(false);
                partyRepository.save(p);
                unlockIfOwnedByCase(a, c);
            }
        }
        appendIncidentResolution(c, "责任判定：" + req.liability().getLabel()
                + (req.liabilityNote() == null || req.liabilityNote().isBlank() ? "" : "；" + req.liabilityNote()));
        return caseRepository.save(c);
    }

    /** 更新涉及摊位的实测功率/责任/受影响标记（依据现场照片与电工记录） */
    @Transactional
    public PowerCaseParty updateParty(Long partyId, PowerDtos.PartyItem req) {
        PowerCaseParty p = partyRepository.findById(partyId).orElseThrow(() -> new BizException("涉及摊位记录不存在"));
        p.setDeclaredWatt(req.declaredWatt());
        p.setActualWatt(req.actualWatt());
        p.setLiable(req.liable());
        p.setAffectedByOutage(req.affectedByOutage());
        p.setNote(req.note());
        return partyRepository.save(p);
    }

    // ==================== 电工处理 ====================

    @Transactional
    public PowerCase recordElectrician(Long caseId, PowerDtos.ElectricianRequest req) {
        PowerCase c = must(caseId);
        c.setElectricianName(req.electricianName());
        c.setElectricianRecord(req.electricianRecord());
        if (req.totalActualWatt() != null) {
            c.setTotalActualWatt(req.totalActualWatt());
        }
        if (c.getStatus() == PowerCaseStatus.REPORTED) {
            c.setStatus(PowerCaseStatus.HANDLING);
        }
        touch(c);
        return caseRepository.save(c);
    }

    // ==================== 处置措施 ====================

    @Transactional
    public PowerAction addAction(Long operatorId, Long caseId, PowerDtos.ActionRequest req) {
        PowerCase c = must(caseId);
        Application a = req.applicationId() == null ? null
                : applicationRepository.findById(req.applicationId()).orElseThrow(() -> new BizException("摊位报名不存在"));

        PowerAction act = new PowerAction();
        act.setCaseId(caseId);
        act.setType(req.type());
        act.setApplicationId(req.applicationId());
        act.setShopName(a == null ? null : a.getShopName());
        act.setDetail(req.detail());
        act.setOperatorId(operatorId);

        switch (req.type()) {
            case DISABLE_DEVICE -> {
                if (a == null) {
                    throw new BizException("停用设备措施必须指定摊位");
                }
                act.setExecuted(true);
                lockApplication(a, c, "工单 #" + c.getId() + " 要求停用部分大功率设备，电工复核前不得启用");
            }
            case RELOCATE -> {
                if (a == null || req.targetStallId() == null) {
                    throw new BizException("调整摊位必须指定摊位与目标摊位");
                }
                Stall target = stallRepository.findById(req.targetStallId())
                        .orElseThrow(() -> new BizException("目标摊位不存在"));
                applicationService.reassign(a.getId(), target.getId());
                act.setTargetStallId(target.getId());
                act.setDetail((req.detail() == null || req.detail().isBlank()
                        ? "调整至 " + target.getZone() + " " + target.getCode() : req.detail())
                        + "（原摊" + (a.getStallId() == null ? "?" : stallCode(a.getStallId())) + "）");
                act.setExecuted(true);
            }
            case ADD_BOX -> {
                if (req.addedCapacityWatt() == null || req.addedCapacityWatt() <= 0) {
                    throw new BizException("增加电箱需填写新增容量（瓦）");
                }
                act.setAddedCapacityWatt(req.addedCapacityWatt());
                act.setExecuted(true);
                int base = c.getBoxCapacityWatt() == null ? 0 : c.getBoxCapacityWatt();
                c.setBoxCapacityWatt(base + req.addedCapacityWatt());
            }
        }
        actionRepository.save(act);
        c.setStatus(PowerCaseStatus.HANDLING);
        touch(c);
        caseRepository.save(c);
        return act;
    }

    // ==================== 停电补偿 ====================

    @Transactional
    public PowerCompensation addCompensation(Long operatorId, Long caseId, PowerDtos.CompensationRequest req) {
        PowerCase c = must(caseId);
        Application a = applicationRepository.findById(req.applicationId())
                .orElseThrow(() -> new BizException("受影响摊位不存在"));
        if (req.amount().signum() < 0) {
            throw new BizException("补偿金额不能为负");
        }
        PowerCompensation comp = new PowerCompensation();
        comp.setCaseId(caseId);
        comp.setApplicationId(a.getId());
        comp.setShopName(a.getShopName());
        comp.setAmount(req.amount());
        comp.setReason(req.reason());
        comp.setOutageMinutes(req.outageMinutes());
        comp.setStatus("PLANNED");
        comp.setOperatorId(operatorId);
        compensationRepository.save(comp);

        // 受停电波及的摊位在 party 中标记，便于档案归集
        PowerCaseParty party = partyRepository.findByCaseIdOrderByIdAsc(caseId).stream()
                .filter(x -> Objects.equals(x.getApplicationId(), a.getId())).findFirst().orElseGet(() -> {
                    PowerCaseParty p = new PowerCaseParty();
                    p.setCaseId(caseId);
                    p.setApplicationId(a.getId());
                    p.setShopName(a.getShopName());
                    p.setDeclaredWatt(a.getPowerWanted());
                    return p;
                });
        party.setAffectedByOutage(true);
        if (party.getNote() == null) {
            party.setNote("停电波及，登记补偿");
        }
        partyRepository.save(party);

        appendIncidentResolution(c, "受停电波及摊主 " + a.getShopName()
                + " 登记补偿 " + req.amount() + " 元，责任说明与补偿进入活动复盘");
        return comp;
    }

    @Transactional
    public PowerCompensation payCompensation(Long compId) {
        PowerCompensation comp = compensationRepository.findById(compId)
                .orElseThrow(() -> new BizException("补偿记录不存在"));
        comp.setStatus("PAID");
        comp.setPaidAt(LocalDateTime.now());
        return compensationRepository.save(comp);
    }

    // ==================== 整改提交 + 电工复核 ====================

    @Transactional
    public PowerCase submitRectification(Long caseId) {
        PowerCase c = must(caseId);
        if (c.getElectricianName() == null || c.getElectricianRecord() == null) {
            throw new BizException("请先登记电工到场处理记录，再提交整改复核");
        }
        if (actionRepository.findByCaseIdOrderByIdAsc(caseId).isEmpty()) {
            throw new BizException("尚未下达任何处置措施（停用设备/调摊/增加电箱），不能提交整改");
        }
        c.setStatus(PowerCaseStatus.PENDING_REVIEW);
        touch(c);
        return caseRepository.save(c);
    }

    /**
     * 电工复核线路负载：实测负载不得超过（增容后的）电箱容量；
     * 通过后工单闭环、同步关闭异常档案、解除相关摊位大功率设备封锁。
     */
    @Transactional
    public PowerCase review(Long reviewerId, Long caseId, PowerDtos.ReviewRequest req) {
        PowerCase c = must(caseId);
        if (c.getStatus() != PowerCaseStatus.PENDING_REVIEW) {
            throw new BizException("仅「整改完成待复核」的工单可以执行电工复核");
        }
        int cap = c.getBoxCapacityWatt() == null ? 0 : c.getBoxCapacityWatt();
        if (cap > 0 && req.reviewLoadWatt() > cap) {
            throw new BizException("复核未通过：实测负载 " + req.reviewLoadWatt() + "W 仍超过电箱容量 " + cap
                    + "W，请继续整改（减设备/调摊/增容）后重新提交");
        }
        int ratio = cap <= 0 ? 0 : (int) Math.round(req.reviewLoadWatt() * 100.0 / cap);
        c.setReviewLoadWatt(req.reviewLoadWatt());
        c.setReviewLoadRatio(ratio);
        c.setReviewComment(req.reviewComment());
        c.setReviewedBy(reviewerId);
        c.setReviewedAt(LocalDateTime.now());
        c.setStatus(PowerCaseStatus.REVIEWED);
        touch(c);
        caseRepository.save(c);

        // 解除封锁：本工单锁定过的摊位（含改判后无责的）均可恢复使用大功率设备
        for (PowerCaseParty p : partyRepository.findByCaseIdOrderByIdAsc(caseId)) {
            applicationRepository.findById(p.getApplicationId()).ifPresent(a -> unlockIfOwnedByCase(a, c));
        }

        // 闭环异常档案
        if (c.getIncidentId() != null) {
            incidentRepository.findById(c.getIncidentId()).ifPresent(inc -> {
                if (inc.getStatus() != IncidentStatus.RESOLVED) {
                    inc.setStatus(IncidentStatus.RESOLVED);
                    inc.setResolvedBy(reviewerId);
                    inc.setResolvedAt(LocalDateTime.now());
                    inc.setResolution(buildClosureSummary(c));
                    incidentRepository.save(inc);
                }
            });
        }
        return c;
    }

    // ==================== 查询 ====================

    @Transactional
    public PowerCase appendPhotos(Long caseId, List<String> urls) {
        PowerCase c = must(caseId);
        Set<String> all = new LinkedHashSet<>();
        if (c.getDevicePhotoUrls() != null && !c.getDevicePhotoUrls().isBlank()) {
            all.addAll(Arrays.asList(c.getDevicePhotoUrls().split(",")));
        }
        if (urls != null) {
            all.addAll(urls.stream().filter(u -> u != null && !u.isBlank()).toList());
        }
        c.setDevicePhotoUrls(String.join(",", all));
        touch(c);
        return caseRepository.save(c);
    }

    public List<PowerCase> list(Long eventId) {
        return caseRepository.findByEventIdOrderByIdDesc(eventId);
    }

    public Map<String, Object> detail(Long caseId) {
        PowerCase c = must(caseId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("powerCase", c);
        m.put("parties", partyRepository.findByCaseIdOrderByIdAsc(caseId));
        m.put("actions", actionRepository.findByCaseIdOrderByIdAsc(caseId));
        m.put("compensations", compensationRepository.findByCaseIdOrderByIdAsc(caseId));
        m.put("incident", c.getIncidentId() == null ? null
                : incidentRepository.findById(c.getIncidentId()).orElse(null));
        BigDecimal compTotal = compensationRepository.findByCaseIdOrderByIdAsc(caseId).stream()
                .map(PowerCompensation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        m.put("compensationTotal", compTotal);
        return m;
    }

    // ==================== 辅助 ====================

    private PowerCase must(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new BizException("用电处置工单不存在"));
    }

    private void touch(PowerCase c) {
        c.setUpdatedAt(LocalDateTime.now());
    }

    private void lockApplication(Application a, PowerCase c, String reason) {
        if (!a.isPowerLocked()) {
            a.setPowerLocked(true);
            a.setPowerLockedAt(LocalDateTime.now());
        }
        a.setPowerLockReason(reason);
        applicationRepository.save(a);
    }

    /** 仅当摊位封锁由本工单设置时才解除（避免误解其他未闭环工单的封锁） */
    private void unlockIfOwnedByCase(Application a, PowerCase c) {
        if (a.isPowerLocked() && a.getPowerLockReason() != null
                && a.getPowerLockReason().contains("工单 #" + c.getId())) {
            a.setPowerLocked(false);
            a.setPowerLockReason(null);
            a.setPowerLockedAt(null);
            applicationRepository.save(a);
        }
    }

    private String stallCode(Long stallId) {
        return stallRepository.findById(stallId).map(Stall::getCode).orElse("?");
    }

    /** 复核结论追加到异常处理记录（不覆盖，最终闭环时写 resolution） */
    private void appendIncidentResolution(PowerCase c, String text) {
        if (c.getIncidentId() == null) {
            return;
        }
        incidentRepository.findById(c.getIncidentId()).ifPresent(inc -> {
            String prev = inc.getResolution() == null ? "" : inc.getResolution() + "\n";
            inc.setResolution(prev + "【" + LocalDateTime.now().toLocalDate() + "】" + text);
            incidentRepository.save(inc);
        });
    }

    private String buildClosureSummary(PowerCase c) {
        StringBuilder sb = new StringBuilder();
        sb.append("电工复核通过，工单闭环：实测负载 ").append(c.getReviewLoadWatt()).append("W");
        if (c.getBoxCapacityWatt() != null && c.getBoxCapacityWatt() > 0) {
            sb.append("，负载率 ").append(c.getReviewLoadRatio()).append("%（容量 ").append(c.getBoxCapacityWatt()).append("W）");
        }
        sb.append("。责任：").append(c.getLiability() == null ? "待定" : c.getLiability().getLabel());
        if (c.getLiabilityNote() != null && !c.getLiabilityNote().isBlank()) {
            sb.append("（").append(c.getLiabilityNote()).append("）");
        }
        List<PowerAction> acts = actionRepository.findByCaseIdOrderByIdAsc(c.getId());
        if (!acts.isEmpty()) {
            sb.append("。措施：");
            sb.append(String.join("；", acts.stream()
                    .map(x -> x.getType().getLabel()
                            + (x.getShopName() == null ? "" : "（" + x.getShopName() + "）")
                            + (x.getDetail() == null ? "" : " " + x.getDetail())).toList()));
        }
        List<PowerCompensation> comps = compensationRepository.findByCaseIdOrderByIdAsc(c.getId());
        if (!comps.isEmpty()) {
            BigDecimal total = comps.stream().map(PowerCompensation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            sb.append("。受影响摊主补偿合计 ").append(total).append(" 元");
        }
        if (c.getReviewComment() != null && !c.getReviewComment().isBlank()) {
            sb.append("。电工备注：").append(c.getReviewComment());
        }
        return sb.toString();
    }
}
