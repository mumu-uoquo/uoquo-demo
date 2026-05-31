/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.ssdp.pojo;

import java.util.Date;
import java.util.List;

public class User1 {
    private String name;
    private Date birthday;
    private Integer age;
    private List<String> roles;
    private List<User1> friends;

    public User1() {
    }

    public User1(String name, Date birthday, Integer age) {
        this.name = name;
        this.birthday = birthday;
        this.age = age;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getBirthday() {
        return this.birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    public Integer getAge() {
        return this.age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public List<String> getRoles() {
        return this.roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<User1> getFriends() {
        return this.friends;
    }

    public void setFriends(List<User1> friends) {
        this.friends = friends;
    }
}
