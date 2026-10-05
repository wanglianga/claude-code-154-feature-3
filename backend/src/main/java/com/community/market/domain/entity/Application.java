package com.community.market.domain.entity;

import com.community.market.domain.enums.ApplicationStatus;
import com.community.market.domain.enums.CheckInStatus;
import com.community.market.domain.enums.FoodQualStatus;
import com.community.market.domain.enums.StallType;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 摊主报名（活动档案的核心单据，串联审核、摊位、核销、收款、捐赠、异常、公示）。
 */
@Data
@Entity
@Table(name = "application",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "vendor_id"}))
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(length = 100)
    private String shopName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StallType category;

    /** 具体售卖内容描述 */
    @Column(length = 500)
    private String goodsDescription;

    // ---------- 食品资质 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FoodQualStatus foodQualification = FoodQualStatus.NONE;

    /** 资质证照编号/说明 */
    @Column(length = 200)
    private String foodLicenseNo;

    // ---------- 公益 ----------
    /** 承诺公益捐赠比例（0-100，百分比） */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal donationRate = BigDecimal.ZERO;

    // ---------- 现场需求 ----------
    private boolean powerNeeded;

    /** 申请用电功率（瓦） */
    private Integer powerWanted = 0;

    private boolean tableNeeded;
    private Integer tableCount = 0;
    private boolean chairNeeded;
    private Integer chairCount = 0;

    /** 是否亲子摊位 */
    private boolean familyFriendly;

    /** 是否有音响/叫卖等噪音 */
    private boolean noiseMaking;

    // ---------- 收款 ----------
    /** 收款方式：CASH 现金 / WECHAT 微信 / ALIPAY 支付宝 / MIXED 混合 */
    @Column(length = 20)
    private String paymentMethod = "CASH";

    /** 收款账户说明（如微信昵称/支付宝账号，用于核对流水） */
    @Column(length = 100)
    private String paymentAccount;

    // ---------- 审核 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    private Long reviewerId;

    @Column(length = 500)
    private String reviewComment;

    private LocalDateTime reviewedAt;

    // ---------- 分配 ----------
    private Long stallId;

    /** 分配评分（越大越优先，高人流/大面积给高分摊主） */
    private Integer allocationScore;

    @Column(length = 500)
    private String allocationReason;

    /** 摊主入场二维码内容（token） */
    @Column(length = 100)
    private String checkinCode;

    // ---------- 现场核销 ----------
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckInStatus checkInStatus = CheckInStatus.NOT_CHECKED;

    private LocalDateTime checkInAt;
    private Long checkInStaffId;

    /** 物资是否已领取 */
    private boolean materialsCollected;
    private LocalDateTime materialsCollectedAt;

    // ---------- 现场结果 ----------
    /** 现场实际售卖内容（巡检填写） */
    @Column(length = 500)
    private String actualGoods;

    // ---------- 用电整改封锁 ----------
    /** 大功率设备封锁：跳闸整改未经电工复核通过前，相关摊位不得继续使用大功率设备 */
    private boolean powerLocked;

    @Column(length = 500)
    private String powerLockReason;

    private LocalDateTime powerLockedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
