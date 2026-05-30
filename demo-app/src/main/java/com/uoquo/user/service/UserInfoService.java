/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.user.service;

import java.util.Map;

import com.uoquo.mybatis.page.PageResult;
import com.uoquo.user.model.dto.UserInfoDto;

/**
 * 描述： 系统用户信息 Service接口（精简版，仅保留分页查询与修改密码）. <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-06     uoquo team        创建
 * 2.0          2025           uoquo team        精简为两个操作
 * </pre>
 * @since   JDK 1.8
 * @version 2.0
 * @author  uoquo team
 */
public interface UserInfoService {

    /**
     * 分页查询用户信息.
     * @param map 查询条件
     * @param page 当前页码
     * @param pageSize 每页数据条数
     * @return 用户信息分页结果
     */
    PageResult<UserInfoDto> getListByPage(Map<String, Object> map, int page, int pageSize);

    /**
     * 修改用户密码.
     * @param id 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean changePassword(String id, String oldPassword, String newPassword);

}
