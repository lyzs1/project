package com.mall.service;

import com.github.pagehelper.PageInfo;
import com.mall.vo.ProductDetailVo;
import com.mall.vo.ResponseVo;


public interface IProductService {

	ResponseVo<PageInfo> list(Integer categoryId, Integer pageNum, Integer pageSize);

	ResponseVo<ProductDetailVo> detail(Integer productId);
}
