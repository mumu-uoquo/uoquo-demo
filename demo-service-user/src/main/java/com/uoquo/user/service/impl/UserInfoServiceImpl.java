/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.user.service.impl;

import com.uoquo.mybatis.page.PageList;
import com.uoquo.mybatis.page.PageResult;
import com.uoquo.user.mapper.UserInfoMapper;
import com.uoquo.user.model.dto.UserInfoDto;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.user.service.UserInfoService;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.UoquoEventPublisher;
import com.uoquo.web.mybatis.page.PageHelper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 描述： 系统用户信息 Service实现（精简版，仅保留分页查询与修改密码）. <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-07     uoquo team        创建
 * 2.0          2025           uoquo team        精简为两个操作，改用框架发布器
 * </pre>
 * @since   JDK 1.8
 * @version 2.0
 * @author  uoquo team
 */
@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 框架事件发布器（需求 10.1）：统一经 {@link UoquoEventPublisher} 发布事件，
     * 替换 Spring 的 {@code ApplicationEventPublisher}。
     */
    @Autowired
    private UoquoEventPublisher eventPublisher;

    @Override
    public PageResult<UserInfoDto> getListByPage(Map<String, Object> map, int page, int pageSize) {
        // 需求 2.2：启动分页后查询，selectBySearch 在分页拦截后返回 PageList<UserInfo>
        PageHelper.startPage(page, pageSize);
        PageList<UserInfo> list = (PageList<UserInfo>) userInfoMapper.selectBySearch(map);
        // 转换对象
        List<UserInfoDto> result = new ArrayList<>();
        for (UserInfo item : list) {
            UserInfoDto dto = new UserInfoDto();
            BeanUtils.copyProperties(item, dto);
            result.add(dto);
        }

        return PageResult.of(list, result);
    }

    @Override
    @Transactional
    public boolean changePassword(String id, String oldPassword, String newPassword) {
        UserInfo user = userInfoMapper.selectByPrimaryKey(id);
        if (user == null) {
            // 需求 2.4：用户不存在则返回 false，不更新、不发布事件
            return false;
        }
        if (oldPassword == null || !oldPassword.equals(user.getPassword())) {
            // 需求 2.5：旧密码不匹配则返回 false，不更新、不发布事件
            return false;
        }
        user.setPassword(newPassword);
        int rows = userInfoMapper.updateByPrimaryKey(user);
        if (rows > 0) {
            // 需求 4.1、4.3：更新成功后经框架发布器发布 UserEvent，newData 为更新后的 UserInfo
            AppEvent<UserInfo> event = new AppEvent<>("USER", "CHANGE_PASSWORD", "SUCCESS");
            event.setNewData(user);
            eventPublisher.publishEvent(event);
            // 需求 2.3：成功返回 true
            return true;
        }
        // 需求 4.5：更新未成功则不发布事件
        return false;
    }

}
