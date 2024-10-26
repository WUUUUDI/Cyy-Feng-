//package com.clf.mianshiren.config;
//
//import com.alibaba.csp.sentinel.adapter.servlet.CommonFilter;
//import org.springframework.boot.web.servlet.FilterRegistrationBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import javax.servlet.Filter;
//
///**
// * @author clf
// * @version 1.0
// */
//@Configuration
//public class FilterConfig {
//
//    @Bean
//    public FilterRegistrationBean sentinelFilterRegistration() {
//        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
//        registration.setFilter(new CommonFilter());
//        registration.addUrlPatterns("/*");
//        registration.setName("sentinelFilter");
//        registration.setOrder(1);
//        return registration;
//    }
//
//}
