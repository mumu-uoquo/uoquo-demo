package com.uoquo.scheduler.main;

import com.uoquo.web.ServiceApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.*;
import org.springframework.context.ApplicationListener;

@SpringBootApplication
public class SchedulerApplication extends ServiceApplication {

    /**
     * 主程序入口.
     */
    public static void main(String[] args) {
        init();
        // 启动应用
        SpringApplication application = new SpringApplication(SchedulerApplication.class);
        // 常用监听器（预留，暂无实际用途）
        application.addListeners((ApplicationListener<ApplicationStartingEvent>) event -> {
            System.out.println("application starting event"); // 系统启动时，此时logback还没有初始化，因此不能使用log
        });
        application.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            log.info("application environment prepared event"); // 加载完properties，打印banner之前
        });
        application.addListeners((ApplicationListener<ApplicationPreparedEvent>) event -> {
            log.info("application prepared event"); // 启动过程中多次执行
        });
        application.addListeners((ApplicationListener<ApplicationStartedEvent>) event -> {
            log.info("application started event");  // 系统main运行完成（JVM running 后, ApplicationRunner.run前）
        });
        application.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> {
            log.info("application ready event");   // 系统启动完毕后（ApplicationRunner.run后）
        });
        // 启动
        try {
            application.run(args);
        } catch (Throwable ex) {
            // 启动失败（如 Redis/Nacos 等外部依赖不可用导致 ApplicationContext 启动异常）时，
            // Lettuce/Netty 的非守护线程会一直挂住 JVM，导致“进程在但服务已死”的假死状态。
            // 这里强制退出，让容器编排/守护进程能感知失败并重新拉起。
            System.exit(1);
        }
    }

}
