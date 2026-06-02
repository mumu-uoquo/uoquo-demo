/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.ssdp;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.uoquo.main.DemoCloudBookApplication;
import com.uoquo.test.ssdp.pojo.User1;
import com.uoquo.test.ssdp.pojo.User2;
import com.uoquo.test.ssdp.pojo.User3;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.json.jackson.DateContextualDeserializer;
import com.uoquo.utils.json.jackson.DateContextualSerializer;
import com.uoquo.utils.spring.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;


@SpringBootTest(classes = {DemoCloudBookApplication.class})
public class RedisTest {

    @Test
    public void testJson(){
        // 2. 设置val的序列化方法(采用jackjson这个里面含有类名)
        // 2.1 自定义序列化和反序列化
        SimpleModule simpleModule = new SimpleModule();
        // 将Long序列化为String，防止JS端的精度丢失
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE,  ToStringSerializer.instance);
        // 日期时间序列化（默认"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"）
        simpleModule.addSerializer(Date.class, new DateContextualSerializer());
        // 日期时间反序列化
        simpleModule.addDeserializer(Date.class, new DateContextualDeserializer());
        // 2.2 将自定义规则加入jackson中
        ObjectMapper mapper = new ObjectMapper();
        //mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        // 反序列化时：忽略多余的属性
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//
        mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        // 允许出现单引号
        //mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

        //mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.registerModule(simpleModule);
        // 序列化时带上对象类
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        //
        User1 user = new User1("张三", new Date(), 20);
        User1 friend = new User1("李四", new Date(), 30);
        user.setRoles(Arrays.asList("roleId"));
        user.setFriends(Arrays.asList(friend));

        byte[] byts = serializer.serialize(user);
        System.out.println(new String(byts));

        Object u0 = serializer.deserialize(byts);
//        System.out.println(u0.getBirthday());

//        mapper.activateDefaultTyping(
//                LaissezFaireSubTypeValidator.instance,
//                ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS,
//                JsonTypeInfo.As.PROPERTY);

        ObjectMapper dmapper = mapper.copy();

        System.out.println(mapper);
        System.out.println(dmapper);

        try {
            User1 u1 = (User1)dmapper.readValue(byts, Object.class);
            System.out.println(u1);
        } catch (Exception ex) {
            ex.printStackTrace();
        }


        String str = "{\"name\":\"张三\",\"birthday\":\"2021-05-11T06:24:28.355Z\",\"age\":20}";
        User2 u2 = serializer.deserialize(str.getBytes(StandardCharsets.UTF_8), User2.class);
        System.out.println(u2);

        try {
            User3 u1 = dmapper.readValue(byts, User3.class);
            System.out.println(u1);
        } catch (Exception ex) {
            try {
                dmapper.deactivateDefaultTyping();
                User3 u1 = dmapper.readValue(byts, User3.class);
                System.out.println(u1);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

//        User3 u3 = serializer.deserialize(byts, User3.class);
//        System.out.println(u3);
//
//        User2 u4 = serializer.deserialize(byts, User2.class);
//        System.out.println(u4);

        System.out.println(u2.getBirthday());
    }

    @Test
    public void testRedis2() {
        User1 user = new User1("张三", new Date(), 20);
        User1 friend = new User1("李四", new Date(), 30);
        user.setRoles(Arrays.asList("roleId"));
        user.setFriends(Arrays.asList(friend));

        System.out.println("before: " + JsonUtil.serialize(user));
        RedisUtil.putListItem("ab", user, 10);
        User1 user1 = RedisUtil.getListPop("ab", User1.class);
        System.out.println("after : " + JsonUtil.serialize(user1));
    }
}
