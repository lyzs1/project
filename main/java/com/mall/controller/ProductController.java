package com.mall.controller;

import com.github.pagehelper.PageInfo;
import com.mall.service.IProductService;
import com.mall.vo.ProductDetailVo;
import com.mall.vo.ResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ProductController {

	@Autowired
	private IProductService productService;

	//商品列表api
	// required = false：非必传参数
	// 若未给categoryId赋值，默认为null
	@GetMapping("/products")
	public ResponseVo<PageInfo> list(@RequestParam(required = false) Integer categoryId,
									 @RequestParam(required = false, defaultValue = "1") Integer pageNum,
									 @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
		return productService.list(categoryId, pageNum, pageSize);
	}

	//商品详情api
	@GetMapping("/products/{productId}")
	public ResponseVo<ProductDetailVo> detail(@PathVariable Integer productId) {
		return productService.detail(productId);
	}
}
