package com.community.market.domain.enums;

/** 用电处置措施类型：停用部分设备 / 调整摊位 / 增加电箱（分路增容） */
public enum PowerActionType {
    DISABLE_DEVICE("停用设备"),
    RELOCATE("调整摊位"),
    ADD_BOX("增加电箱/分路");

    private final String label;

    PowerActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
