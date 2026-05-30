/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.user.mapper;

import java.util.List;
import java.util.Map;

import com.uoquo.user.model.pojo.UserInfo;

public interface UserInfoMapper {

    /**
     * 列表查询（分页）
     */
    List<UserInfo> selectBySearch(Map<String, Object> map);

    /**
     * 单条查询：用户ID
     */
    UserInfo selectByPrimaryKey(String id);

    /**
     * 修改
     */
    int updateByPrimaryKey(UserInfo row);
}
