package com.smartfix.communityapp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
@org.springframework.boot.autoconfigure.domain.EntityScan("com.smartfix.community.domain")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.smartfix.community.repository")
@SpringBootApplication(scanBasePackages={"com.smartfix.communityapp","com.smartfix.community"},exclude=UserDetailsServiceAutoConfiguration.class)
public class CommunityApplication { @org.springframework.context.annotation.Bean java.time.Clock clock(){return java.time.Clock.systemUTC();} public static void main(String[] args) { SpringApplication.run(CommunityApplication.class,args); } }
