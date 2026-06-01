package com.uoquo.scheduler.platform.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DemoTask {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Scheduled(cron = "0/10 * * * * ?")
    public void demoTask() {
        log.info("demoTask");
    }
}
