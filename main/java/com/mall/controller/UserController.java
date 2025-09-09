package com.mall.controller;

import com.mall.consts.MallConst;
import com.mall.form.UserLoginForm;
import com.mall.form.UserRegisterForm;
import com.mall.pojo.User;
import com.mall.service.IUserService;
import com.mall.vo.ResponseVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;


@RestController
@Slf4j
public class UserController {

	@Autowired
	private IUserService userService;

	@PostMapping("/user/register")
	public ResponseVo<User> register(@Valid @RequestBody UserRegisterForm userForm) {
		User user = new User();
		//copyProperties:将一个 JavaBean 对象的属性值复制到另一个 JavaBean 对象中
		BeanUtils.copyProperties(userForm, user);
		//dto
		return userService.register(user);
	}

	@PostMapping("/user/login")
	public ResponseVo<User> login(@Valid @RequestBody UserLoginForm userLoginForm,
								  HttpSession session) {
		ResponseVo<User> userResponseVo = userService.login(userLoginForm.getUsername(), userLoginForm.getPassword());

		//相同浏览器、相同设备 ——>  同一个session
		//两层key - value
		//第一层：从请求携带的cookie的JSESSIONID（key）获得sessionId（value） →容器通过sessionId 找到对应的 HttpSession对象
		//第二层：不同用户的session对象互相隔离，通过"MallConst.CURRENT_USER"（key）相同的属性名 在各自session对象中 存入user对象（value）

		//第一次请求
		//客户端发请求→因为未携带sessionId，服务器自动生成sessionId
		//在服务端开辟存储/服务器在响应头里Set-Cookie: JSESSIONID(key)=sessionId(value);
		session.setAttribute(MallConst.CURRENT_USER, userResponseVo.getData());
		log.info("/login sessionId={}", session.getId());

		return userResponseVo;
	}
	//后续请求大部分接口时（除一些无需拦截的接口，见InterceptorConfig.py）
	//浏览器会自动在请求头带上Cookie: JSESSIONID=sessionId
	//服务器取出sessionId，去自己保存的会话存储里读／写该用户的会话数据

	//session保存在内存里，改进版：token+redis
	@GetMapping("/user")
	public ResponseVo<User> userInfo(HttpSession session) {
		log.info("/user sessionId={}", session.getId());
		User user = (User) session.getAttribute(MallConst.CURRENT_USER);
		return ResponseVo.success(user);
	}

	/**
	 * {@link TomcatServletWebServerFactory} getSessionTimeoutInMinutes
	 */
	@PostMapping("/user/logout")
	public ResponseVo logout(HttpSession session) {
		log.info("/user/logout sessionId={}", session.getId());
		//退出登录时，会去除对应session的用户信息
		session.removeAttribute(MallConst.CURRENT_USER);
		return ResponseVo.success();
	}
}

/**
 * cookie与session
 *
 * 当方法中通过HttpSession访问session时，隐式调用request.getSession();
 * 若请求中无session id（第一次登录），服务器自动创建新session对象,生成唯一session id；
 * 若请求中有session id（访问拦截接口时），复用现有session
 * 注：服务器通过解析 session id对应不同 HttpSession 对象
 *
 * session使用键值对持久化存储会话信息
 * 服务器通过Set-Cookie响应头将session id发送给客户端
 * 客户端保存到本地cookie存储session id，后续请求自动携带该cookie(Cookie头中)
 *
 * cookie的生成与存储由服务器控制
 *
 * 登陆失败情况：
 * 1.前端篡改session id
 * 2.服务器重启
 * 3.session过期
 */
/**
 * @Valid : 入参校验 在form类中
 * @RequestBody：接收请求体的结构化数据（JSON,XML）,需要对象接收
 * @RequestParam: 参数赋值，由属性或对象接受
 *
 */
/**
 *
 * 此处使用基于session的身份验证：
 *
 * 服务端验证传入的用户名与密码，验证通过后，生成 session id 保存在服务端（session），
 * 浏览器再次访问时，通过用户拦截器，实现登陆状态保持
 *
 * 缺点：
 * 单机节点session存储在内存中，由tomcat服务器管理和运⾏
 * 1.由于存储在内存，重启后session数据就会丢失。
 * 2.⽹站⽤户量增大，很容易把jvm内存撑满
 * 3.若浏览器 cookie 被窃取，可以通过盗用sessionId绕过身份认证，攻击服务器
 * 4.分布式系统下扩展性不强；
 * 5.跨域问题：浏览器发送http请求时会自动携带与该域匹配的cookie
 *
 * 问题4、5是在分布式系统下才出现的问题，应该用单点登录解决。不是JWT解决的
 *
 * 改进方法：JWT（有过期时间的有效信息的token）：（见项目4）
 */

