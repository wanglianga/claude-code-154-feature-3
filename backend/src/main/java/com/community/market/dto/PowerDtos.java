package com.community.market.dto;

import com.community.market.domain.enums.PowerResponsibility;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class PowerDtos {

    /** 登记临时电箱跳闸（可同时带证据与涉及/受影响摊位，也可随后补录） */
    public record PowerCaseRequest(
            @NotNull Long eventId,
            String boxCode,
            Integer boxCapacityWatt,
            Integer trippedLoadWatt,
            @NotNull String description,
            List<String> devicePhotoUrls,
            String electricianRecord,
            String electricianName,
            List<Long> involvedApplicationIds,
            List<Long> affectedApplicationIds) {}

    /** 补录/更新判责证据：现场设备照片、电工处理记录、涉及与受影响摊位 */
    public record EvidenceRequest(
            List<String> devicePhotoUrls,
            String electricianRecord,
            String electricianName,
            List<Long> involvedApplicationIds,
            List<Long> affectedApplicationIds) {}

    /** 平台判责 */
    public record JudgeRequest(
            @NotNull PowerResponsibility responsibility,
            @NotNull String responsibilityNote) {}

    /** 停电补偿明细 */
    public record CompensationItem(
            @NotNull Long applicationId,
            @NotNull BigDecimal amount,
            String reason) {}

    /** 下发处置措施：停用设备 / 调整摊位 / 增加电箱；波及其他摊主时登记补偿 */
    public record DisposeRequest(
            @NotNull List<String> measures,
            String measureNote,
            Long moveToStallId,
            List<CompensationItem> compensations) {}

    /** 电工复核线路负载 */
    public record ReviewRequest(
            @NotNull Integer reviewedLoadWatt,
            @NotNull String reviewNote) {}
}
