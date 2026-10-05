package com.community.market.dto;

import com.community.market.domain.enums.PowerActionType;
import com.community.market.domain.enums.PowerLiability;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** 用电超载/跳闸处置 DTO */
public class PowerDtos {

    public record PartyItem(
            @NotNull Long applicationId,
            Integer declaredWatt,
            Integer actualWatt,
            boolean liable,
            boolean affectedByOutage,
            String note) {}

    /** 登记跳闸工单：摊主申报用电、现场设备照片、涉及摊位实测 */
    public record CreateCaseRequest(
            @NotNull Long eventId,
            String boxCode,
            @NotBlank String title,
            String description,
            String devicePhotoUrls,
            Integer boxCapacityWatt,
            Integer totalDeclaredWatt,
            Integer totalActualWatt,
            Long triggeredByApplicationId,
            List<PartyItem> parties) {}

    /** 结合申报/照片/电工记录判定责任 */
    public record LiabilityRequest(
            @NotNull PowerLiability liability,
            String liabilityNote) {}

    /** 电工到场处理记录 */
    public record ElectricianRequest(
            @NotBlank String electricianName,
            @NotBlank String electricianRecord,
            Integer totalActualWatt) {}

    /** 处置措施：停用设备 / 调摊 / 增加电箱 */
    public record ActionRequest(
            @NotNull PowerActionType type,
            Long applicationId,
            String detail,
            Long targetStallId,
            Integer addedCapacityWatt) {}

    /** 停电波及其他摊主的补偿登记 */
    public record CompensationRequest(
            @NotNull Long applicationId,
            @NotNull BigDecimal amount,
            String reason,
            Integer outageMinutes) {}

    /** 整改后电工复核线路负载 */
    public record ReviewRequest(
            @NotNull Integer reviewLoadWatt,
            String reviewComment) {}
}
