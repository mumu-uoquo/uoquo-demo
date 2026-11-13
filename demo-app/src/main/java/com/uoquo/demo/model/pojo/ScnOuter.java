package com.uoquo.demo.model.pojo;

/**
 * 事件场景测试用：嵌套类形式的业务数据（{@code ScnOuter$Inner}），
 * 用于验证嵌套类的 dataType 能被正确匹配与还原。
 */
public class ScnOuter {

    public static class Inner {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
