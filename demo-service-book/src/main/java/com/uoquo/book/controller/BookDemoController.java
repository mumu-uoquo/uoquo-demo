/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.book.controller;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.book.service.BookDemoService;
import com.uoquo.web.ReturnData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@IgnoreAuth(all = true)
@RestController
@Tag(name = "book-demo")
@RequestMapping("/book")
public class BookDemoController {

    @Autowired
    private BookDemoService bookDemoService;

    @GetMapping("/name")
    @Operation(summary = "获取书名", operationId = "bookGetName")
    public ReturnData<String> getBookName() {
        String name = bookDemoService.getName();
        return new ReturnData<>(name);
    }
}
