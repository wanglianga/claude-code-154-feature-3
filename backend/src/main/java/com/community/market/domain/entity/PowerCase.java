package com.community.market.domain.entity;

import com.community.market.domain.enums.PowerCaseStatus;
import com.community.market.domain.enums.PowerResponsibility;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用电超载处置单：多个饮品/加热摊位同时使用大功率设备导致临时电箱跳闸。
 * 平台综合「摊主申报用电 + 现场设备照片 + 电工处理记录」判责，
 * 可下发停用设备 / 调整摊位 / 增加电箱等措施；停电波及其他摊主时登记补偿；
 * 处置后须由电工复核线路负载，复核前相关摊位锁定、不得继续使用大功率设备。
 */
@Data
@Entity
@Table(name = "power_case")
public class PowerCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    /** 同步生成的现场异常档案 ID（用电超载），复核通过后自动闭环 */
    private Long incidentId;

    /** 跳闸临时电箱编号 */
    @Column(length = 50)
    private String boxCode;

    /** 电箱额定容量（瓦） */
    private Integer boxCapacityWatt;

    /** 跳闸时估算叠加负载（瓦） */
    private Integer trippedLoadWatt;

    /** 跳闸经过描述 */
    @Column(length = 500, nullable = false)
    private String description;

    /** 现场设备照片（URL/标识，可多张，逗号分隔） */
    @Column(length = 1000)
    private String devicePhotoUrls;

    /** 电工处理记录（跳闸后现场电工的排查/送电记录） */
    @Column(length = 1000)
    private String electricianRecord;

    /** 到场电工姓名 */
    @Column(length = 50)
    private String electricianName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PowerCaseStatus status = PowerCaseStatus.REPORTED;

    /** 判责涉及的责任摊位（报名 ID，可能多个） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "power_case_involved", joinColumns = @JoinColumn(name = "power_case_id"))
    @Column(name = "application_id")
    private List<Long> involvedApplicationIds = new ArrayList<>();

    /** 停电波及的其他摊主（报名 ID，用于补偿与复盘） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "power_case_affected", joinColumns = @JoinColumn(name = "power_case_id"))
    @Column(name = "application_id")
    private List<Long> affectedApplicationIds = new ArrayList<>();

    // ---------- 判责 ----------
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PowerResponsibility responsibility;

    /** 责任认定说明（含依据：申报用电/照片/电工记录） */
    @Column(length = 1000)
    private String responsibilityNote;

    private Long judgedBy;
    private LocalDateTime judgedAt;

    // ---------- 处置措施 ----------
    /** 措施：DEVICE_STOP 停用设备 / STALL_MOVE 调整摊位 / BOX_ADD 增加电箱（可多选） */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "power_case_measure", joinColumns = @JoinColumn(name = "power_case_id"))
    @Column(name = "measure")
    private List<String> measures = new ArrayList<>();

    /** 措施说明（停用哪些设备、迁往何处、新增电箱编号容量等） */
    @Column(length = 1000)
    private String measureNote;

    /** 因调整摊位而改派的目标摊位 ID（STALL_MOVE 时） */
    private Long moveToStallId;

    private Long disposedBy;
    private LocalDateTime disposedAt;

    // ---------- 补偿 ----------
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "power_case_compensation", joinColumns = @JoinColumn(name = "power_case_id"))
    private List<Compensation> compensations = new ArrayList<>();

    // ---------- 电工复核 ----------
    /** 复核后实测线路负载（瓦） */
    private Integer reviewedLoadWatt;

    /** 电工复核结论 */
    @Column(length = 1000)
    private String reviewNote;

    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    private Long raisedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public String getStatusLabel() {
        return status == null ? null : status.getLabel();
    }

    @Transient
    public String getResponsibilityLabel() {
        return responsibility == null ? null : responsibility.getLabel();
    }

    /** 停电补偿明细：补偿给被波及摊主 */
    @Data
    @Embeddable
    public static class Compensation {
        @Column(name = "application_id")
        private Long applicationId;

        @Column(name = "amount", precision = 10, scale = 2)
        private BigDecimal amount;

        @Column(name = "reason", length = 500)
        private String reason;

        protected Compensation() {
        }

        public Compensation(Long applicationId, BigDecimal amount, String reason) {
            this.applicationId = applicationId;
            this.amount = amount;
            this.reason = reason;
        }
    }
}
