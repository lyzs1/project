package com.mall.controller;

import com.mall.service.ICategoryService;
import com.mall.vo.CategoryVo;
import com.mall.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
public class CategoryController {

	@Autowired
	private ICategoryService categoryService;

	//类目树api
	@GetMapping("/categories")
	public ResponseVo<List<CategoryVo>> selectAll() {
		return categoryService.selectAll();
	}
}
