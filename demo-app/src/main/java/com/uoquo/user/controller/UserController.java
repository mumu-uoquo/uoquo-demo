package com.uoquo.user.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.mybatis.page.PageResult;
import com.uoquo.user.model.dto.UserInfoDto;
import com.uoquo.user.model.param.ChangePasswordParam;
import com.uoquo.user.service.UserInfoService;
import com.uoquo.web.ReturnData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 用户管理控制器（精简版，仅保留分页查询与修改密码两个接口）。
 */
@Tag(name = "user")
@RestController
@RequestMapping("/user")
@IgnoreAuth(all = true)
public class UserController {

    @Autowired
    private UserInfoService userInfoService;

    /**
     * 用户分页查询，返回 ReturnData<PageResult<UserInfoDto>>。
     */
    @GetMapping("/list")
    @Operation(description = "用户分页查询", operationId = "userList")
    public ReturnData<PageResult<UserInfoDto>> list(
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> params = new HashMap<>();
        if (userName != null && !userName.trim().isEmpty()) {
            params.put("userName", userName);
        }
        return new ReturnData<>(userInfoService.getListByPage(params, page, pageSize));
    }

    /**
     * 修改密码，返回 ReturnData<Boolean>。
     */
    @PostMapping("/changePassword")
    @Operation(description = "修改密码", operationId = "changePassword")
    public ReturnData<Boolean> changePassword(@Valid @RequestBody ChangePasswordParam param) {
        boolean ok = userInfoService.changePassword(
                param.getId(), param.getOldPassword(), param.getNewPassword());
        return new ReturnData<>(ok);
    }
}
