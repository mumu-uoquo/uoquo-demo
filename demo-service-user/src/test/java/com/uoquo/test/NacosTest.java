/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class NacosTest {

    @Test
    public void testPsw(){
        System.out.println(new BCryptPasswordEncoder().encode("nacos"));

    }
}
