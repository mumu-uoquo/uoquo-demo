package com.uoquo.scheduler.main.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;

/**
 * 应用配置类
 * <ul>
 *   <li>同一个服务只需要注册一个LoadBalancerClient负载均衡器（所以提取到配置类中）</li>
 * </ul>
 */
@Configuration
@Order(0)
public class ApplicationConfig {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @PostConstruct
    public void setProperties() {
        log.debug("ApplicationConfig init ...");
    }

}
