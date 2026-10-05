package com.community.market.service;

import com.community.market.domain.entity.*;
import com.community.market.domain.enums.ApplicationStatus;
import com.community.market.domain.enums.CheckInStatus;
import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import com.community.market.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 视图组装：把摊主、摊位、收款、捐赠、投诉、异常等信息聚合到同一"活动档案"视图。
 */
@Component
@RequiredArgsConstructor
public class ViewSupport {

    private final UserRepository userRepository;
    private final StallRepository stallRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final DonationRecordRepository donationRecordRepository;
    private final ComplaintRepository complaintRepository;
    private final IncidentRepository incidentRepository;
    private final InspectionRepository inspectionRepository;
    private final PowerCompensationRepository powerCompensationRepository;
    private final PowerCasePartyRepository powerCasePartyRepository;
    private final PowerCaseRepository powerCaseRepository;

    public Map<String, Object> userBrief(Long id) {
        if (id == null) {
            return null;
        }
        return userRepository.findById(id).map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("phone", u.getPhone());
            m.put("creditScore", u.getCreditScore());
            return m;
        }).orElse(null);
    }

    public Map<String, Object> stallBrief(Stall s) {
        if (s == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("code", s.getCode());
        m.put("zone", s.getZone());
        m.put("rowNo", s.getRowNo());
        m.put("colNo", s.getColNo());
        m.put("sizeLevel", s.getSizeLevel());
        m.put("trafficLevel", s.getTrafficLevel());
        m.put("powerAvailable", s.isPowerAvailable());
        m.put("powerCapacity", s.getPowerCapacity());
        m.put("noiseSensitive", s.isNoiseSensitive());
        m.put("status", s.getStatus());
        return m;
    }

    /** 完整报名视图：活动档案中的一行 */
    public Map<String, Object> applicationView(Application a, boolean withRelations) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("eventId", a.getEventId());
        m.put("vendorId", a.getVendorId());
        m.put("vendor", userBrief(a.getVendorId()));
        m.put("shopName", a.getShopName());
        m.put("category", a.getCategory().name());
        m.put("categoryLabel", a.getCategory().getLabel());
        m.put("goodsDescription", a.getGoodsDescription());
        m.put("foodQualification", a.getFoodQualification().name());
        m.put("foodQualificationLabel", a.getFoodQualification().getLabel());
        m.put("foodLicenseNo", a.getFoodLicenseNo());
        m.put("donationRate", a.getDonationRate());
        m.put("powerNeeded", a.isPowerNeeded());
        m.put("powerWanted", a.getPowerWanted());
        m.put("tableNeeded", a.isTableNeeded());
        m.put("tableCount", a.getTableCount());
        m.put("chairNeeded", a.isChairNeeded());
        m.put("chairCount", a.getChairCount());
        m.put("familyFriendly", a.isFamilyFriendly());
        m.put("noiseMaking", a.isNoiseMaking());
        m.put("paymentMethod", a.getPaymentMethod());
        m.put("paymentAccount", a.getPaymentAccount());
        m.put("status", a.getStatus().name());
        m.put("statusLabel", a.getStatus().getLabel());
        m.put("reviewComment", a.getReviewComment());
        m.put("reviewer", userBrief(a.getReviewerId()));
        m.put("reviewedAt", a.getReviewedAt());
        m.put("stallId", a.getStallId());
        m.put("stall", a.getStallId() == null ? null : stallRepository.findById(a.getStallId()).map(this::stallBrief).orElse(null));
        m.put("allocationScore", a.getAllocationScore());
        m.put("allocationReason", a.getAllocationReason());
        m.put("checkinCode", a.getCheckinCode());
        m.put("checkInStatus", a.getCheckInStatus().name());
        m.put("checkInStatusLabel", a.getCheckInStatus().getLabel());
        m.put("checkInAt", a.getCheckInAt());
        m.put("materialsCollected", a.isMaterialsCollected());
        m.put("materialsCollectedAt", a.getMaterialsCollectedAt());
        m.put("actualGoods", a.getActualGoods());
        m.put("powerLocked", a.isPowerLocked());
        m.put("powerLockReason", a.getPowerLockReason());
        m.put("powerLockedAt", a.getPowerLockedAt());
        m.put("createdAt", a.getCreatedAt());

        if (withRelations) {
            List<PaymentRecord> payments = paymentRecordRepository.findByApplicationIdOrderByRecordedAtDesc(a.getId());
            BigDecimal sales = payments.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("payments", payments);
            m.put("salesAmount", sales);

            DonationRecord donation = donationRecordRepository.findByApplicationId(a.getId()).orElse(null);
            m.put("donation", donationView(donation));

            m.put("complaints", complaintRepository.findByApplicationIdOrderByIdDesc(a.getId()));
            m.put("incidents", incidentRepository.findByApplicationIdOrderByIdDesc(a.getId()));
            m.put("inspections", inspectionRepository.findByApplicationIdOrderByCreatedAtDesc(a.getId()));

            // 用电跳闸：作为责任方/受影响方参与的工单与停电补偿
            List<Map<String, Object>> powerRows = new ArrayList<>();
            for (PowerCaseParty p : powerCasePartyRepository.findByApplicationId(a.getId())) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("party", p);
                powerCaseRepository.findById(p.getCaseId()).ifPresent(pc -> {
                    row.put("title", pc.getTitle());
                    row.put("boxCode", pc.getBoxCode());
                    row.put("status", pc.getStatus().name());
                    row.put("statusLabel", pc.getStatus().getLabel());
                    row.put("liability", pc.getLiability() == null ? null : pc.getLiability().name());
                    row.put("liabilityLabel", pc.getLiability() == null ? "待判定" : pc.getLiability().getLabel());
                    row.put("reviewedAt", pc.getReviewedAt());
                });
                powerRows.add(row);
            }
            m.put("powerCases", powerRows);
            m.put("powerCompensations", powerCompensationRepository.findByApplicationIdOrderByIdDesc(a.getId()));
        }
        return m;
    }

    public Map<String, Object> donationView(DonationRecord d) {
        if (d == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("eventId", d.getEventId());
        m.put("applicationId", d.getApplicationId());
        m.put("rate", d.getRate());
        m.put("salesAmount", d.getSalesAmount());
        m.put("expectedAmount", d.getExpectedAmount());
        m.put("actualAmount", d.getActualAmount());
        m.put("status", d.getStatus().name());
        m.put("voucherNo", d.getVoucherNo());
        m.put("confirmedAt", d.getConfirmedAt());
        return m;
    }

    /** 物资领取清单：根据报名需求生成 */
    public List<Map<String, Object>> materialList(Application a) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (a.isTableNeeded()) {
            list.add(item("桌子", a.getTableCount() == null ? 1 : a.getTableCount(), "张"));
        }
        if (a.isChairNeeded()) {
            list.add(item("椅子", a.getChairCount() == null ? 2 : a.getChairCount(), "把"));
        }
        if (a.isPowerNeeded()) {
            list.add(item("供电插线板", 1, "个（核定功率 " + (a.getPowerWanted() == null ? 0 : a.getPowerWanted()) + "W）"));
        }
        list.add(item("摊主证件/胸牌", 1, "个"));
        list.add(item("垃圾收纳袋", 2, "个"));
        if (a.isFamilyFriendly()) {
            list.add(item("亲子摊位安全角护条", 1, "套"));
        }
        if (a.getCategory() == StallType.FOOD) {
            list.add(item("食品经营者公示卡", 1, "张"));
            list.add(item("防油污垫布", 1, "块"));
        }
        return list;
    }

    private Map<String, Object> item(String name, int qty, String unit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("qty", qty);
        m.put("unit", unit);
        return m;
    }

    /** 活动维度统计（复盘） */
    public Map<String, Object> eventStats(Event event, List<Application> apps) {
        Map<String, Object> stats = new LinkedHashMap<>();
        long approved = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count();
        long checkedIn = apps.stream().filter(a -> a.getCheckInStatus() != CheckInStatus.NOT_CHECKED).count();
        long earlyLeft = apps.stream().filter(a -> a.getCheckInStatus() == CheckInStatus.LEFT_EARLY).count();
        long family = apps.stream().filter(Application::isFamilyFriendly).count();
        long foodIncomplete = apps.stream().filter(a -> a.getFoodQualification() == FoodQualStatus.INCOMPLETE).count();

        Map<StallType, Long> byCategory = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.APPROVED)
                .collect(Collectors.groupingBy(Application::getCategory, Collectors.counting()));

        List<PaymentRecord> payments = paymentRecordRepository.findByEventIdOrderByIdDesc(event.getId());
        BigDecimal totalSales = payments.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DonationRecord> donations = donationRecordRepository.findByEventIdOrderByIdAsc(event.getId());
        BigDecimal totalDonation = donations.stream().map(DonationRecord::getActualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedDonation = donations.stream().map(DonationRecord::getExpectedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Complaint> complaints = complaintRepository.findByEventIdOrderByIdDesc(event.getId());
        long complaintOpen = complaints.stream().filter(c -> c.getStatus().name().equals("OPEN") || c.getStatus().name().equals("PROCESSING")).count();
        List<Incident> incidents = incidentRepository.findByEventIdOrderByIdDesc(event.getId());
        long incidentOpen = incidents.stream().filter(i -> i.getStatus().name().equals("OPEN")).count();

        stats.put("totalApplications", apps.size());
        stats.put("approved", approved);
        stats.put("checkedIn", checkedIn);
        stats.put("attendanceRate", approved == 0 ? 0 : Math.round(checkedIn * 100.0 / approved));
        stats.put("earlyLeft", earlyLeft);
        stats.put("familyStalls", family);
        stats.put("foodIncomplete", foodIncomplete);
        stats.put("byCategory", byCategory.entrySet().stream().collect(
                Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue, (x, y) -> x, LinkedHashMap::new)));
        stats.put("visitorCount", event.getVisitorCount());
        stats.put("totalSales", totalSales);
        stats.put("totalDonation", totalDonation);
        stats.put("expectedDonation", expectedDonation);
        stats.put("goalAmount", event.getGoalAmount());
        stats.put("goalReached", totalDonation.compareTo(event.getGoalAmount() == null ? BigDecimal.ZERO : event.getGoalAmount()) >= 0);
        stats.put("complaintCount", complaints.size());
        stats.put("complaintOpen", complaintOpen);
        stats.put("incidentCount", incidents.size());
        stats.put("incidentOpen", incidentOpen);
        return stats;
    }
}
