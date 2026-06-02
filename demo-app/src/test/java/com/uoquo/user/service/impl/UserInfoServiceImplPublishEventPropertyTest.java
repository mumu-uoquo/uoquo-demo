// Feature: app-core-demo-controllers, Property 2: 修改密码成功才发布携带更新数据的 User_Event
package com.uoquo.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Date;

import com.uoquo.web.events.AppEvent;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;

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
 * Property 2: 修改密码成功才发布携带更新数据的 User_Event。
 *
 * <p>For any UserInfo 记录与任意 oldPassword 字符串，当 {@code changePassword} 成功（返回 true）时，
 * 恰好通过 {@link UoquoEventPublisher} 发布一个 {@link AppEvent}，且该事件的 newData 等于更新后的
 * UserInfo 记录；当 {@code changePassword} 失败（返回 false）时，不发布任何事件。</p>
 *
 * <p>Validates: Requirements 4.1, 4.3, 4.5</p>
 */
class UserInfoServiceImplPublishEventPropertyTest {

    /**
     * 单一属性测试：覆盖成功（恰好发布一个携带更新后 UserInfo 的 UserEvent）与失败（零发布）两类路径。
     *
     * <p>三类失败被生成器覆盖：用户不存在（selectByPrimaryKey 返回 null）、旧密码不匹配、
     * 更新未生效（updateByPrimaryKey 返回 0）。</p>
     */
    @Property(tries = 100)
    void successPublishesExactlyOneEventWithUpdatedData(
            @ForAll("userInfos") UserInfo stored,
            @ForAll("passwords") String newPassword,
            @ForAll("oldPasswordInputs") String randomOld,
            @ForAll("mostlyTrue") boolean matchOld,
            @ForAll("mostlyTrue") boolean userExists,
            @ForAll("updateRows") int updateRows) throws Exception {

        // --- 准备被测对象与 mock（impl 使用 @Autowired 字段注入，借助反射注入 mock） ---
        UserInfoMapper mapper = mock(UserInfoMapper.class);
        UoquoEventPublisher publisher = mock(UoquoEventPublisher.class);

        UserInfoServiceImpl service = new UserInfoServiceImpl();
        injectField(service, "userInfoMapper", mapper);
        injectField(service, "eventPublisher", publisher);

        // oldPassword 以一定概率等于存储密码，以覆盖匹配 / 不匹配两条路径
        String oldPasswordInput = matchOld ? stored.getPassword() : randomOld;

        // 必须在调用前快照存储密码：changePassword 成功时会就地修改 stored 的密码
        String storedPasswordBefore = stored.getPassword();

        // 期望成功的充要条件（与实现逻辑一致）：用户存在 + 旧密码非空且匹配 + 更新生效
        boolean expectedSuccess = userExists
                && oldPasswordInput != null
                && oldPasswordInput.equals(storedPasswordBefore)
                && updateRows > 0;

        when(mapper.selectByPrimaryKey(any())).thenReturn(userExists ? stored : null);
        when(mapper.updateByPrimaryKey(any())).thenReturn(updateRows);

        // --- 执行 ---
        boolean result = service.changePassword(stored.getId(), oldPasswordInput, newPassword);

        // --- 断言 ---
        assertThat(result).isEqualTo(expectedSuccess);

        if (expectedSuccess) {
            // 需求 4.1：成功时恰好发布一个事件
            // 注意：UoquoEventPublisher 有两个重载——publishEvent(ApplicationEvent) 与 publishEvent(Object)。
            // UserEvent extends AppEvent extends ApplicationEvent，编译期解析到更具体的
            // publishEvent(ApplicationEvent) 重载，故需对该重载做捕获/验证。
            ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(publisher, times(1)).publishEvent(captor.capture());

            ApplicationEvent published = captor.getValue();
            // 需求 4.2（间接）：发布的是 UserEvent（AppEvent<UserInfo> 的具体子类）
            assertThat(published).isInstanceOf(AppEvent.class);

            AppEvent<UserInfo> event = (AppEvent<UserInfo>) published;
            // 需求 4.3：newData 等于更新后的 UserInfo（同一被更新对象，且密码为新密码）
            assertThat(event.getNewData()).isSameAs(stored);
            String expectedNewPassword = newPassword == null ? null : newPassword.trim();
            assertThat(event.getNewData().getPassword()).isEqualTo(expectedNewPassword);
        } else {
            // 需求 4.5：失败时零发布（两个重载均不应被调用）
            verify(publisher, never()).publishEvent(any(ApplicationEvent.class));
            verify(publisher, never()).publishEvent(any(Object.class));
        }
    }

    // ----------------------------------------------------------------------
    // 生成器（Generators）
    // ----------------------------------------------------------------------

    /** 随机 UserInfo：覆盖空串、特殊字符、中文等密码形态。 */
    @Provide
    Arbitrary<UserInfo> userInfos() {
        Arbitrary<String> ids = Arbitraries.strings().withCharRange('a', 'z').numeric()
                .ofMinLength(1).ofMaxLength(32);
        Arbitrary<String> names = Arbitraries.strings().ofMinLength(0).ofMaxLength(20);
        Arbitrary<String> passwords = passwordArbitrary();
        return Combinators.combine(ids, names, passwords).as((id, name, pwd) -> {
            UserInfo user = new UserInfo();
            user.setId(id);
            user.setUserName(name);
            user.setRealName(name);
            user.setPhone("13800000000");
            user.setPassword(pwd);
            user.setEmail("user@example.com");
            user.setCreateTime(new Date());
            return user;
        });
    }

    /** 新密码 / 存储密码生成器（非空）：覆盖空串、ASCII、特殊字符、中文、含空白。 */
    @Provide
    Arbitrary<String> passwords() {
        return passwordArbitrary();
    }

    /** 旧密码输入生成器：可为 null（覆盖 oldPassword == null 的失败分支）。 */
    @Provide
    Arbitrary<String> oldPasswordInputs() {
        return passwordArbitrary().injectNull(0.1);
    }

    /** 偏向 true 的布尔生成器：保证成功路径被充分采样，同时仍覆盖 false。 */
    @Provide
    Arbitrary<Boolean> mostlyTrue() {
        return Arbitraries.of(true, true, true, false);
    }

    /** 更新行数：偏向 1（成功路径），同时覆盖 0（更新未生效则不发布）。 */
    @Provide
    Arbitrary<Integer> updateRows() {
        return Arbitraries.of(1, 1, 1, 0);
    }

    private Arbitrary<String> passwordArbitrary() {
        Arbitrary<String> ascii = Arbitraries.strings().ofMinLength(0).ofMaxLength(16);
        Arbitrary<String> samples = Arbitraries.of(
                "", "p@ss!#$%", "密码123", "中文密码", "a b c", "  spaced  ", "1234567890");
        return Arbitraries.oneOf(ascii, samples);
    }

    /** 反射注入字段（实现采用 @Autowired 字段注入）。 */
    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = UserInfoServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
