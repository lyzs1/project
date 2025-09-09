package com.mall.service.impl;

import com.mall.dao.CategoryMapper;
import com.mall.pojo.Category;
import com.mall.service.ICategoryService;
import com.mall.vo.CategoryVo;
import com.mall.vo.ResponseVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mall.consts.MallConst.ROOT_PARENT_ID;


@Service
public class CategoryServiceImpl implements ICategoryService {

	@Autowired
	private CategoryMapper categoryMapper;

	/**
	 * 耗时：http(请求微信api) > 磁盘 > 内存
	 * mysql(内网+磁盘)
	 * @return
	 */
	@Override
	public ResponseVo<List<CategoryVo>> selectAll() {

		//一次性取出所有类别对象
		List<Category> categories = categoryMapper.selectAll();
		//查出parent_id=0
//		for (Category category : categories) {
//			if (category.getParentId().equals(ROOT_PARENT_ID)) {
//				CategoryVo categoryVo = new CategoryVo();
//				BeanUtils.copyProperties(category, categoryVo);
//				categoryVoList.add(categoryVo);
//			}
//		}

		//lambda + stream
		//对categories过滤出根类别（parentId = 0）
		//倒序排入categoryVoList中，得到第一层
		List<CategoryVo> categoryVoList = categories.stream()
				.filter(e -> e.getParentId().equals(ROOT_PARENT_ID))
				.map(this::category2CategoryVo)
				.sorted(Comparator.comparing(CategoryVo::getSortOrder).reversed())
				.collect(Collectors.toList());

		findSubCategory(categoryVoList, categories);

		return ResponseVo.success(categoryVoList);
	}

	@Override
	public void findSubCategoryId(Integer id, Set<Integer> resultSet) {
		List<Category> categories = categoryMapper.selectAll();
		findSubCategoryId(id, resultSet, categories);
	}

	//递归方法 将本类目及其所有子类目id放进集合
	private void findSubCategoryId(Integer id, Set<Integer> resultSet, List<Category> categories) {
		for (Category category : categories) {
			if (category.getParentId().equals(id)) {
				resultSet.add(category.getId());
				findSubCategoryId(category.getId(), resultSet, categories);
			}
		}
	}

	//递归
	private void findSubCategory(List<CategoryVo> categoryVoList, List<Category> categories) {
		for (CategoryVo categoryVo : categoryVoList) {
			List<CategoryVo> subCategoryVoList = new ArrayList<>();

			//对每个父 CategoryVo，扫描全量 categories；
			//凡是 category.parentId == 父vo.id 的就转成 CategoryVo 加进 subCategoryVoList；
			for (Category category : categories) {
				//如果查到内容，设置subCategory, 继续往下查
				if (categoryVo.getId().equals(category.getParentId())) {
					CategoryVo subCategoryVo = category2CategoryVo(category);
					subCategoryVoList.add(subCategoryVo);
				}

				subCategoryVoList.sort(Comparator.comparing(CategoryVo::getSortOrder).reversed());
				categoryVo.setSubCategories(subCategoryVoList);

				//递归对这批子节点再找它们的子节点
				findSubCategory(subCategoryVoList, categories);
			}
		}
	}

	private CategoryVo category2CategoryVo(Category category) {
		CategoryVo categoryVo = new CategoryVo();
		BeanUtils.copyProperties(category, categoryVo);
		return categoryVo;
	}
}
