package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private RefreshInterceptor refreshInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor).
                excludePathPatterns(
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/upload/**",
                        "/user/login",
                        "/user/code"
                ).order(1);
        registry.addInterceptor(refreshInterceptor).addPathPatterns("/**").order(0);

    }
}
