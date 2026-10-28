package com.smartfix.communityapp;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
class SkeletonSecurity {
 @Bean SecurityFilterChain skeleton(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a->a.requestMatchers("/actuator/health").permitAll().anyRequest().denyAll()).build();
 }
}
