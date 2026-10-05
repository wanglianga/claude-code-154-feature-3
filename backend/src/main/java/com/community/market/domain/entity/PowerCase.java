package com.community.market.domain.entity;

import com.community.market.domain.enums.PowerCaseStatus;
import com.community.market.domain.enums.PowerLiability;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用电超载/跳闸处置工单。
 * 依据「摊主申报用电 + 现场设备照片 + 电工处理记录」三方比对判定责任；
 * 处置措施（停用设备/调摊/增箱）、受影响摊主补偿、电工复核均挂在工单下，
 * 并同步进入活动异常档案与复盘。
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

    /** 跳闸的临时电箱编号（同箱摊位用电叠加） */
    @Column(length = 50)
    private String boxCode;

    /** 工单标题，如「食品区 1 号临时电箱跳闸」 */
    @Column(nullable = false, length = 200)
    private String title;

    /** 跳闸经过描述 */
    @Column(length = 1000)
    private String description;

    /** 现场设备照片地址（多张以逗号分隔） */
    @Column(length = 2000)
    private String devicePhotoUrls;

    /** 电箱额定容量（瓦） */
    private Integer boxCapacityWatt;

    /** 同箱各摊申报功率合计（瓦） */
    private Integer totalDeclaredWatt;

    /** 电工现场实测总功率（瓦） */
    private Integer totalActualWatt;

    /** 首个申报/触发跳闸的摊位报名 ID（可空，表示活动整体事件） */
    private Long triggeredByApplicationId;

    /** 责任判定 */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PowerLiability liability;

    /** 责任说明（随补偿一起进入活动复盘） */
    @Column(length = 1000)
    private String liabilityNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PowerCaseStatus status = PowerCaseStatus.REPORTED;

    private Long raisedBy;
    private LocalDateTime raisedAt = LocalDateTime.now();

    /** 同步生成的现场异常档案 ID */
    private Long incidentId;

    // ---------- 电工处理与复核 ----------
    /** 到场电工姓名 */
    @Column(length = 50)
    private String electricianName;

    /** 电工处理记录（合闸、分路、更换等） */
    @Column(length = 1000)
    private String electricianRecord;

    /** 整改后复核：实测线路负载（瓦） */
    private Integer reviewLoadWatt;

    /** 复核负载率（%） */
    private Integer reviewLoadRatio;

    @Column(length = 1000)
    private String reviewComment;

    private Long reviewedBy;
    private LocalDateTime reviewedAt;

    private LocalDateTime updatedAt = LocalDateTime.now();
}
