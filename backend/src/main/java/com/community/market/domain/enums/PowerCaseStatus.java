package com.community.market.domain.enums;

/**
 * 用电跳闸处置工单状态：
 * REPORTED 已登记（摊主申报+现场照片，待处置）
 * HANDLING 处置中（已下达停用/调摊/增箱措施）
 * PENDING_REVIEW 整改完成，等待电工复核线路负载
 * REVIEWED 电工复核通过，工单闭环
 */
public enum PowerCaseStatus {
    REPORTED("已登记待处置"),
    HANDLING("处置中"),
    PENDING_REVIEW("整改完成待复核"),
    REVIEWED("复核通过已闭环");

    private final String label;

    PowerCaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
