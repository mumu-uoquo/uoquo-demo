package com.uoquo.demo.controller;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.user.model.dto.UserInfoDto;
import com.uoquo.web.ReturnData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 返回类型演示控制器。
 *
 * <p>为每种常见返回类型暴露一个接口，统一包装在框架响应对象
 * {@link ReturnData} 中，演示 app-core 如何序列化各种数据类型。</p>
 *
 * <p>全部接口免认证（{@code @IgnoreAuth(all = true)}），仅用于演示。</p>
 */
@Tag(name = "response-demo")
@RestController
@RequestMapping("/demo/response")
@IgnoreAuth(all = true)
public class ResponseDemoController {

    /**
     * 需求 5.1：返回包装了 String 值的 Return_Data。
     */
    @GetMapping("/string")
    @Operation(description = "返回 String 类型", operationId = "responseString")
    public ReturnData<String> string() {
        return new ReturnData<>("hello world");
    }

    /**
     * 需求 5.2：返回包装了 int 值的 Return_Data。
     */
    @GetMapping("/int")
    @Operation(description = "返回 int 类型", operationId = "responseInt")
    public ReturnData<Integer> integer() {
        return new ReturnData<>(42);
    }

    /**
     * 需求 5.3：返回包装了 long 值的 Return_Data。
     */
    @GetMapping("/long")
    @Operation(description = "返回 long 类型", operationId = "responseLong")
    public ReturnData<Long> longValue() {
        return new ReturnData<>(9_223_372_036_854_775_807L);
    }

    /**
     * 需求 5.4：返回包装了 double 值的 Return_Data。
     */
    @GetMapping("/double")
    @Operation(description = "返回 double 类型", operationId = "responseDouble")
    public ReturnData<Double> doubleValue() {
        return new ReturnData<>(3.1415926D);
    }

    /**
     * 需求 5.5：返回包装了 boolean 值的 Return_Data。
     */
    @GetMapping("/boolean")
    @Operation(description = "返回 boolean 类型", operationId = "responseBoolean")
    public ReturnData<Boolean> booleanValue() {
        return new ReturnData<>(true);
    }

    /**
     * 需求 5.6：返回包装了 key 为 String、value 为 Object 的 Map 的 Return_Data。
     */
    @GetMapping("/map")
    @Operation(description = "返回 Map 类型", operationId = "responseMap")
    public ReturnData<Map<String, Object>> map() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "uoquo");
        data.put("count", 100);
        data.put("enabled", true);
        return new ReturnData<>(data);
    }

    /**
     * 需求 5.7：返回包装了 User_Info 对象的 Return_Data。
     */
    @GetMapping("/user")
    @Operation(description = "返回 UserInfo 类型", operationId = "responseUser")
    public ReturnData<UserInfoDto> user() {
        UserInfoDto user = new UserInfoDto();
        user.setId("1");
        user.setUserName("demo");
        user.setRealName("演示用户");
        user.setPhone("13800000000");
        user.setEmail("demo@uoquo.com");
        user.setCreateTime(new Date());
        return new ReturnData<>(user);
    }
}
