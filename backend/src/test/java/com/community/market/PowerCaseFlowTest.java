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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 用电超载处置闭环：跳闸建档（申报用电/设备照片/电工记录）→ 判责 →
 * 停用设备/调整摊位/增加电箱 + 停电补偿 → 锁定大功率设备（巡检拦截）→
 * 电工复核解锁、异常自动闭环、补偿进入复盘。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PowerCaseFlowTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper om;

    private String login(String username, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("username", username, "password", password))))
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
        JsonNode root = om.readTree(r.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(0, root.path("code").asInt(),
                "接口应成功：" + r.getResponse().getContentAsString());
        return root.path("data");
    }

    private int code(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).path("code").asInt();
    }

    @Test
    void powerOverloadHandlingFlow() throws Exception {
        String admin = login("admin", "admin123");
        String staff = login("staff", "staff123");

        long eventId = data(call(admin, "GET", "/api/events", null)).get(0).path("id").asLong();

        // 审核通过全部报名并智能分配
        JsonNode apps = data(call(admin, "GET", "/api/events/" + eventId + "/applications", null));
        long foodAppId = 0, charityAppId = 0;
        for (JsonNode a : apps) {
            long id = a.path("id").asLong();
            // 测试类共享同一上下文数据库，仅审核仍处于待审核状态的报名
            if ("SUBMITTED".equals(a.path("status").asText())) {
                data(call(admin, "POST", "/api/applications/" + id + "/review",
                        Map.of("approved", true, "comment", "通过")));
            }
            if ("FOOD".equals(a.path("category").asText())
                    && "laowang".equals(a.path("vendor").path("username").asText())) {
                foodAppId = id;
            }
            if ("CHARITY".equals(a.path("category").asText())) {
                charityAppId = id;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foodAppId > 0 && charityAppId > 0);
        data(call(admin, "POST", "/api/allocations/auto", Map.of("eventId", eventId)));

        // 找一个可供电的空闲摊位作为调整目标（食品区有 5 个供电摊，仅占用部分）
        JsonNode stalls = data(call(admin, "GET", "/api/stalls?eventId=" + eventId, null));
        long moveTarget = 0;
        for (JsonNode s : stalls) {
            if ("FREE".equals(s.path("status").asText()) && s.path("powerAvailable").asBoolean()) {
                moveTarget = s.path("id").asLong();
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(moveTarget > 0, "应存在可供电的空闲摊位用于调整");

        // 1. 登记电箱跳闸（证据：申报用电/设备照片/电工记录；涉及老王凉粉，波及公益摊）
        Map<String, Object> report = new HashMap<>();
        report.put("eventId", eventId);
        report.put("boxCode", "PX-FOOD-02");
        report.put("boxCapacityWatt", 3000);
        report.put("trippedLoadWatt", 4200);
        report.put("description", "老王凉粉等饮品摊同时启动加热设备，叠加超载致漏保跳闸");
        report.put("devicePhotoUrls", List.of("photo://device-001.jpg", "photo://box-002.jpg"));
        report.put("electricianName", "电工老赵");
        report.put("electricianRecord", "现场测得跳闸时回路 4200W，超过额定 3000W，临时断电排查");
        report.put("involvedApplicationIds", List.of(foodAppId));
        report.put("affectedApplicationIds", List.of(charityAppId));
        JsonNode pc = data(call(staff, "POST", "/api/onsite/power-cases", report));
        long caseId = pc.path("id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals("REPORTED", pc.path("status").asText());
        org.junit.jupiter.api.Assertions.assertTrue(pc.path("incidentId").asLong() > 0);
        org.junit.jupiter.api.Assertions.assertEquals(2, pc.path("devicePhotoUrls").size());

        // 同步生成的用电超载异常
        JsonNode incidents = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean hasPowerIncident = false;
        long powerIncidentId = pc.path("incidentId").asLong();
        for (JsonNode i : incidents) {
            if (i.path("id").asLong() == powerIncidentId) {
                hasPowerIncident = "POWER_OVERLOAD".equals(i.path("type").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasPowerIncident);

        // 2. 判责：摊主违规超载，摊主担责 → 信用扣减
        int creditBefore = data(call(admin, "GET", "/api/applications/" + foodAppId, null))
                .path("vendor").path("creditScore").asInt();
        data(call(staff, "POST", "/api/onsite/power-cases/" + caseId + "/judge",
                Map.of("responsibility", "VENDOR_FAULT",
                        "responsibilityNote", "申报800W实际多台加热叠加，照片与电工记录印证超载")));
        int creditAfter = data(call(admin, "GET", "/api/applications/" + foodAppId, null))
                .path("vendor").path("creditScore").asInt();
        org.junit.jupiter.api.Assertions.assertTrue(creditAfter < creditBefore, "摊主担责应扣信用");

        // 3. 处置：停用设备 + 调整摊位 + 增加电箱；对波及摊主补偿 30 元
        Map<String, Object> dispose = new HashMap<>();
        dispose.put("measures", List.of("DEVICE_STOP", "STALL_MOVE", "BOX_ADD"));
        dispose.put("moveToStallId", moveTarget);
        dispose.put("measureNote", "停用第二台电磁炉，迁往独立回路摊位，新增 2000W 电箱 PX-FOOD-03");
        dispose.put("compensations", List.of(Map.of(
                "applicationId", charityAppId, "amount", new BigDecimal("30.00"),
                "reason", "停电约40分钟影响义卖经营")));
        JsonNode disposed = data(call(staff, "POST", "/api/onsite/power-cases/" + caseId + "/dispose", dispose));
        org.junit.jupiter.api.Assertions.assertEquals("DISPOSED", disposed.path("status").asText());

        // 责任摊位被锁定、已改派
        JsonNode foodAfter = data(call(admin, "GET", "/api/applications/" + foodAppId, null));
        org.junit.jupiter.api.Assertions.assertTrue(foodAfter.path("highPowerLocked").asBoolean());
        org.junit.jupiter.api.Assertions.assertEquals(moveTarget, foodAfter.path("stallId").asLong());

        // 4. 未复核前巡检使用大功率设备被拦截；低功率可正常巡检
        Map<String, Object> highPowerInspection = new HashMap<>();
        highPowerInspection.put("applicationId", foodAppId);
        highPowerInspection.put("actualGoods", "凉粉、冰粉");
        highPowerInspection.put("matched", true);
        highPowerInspection.put("powerUsedWatt", 1500);
        org.junit.jupiter.api.Assertions.assertNotEquals(0,
                code(call(staff, "POST", "/api/onsite/inspections", highPowerInspection)),
                "锁定期间大功率设备巡检应被拦截");

        Map<String, Object> lowPowerInspection = new HashMap<>();
        lowPowerInspection.put("applicationId", foodAppId);
        lowPowerInspection.put("actualGoods", "凉粉、冰粉");
        lowPowerInspection.put("matched", true);
        lowPowerInspection.put("powerUsedWatt", 300);
        data(call(staff, "POST", "/api/onsite/inspections", lowPowerInspection));

        // 5. 电工复核线路负载通过 → 解锁、异常自动闭环
        JsonNode reviewed = data(call(staff, "POST", "/api/onsite/power-cases/" + caseId + "/electrician-review",
                Map.of("reviewedLoadWatt", 1800, "reviewNote", "分路后实测1800W，低于额定，漏保正常")));
        org.junit.jupiter.api.Assertions.assertEquals("REVIEWED", reviewed.path("status").asText());

        JsonNode foodUnlocked = data(call(admin, "GET", "/api/applications/" + foodAppId, null));
        org.junit.jupiter.api.Assertions.assertFalse(foodUnlocked.path("highPowerLocked").asBoolean(), "复核后应解锁");

        JsonNode incidentAfter = data(call(staff, "GET", "/api/onsite/events/" + eventId + "/incidents", null));
        boolean incidentResolved = false;
        for (JsonNode i : incidentAfter) {
            if (i.path("id").asLong() == powerIncidentId) {
                incidentResolved = "RESOLVED".equals(i.path("status").asText());
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(incidentResolved, "复核通过后用电超载异常应自动闭环");

        // 复核后大功率巡检恢复正常
        highPowerInspection.put("powerUsedWatt", 900);
        data(call(staff, "POST", "/api/onsite/inspections", highPowerInspection));

        // 6. 复盘含处置单与停电补偿合计
        JsonNode review = data(call(admin, "GET", "/api/events/" + eventId + "/review", null));
        org.junit.jupiter.api.Assertions.assertTrue(review.path("powerCases").size() >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(0,
                review.path("powerCompensationTotal").decimalValue().compareTo(new BigDecimal("30.00")));
        org.junit.jupiter.api.Assertions.assertEquals(0, review.path("powerCaseUnreviewed").asInt());

        // 7. 流程约束：未勾选责任摊位不能判摊主责任；未判责不能处置/复核
        Map<String, Object> report2 = new HashMap<>();
        report2.put("eventId", eventId);
        report2.put("description", "另一路电箱跳闸，原因待查");
        long case2 = data(call(staff, "POST", "/api/onsite/power-cases", report2)).path("id").asLong();
        org.junit.jupiter.api.Assertions.assertNotEquals(0, code(call(staff, "POST",
                "/api/onsite/power-cases/" + case2 + "/judge",
                Map.of("responsibility", "VENDOR_FAULT", "responsibilityNote", "无责任摊位"))));
        org.junit.jupiter.api.Assertions.assertNotEquals(0, code(call(staff, "POST",
                "/api/onsite/power-cases/" + case2 + "/dispose",
                Map.of("measures", List.of("DEVICE_STOP")))));
        // 未处置直接复核也应被拒绝
        org.junit.jupiter.api.Assertions.assertNotEquals(0, code(call(staff, "POST",
                "/api/onsite/power-cases/" + case2 + "/electrician-review",
                Map.of("reviewedLoadWatt", 1000, "reviewNote", "x"))));

        // 8. 平台侧判责（供电配置不足）不应扣摊主信用
        int beforePlatform = data(call(admin, "GET", "/api/applications/" + charityAppId, null))
                .path("vendor").path("creditScore").asInt();
        data(call(staff, "PUT", "/api/onsite/power-cases/" + case2 + "/evidence",
                Map.of("involvedApplicationIds", List.of(charityAppId))));
        data(call(staff, "POST", "/api/onsite/power-cases/" + case2 + "/judge",
                Map.of("responsibility", "PLATFORM_FAULT", "responsibilityNote", "电箱容量规划不足")));
        int afterPlatform = data(call(admin, "GET", "/api/applications/" + charityAppId, null))
                .path("vendor").path("creditScore").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(beforePlatform, afterPlatform, "平台担责不应扣摊主信用");
    }
}
