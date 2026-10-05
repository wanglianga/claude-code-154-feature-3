package com.community.market.domain.entity;

import com.community.market.domain.enums.PowerActionType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用电处置措施：工作人员可要求部分设备停用、调整摊位或增加电箱。
 */
@Data
@Entity
@Table(name = "power_action")
public class PowerAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PowerActionType type;

    /** 措施针对的报名（ADD_BOX 可为空） */
    private Long applicationId;

    @Column(length = 100)
    private String shopName;

    /** 措施内容，如「停用商用炸炉 1 台（2500W）」 */
    @Column(length = 500)
    private String detail;

    /** RELOCATE：目标摊位 ID */
    private Long targetStallId;

    /** ADD_BOX：新增电箱/分路容量（瓦） */
    private Integer addedCapacityWatt;

    /** RELOCATE/ADD_BOX 后，相关摊大功率设备封锁是否已随措施解除前置（仍以工单复核为准） */
    private boolean executed;

    private Long operatorId;
    private LocalDateTime createdAt = LocalDateTime.now();
}
