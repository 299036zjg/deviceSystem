package com.sems.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.sems.security.AuthInterceptor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;

/**
 * Web 层配置：统一令牌拦截、开发期跨域、日期时间格式。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login");
    }

    /**
     * Web 后台（Vite 开发服务器）与小程序调试时允许跨域；生产部署同源时该配置不生效。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }

    /**
     * 时间统一为 yyyy-MM-dd HH:mm:ss，日期为 yyyy-MM-dd（设计文档 5.1）。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME), new LocalDateSerializer(DATE))
                .deserializers(new LocalDateTimeDeserializer(DATE_TIME), new LocalDateDeserializer(DATE));
    }
}
