package com.uoquo.user.model.dto;

import com.uoquo.annotation.json.Sensitive;
import com.uoquo.annotation.json.SensitiveType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

/**
 * 出参：用户信息
 */
@Schema(description = "用户信息")
public class UserInfoDto {

    @Schema(description = "用户id", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "用户账号")
    private String userName;

    @Sensitive(type = SensitiveType.NAME) // JSON 敏感数据处理
    @Schema(description = "真实姓名")
    private String realName;

    @Sensitive(type = SensitiveType.PHONE) // JSON 敏感数据处理
    @Schema(description = "电话")
    private String phone;

    @Sensitive(type = SensitiveType.EMAIL) // JSON 敏感数据处理
    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "创建时间")
    private Date createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
