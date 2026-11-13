package com.uoquo.demo.model.pojo;

/**
 * 事件场景测试用的业务数据（商品）：用于验证监听方法声明的泛型与事件数据类型不一致时不会被调用。
 */
public class ScnProduct {

    private String id;

    private String title;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
