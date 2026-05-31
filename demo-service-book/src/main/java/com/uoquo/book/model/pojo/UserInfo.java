/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.book.model.pojo;

import java.util.Date;

/**
 * Table: bko_user
 */
public class UserInfo {
    /**
     * Column: id
     * Type: VARCHAR(32)
     * Remark: ID
     */
    private String id;

    /**
     * Column: institute_id
     * Type: VARCHAR(32)
     * Remark: 所属企业
     */
    private String instituteId;

    /**
     * Column: referral_code
     * Type: VARCHAR(10)
     * Remark: 推荐码
     */
    private String referralCode;

    /**
     * Column: user_name
     * Type: VARCHAR(50)
     * Remark: 姓名
     */
    private String userName;

    /**
     * Column: phone
     * Type: VARCHAR(20)
     * Remark: 电话
     * TODO 需加密存储
     */
    private String phone;

    /**
     * Column: password
     * Type: VARCHAR(100)
     * Remark: 密码
     */
    private String password;

    /**
     * Column: email
     * Type: VARCHAR(100)
     * Remark: 邮箱
     */
    private String email;

    /**
     * Column: state
     * Type: CHAR(6)
     * Remark: 可用状态（001）
     */
    private String state;

    /**
     * Column: state_time
     * Type: DATETIME
     * Remark: 状态时间
     */
    private Date stateTime;

    /**
     * Column: state_memo
     * Type: VARCHAR(100)
     * Remark: 状态备注
     */
    private String stateMemo;

    /**
     * Column: is_pwd_expired
     * Type: BIT
     * Default value: 0
     * Remark: 密码是否过期
     */
    private Boolean pwdExpired;

    /**
     * Column: pwd_level
     * Type: CHAR(6)
     * Remark: 密码强度（002）
     */
    private String pwdLevel;

    /**
     * Column: pwd_edit_time
     * Type: DATETIME
     * Remark: 密码修改时间
     */
    private Date pwdEditTime;

    /**
     * Column: login_error_count
     * Type: INT
     * Default value: 0
     * Remark: 连续登录错误次数
     */
    private Integer loginErrorCount;

    /**
     * Column: lasted_login_ip
     * Type: VARCHAR(50)
     * Remark: 最后登录ip
     */
    private String lastedLoginIp;

    /**
     * Column: lasted_login_time
     * Type: DATETIME
     * Remark: 最后登录时间
     */
    private Date lastedLoginTime;

    /**
     * Column: create_user
     * Type: VARCHAR(32)
     * Remark: 创建人
     */
    private String createUser;

    /**
     * Column: create_time
     * Type: DATETIME
     * Remark: 创建时间
     */
    private Date createTime;

    /**
     * Column: update_user
     * Type: VARCHAR(32)
     * Remark: 更新人
     */
    private String updateUser;

    /**
     * Column: update_time
     * Type: DATETIME
     * Remark: 更新时间
     */
    private Date updateTime;

    /**
     * Column: delete_state
     * Type: BIGINT
     * Default value: 0
     * Remark: 删除标识
     */
    private Long deleteState;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? null : id.trim();
    }

    public String getInstituteId() {
        return instituteId;
    }

    public void setInstituteId(String instituteId) {
        this.instituteId = instituteId == null ? null : instituteId.trim();
    }

    public String getReferralCode() {
        return referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode == null ? null : referralCode.trim();
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName == null ? null : userName.trim();
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state == null ? null : state.trim();
    }

    public Date getStateTime() {
        return stateTime;
    }

    public void setStateTime(Date stateTime) {
        this.stateTime = stateTime;
    }

    public String getStateMemo() {
        return stateMemo;
    }

    public void setStateMemo(String stateMemo) {
        this.stateMemo = stateMemo == null ? null : stateMemo.trim();
    }

    public Boolean getPwdExpired() {
        return pwdExpired;
    }

    public void setPwdExpired(Boolean pwdExpired) {
        this.pwdExpired = pwdExpired;
    }

    public String getPwdLevel() {
        return pwdLevel;
    }

    public void setPwdLevel(String pwdLevel) {
        this.pwdLevel = pwdLevel == null ? null : pwdLevel.trim();
    }

    public Date getPwdEditTime() {
        return pwdEditTime;
    }

    public void setPwdEditTime(Date pwdEditTime) {
        this.pwdEditTime = pwdEditTime;
    }

    public Integer getLoginErrorCount() {
        return loginErrorCount;
    }

    public void setLoginErrorCount(Integer loginErrorCount) {
        this.loginErrorCount = loginErrorCount;
    }

    public String getLastedLoginIp() {
        return lastedLoginIp;
    }

    public void setLastedLoginIp(String lastedLoginIp) {
        this.lastedLoginIp = lastedLoginIp == null ? null : lastedLoginIp.trim();
    }

    public Date getLastedLoginTime() {
        return lastedLoginTime;
    }

    public void setLastedLoginTime(Date lastedLoginTime) {
        this.lastedLoginTime = lastedLoginTime;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser == null ? null : createUser.trim();
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(String updateUser) {
        this.updateUser = updateUser == null ? null : updateUser.trim();
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Long getDeleteState() {
        return deleteState;
    }

    public void setDeleteState(Long deleteState) {
        this.deleteState = deleteState;
    }
}
