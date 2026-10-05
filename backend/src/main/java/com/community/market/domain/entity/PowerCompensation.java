package com.community.market.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停电补偿：跳闸影响到其他正常经营的摊主时记录，
 * 补偿金额与责任说明一起进入活动复盘。
 */
@Data
@Entity
@Table(name = "power_compensation")
public class PowerCompensation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false)
    private Long applicationId;

    @Column(length = 100)
    private String shopName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 500)
    private String reason;

    /** 停电时长（分钟），便于复盘核损 */
    private Integer outageMinutes;

    /** PLANNED 已登记 / PAID 已发放 */
    @Column(length = 20)
    private String status = "PLANNED";

    private Long operatorId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;
}
