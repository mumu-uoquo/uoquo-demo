package com.uoquo.demo.model.pojo;

/**
 * 事件场景测试用的业务数据：只在 demo-service-user 中存在，demo-service-book 没有同名类。
 *
 * <p>用来验证接收方无法还原数据类型时，数据降级为 Map，且声明了具体泛型的监听器不会被误调用。</p>
 */
public class ScnUserOnly {

    private String note;

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
