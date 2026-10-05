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

/**
 * 用电超载/跳闸处置全流程集成测试：
 * 多个饮品摊加热设备同时使用导致临时电箱跳闸 → 依据申报用电/现场照片/电工记录判定责任
 * → 停用设备/调摊/增加电箱 → 停电波及其他摊主登记补偿（进入复盘）
 * → 整改后电工复核线路负载；未复核前责任摊位大功率设备封锁，复核失败被拒、通过后解封。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PowerOverloadFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        if (root.path("code").asInt() != 0) {
            throw new IllegalStateException("登录失败：" + root);
        }
        return root.path("data").path("token").asText();
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

    private long createEventAndStalls(String admin) throws Exception {
        Map<String, Object> ev = new HashMap<>();
        ev.put("name", "2026 冬季热饮公益集市");
        ev.put("location", "幸福社区广场");
        ev.put("publicGoal", "为社区取暖点募集物资");
        ev.put("goalAmount", new BigDecimal("3000"));
        JsonNode e = data(call(admin, "POST", "/api/events", ev));
        long eventId = e.path("id").asLong();

        // 3 个供电摊（同箱 1000W）：饮品 A、饮品 B、手作摊 C
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> s = new HashMap<>();
            s.put("eventId", eventId);
            s.put("code", "D-0" + i);
            s.put("zone", "食品区");
            s.put("rowNo", 1);
            s.put("colNo", i);
            s.put("powerAvailable", true);
            s.put("powerCapacity", 1000);
            data(call(admin, "POST", "/api/stalls", s));
        }
        return eventId;
    }

    private long registerVendor(String uname, String shop) throws Exception {
        data(call(null, "POST", "/api/auth/register", Map.of(
                "username", uname, "password", "123456", "realName", shop, "phone", "13600000000")));
        String token = login(uname, "123456");
        return token == null ? 0 : 1;
    }

    private long applyApp(String vendorToken, long eventId, String shop, String category, int watt) throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("eventId", eventId);
        a.put("shopName", shop);
        a.put("category", category);
        a.put("goodsDescription", shop + "商品");
        a.put("foodQualification", category.equals("FOOD") ? "COMPLETE" : "NONE");
        a.put("donationRate", new BigDecimal("10"));
        a.put("powerNeeded", watt > 0);
        a.put("powerWanted", watt);
        a.put("paymentMethod", "CASH");
        JsonNode created = data(call(vendorToken, "POST", "/api/my/applications", a));
        return created.path("id").asLong();
    }

    @Test
    void powerOverloadFullFlow() throws Exception {
        String admin = login("admin", "admin123");
        String staff = login("staff", "staff123");
        long eventId = createEventAndStalls(admin);

        // ---------- 1. 三个摊主注册报名：饮品 A 申报 800W、饮品 B 申报 600W、手作 C 申报 0W ----------
        registerVendor("drinkA", "暖冬热饮A");
        registerVendor("drinkB", "暖冬热饮B");
        registerVendor("craftC", "手工饰品C");
        String tA = login("drinkA", "123456");
        String tB = login("drinkB", "123456");
        String tC = login("craftC", "123456");
        long appA = applyApp(tA, eventId, "暖冬热饮A", "FOOD", 800);
        long appB = applyApp(tB, eventId, "暖冬热饮B", "FOOD", 600);
        long appC = applyApp(tC, eventId, "手工饰品C", "HANDMADE", 0);

        // ---------- 2. 审核通过 + 自动分配 ----------
        for (long id : new long[]{appA, appB, appC}) {
            data(call(admin, "POST", "/api/applications/" + id + "/review",
                    Map.of("approved", true, "comment", "符合规则")));
        }
        JsonNode alloc = data(call(admin, "POST", "/api/allocations/auto", Map.of("eventId", eventId)));
        org.junit.jupiter.api.Assertions.assertEquals(3, countAllocated(alloc));

        // ---------- 3. 登记跳闸工单：申报合计 1400W > 箱容 1000W，现场实测 2600W ----------
        Map<String, Object> pc = new HashMap<>();
        pc.put("eventId", eventId);
        pc.put("boxCode", "PDU-D-01");
        pc.put("title", "食品区临时电箱跳闸：多台加热设备同时使用");
        pc.put("description", "两台商用电磁炉+蒸箱同时开启，电箱过载跳闸");
        pc.put("devicePhotoUrls", "/api/public/power-files/demo-photo.jpg");
        pc.put("boxCapacityWatt", 1000);
        pc.put("totalDeclaredWatt", 1400);
        pc.put("totalActualWatt", 2600);
        pc.put("triggeredByApplicationId", appA);
        pc.put("parties", java.util.List.of(
                Map.of("applicationId", appA, "declaredWatt", 800, "actualWatt", 1500,
                        "liable", true, "affectedByOutage", true, "note", "新增炸炉1500W未申报"),
                Map.of("applicationId", appB, "declaredWatt", 600, "actualWatt", 1100,
                        "liable", true, "affectedByOutage", true, "note", "蒸箱实测1100W"),
                Map.of("applicationId", appC, "declaredWatt", 0, "actualWatt", 0,
                        "liable", false, "affectedByOutage", true, "note", "无辜受停电影响")));
        JsonNode caseNode = data(call(staff, "POST", "/api/onsite/power/cases", pc));
        long caseId = caseNode.path("id").asLong();

        // 同步生成用电超载异常档案
        JsonNode incidents = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        long incidentId = 0;
        for (JsonNode i : incidents) {
            if ("POWER_OVERLOAD".equals(i.path("type").asText())) {
                incidentId = i.path("id").asLong();
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(incidentId > 0, "应同步生成用电超载异常");

        // 责任摊位立即封锁大功率设备
        JsonNode aView = data(call(admin, "GET", "/api/applications/" + appA, null));
        org.junit.jupiter.api.Assertions.assertTrue(aView.path("powerLocked").asBoolean(), "责任摊位应被封锁大功率设备");
        JsonNode cView = data(call(admin, "GET", "/api/applications/" + appC, null));
        org.junit.jupiter.api.Assertions.assertFalse(cView.path("powerLocked").asBoolean(), "非责任摊位不应封锁");

        // ---------- 4. 责任判定：多摊叠加共同超载，责任摊主各扣 5 分 ----------
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/liability", Map.of(
                "liability", "JOINT_OVERLOAD",
                "liabilityNote", "A/B 两摊加热设备同时开启，实测 2600W 超电箱 1000W，共同承担责任；C 摊无责")));
        JsonNode aAfter = data(call(admin, "GET", "/api/applications/" + appA, null));
        org.junit.jupiter.api.Assertions.assertEquals(95, aAfter.path("vendor").path("creditScore").asInt(),
                "共同责任应扣 5 分：100-5=95");
        JsonNode cAfter = data(call(admin, "GET", "/api/applications/" + appC, null));
        org.junit.jupiter.api.Assertions.assertEquals(100, cAfter.path("vendor").path("creditScore").asInt(),
                "无责摊主不扣分");

        // ---------- 5. 电工到场处理 ----------
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/electrician", Map.of(
                "electricianName", "周电工",
                "electricianRecord", "断开过载回路，撤除未申报炸炉，将B摊蒸箱临时改接2号箱，合闸恢复",
                "totalActualWatt", 2600)));

        // ---------- 6. 措施：停用 A 摊设备 ----------
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/actions", Map.of(
                "type", "DISABLE_DEVICE", "applicationId", appA,
                "detail", "停用未申报商用炸炉1台（1500W），仅保留 800W 保温桶")));

        // 措施：B 摊调整到空闲摊位 D-03?（已分配完，新建一个供电摊）
        Map<String, Object> s4 = new HashMap<>();
        s4.put("eventId", eventId);
        s4.put("code", "D-04");
        s4.put("zone", "食品区");
        s4.put("powerAvailable", true);
        s4.put("powerCapacity", 2000);
        JsonNode stall4 = data(call(admin, "POST", "/api/stalls", s4));
        long stall4Id = stall4.path("id").asLong();
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/actions", Map.of(
                "type", "RELOCATE", "applicationId", appB, "targetStallId", stall4Id,
                "detail", "调整到独立回路摊位 D-04")));
        JsonNode bMoved = data(call(admin, "GET", "/api/applications/" + appB, null));
        org.junit.jupiter.api.Assertions.assertEquals(stall4Id, bMoved.path("stallId").asLong(), "调摊应生效");

        // 措施：增加电箱 1500W
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/actions", Map.of(
                "type", "ADD_BOX", "detail", "食品区增设 2 号临时分路电箱", "addedCapacityWatt", 1500)));
        JsonNode caseAfterAdd = data(call(staff, "GET", "/api/onsite/power/cases/" + caseId, null));
        org.junit.jupiter.api.Assertions.assertEquals(2500,
                caseAfterAdd.path("powerCase").path("boxCapacityWatt").asInt(), "电箱容量应增加到 2500W");

        // ---------- 7. 受停电波及的 C 摊登记补偿 ----------
        JsonNode comp = data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/compensations", Map.of(
                "applicationId", appC, "amount", new BigDecimal("60"),
                "reason", "停电40分钟，手工灯串断电并错过客流高峰", "outageMinutes", 40)));
        long compId = comp.path("id").asLong();
        JsonNode det1 = data(call(staff, "GET", "/api/onsite/power/cases/" + caseId, null));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                det1.path("compensationTotal").decimalValue().compareTo(new BigDecimal("60")));

        // ---------- 8. 整改完成提交复核：无措施/无电工记录的工单会被拒（本单合法，应进入待复核） ----------
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/submit-rectification", null));
        JsonNode pending = data(call(staff, "GET", "/api/onsite/power/cases/" + caseId, null));
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_REVIEW",
                pending.path("powerCase").path("status").asText());

        // ---------- 9. 电工复核：负载仍超容量（2600 > 2500）必须拒绝 ----------
        MvcResult badReview = call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/review",
                Map.of("reviewLoadWatt", 2600, "reviewComment", "仍然偏高"));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, respCode(badReview), "超载复核应被拒绝");
        JsonNode stillPending = data(call(staff, "GET", "/api/onsite/power/cases/" + caseId, null));
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_REVIEW",
                stillPending.path("powerCase").path("status").asText(), "复核失败应维持待复核状态");
        JsonNode stillLocked = data(call(admin, "GET", "/api/applications/" + appA, null));
        org.junit.jupiter.api.Assertions.assertTrue(stillLocked.path("powerLocked").asBoolean(), "复核未通过不得解封");

        // 封锁期违规巡检使用大功率设备 → 再登记异常并扣分
        MvcResult violInspection = call(staff, "POST", "/api/onsite/inspections", Map.of(
                "applicationId", appA, "actualGoods", "热饮", "matched", true,
                "goodsChanged", false, "powerUsedWatt", 1500, "remark", "封锁期仍开炸炉"));
        data(violInspection);
        JsonNode aViol = data(call(admin, "GET", "/api/applications/" + appA, null));
        org.junit.jupiter.api.Assertions.assertTrue(aViol.path("vendor").path("creditScore").asInt() <= 85,
                "封锁期违规大功率使用应再扣10分（95-10=85）");

        // ---------- 10. 复核通过：实测 1800W ≤ 2500W，工单闭环、异常关闭、解除封锁 ----------
        data(call(staff, "POST", "/api/onsite/power/cases/" + caseId + "/review", Map.of(
                "reviewLoadWatt", 1800, "reviewComment", "各分路电流正常，负载率72%，漏电保护有效")));
        JsonNode done = data(call(staff, "GET", "/api/onsite/power/cases/" + caseId, null));
        org.junit.jupiter.api.Assertions.assertEquals("REVIEWED", done.path("powerCase").path("status").asText());
        org.junit.jupiter.api.Assertions.assertEquals(72, done.path("powerCase").path("reviewLoadRatio").asInt());

        JsonNode unlocked = data(call(admin, "GET", "/api/applications/" + appA, null));
        org.junit.jupiter.api.Assertions.assertFalse(unlocked.path("powerLocked").asBoolean(), "复核通过应解除封锁");
        JsonNode incs = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean incidentClosed = false;
        for (JsonNode x : incs) {
            if (x.path("id").asLong() == incidentId) {
                incidentClosed = "RESOLVED".equals(x.path("status").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(incidentClosed, "复核通过应自动闭环异常档案");

        // ---------- 11. 补偿发放 ----------
        data(call(staff, "POST", "/api/onsite/power/compensations/" + compId + "/pay", null));

        // ---------- 12. 摊主端可见工单与补偿 ----------
        JsonNode mineC = data(call(tC, "GET", "/api/my/applications", null));
        JsonNode cRow = findApp(mineC, appC);
        org.junit.jupiter.api.Assertions.assertTrue(cRow.path("powerCompensations").size() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals("PAID",
                cRow.path("powerCompensations").get(0).path("status").asText());
        org.junit.jupiter.api.Assertions.assertTrue(cRow.path("powerCases").size() >= 1);

        // ---------- 13. 活动档案归集用电工单 ----------
        JsonNode archive = data(call(admin, "GET", "/api/events/" + eventId + "/archive", null));
        org.junit.jupiter.api.Assertions.assertTrue(archive.path("powerCases").size() >= 1);

        // ---------- 14. 复盘含责任说明、补偿、专项建议 ----------
        JsonNode review = data(call(admin, "GET", "/api/events/" + eventId + "/review", null));
        org.junit.jupiter.api.Assertions.assertEquals(1, review.path("stats").path("powerCaseCount").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(0, review.path("stats").path("powerCaseOpen").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(0,
                review.path("stats").path("powerCompensationTotal").decimalValue().compareTo(new BigDecimal("60")));
        org.junit.jupiter.api.Assertions.assertTrue(review.path("powerCases").size() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals("JOINT_OVERLOAD",
                review.path("powerCases").get(0).path("liability").asText());
        boolean hasPowerTip = false;
        for (JsonNode tip : review.path("suggestions")) {
            if (tip.asText().contains("叠加跳闸") || tip.asText().contains("补偿")) {
                hasPowerTip = true;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasPowerTip, "复盘应包含用电处置专项建议");
    }

    private int countAllocated(JsonNode alloc) {
        int n = 0;
        for (JsonNode row : alloc) {
            if (row.path("allocated").asBoolean()) {
                n++;
            }
        }
        return n;
    }

    private JsonNode findApp(JsonNode arr, long appId) {
        for (JsonNode a : arr) {
            if (a.path("id").asLong() == appId) {
                return a;
            }
        }
        throw new IllegalStateException("找不到报名 " + appId);
    }
}
