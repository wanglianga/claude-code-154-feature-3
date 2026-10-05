package com.community.market.controller;

import com.community.market.common.ApiResult;
import com.community.market.common.BizException;
import com.community.market.domain.enums.UserRole;
import com.community.market.dto.PowerDtos;
import com.community.market.security.CurrentUser;
import com.community.market.security.TokenStore;
import com.community.market.service.PowerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 用电超载/跳闸处置接口（现场工作人员/管理员）：
 * 登记工单（申报用电+设备照片）→ 责任判定 → 电工处理 → 停用/调摊/增箱措施
 * → 停电补偿 → 整改提交 → 电工复核线路负载后闭环。
 */
@RestController
@RequestMapping("/api/onsite/power")
@RequiredArgsConstructor
public class PowerController {

    private final PowerService powerService;
    private final TokenStore tokenStore;

    @Value("${app.power.upload-dir:uploads/power}")
    private String uploadDir;

    // ---------- 工单 ----------

    @PostMapping("/cases")
    public ApiResult<Object> report(@Valid @RequestBody PowerDtos.CreateCaseRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.reportCase(CurrentUser.userId(), req));
    }

    @GetMapping("/events/{eventId}/cases")
    public ApiResult<Object> list(@PathVariable Long eventId) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.list(eventId));
    }

    @GetMapping("/cases/{id}")
    public ApiResult<Object> detail(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.detail(id));
    }

    /** 追加上传的现场设备照片地址 */
    @PostMapping("/cases/{id}/photos")
    public ApiResult<Object> appendPhotos(@PathVariable Long id, @RequestBody Map<String, List<String>> body) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.appendPhotos(id, body.getOrDefault("urls", List.of())));
    }

    @PostMapping("/cases/{id}/liability")
    public ApiResult<Object> liability(@PathVariable Long id, @Valid @RequestBody PowerDtos.LiabilityRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.judgeLiability(id, req));
    }

    @PostMapping("/cases/{id}/electrician")
    public ApiResult<Object> electrician(@PathVariable Long id, @Valid @RequestBody PowerDtos.ElectricianRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.recordElectrician(id, req));
    }

    @PostMapping("/cases/{id}/actions")
    public ApiResult<Object> action(@PathVariable Long id, @Valid @RequestBody PowerDtos.ActionRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.addAction(CurrentUser.userId(), id, req));
    }

    @PostMapping("/cases/{id}/compensations")
    public ApiResult<Object> compensation(@PathVariable Long id, @Valid @RequestBody PowerDtos.CompensationRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.addCompensation(CurrentUser.userId(), id, req));
    }

    @PostMapping("/compensations/{id}/pay")
    public ApiResult<Object> payCompensation(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.payCompensation(id));
    }

    @PostMapping("/parties/{partyId}")
    public ApiResult<Object> updateParty(@PathVariable Long partyId, @Valid @RequestBody PowerDtos.PartyItem req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.updateParty(partyId, req));
    }

    @PostMapping("/cases/{id}/submit-rectification")
    public ApiResult<Object> submitRectification(@PathVariable Long id) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.submitRectification(id));
    }

    @PostMapping("/cases/{id}/review")
    public ApiResult<Object> review(@PathVariable Long id, @Valid @RequestBody PowerDtos.ReviewRequest req) {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        return ApiResult.ok(powerService.review(CurrentUser.userId(), id, req));
    }

    // ---------- 现场设备照片上传（存本地，公开读路径 /api/public/power-files/**） ----------

    @PostMapping("/photos")
    public ApiResult<Object> uploadPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        tokenStore.requireRole(UserRole.ADMIN, UserRole.STAFF);
        if (file.isEmpty()) {
            throw new BizException("照片文件为空");
        }
        String original = file.getOriginalFilename() == null ? "photo.jpg" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String fileName = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dir = Paths.get(uploadDir).toAbsolutePath();
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(fileName));
        return ApiResult.ok(Map.of(
                "url", "/api/public/power-files/" + fileName,
                "name", original));
    }
}
