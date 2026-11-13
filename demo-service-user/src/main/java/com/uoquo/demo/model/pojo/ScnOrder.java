package com.uoquo.demo.model.pojo;

import java.math.BigDecimal;

/**
 * 事件场景测试用的业务数据（订单）。
 *
 * <p>demo-service-book 中有一个同名但包名不同的 {@code ScnOrder}，
 * 用来验证微服务事件按“简单类名”还原数据类型（发送方、接收方各自持有一份数据类）。</p>
 */
public class ScnOrder {

    private String id;

    private String name;

    private BigDecimal amount;

    public ScnOrder() {
    }

    public ScnOrder(String id, String name, BigDecimal amount) {
        this.id = id;
        this.name = name;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
