package com.uoquo.demo.model.pojo;

import java.math.BigDecimal;

/**
 * 事件场景测试用的业务数据（订单）。
 *
 * <p>放在 {@code *.model.*} 包下，会被事件类扫描器扫描到，用于按简单类名还原事件中的业务数据。</p>
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
