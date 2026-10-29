package com.suishi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@MapperScan({"com.suishi.system.mapper", "com.suishi.story.mapper", "com.suishi.ai.mapper"})
public class SuishiApplication {
    public static void main(String[] args) { SpringApplication.run(SuishiApplication.class, args); }
}
