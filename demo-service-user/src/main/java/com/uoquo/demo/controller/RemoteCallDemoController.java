/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.controller;

import com.uoquo.demo.remote.BookRemoteService;
import com.uoquo.web.ReturnData;
import com.uoquo.annotation.web.IgnoreAuth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 远程调用演示控制器（User_Service）。
 *
 * <p>专用于演示 User_Service 经由 {@link BookRemoteService} 对 Book_Service 的远程调用。
 * 该控制器不包含任何 Redis 演示或事件发布接口。</p>
 *
 * <p>全部接口标注 {@code @IgnoreAuth(all = true)}，免认证即可访问。</p>
 */
@Tag(name = "remote-call-demo")
@RestController
@RequestMapping("/demo/remote")
@IgnoreAuth(all = true)
public class RemoteCallDemoController {

    private static final Logger log = LoggerFactory.getLogger(RemoteCallDemoController.class);

    @Autowired
    private BookRemoteService bookRemoteService;

    /**
     * 经由 {@link BookRemoteService} 远程调用 Book_Service 获取书名。
     *
     * <p>成功时返回包含书名的 {@link ReturnData}；
     * 异常时捕获并返回包含错误说明的非成功 {@link ReturnData}，不向调用方抛出未处理异常。</p>
     *
     * @return 包装书名字符串的 ReturnData，或包装错误说明的非成功 ReturnData
     */
    @GetMapping("/book/name")
    @Operation(summary = "远程调用 Book_Service 获取书名", operationId = "remoteGetBookName")
    public ReturnData<String> getBookName() {
        try {
            String name = bookRemoteService.getBookName();
            return new ReturnData<>(name);
        } catch (Exception e) {
            log.error("远程调用 Book_Service 失败", e);
            throw e;
        }
    }
}
