package com.uoquo.user.model.param;

import com.uoquo.annotation.json.Sensitive;
import com.uoquo.annotation.json.SensitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 入参：修改用户密码
 */
@Schema(description = "新增用户信息")
public class ChangePasswordParam {

    @Schema(description = "用户id")
    private String id;

    @Schema(description = "旧密码")
    private String oldPassword;

    @Schema(description = "新密码")
    @NotBlank
    @Sensitive(type = SensitiveType.CRYPT_AES) // JSON AES解密
    private String newPassword;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
