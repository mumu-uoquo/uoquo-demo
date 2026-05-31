package com.uoquo.user.model.pojo;

import com.uoquo.mybatis.sensitive.SensitiveData;
import com.uoquo.mybatis.sensitive.SensitiveField;

import java.util.Date;

/**
 * Table: bko_user
 */
@SensitiveData
public class UserInfo {
    /**
     * Column: id
     * Type: VARCHAR(32)
     * Remark: ID
     */
    private String id;

    /**
     * Column: user_name
     * Type: VARCHAR(50)
     * Remark: 账户
     */
    private String userName;

    /**
     * Column: real_name
     * Type: VARCHAR(100)
     * Remark: 姓名
     */
    @SensitiveField
    private String realName;

    /**
     * Column: phone
     * Type: VARCHAR(100)
     * Remark: 电话
     */
    @SensitiveField
    private String phone;

    /**
     * Column: password
     * Type: VARCHAR(100)
     * Remark: 密码
     */
    private String password;

    /**
     * Column: email
     * Type: VARCHAR(200)
     * Remark: 邮箱
     */
    @SensitiveField
    private String email;

    /**
     * Column: create_time
     * Type: DATETIME
     * Remark: 创建时间
     */
    private Date createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? null : id.trim();
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName == null ? null : userName.trim();
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? null : password.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}