package com.community.market.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 跳闸工单涉及的摊位（责任方与受影响方）。
 * 记录该摊申报功率 vs 现场实测功率，作为责任判定依据。
 */
@Data
@Entity
@Table(name = "power_case_party")
public class PowerCaseParty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false)
    private Long applicationId;

    /** 摊位名称快照 */
    @Column(length = 100)
    private String shopName;

    /** 报名申报功率（瓦） */
    private Integer declaredWatt;

    /** 现场实测/照片核定功率（瓦） */
    private Integer actualWatt;

    /** 是否承担超载责任 */
    private boolean liable;

    /** 是否被停电波及（可与 liable 同时成立） */
    private boolean affectedByOutage;

    @Column(length = 500)
    private String note;

    /** 判定责任后是否已扣减信用（防止重复判定重复扣分） */
    private boolean creditDeducted;
}
