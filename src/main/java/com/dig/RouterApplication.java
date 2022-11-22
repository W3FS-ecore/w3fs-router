package com.dig;

import com.dig.common.utils.SpringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.annotation.EnableRetry;

/**
 * @author lp
 * @date 2022-06-20 13:34
 */
@SpringBootApplication
@EnableRetry
public class RouterApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(RouterApplication.class, args);
        SpringUtils.set(context);
        System.out.println("=================Dns application Started.====================");
    }
}
