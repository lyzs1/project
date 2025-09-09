package com.mall;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

	//重写addInterceptors方法，用于注册拦截器
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new UserLoginInterceptor())
				//拦截 所有接口
				.addPathPatterns("/**")
				//无需拦截的接口
				.excludePathPatterns("/error", "/user/login", "/user/register", "/categories", "/products", "/products/*");
	}
}
//拦截器说明：https://www.cnblogs.com/imreW/p/17438255.html