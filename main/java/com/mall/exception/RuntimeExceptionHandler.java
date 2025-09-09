package com.mall.exception;

import com.mall.enums.ResponseEnum;
import com.mall.vo.ResponseVo;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Objects;

import static com.mall.enums.ResponseEnum.ERROR;


@ControllerAdvice
public class RuntimeExceptionHandler {

	@ExceptionHandler(RuntimeException.class)
	@ResponseBody
//	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ResponseVo handle(RuntimeException e) {
		return ResponseVo.error(ERROR, e.getMessage());
	}

	@ExceptionHandler(UserLoginException.class)
	@ResponseBody
	public ResponseVo userLoginHandle() {
		return ResponseVo.error(ResponseEnum.NEED_LOGIN);
	}


	//表单统一验证处理
	//controller的入参若违反约束（form类），会throw MethodArgumentNotValidException
	//转至此异常处理，return统一格式ResponseVo.error()
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseBody
	public ResponseVo notValidExceptionHandle(MethodArgumentNotValidException e) {
		BindingResult bindingResult = e.getBindingResult();
		Objects.requireNonNull(bindingResult.getFieldError());
		return ResponseVo.error(ResponseEnum.PARAM_ERROR,
				bindingResult.getFieldError().getField() + " " + bindingResult.getFieldError().getDefaultMessage());
	}
}
