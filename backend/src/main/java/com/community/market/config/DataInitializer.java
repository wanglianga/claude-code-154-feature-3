package com.community.market.config;

import com.community.market.domain.entity.Application;
import com.community.market.domain.entity.Event;
import com.community.market.domain.entity.Stall;
import com.community.market.domain.entity.User;
import com.community.market.domain.enums.*;
import com.community.market.repository.ApplicationRepository;
import com.community.market.repository.EventRepository;
import com.community.market.repository.StallRepository;
import com.community.market.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 演示种子数据（幂等：已存在用户则跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final StallRepository stallRepository;
    private final ApplicationRepository applicationRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User admin = user("admin", "admin123", "社区管理员-李主任", "13800000001", UserRole.ADMIN, 100);
        User staff = user("staff", "staff123", "现场工作人员-小王", "13800000002", UserRole.STAFF, 100);
        userRepository.saveAll(List.of(admin, staff));

        User handmade = user("wangni", "123456", "王姐手作", "13900000001", UserRole.VENDOR, 108);
        User second = user("mayi", "123456", "蚂蚁闲置", "13900000002", UserRole.VENDOR, 102);
        User food = user("laowang", "123456", "老王凉粉", "13900000003", UserRole.VENDOR, 96);
        User foodRisk = user("xiaobai", "123456", "小白烘焙(证照办理中)", "13900000004", UserRole.VENDOR, 85);
        User charity = user("shanyi", "123456", "善意公益组织", "13900000005", UserRole.VENDOR, 115);
        User commercial = user("dajian", "123456", "大健生活馆", "13900000006", UserRole.VENDOR, 91);
        User drink = user("tianmi", "123456", "甜蜜热饮", "13900000007", UserRole.VENDOR, 98);
        userRepository.saveAll(List.of(handmade, second, food, foodRisk, charity, commercial, drink));

        Event event = new Event();
        event.setName("2026 秋季社区公益集市");
        event.setEventDate(LocalDate.of(2026, 10, 17));
        event.setLocation("幸福社区中心广场");
        event.setPublicGoal("为本社区 12 户困难家庭学生筹集助学款及冬季慰问物资");
        event.setGoalAmount(new BigDecimal("8000"));
        event.setStatus(EventStatus.RECRUITING);
        eventRepository.save(event);
        Long eid = event.getId();

        // 平面图：4 行 × 5 列；食品区在第 4 行；入口区第 1 行（高人流、噪音敏感）
        createStalls(eid);

        // 报名（默认待审核，演示完整审核流程）
        applicationRepository.save(buildApp(eid, handmade.getId(), "王姐手工编织", StallType.HANDMADE,
                "手工编织围巾、玩偶", FoodQualStatus.NONE, "", new BigDecimal("15"),
                false, 0, true, 1, true, 2, false, false, "WECHAT", "王姐微信收款"));
        applicationRepository.save(buildApp(eid, second.getId(), "蚂蚁闲置铺", StallType.SECONDHAND,
                "二手绘本、玩具、小家电", FoodQualStatus.NONE, "", new BigDecimal("30"),
                false, 0, true, 1, false, 0, true, false, "MIXED", "微信+现金"));
        applicationRepository.save(buildApp(eid, food.getId(), "老王手工凉粉", StallType.FOOD,
                "现做手工凉粉、冰粉", FoodQualStatus.COMPLETE, "食摊字 2026-0188", new BigDecimal("20"),
                true, 800, true, 1, true, 2, false, true, "WECHAT", "老王商户码"));
        applicationRepository.save(buildApp(eid, foodRisk.getId(), "小白家庭烘焙", StallType.FOOD,
                "手工曲奇、蛋糕杯", FoodQualStatus.INCOMPLETE, "证照办理中（缺健康证）", new BigDecimal("10"),
                true, 500, true, 1, true, 2, true, false, "ALIPAY", "小白支付宝"));
        applicationRepository.save(buildApp(eid, charity.getId(), "善意公益义卖台", StallType.CHARITY,
                "公益文创、社区书画义卖", FoodQualStatus.NONE, "", new BigDecimal("100"),
                false, 0, true, 2, true, 4, true, false, "CASH", "公益募捐专户"));
        applicationRepository.save(buildApp(eid, commercial.getId(), "大健日用品", StallType.COMMERCIAL,
                "环保清洁剂、家居好物", FoodQualStatus.NONE, "", new BigDecimal("5"),
                true, 300, true, 1, false, 0, false, true, "MIXED", "门店聚合码"));
        // 饮品摊（加热设备），与老王凉粉同属食品区供电回路，用于演示多摊叠加跳闸处置
        applicationRepository.save(buildApp(eid, drink.getId(), "甜蜜热饮站", StallType.FOOD,
                "现煮奶茶、热可可、烤肠", FoodQualStatus.COMPLETE, "食摊字 2026-0266", new BigDecimal("10"),
                true, 600, true, 1, true, 2, false, true, "WECHAT", "甜蜜热饮商户码"));

        log.info("演示数据初始化完成：活动「{}」+ 20 个摊位 + 7 条摊主报名", event.getName());
    }

    private void createStalls(Long eid) {
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 5; c++) {
                Stall s = new Stall();
                s.setEventId(eid);
                s.setCode((char) ('A' + r - 1) + "-" + String.format("%02d", c));
                String zone;
                if (r == 1) {
                    zone = "入口区";
                } else if (r == 4) {
                    zone = "食品区";
                } else if (c == 1 || c == 5) {
                    zone = "边角区";
                } else {
                    zone = "中心区";
                }
                s.setZone(zone);
                s.setRowNo(r);
                s.setColNo(c);
                s.setSizeLevel(r == 4 ? 3 : (c == 3 && r <= 2 ? 3 : 2));
                s.setTrafficLevel(switch (zone) {
                    case "入口区" -> 3;
                    case "中心区" -> 3;
                    case "食品区" -> 2;
                    default -> 1;
                });
                s.setPowerAvailable(r == 4 || (r == 2 && c == 3));
                s.setPowerCapacity(s.isPowerAvailable() ? 1000 : 0);
                s.setNoiseSensitive(r == 1);
                stallRepository.save(s);
            }
        }
    }

    private Application buildApp(Long eid, Long vid, String shop, StallType category, String goods,
                                 FoodQualStatus food, String licenseNo, BigDecimal rate,
                                 boolean power, int watt, boolean table, int tableCnt,
                                 boolean chair, int chairCnt, boolean family, boolean noise,
                                 String payMethod, String payAccount) {
        Application a = new Application();
        a.setEventId(eid);
        a.setVendorId(vid);
        a.setShopName(shop);
        a.setCategory(category);
        a.setGoodsDescription(goods);
        a.setFoodQualification(food);
        a.setFoodLicenseNo(licenseNo);
        a.setDonationRate(rate);
        a.setPowerNeeded(power);
        a.setPowerWanted(watt);
        a.setTableNeeded(table);
        a.setTableCount(tableCnt);
        a.setChairNeeded(chair);
        a.setChairCount(chairCnt);
        a.setFamilyFriendly(family);
        a.setNoiseMaking(noise);
        a.setPaymentMethod(payMethod);
        a.setPaymentAccount(payAccount);
        a.setStatus(ApplicationStatus.SUBMITTED);
        a.setCheckInStatus(CheckInStatus.NOT_CHECKED);
        return a;
    }

    private User user(String username, String pwd, String name, String phone, UserRole role, int credit) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setRealName(name);
        u.setPhone(phone);
        u.setRole(role);
        u.setCreditScore(credit);
        u.setEnabled(true);
        return u;
    }
}
