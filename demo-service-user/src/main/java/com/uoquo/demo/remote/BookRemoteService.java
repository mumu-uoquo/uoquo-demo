/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.demo.remote;

import com.uoquo.cloud.loadbalancer.IphashLoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;

@FeignClient(name = "demo-service-book", contextId = "BookRemoteService", path = "/api/book")
@LoadBalancerClient(name = "demo-service-book", configuration = IphashLoadBalancerConfiguration.class)
public interface BookRemoteService {

    /**
     * 同步数据状态
     */
    @RequestMapping(value = "/book/name")
    String getBookName();
}
