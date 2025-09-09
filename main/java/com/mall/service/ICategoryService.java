package com.mall.service;

import com.mall.vo.CategoryVo;
import com.mall.vo.ResponseVo;

import java.util.List;
import java.util.Set;


public interface ICategoryService {

	ResponseVo<List<CategoryVo>> selectAll();

	void findSubCategoryId(Integer id, Set<Integer> resultSet);
}
