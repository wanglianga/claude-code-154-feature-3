package com.community.market.service;

import com.community.market.common.BizException;
import com.community.market.domain.entity.*;
import com.community.market.domain.enums.*;
import com.community.market.dto.EventDtos;
import com.community.market.dto.PublicityDtos;
import com.community.market.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动档案 / 复盘 / 对居民公示 / 下次招商建议。
 * 把报名、审核、摊位、收款、捐赠、投诉、异常、公示归集到同一活动档案。
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ApplicationRepository applicationRepository;
    private final StallRepository stallRepository;
    private final PaymentRecordRepository paymentRepository;
    private final DonationRecordRepository donationRepository;
    private final ComplaintRepository complaintRepository;
    private final IncidentRepository incidentRepository;
    private final PowerCaseRepository powerCaseRepository;
    private final UserRepository userRepository;
    private final PublicityRepository publicityRepository;
    private final ViewSupport viewSupport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Event create(EventDtos.EventRequest req) {
        Event e = new Event();
        apply(e, req);
        e.setStatus(EventStatus.RECRUITING);
        return eventRepository.save(e);
    }

    @Transactional
    public Event update(Long id, EventDtos.EventRequest req) {
        Event e = eventRepository.findById(id).orElseThrow(() -> new BizException("活动不存在"));
        apply(e, req);
        return eventRepository.save(e);
    }

    private void apply(Event e, EventDtos.EventRequest req) {
        e.setName(req.name());
        e.setEventDate(req.eventDate());
        e.setLocation(req.location());
        e.setPublicGoal(req.publicGoal());
        e.setGoalAmount(req.goalAmount() == null ? BigDecimal.ZERO : req.goalAmount());
        if (req.visitorCount() != null) {
            e.setVisitorCount(req.visitorCount());
        }
        if (req.status() != null) {
            try {
                e.setStatus(EventStatus.valueOf(req.status()));
            } catch (Exception ignored) {
            }
        }
    }

    /** 活动状态推进 */
    @Transactional
    public Event changeStatus(Long id, String statusText) {
        Event e = eventRepository.findById(id).orElseThrow(() -> new BizException("活动不存在"));
        e.setStatus(EventStatus.valueOf(statusText));
        if (e.getStatus() == EventStatus.PUBLISHED) {
            e.setStatus(EventStatus.PUBLISHED);
        }
        return eventRepository.save(e);
    }

    // ==================== 活动档案 ====================

    public Map<String, Object> archive(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new BizException("活动不存在"));
        List<Application> apps = applicationRepository.findByEventIdOrderByIdAsc(eventId);

        Map<String, Object> archive = new LinkedHashMap<>();
        archive.put("event", event);
        archive.put("stats", viewSupport.eventStats(event, apps));
        archive.put("stalls", stallRepository.findByEventIdOrderByCodeAsc(eventId));
        archive.put("applications", apps.stream().map(a -> viewSupport.applicationView(a, true)).toList());
        archive.put("complaints", complaintRepository.findByEventIdOrderByIdDesc(eventId));
        archive.put("incidents", incidentRepository.findByEventIdOrderByIdDesc(eventId));
        archive.put("powerCases", powerCaseRepository.findByEventIdOrderByIdDesc(eventId));
        archive.put("payments", paymentRepository.findByEventIdOrderByIdDesc(eventId));
        archive.put("donations", donationRepository.findByEventIdOrderByIdAsc(eventId));
        archive.put("publicity", publicityRepository.findByEventId(eventId).orElse(null));
        return archive;
    }

    // ==================== 复盘 + 招商建议 ====================

    public Map<String, Object> review(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new BizException("活动不存在"));
        List<Application> apps = applicationRepository.findByEventIdOrderByIdAsc(eventId);
        List<Incident> incidents = incidentRepository.findByEventIdOrderByIdDesc(eventId);
        List<Complaint> complaints = complaintRepository.findByEventIdOrderByIdDesc(eventId);
        List<PowerCase> powerCases = powerCaseRepository.findByEventIdOrderByIdDesc(eventId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        Map<String, Object> stats = viewSupport.eventStats(event, apps);
        result.put("stats", stats);

        // 用电超载处置：停电补偿与责任说明进入复盘
        BigDecimal powerCompensationTotal = powerCases.stream()
                .flatMap(pc -> pc.getCompensations().stream())
                .map(PowerCase.Compensation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long powerCaseUnreviewed = powerCases.stream()
                .filter(pc -> pc.getStatus() != PowerCaseStatus.REVIEWED).count();
        result.put("powerCases", powerCases);
        result.put("powerCompensationTotal", powerCompensationTotal);
        result.put("powerCaseUnreviewed", powerCaseUnreviewed);

        // 履约红黑榜
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Application a : apps.stream().filter(x -> x.getStatus() == ApplicationStatus.APPROVED).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            User v = userRepository.findById(a.getVendorId()).orElse(null);
            row.put("applicationId", a.getId());
            row.put("shopName", a.getShopName());
            row.put("vendorId", a.getVendorId());
            row.put("creditScore", v == null ? null : v.getCreditScore());
            row.put("checkInStatus", a.getCheckInStatus().name());
            DonationRecord d = donationRepository.findByApplicationId(a.getId()).orElse(null);
            row.put("donation", d == null ? BigDecimal.ZERO : d.getActualAmount());
            row.put("complaintCount", complaintRepository.findByApplicationIdOrderByIdDesc(a.getId()).size());
            row.put("incidentCount", incidentRepository.findByApplicationIdOrderByIdDesc(a.getId()).size());
            ranking.add(row);
        }
        ranking.sort((x, y) -> ((BigDecimal) y.get("donation")).compareTo((BigDecimal) x.get("donation")));
        result.put("vendorRanking", ranking);
        result.put("suggestions", suggestions(event, apps, incidents, complaints, stats, powerCases));
        return result;
    }

    /** 下次招商建议：品类结构、公益目标、食品风险、用电规划、信用准入 */
    private List<String> suggestions(Event event, List<Application> apps, List<Incident> incidents,
                                     List<Complaint> complaints, Map<String, Object> stats,
                                     List<PowerCase> powerCases) {
        List<String> tips = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Map<String, Long> byCategory = (Map<String, Long>) stats.get("byCategory");
        long food = byCategory.getOrDefault("FOOD", 0L);
        long secondhand = byCategory.getOrDefault("SECONDHAND", 0L);
        long charity = byCategory.getOrDefault("CHARITY", 0L);
        long commercial = byCategory.getOrDefault("COMMERCIAL", 0L);
        long handmade = byCategory.getOrDefault("HANDMADE", 0L);
        long approved = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.APPROVED).count();

        if (food * 3 > approved) {
            tips.add("食品摊位占比偏高（" + food + "个），建议下次控制在总摊位数 1/3 以内并扩大食品区、增加巡检");
        } else if (food == 0) {
            tips.add("本次无食品摊位，居民反馈如有需求，可定向招募有资质的轻食/饮品摊主");
        }
        long foodIncidents = incidents.stream().filter(i -> i.getType() == IncidentType.FOOD_QUALIFICATION).count();
        if (foodIncidents > 0) {
            tips.add("出现 " + foodIncidents + " 起食品资质问题，下次报名应强制上传证照照片，资质不全直接驳回");
        }
        if (secondhand * 2 < handmade) {
            tips.add("二手物品摊位偏少，可与社区闲置群合作招募，强化公益循环主题");
        }
        if (charity * 4 < approved) {
            tips.add("公益义卖摊位占比不足 1/4，建议预留 1/4 黄金摊位给公益组织并提高最低捐赠比例引导");
        }
        if (commercial * 2 > approved) {
            tips.add("商业摊位占比偏高，可能稀释公益氛围，下次需限制商业摊位比例");
        }

        long powerOverload = incidents.stream().filter(i -> i.getType() == IncidentType.POWER_OVERLOAD).count();
        long powerNeed = apps.stream().filter(Application::isPowerNeeded).count();
        if (!powerCases.isEmpty()) {
            long boxAdd = powerCases.stream().filter(p -> p.getMeasures().contains("BOX_ADD")).count();
            long moved = powerCases.stream().filter(p -> p.getMeasures().contains("STALL_MOVE")).count();
            long unreviewed = powerCases.stream()
                    .filter(p -> p.getStatus() != PowerCaseStatus.REVIEWED).count();
            BigDecimal comp = powerCases.stream().flatMap(p -> p.getCompensations().stream())
                    .map(PowerCase.Compensation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            long vendorFault = powerCases.stream()
                    .filter(p -> p.getResponsibility() == PowerResponsibility.VENDOR_FAULT
                            || p.getResponsibility() == PowerResponsibility.JOINT).count();
            tips.add("本次发生 " + powerCases.size() + " 起临时电箱跳闸（停用设备 "
                    + powerCases.stream().filter(p -> p.getMeasures().contains("DEVICE_STOP")).count()
                    + " 起、调整摊位 " + moved + " 起、增加电箱 " + boxAdd + " 起），其中 " + vendorFault
                    + " 起判定摊主侧责任；下次应按申报功率分路配电、加热设备专区接入并提高电箱容量冗余");
            if (comp.signum() > 0) {
                tips.add("停电波及其他摊主，累计补偿 ¥" + comp + "，已计入活动成本，下次应预留供电应急预算");
            }
            if (unreviewed > 0) {
                tips.add("尚有 " + unreviewed + " 起用电处置未经电工复核线路负载，相关摊位大功率设备仍处于停用锁定，须尽快复核闭环");
            }
        } else if (powerOverload > 0) {
            tips.add("发生 " + powerOverload + " 起用电超载，下次需提高供电摊位容量，并按申报功率分路配电");
        }
        if (powerNeed * 2 > approved) {
            tips.add("超半数摊位有用电需求，建议增加可供电摊位数量");
        }

        long earlyLeave = (long) stats.get("earlyLeft");
        if (earlyLeave > 0) {
            tips.add("有 " + earlyLeave + " 个摊位提前离场，下次可收取履约保证金并将离场记录纳入信用准入");
        }
        long mismatch = incidents.stream().filter(i -> i.getType() == IncidentType.MISMATCH).count();
        if (mismatch > 0) {
            tips.add("出现 " + mismatch + " 起卖品与报名不符，下次报名应细化品类并在入场时增加货品核验");
        }
        long donationDispute = incidents.stream().filter(i -> i.getType() == IncidentType.DONATION_MISMATCH).count();
        if (donationDispute > 0) {
            tips.add("出现 " + donationDispute + " 起捐赠与流水不符，下次统一使用现场收款码归集，结账时即时核销捐赠");
        }
        long unresolvedComplaints = complaints.stream()
                .filter(c -> c.getStatus() != ComplaintStatus.RESOLVED).count();
        if (unresolvedComplaints > 0) {
            tips.add("有 " + unresolvedComplaints + " 件投诉未闭环，下次现场需设置投诉专窗并限时响应");
        }

        BigDecimal totalDonation = (BigDecimal) stats.get("totalDonation");
        BigDecimal goal = (BigDecimal) stats.get("goalAmount");
        if (goal.signum() > 0) {
            int percent = totalDonation.multiply(new BigDecimal("100"))
                    .divide(goal, 0, java.math.RoundingMode.HALF_UP).intValue();
            tips.add("公益目标完成度 " + percent + "%，" + (percent < 80
                    ? "下次应提高公益摊位比例与捐赠引导"
                    : "目标达成良好，可适度提高筹款目标"));
        }

        // 信用准入建议：低分摊主
        List<String> lowCredit = apps.stream()
                .map(Application::getVendorId).distinct()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(u -> u.getCreditScore() != null && u.getCreditScore() < 80)
                .map(u -> u.getRealName() == null ? u.getUsername() : u.getRealName())
                .toList();
        if (!lowCredit.isEmpty()) {
            tips.add("以下摊主信用低于 80，下次报名需重点审核或限制黄金摊位：" + String.join("、", lowCredit));
        }

        long family = (long) stats.get("familyStalls");
        if (family > 0) {
            tips.add("亲子摊位反馈良好，下次可在中心区设置亲子专区并配套护角、引导志愿者");
        }
        if (tips.isEmpty()) {
            tips.add("整体秩序良好，可延续本次品类结构与摊位布局，适度扩大规模");
        }
        return tips;
    }

    // ==================== 对居民公示 ====================

    @Transactional
    public Publicity savePublicity(Long operatorId, PublicityDtos.PublicityRequest req) {
        Event event = eventRepository.findById(req.eventId()).orElseThrow(() -> new BizException("活动不存在"));
        List<Application> apps = applicationRepository.findByEventIdOrderByIdAsc(req.eventId());
        List<PaymentRecord> payments = paymentRepository.findByEventIdOrderByIdDesc(req.eventId());
        List<DonationRecord> donations = donationRepository.findByEventIdOrderByIdAsc(req.eventId());
        List<Incident> incidents = incidentRepository.findByEventIdOrderByIdDesc(req.eventId());

        BigDecimal totalSales = payments.stream().map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDonation = donations.stream().map(DonationRecord::getActualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Publicity p = publicityRepository.findByEventId(req.eventId()).orElseGet(Publicity::new);
        p.setEventId(req.eventId());
        p.setTitle(req.title() == null || req.title().isBlank() ? event.getName() + " 公益成果公示" : req.title());
        p.setTotalSales(totalSales);
        p.setTotalDonation(totalDonation);
        p.setFundUsage(req.fundUsage());
        p.setFulfillmentSummary(req.fulfillmentSummary());
        p.setIncidentSummary(req.incidentSummary());
        p.setCreatedBy(operatorId);

        if (req.includeDetail()) {
            List<Map<String, Object>> details = new ArrayList<>();
            for (DonationRecord d : donations) {
                Application a = apps.stream().filter(x -> x.getId().equals(d.getApplicationId())).findFirst().orElse(null);
                if (a == null) {
                    continue;
                }
                User v = userRepository.findById(a.getVendorId()).orElse(null);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("shopName", a.getShopName());
                row.put("vendorName", v == null ? "" : (v.getRealName() == null ? v.getUsername() : v.getRealName()));
                row.put("category", a.getCategory().getLabel());
                row.put("rate", d.getRate());
                row.put("salesAmount", d.getSalesAmount());
                row.put("donationAmount", d.getActualAmount());
                row.put("voucherNo", d.getVoucherNo());
                details.add(row);
            }
            try {
                p.setDonationDetailJson(objectMapper.writeValueAsString(details));
            } catch (JsonProcessingException e) {
                p.setDonationDetailJson("[]");
            }
        }

        if (req.publish()) {
            p.setPublished(true);
            p.setPublishedAt(LocalDateTime.now());
            event.setStatus(EventStatus.PUBLISHED);
            eventRepository.save(event);
        }
        return publicityRepository.save(p);
    }
}
