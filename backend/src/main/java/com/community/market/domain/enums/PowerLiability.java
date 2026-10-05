package com.community.market.domain.enums;

/**
 * 跳闸责任判定依据：摊主申报用电、现场设备照片、电工处理记录三方比对。
 * VENDOR_EXCESS 摊主实际设备超出申报/摊位核定（责任在摊主）
 * JOINT_OVERLOAD 多个饮品摊加热设备同时使用，叠加超载（共同责任）
 * CAPACITY_INSUFFICIENT 申报属实但临时电箱容量/分路配置不足（平台责任）
 */
public enum PowerLiability {
    VENDOR_EXCESS("摊主超额用电"),
    JOINT_OVERLOAD("多摊叠加共同超载"),
    CAPACITY_INSUFFICIENT("电箱容量配置不足"),
    MIXED("摊主与场地混合责任");

    private final String label;

    PowerLiability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
