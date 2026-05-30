// Feature: app-core-demo-controllers, Property 1: 修改密码校验逻辑
/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.uoquo.user.mapper.UserInfoMapper;
import com.uoquo.user.model.pojo.UserInfo;
import com.uoquo.web.events.UoquoEventPublisher;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property 1：修改密码校验逻辑。
 *
 * <p>For any UserInfo 记录与任意 oldPassword 字符串，
 * {@code changePassword(id, oldPassword, newPassword)} 当且仅当该 id 能解析到存储记录、
 * 且 oldPassword 等于存储密码时返回 true，并将存储密码更新为 newPassword；
 * 在其余情况下返回 false 且不发生任何密码更新。</p>
 *
 * <p>Validates: Requirements 2.3, 2.4, 2.5</p>
 */
class UserInfoServiceImplChangePasswordPropertyTest {

    /**
     * 一次修改密码调用的输入场景。
     *
     * @param storedUser  mapper 在用户存在时返回的记录（其 password 为存储密码）
     * @param userExists  id 是否能解析到存储记录（false 时 selectByPrimaryKey 返回 null）
     * @param oldPassword 提供的旧密码（可能等于存储密码、随机或 null）
     * @param newPassword 新密码
     */
    record Scenario(UserInfo storedUser, boolean userExists, String oldPassword, String newPassword) {
    }

    /**
     * password 生成器：覆盖空串、ASCII、特殊字符与中文。
     */
    @Provide
    Arbitrary<String> passwords() {
        Arbitrary<String> empty = Arbitraries.just("");
        Arbitrary<String> ascii = Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> special = Arbitraries.strings()
                .withChars("!@#$%^&*()_+-=[]{}|;:',.<>/?`~".toCharArray())
                .ofMinLength(1).ofMaxLength(15);
        Arbitrary<String> chinese = Arbitraries.strings()
                .withCharRange('\u4e00', '\u9fa5')
                .ofMinLength(1).ofMaxLength(10);
        return Arbitraries.oneOf(empty, ascii, special, chinese);
    }

    /**
     * UserInfo 生成器：随机填充全部字段，password 覆盖空串/特殊字符/中文。
     */
    @Provide
    Arbitrary<UserInfo> userInfos() {
        Arbitrary<String> ids = Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(16);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(12);
        Arbitrary<String> phones = Arbitraries.strings().numeric().ofMinLength(0).ofMaxLength(11);
        Arbitrary<String> emails = Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(12)
                .map(s -> s.isEmpty() ? "" : s + "@example.com");
        Arbitrary<Date> createTimes = Arbitraries.longs().between(0L, 4102444800000L).map(Date::new);
        return Combinators.combine(ids, names, names, phones, passwords(), emails, createTimes)
                .as((id, userName, realName, phone, password, email, createTime) -> {
                    UserInfo u = new UserInfo();
                    u.setId(id);
                    u.setUserName(userName);
                    u.setRealName(realName);
                    u.setPhone(phone);
                    u.setPassword(password);
                    u.setEmail(email);
                    u.setCreateTime(createTime);
                    return u;
                });
    }

    /**
     * 场景生成器：
     * <ul>
     *   <li>userExists：约 75% 为 true，以覆盖「用户存在」与「用户不存在」两种情形；</li>
     *   <li>oldPassword：约 50% 等于存储（已 trim）密码以命中匹配分支，约 40% 随机、约 10% 为 null。</li>
     * </ul>
     */
    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Boolean> exists = Arbitraries.of(true, true, true, false);
        // 0 = 等于存储密码（匹配）, 1 = 随机, 2 = null
        Arbitrary<Integer> oldPwChoice = Arbitraries.of(0, 0, 0, 0, 0, 1, 1, 1, 1, 2);
        return Combinators.combine(userInfos(), passwords(), exists, oldPwChoice, passwords())
                .as((user, newPassword, userExists, choice, randomOld) -> {
                    String oldPassword;
                    if (choice == 0) {
                        // user.getPassword() 已被 setter trim，等于存储密码
                        oldPassword = user.getPassword();
                    } else if (choice == 1) {
                        oldPassword = randomOld;
                    } else {
                        oldPassword = null;
                    }
                    return new Scenario(user, userExists, oldPassword, newPassword);
                });
    }

    /**
     * Property 1：当且仅当 id 解析到存储记录且 oldPassword 等于存储密码时返回 true
     * 并将密码更新为 newPassword；否则返回 false 且不发生任何密码更新。
     *
     * <p>Validates: Requirements 2.3, 2.4, 2.5</p>
     */
    @Property(tries = 200)
    void changePasswordSucceedsIffUserExistsAndOldPasswordMatches(@ForAll("scenarios") Scenario scenario) {
        UserInfoMapper mapper = Mockito.mock(UserInfoMapper.class);
        UoquoEventPublisher publisher = Mockito.mock(UoquoEventPublisher.class);

        UserInfo storedUser = scenario.storedUser();
        UserInfo returnedUser = scenario.userExists() ? storedUser : null;
        Mockito.when(mapper.selectByPrimaryKey(Mockito.any())).thenReturn(returnedUser);
        // 让更新成功（rows > 0），以驱动成功路径完整执行
        Mockito.when(mapper.updateByPrimaryKey(Mockito.any())).thenReturn(1);

        UserInfoServiceImpl service = new UserInfoServiceImpl();
        ReflectionTestUtils.setField(service, "userInfoMapper", mapper);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);

        // 在调用前捕获存储密码（成功路径会就地修改 user.password）
        String storedPassword = scenario.userExists() ? storedUser.getPassword() : null;
        String oldPassword = scenario.oldPassword();
        String newPassword = scenario.newPassword();

        // 规约级期望：id 解析到记录 且 oldPassword 等于存储密码
        boolean expectedSuccess = scenario.userExists()
                && oldPassword != null
                && oldPassword.equals(storedPassword);

        boolean result = service.changePassword(scenario.storedUser().getId(), oldPassword, newPassword);

        assertThat(result)
                .as("当且仅当用户存在且旧密码匹配时返回 true")
                .isEqualTo(expectedSuccess);

        if (expectedSuccess) {
            // 发生了密码更新：恰好调用一次 updateByPrimaryKey，存储密码更新为 newPassword（setter 会 trim）
            Mockito.verify(mapper, Mockito.times(1)).updateByPrimaryKey(storedUser);
            assertThat(storedUser.getPassword())
                    .as("成功时存储密码应更新为 newPassword")
                    .isEqualTo(newPassword == null ? null : newPassword.trim());
        } else {
            // 不发生任何密码更新
            Mockito.verify(mapper, Mockito.never()).updateByPrimaryKey(Mockito.any());
            if (returnedUser != null) {
                assertThat(returnedUser.getPassword())
                        .as("失败时存储密码保持不变")
                        .isEqualTo(storedPassword);
            }
        }
    }
}
