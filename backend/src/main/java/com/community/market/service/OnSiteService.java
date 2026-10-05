package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.*;
import com.community.market.domain.enums.*;
import com.community.market.dto.OnSiteDtos;
import com.community.market.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 现场运营：二维码核销、物资清单、巡检/临时换品、投诉、提前离场、客流。
 * 所有现场问题同步进入活动异常档案并影响摊主信用。
 */
@Service
@RequiredArgsConstructor
public class OnSiteService {

    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final EventRepository eventRepository;
    private final InspectionRepository inspectionRepository;
    private final ComplaintRepository complaintRepository;
    private final IncidentService incidentService;
    private final CreditService creditService;
    private final ViewSupport viewSupport;

    /** 扫码核销入场，返回摊主信息与物资领取清单 */
    @Transactional
    public Map<String, Object> checkin(Long staffId, String code) {
        Application a = applicationRepository.findByCheckinCode(code)
                .orElseThrow(() -> new BizException("二维码无效：查不到对应报名"));
        if (a.getStatus() != ApplicationStatus.APPROVED) {
            throw new BizException("该报名未通过审核，不可入场");
        }
        if (a.getStallId() == null) {
            throw new BizException("摊位尚未分配，请联系管理员完成分配后入场");
        }
        if (a.getCheckInStatus() == CheckInStatus.CHECKED_IN) {
            throw new BizException("该摊主已核销入场，请勿重复扫码");
        }
        a.setCheckInStatus(CheckInStatus.CHECKED_IN);
        a.setCheckInAt(LocalDateTime.now());
        a.setCheckInStaffId(staffId);
        applicationRepository.save(a);

        // 信用：正常到场 +2
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), 2, "按时核销入场");

        // 资质不完整：入场即登记高优先级异常，现场重点监管
        if (a.getFoodQualification() == FoodQualStatus.INCOMPLETE) {
            incidentService.raise(a.getEventId(), a.getId(), IncidentType.FOOD_QUALIFICATION,
                    "摊主 " + a.getShopName() + " 食品资质不完整仍入场，需现场核验并限制制售", 3, staffId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", viewSupport.applicationView(a, false));
        result.put("materials", viewSupport.materialList(a));
        return result;
    }

    @Transactional
    public Application collectMaterials(Long applicationId) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> new BizException("报名不存在"));
        if (a.getCheckInStatus() == CheckInStatus.NOT_CHECKED) {
            throw new BizException("摊主尚未核销入场，不能领取物资");
        }
        a.setMaterialsCollected(true);
        a.setMaterialsCollectedAt(LocalDateTime.now());
        return applicationRepository.save(a);
    }

    /** 现场巡检：检查实际售卖内容、记录临时换品、实测用电 */
    @Transactional
    public Inspection inspect(Long staffId, OnSiteDtos.InspectionRequest req) {
        Application a = applicationRepository.findById(req.applicationId())
                .orElseThrow(() -> new BizException("报名不存在"));

        Inspection ins = new Inspection();
        ins.setEventId(a.getEventId());
        ins.setApplicationId(a.getId());
        ins.setStaffId(staffId);
        ins.setActualGoods(req.actualGoods());
        ins.setMatched(req.matched());
        ins.setGoodsChanged(req.goodsChanged());
        ins.setChangeDescription(req.changeDescription());
        ins.setPowerUsedWatt(req.powerUsedWatt());
        ins.setRemark(req.remark());
        inspectionRepository.save(ins);

        a.setActualGoods(req.actualGoods());
        applicationRepository.save(a);

        // 现场卖品与报名不符 → 异常 + 信用扣分
        if (!req.matched()) {
            incidentService.raise(a.getEventId(), a.getId(), IncidentType.MISMATCH,
                    "现场售卖内容与报名不符：报名「" + a.getGoodsDescription()
                            + "」，实际「" + req.actualGoods() + "」", 3, staffId);
            creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -10, "现场售卖与报名不符");
        }

        // 用电超载
        if (req.powerUsedWatt() != null && a.getStallId() != null) {
            stallRepository.findById(a.getStallId()).ifPresent(stall -> {
                int cap = stall.getPowerCapacity() == null ? 0 : stall.getPowerCapacity();
                if (cap > 0 && req.powerUsedWatt() > cap) {
                    incidentService.raise(a.getEventId(), a.getId(), IncidentType.POWER_OVERLOAD,
                            "实测用电 " + req.powerUsedWatt() + "W 超过摊位核定 " + cap + "W，已要求撤减设备", 3, staffId);
                    creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -8, "用电超载");
                }
            });
        }

        // 大功率设备封锁期间违规使用 → 严重异常并再次扣分（须电工复核通过后才解封）
        if (a.isPowerLocked() && req.powerUsedWatt() != null
                && req.powerUsedWatt() >= PowerService.HIGH_POWER_WATT) {
            incidentService.raise(a.getEventId(), a.getId(), IncidentType.POWER_OVERLOAD,
                    "该摊处于用电整改封锁期（" + a.getPowerLockReason()
                            + "），电工未复核前仍使用大功率设备，实测 " + req.powerUsedWatt() + "W，已当场断电制止",
                    3, staffId);
            creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -10, "封锁期违规使用大功率设备");
        }
        return ins;
    }

    /** 摊主提前离场 */
    @Transactional
    public Application leaveEarly(Long staffId, Long applicationId) {
        Application a = applicationRepository.findById(applicationId).orElseThrow(() -> new BizException("报名不存在"));
        if (a.getCheckInStatus() != CheckInStatus.CHECKED_IN) {
            throw new BizException("摊主未处于在场状态");
        }
        a.setCheckInStatus(CheckInStatus.LEFT_EARLY);
        applicationRepository.save(a);
        incidentService.raise(a.getEventId(), a.getId(), IncidentType.EARLY_LEAVE,
                "摊主 " + a.getShopName() + " 提前离场", 2, staffId);
        creditService.adjust(a.getVendorId(), a.getEventId(), a.getId(), -10, "活动未结束提前离场");
        return a;
    }

    // ==================== 投诉 ====================

    @Transactional
    public Complaint createComplaint(OnSiteDtos.ComplaintRequest req) {
        eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("活动不存在"));
        Complaint c = new Complaint();
        c.setEventId(req.eventId());
        c.setApplicationId(req.applicationId());
        c.setReporterName(req.reporterName());
        c.setReporterPhone(req.reporterPhone());
        c.setComplaintType(req.complaintType());
        c.setContent(req.content());
        c.setStatus(ComplaintStatus.OPEN);
        return complaintRepository.save(c);
    }

    @Transactional
    public Complaint handleComplaint(Long staffId, Long id, String statusText, String result) {
        Complaint c = complaintRepository.findById(id).orElseThrow(() -> new BizException("投诉不存在"));
        ComplaintStatus newStatus;
        try {
            newStatus = ComplaintStatus.valueOf(statusText);
        } catch (Exception e) {
            throw new BizException("投诉状态不合法");
        }
        c.setStatus(newStatus);
        c.setHandleResult(result);
        c.setHandlerId(staffId);
        c.setHandledAt(LocalDateTime.now());
        complaintRepository.save(c);

        if (newStatus == ComplaintStatus.RESOLVED && c.getApplicationId() != null) {
            Application a = applicationRepository.findById(c.getApplicationId()).orElse(null);
            if (a != null) {
                creditService.adjust(a.getVendorId(), c.getEventId(), a.getId(), -5,
                        "被居民投诉成立：" + c.getComplaintType());
            }
        }
        return c;
    }

    @Transactional
    public Event updateVisitorCount(Long eventId, Integer count) {
        Event e = eventRepository.findById(eventId).orElseThrow(() -> new BizException("活动不存在"));
        e.setVisitorCount(count == null ? 0 : count);
        return eventRepository.save(e);
    }
}
