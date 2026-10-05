package com.community.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 全流程集成测试：登录 → 审核 → 智能分配 → 核销 → 巡检/异常 →
 * 收款 → 捐赠核对 → 投诉 → 档案 → 复盘 → 公示（居民公开）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = Map.of("username", username, "password", password);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private MvcResult call(String token, String method, String url, Object body) throws Exception {
        var req = method.equals("POST") ? post(url) : method.equals("PUT") ? put(url) : get(url);
        req.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            req.content(om.writeValueAsString(body));
        }
        return mockMvc.perform(req).andReturn();
    }

    private JsonNode data(MvcResult r) throws Exception {
        JsonNode root = om.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(0, root.path("code").asInt(),
                "接口应成功：" + r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return root.path("data");
    }

    private int respCode(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("code").asInt();
    }

    @Test
    void fullFlow() throws Exception {
        String admin = login("admin", "admin123");
        String staff = login("staff", "staff123");
        String vendor = login("laowang", "123456");

        // ---------- 1. 种子数据：活动 + 20 摊位 + 6 报名 ----------
        JsonNode events = data(call(admin, "GET", "/api/events", null));
        org.junit.jupiter.api.Assertions.assertTrue(events.size() >= 1);
        long eventId = events.get(0).path("id").asLong();

        JsonNode stalls = data(call(admin, "GET", "/api/stalls?eventId=" + eventId, null));
        org.junit.jupiter.api.Assertions.assertEquals(20, stalls.size());

        JsonNode apps = data(call(admin, "GET", "/api/events/" + eventId + "/applications", null));
        org.junit.jupiter.api.Assertions.assertTrue(apps.size() >= 6, "种子数据应包含 6 条报名");

        // ---------- 2. 无 token 访问被拒 ----------
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());

        // ---------- 3. 审核：资质完整的通过，资质不完整的小白烘焙驳回 ----------
        long foodAppId = 0, charityAppId = 0, riskAppId = 0, handmadeAppId = 0;
        int approvedCount = 0;
        for (JsonNode a : apps) {
            long id = a.path("id").asLong();
            String qual = a.path("foodQualification").asText();
            boolean approve = !"INCOMPLETE".equals(qual);
            if (approve) {
                approvedCount++;
            }
            call(admin, "POST", "/api/applications/" + id + "/review",
                    Map.of("approved", approve, "comment", approve ? "符合活动规则" : "食品资质不完整，请补齐健康证后再报名"));
            if (a.path("category").asText().equals("FOOD") && "COMPLETE".equals(qual)
                    && "laowang".equals(a.path("vendor").path("username").asText())) {
                foodAppId = id;
            }
            if (a.path("category").asText().equals("CHARITY")) {
                charityAppId = id;
            }
            if ("INCOMPLETE".equals(qual)) {
                riskAppId = id;
            }
            if ("HANDMADE".equals(a.path("category").asText())
                    && "wangni".equals(a.path("vendor").path("username").asText())) {
                handmadeAppId = id;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foodAppId > 0 && charityAppId > 0 && riskAppId > 0 && handmadeAppId > 0);

        JsonNode riskApp = data(call(admin, "GET", "/api/applications/" + riskAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals("REJECTED", riskApp.path("status").asText());

        // ---------- 4. 智能分配 ----------
        JsonNode allocation = data(call(admin, "POST", "/api/allocations/auto", Map.of("eventId", eventId)));
        int allocated = 0;
        for (JsonNode row : allocation) {
            if (row.path("allocated").asBoolean()) {
                allocated++;
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(approvedCount, allocated, "所有通过审核的报名均应分配到摊位");

        // 硬约束验证：食品在食品区；公益义卖高人流动线
        JsonNode foodAfter = data(call(admin, "GET", "/api/applications/" + foodAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals("食品区", foodAfter.path("stall").path("zone").asText());
        org.junit.jupiter.api.Assertions.assertTrue(foodAfter.path("stall").path("powerAvailable").asBoolean());

        JsonNode charityAfter = data(call(admin, "GET", "/api/applications/" + charityAppId, null));
        org.junit.jupiter.api.Assertions.assertTrue(charityAfter.path("stall").path("trafficLevel").asInt() >= 2,
                "公益义卖应安排在高人流动线");

        // 手动调整：找一个当前空闲摊位，把手工摊改派过去（管理员可手动覆盖）
        JsonNode stallsNow = data(call(admin, "GET", "/api/stalls?eventId=" + eventId, null));
        long targetStall = 0;
        for (JsonNode s : stallsNow) {
            if ("FREE".equals(s.path("status").asText())) {
                targetStall = s.path("id").asLong();
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(targetStall > 0, "20 个摊位仅分配 5 个，应有空闲摊位");
        call(admin, "POST", "/api/allocations/reassign", Map.of("applicationId", handmadeAppId, "stallId", targetStall));
        JsonNode handmadeAfter = data(call(admin, "GET", "/api/applications/" + handmadeAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals(targetStall, handmadeAfter.path("stallId").asLong());

        // ---------- 5. 摊主查看入场二维码与物资清单 ----------
        String wangni = login("wangni", "123456");
        JsonNode myEntry = data(call(wangni, "GET", "/api/my/applications/" + handmadeAppId + "/entry", null));
        org.junit.jupiter.api.Assertions.assertFalse(myEntry.path("application").path("checkinCode").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(myEntry.path("materials").size() >= 1);

        // ---------- 6. 现场扫码核销（用食品摊主的码） ----------
        String checkinCode = foodAfter.path("checkinCode").asText();
        JsonNode checkin = data(call(staff, "POST", "/api/onsite/checkin", Map.of("code", checkinCode)));
        org.junit.jupiter.api.Assertions.assertEquals("CHECKED_IN",
                checkin.path("application").path("checkInStatus").asText());
        org.junit.jupiter.api.Assertions.assertTrue(checkin.path("materials").size() >= 3, "食品摊物资应含公示卡等");

        // 重复核销报错
        MvcResult dup = call(staff, "POST", "/api/onsite/checkin", Map.of("code", checkinCode));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(dup));

        // 物资领取
        data(call(staff, "POST", "/api/onsite/applications/" + foodAppId + "/materials/collect", null));

        // ---------- 7. 巡检：卖品与报名不符 + 用电超载 ----------
        Map<String, Object> inspection = new HashMap<>();
        inspection.put("applicationId", foodAppId);
        inspection.put("actualGoods", "违规现场油炸臭豆腐（与报名凉粉不符）");
        inspection.put("matched", false);
        inspection.put("goodsChanged", true);
        inspection.put("changeDescription", "临时新增油炸食品");
        inspection.put("powerUsedWatt", 1500); // 摊位容量 1000W
        inspection.put("remark", "已当场制止");
        data(call(staff, "POST", "/api/onsite/inspections", inspection));

        JsonNode incidents = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean mismatchFound = false, overloadFound = false;
        for (JsonNode i : incidents) {
            if ("MISMATCH".equals(i.path("type").asText())) {
                mismatchFound = true;
            }
            if ("POWER_OVERLOAD".equals(i.path("type").asText())) {
                overloadFound = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(mismatchFound, "应登记卖品不符异常");
        org.junit.jupiter.api.Assertions.assertTrue(overloadFound, "应登记用电超载异常");

        // 摊主信用应被扣减（初始 96：到场 +2 后 -10 -8）
        JsonNode foodDetail = data(call(admin, "GET", "/api/applications/" + foodAppId, null));
        org.junit.jupiter.api.Assertions.assertTrue(foodDetail.path("vendor").path("creditScore").asInt() < 96);

        // ---------- 8. 提前离场 ----------
        data(call(staff, "POST", "/api/onsite/applications/" + foodAppId + "/leave-early", null));
        JsonNode leftApp = data(call(admin, "GET", "/api/applications/" + foodAppId, null));
        org.junit.jupiter.api.Assertions.assertEquals("LEFT_EARLY", leftApp.path("checkInStatus").asText());

        // ---------- 9. 收款流水 + 捐赠核对 ----------
        // 老王凉粉捐赠比例 20%，登记流水 1000 元 → 应捐 200
        data(call(staff, "POST", "/api/finance/payments", Map.of(
                "eventId", eventId, "applicationId", foodAppId,
                "paymentMethod", "WECHAT", "amount", new BigDecimal("1000"),
                "receiptNo", "WX20261017001", "remark", "现场营业额")));

        // 上缴金额不符（150 vs 应捐 200）→ DISPUTED + 异常
        JsonNode wrongDonation = data(call(staff, "POST", "/api/finance/donations/confirm", Map.of(
                "applicationId", foodAppId, "actualAmount", new BigDecimal("150"), "voucherNo", "RCP-001")));
        org.junit.jupiter.api.Assertions.assertEquals("DISPUTED", wrongDonation.path("status").asText());

        // 补缴至 200 → CONFIRMED
        JsonNode okDonation = data(call(staff, "POST", "/api/finance/donations/confirm", Map.of(
                "applicationId", foodAppId, "actualAmount", new BigDecimal("200"), "voucherNo", "RCP-002")));
        org.junit.jupiter.api.Assertions.assertEquals("CONFIRMED", okDonation.path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(0, okDonation.path("expectedAmount").decimalValue().compareTo(new BigDecimal("200.00")));

        // ---------- 10. 投诉登记与处理 ----------
        JsonNode complaint = data(call(staff, "POST", "/api/onsite/complaints", Map.of(
                "eventId", eventId, "applicationId", foodAppId,
                "reporterName", "居民张阿姨", "reporterPhone", "13700000000",
                "complaintType", "卫生/油烟", "content", "油炸摊油烟影响周边摊位")));
        long complaintId = complaint.path("id").asLong();
        data(call(staff, "POST", "/api/onsite/complaints/" + complaintId + "/handle",
                Map.of("status", "RESOLVED", "handleResult", "已要求停止油炸并清理现场，摊主同意")));

        // ---------- 11. 客流 ----------
        data(call(staff, "PUT", "/api/onsite/events/" + eventId + "/visitors", Map.of("visitorCount", 1280)));

        // ---------- 12. 公益组织要求公示明细 → 登记异常 ----------
        data(call(admin, "POST", "/api/onsite/incidents", Map.of(
                "eventId", eventId, "applicationId", charityAppId,
                "type", "PUBLIC_REQUEST", "description", "善意公益组织要求公开逐摊位捐赠明细", "severity", 2)));

        // ---------- 13. 活动档案：七要素同档 ----------
        JsonNode archive = data(call(admin, "GET", "/api/events/" + eventId + "/archive", null));
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("applications").size() >= 6);
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("payments").size() >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("donations").size() >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("complaints").size() >= 1);
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("incidents").size() >= 4);
        org.junit.jupiter.api.Assertions.assertEquals(1280, archive.path("stats").path("visitorCount").asInt());

        // ---------- 14. 复盘：公益金额、履约、招商建议 ----------
        JsonNode review = data(call(admin, "GET", "/api/events/" + eventId + "/review", null));
        org.junit.jupiter.api.Assertions.assertTrue(review.path("stats").path("totalDonation").decimalValue()
                .compareTo(new BigDecimal("200")) >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(review.path("suggestions").size() >= 3);
        org.junit.jupiter.api.Assertions.assertTrue(review.path("vendorRanking").size() >= 5);

        // ---------- 15. 活动结束 ----------
        mockMvc.perform(put("/api/events/" + eventId + "/status?status=FINISHED")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void publicityVisibleToResidents() throws Exception {
        String admin = login("admin", "admin123");
        JsonNode events = data(call(admin, "GET", "/api/events", null));
        long eventId = events.get(0).path("id").asLong();

        // 活动先结束
        mockMvc.perform(put("/api/events/" + eventId + "/status?status=FINISHED")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        // 发布公示（含逐摊明细）
        Map<String, Object> pub = new HashMap<>();
        pub.put("eventId", eventId);
        pub.put("title", "2026 秋季公益集市成果公示");
        pub.put("fundUsage", "全部捐赠 200 元及公益义卖收入用于 12 户困难家庭学生助学包购买，凭证在社区公告栏公示。");
        pub.put("fulfillmentSummary", "应到 5 摊，实到 5 摊，1 摊因违规经劝阻后正常经营，1 摊提前离场。");
        pub.put("incidentSummary", "现场处置卖品不符、用电超载、捐赠核对等异常均已闭环。");
        pub.put("includeDetail", true);
        pub.put("publish", true);
        data(call(admin, "POST", "/api/admin/publicity", pub));

        // 居民端无需登录
        JsonNode publicEvents = data(call(null, "GET", "/api/public/events", null));
        org.junit.jupiter.api.Assertions.assertTrue(publicEvents.size() >= 1);

        JsonNode view = data(call(null, "GET", "/api/public/events/" + eventId + "/publicity", null));
        org.junit.jupiter.api.Assertions.assertTrue(view.path("published").asBoolean());
        org.junit.jupiter.api.Assertions.assertFalse(view.path("fundUsage").asText().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(view.path("donationDetails").isArray());
    }

    @Test
    void vendorCannotAccessAdminApis() throws Exception {
        String vendor = login("mayi", "123456");
        mockMvc.perform(post("/api/allocations/auto")
                        .header("Authorization", "Bearer " + vendor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":1}"))
                .andExpect(status().isBadRequest()); // 无权限由业务异常返回 400
    }

    @Test
    void vendorSelfRegisterAndApply() throws Exception {
        // 自助注册
        Map<String, Object> reg = Map.of("username", "newvendor", "password", "123456",
                "realName", "新摊主", "phone", "13600000000");
        data(call(null, "POST", "/api/auth/register", reg));
        String token = login("newvendor", "123456");

        JsonNode events = data(call(token, "GET", "/api/events", null));
        long eventId = events.get(0).path("id").asLong();

        Map<String, Object> apply = new HashMap<>();
        apply.put("eventId", eventId);
        apply.put("shopName", "新摊手绘社");
        apply.put("category", "HANDMADE");
        apply.put("goodsDescription", "手绘明信片");
        apply.put("foodQualification", "NONE");
        apply.put("donationRate", new BigDecimal("10"));
        apply.put("paymentMethod", "CASH");
        JsonNode created = data(call(token, "POST", "/api/my/applications", apply));
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", created.path("status").asText());

        // 重复报名应被拒绝
        MvcResult dup = call(token, "POST", "/api/my/applications", apply);
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(dup));
    }
}
