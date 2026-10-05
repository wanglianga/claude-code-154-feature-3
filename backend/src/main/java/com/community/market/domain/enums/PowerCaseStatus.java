package com.community.market.domain.enums;

/**
 * 用电超载处置单状态：
 * REPORTED  跳闸已登记，待平台判责与处置；
 * DISPOSED  已下发处置措施（停用设备/调整摊位/增加电箱），待电工复核线路负载；
 * REVIEWED  电工复核通过，线路恢复，相关摊位解除大功率设备锁定。
 */
public enum PowerCaseStatus {
    REPORTED("待判责处置"),
    DISPOSED("已处置待电工复核"),
    REVIEWED("电工复核通过");

    private final String label;

    PowerCaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
