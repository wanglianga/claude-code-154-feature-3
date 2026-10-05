package com.community.market.domain.enums;

/**
 * 用电跳闸责任判定结果（平台综合摊主申报用电、现场设备照片、电工处理记录判定）。
 */
public enum PowerResponsibility {
    VENDOR_FAULT("摊主违规超载，摊主担责"),
    JOINT("多摊叠加超载，相关摊主分担"),
    PLATFORM_FAULT("供电配置不足，平台侧担责"),
    EQUIPMENT_FAULT("临时电箱/线路设备故障"),
    NONE("无责，外部原因");

    private final String label;

    PowerResponsibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
