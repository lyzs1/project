package com.mall;

import com.mall.consts.MallConst;
import com.mall.exception.UserLoginException;
import com.mall.pojo.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


//拦截器：用于拦截和处理HTTP请求的机制
//用于在请求到达Controller之后执行拦截逻辑
//Spring Boot的拦截器基于Spring MVC框架中的HandlerInterceptor接口实现
//通过创建自定义的拦截器类并实现HandlerInterceptor接口，自定义拦截逻辑
@Slf4j
public class UserLoginInterceptor implements HandlerInterceptor {

	/**
	 * 登录拦截器
	 * true 表示继续流程，false表示中断
	 * @param request
	 * @param response
	 * @param handler
	 * @return
	 * @throws Exception
	 */

	//拦截器的主要方法，在目标方法调用之前执行
	//目标方法在 InterceptorConfig 中定义
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		log.info("preHandle...");
		User user = (User) request.getSession().getAttribute(MallConst.CURRENT_USER);
		if (user == null) {
			log.info("user=null");

			//此处抛出异常，被RuntimeExceptionHandler拦截，进而return ResponseVo.error
			throw new UserLoginException();

//			response.getWriter().print("error");
//			return false;
//			return ResponseVo.error(ResponseEnum.NEED_LOGIN);
		}
		return true;
	}
}
