package com.example.ordersystem.config;

//import com.example.ordersystem.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//WebConfig添加LoginInterceptor的功能完全被SecurityConfig覆盖了
@Configuration
public class WebConfig implements WebMvcConfigurer {

    //如果 WebConfig 还包含其他自定义配置（如静态资源映射、跨域 CORS、消息转换器、参数解析器等）：则保留 WebConfig，仅移除 addInterceptors 方法。
}