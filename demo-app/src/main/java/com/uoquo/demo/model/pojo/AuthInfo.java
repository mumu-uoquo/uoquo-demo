package com.uoquo.demo.model.pojo;

/**
 * demo-app 自有的认证数据 POJO。
 *
 * <p>用于 Auth_Event（{@code AppEvent<AuthInfo>}）承载所模拟的认证信息，
 * 使 demo-app 无需依赖 demo-task 模块即可编译。</p>
 */
public class AuthInfo {

    /**
     * 账号
     */
    private String account;

    /**
     * 密码（仅认证失败时记录）
     */
    private String password;

    /**
     * 设备信息
     */
    private String deviceInfo;

    /**
     * 设备操作系统
     */
    private String deviceOs;

    /**
     * 设备 User-Agent
     */
    private String deviceUa;

    /**
     * 无参构造函数（供 JSON 反序列化）。
     */
    public AuthInfo() {
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getDeviceOs() {
        return deviceOs;
    }

    public void setDeviceOs(String deviceOs) {
        this.deviceOs = deviceOs;
    }

    public String getDeviceUa() {
        return deviceUa;
    }

    public void setDeviceUa(String deviceUa) {
        this.deviceUa = deviceUa;
    }
}
