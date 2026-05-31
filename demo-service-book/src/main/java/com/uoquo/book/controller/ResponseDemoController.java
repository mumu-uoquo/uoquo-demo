/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.book.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.book.model.param.LoginParam;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.SystemErrorException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 返回类型演示控制器（Book_Service）。
 *
 * <p>演示各种返回类型与异常处理，全部接口免认证。</p>
 */
@Tag(name = "response-demo")
@RestController
@RequestMapping("/demo/response")
@IgnoreAuth(all = true)
public class ResponseDemoController {

    /**
     * 返回 String 类型演示。
     */
    @GetMapping("/string")
    @Operation(summary = "返回 String 类型", operationId = "bookResponseString")
    public ReturnData<String> getString() {
        return new ReturnData<>("book-service response string");
    }

    /**
     * 演示 Exception 异常处理。
     */
    @GetMapping("/error1")
    @Operation(summary = "演示 Exception 异常处理", operationId = "bookResponseError1")
    public ReturnData<Long> getError1() throws Exception {
        if (CurrentUser.getInfo().getUserId() == null) {
            throw new Exception("模拟Exception错误");
        }
        return new ReturnData<>(1L);
    }

    /**
     * 演示 SystemErrorException 异常处理。
     */
    @GetMapping("/error2")
    @Operation(summary = "演示 SystemErrorException 异常处理", operationId = "bookResponseError2")
    public ReturnData<Long> getError2() throws Exception {
        if (CurrentUser.getInfo().getUserId() == null) {
            throw new SystemErrorException("模拟BaseException错误");
        }
        return new ReturnData<>(1L);
    }

    /**
     * 演示接收请求体参数。
     */
    @PostMapping("/body")
    @Operation(summary = "演示接收请求体参数", operationId = "bookResponseBody")
    public ReturnData<String> postBody(@Valid @RequestBody LoginParam param) {
        return new ReturnData<>(param.getUsername());
    }
}
