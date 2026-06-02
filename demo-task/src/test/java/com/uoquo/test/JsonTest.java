package com.uoquo.test;

import com.uoquo.mybatis.page.PageResult;
import com.uoquo.scheduler.platform.model.dto.UserInfoDto;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.json.TypeToken;
import com.uoquo.web.ReturnData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

public class JsonTest {

    @Test
    public void testJson() {
        String json = "{\"status\":\"00000\",\"code\":\"00000\",\"level\":\"SUCCESS\",\"message\":\"请求成功\",\"data\":{\"pageNum\":1,\"pageSize\":1000,\"size\":4,\"total\":\"4000\",\"pages\":1,\"hasPrevPage\":false,\"hasNextPage\":false,\"result\":[{\"id\":\"34N82P6K7WPRMBRS\",\"instituteId\":\"34E0XA4XT8AFED6H\",\"instituteName\":null,\"deptId\":\"34E0XA4XT8AFEDBB\",\"deptName\":null,\"referralCode\":\"402854\",\"userName\":\"admin\",\"realName\":\"\",\"userCode\":null,\"thirdId\":null,\"phone\":\"138****8000\",\"email\":null,\"avatar\":null,\"status\":\"001001\",\"statusText\":null,\"statusTime\":\"1729395705000\",\"statusMemo\":null,\"pwdLevel\":null,\"pwdLevelText\":null,\"pwdExpired\":false,\"pwdEditTime\":null,\"lastedLoginIp\":\"106.120.39.139\",\"loginErrorCount\":0,\"lastedLoginTime\":\"1762087942000\",\"createTime\":\"1729395705000\",\"userRoleList\":null,\"userGroupList\":null},{\"id\":\"35YSY2T305892ZEY\",\"instituteId\":\"34E0XA4XT8AFED6H\",\"instituteName\":null,\"deptId\":\"34E0XA4XT8AFEDBB\",\"deptName\":null,\"referralCode\":\"092485\",\"userName\":\"a1\",\"realName\":\"张*\",\"userCode\":\"\",\"thirdId\":\"\",\"phone\":\"138****8001\",\"email\":\"\",\"avatar\":null,\"status\":\"001001\",\"statusText\":null,\"statusTime\":\"1751708120000\",\"statusMemo\":null,\"pwdLevel\":\"002009\",\"pwdLevelText\":null,\"pwdExpired\":false,\"pwdEditTime\":null,\"lastedLoginIp\":null,\"loginErrorCount\":0,\"lastedLoginTime\":null,\"createTime\":\"1751708120000\",\"userRoleList\":null,\"userGroupList\":null},{\"id\":\"35YSY3YNWRTX03V2\",\"instituteId\":\"34E0XA4XT8AFED6H\",\"instituteName\":null,\"deptId\":\"34E0XA4XT8AFEDBB\",\"deptName\":null,\"referralCode\":\"002629\",\"userName\":\"a2\",\"realName\":\"\",\"userCode\":\"\",\"thirdId\":\"\",\"phone\":\"138****8002\",\"email\":\"\",\"avatar\":null,\"status\":\"001001\",\"statusText\":null,\"statusTime\":\"1751708139000\",\"statusMemo\":null,\"pwdLevel\":\"002009\",\"pwdLevelText\":null,\"pwdExpired\":false,\"pwdEditTime\":null,\"lastedLoginIp\":null,\"loginErrorCount\":0,\"lastedLoginTime\":null,\"createTime\":\"1751708139000\",\"userRoleList\":null,\"userGroupList\":null},{\"id\":\"35YSY4Y6PAXR4STY\",\"instituteId\":\"34E0XA4XT8AFED6H\",\"instituteName\":null,\"deptId\":\"34E0XA4XT8AFEDBB\",\"deptName\":null,\"referralCode\":\"795829\",\"userName\":\"a3\",\"realName\":\"\",\"userCode\":\"\",\"thirdId\":\"\",\"phone\":\"138****8003\",\"email\":\"\",\"avatar\":null,\"status\":\"001001\",\"statusText\":null,\"statusTime\":\"1751708155000\",\"statusMemo\":null,\"pwdLevel\":\"002009\",\"pwdLevelText\":null,\"pwdExpired\":false,\"pwdEditTime\":null,\"lastedLoginIp\":null,\"loginErrorCount\":0,\"lastedLoginTime\":null,\"createTime\":\"1751708155000\",\"userRoleList\":null,\"userGroupList\":null}]}}";

        Type respType = TypeToken.getParameterized(ReturnData.class, PageResult.class).getType();
        ReturnData<PageResult<UserInfoDto>> resp = JsonUtil.deserialize(json, respType);
        System.out.println(resp);
    }
}
