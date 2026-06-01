package com.uoquo.scheduler.common;

/**
 * 字典编码：业务操作类型（010xxx）
 * @author xuhz
 */
public enum BusinessOperationEnum {
    UNKNOWN("000000", "未知"),

    // ==================== 认证操作 (01000x) ====================
    /** 登录认证 */
    LOGIN("010001", "登录"),
    /** Token刷新 */
    REFRESH_TOKEN("010003", "刷新令牌"),
    /** 登出 */
    LOGOUT("010005", "登出"),
    /** 注册 */
    REGISTER("010007", "注册"),

    ;

    private final String code;
    private final String text;

    BusinessOperationEnum(String code, String text) {
        this.code = code;
        this.text = text;
    }

    public String getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public static BusinessOperationEnum getByCode(String code) {
        for (BusinessOperationEnum item : BusinessOperationEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return UNKNOWN;
    }

    public static String getTextByCode(String code) {
        return getTextByCode(code, UNKNOWN);
    }

    public static String getTextByCode(String code, BusinessOperationEnum defaultValue) {
        for (BusinessOperationEnum item : BusinessOperationEnum.values()) {
            if (item.getCode().equals(code)) {
                return item.getText();
            }
        }
        return defaultValue.getText();
    }
}
