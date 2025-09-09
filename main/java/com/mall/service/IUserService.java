package com.mall.service;

import com.mall.pojo.User;
import com.mall.vo.ResponseVo;


public interface IUserService {

	/**
	 * 注册
	 */
	ResponseVo<User> register(User user);

	/**
	 * 登录
	 */
	ResponseVo<User> login(String username, String password);
}
