package de.onvif.external;

import java.net.URL;

/**
 * @类名: BaseInfo
 * @描述: (这里叙述类的作用 ， 使用方法 ， 行为等基本信息 ， 越详细约好 ）
 * @作者: LiuKai
 * @版本: 1.0
 * @创建时间: 2022/7/8 15:53
 * @修改历史: （列表如下）
 * 时间    修改人   修改原因  修改内容
 */
public class BaseInfo {
    private String ipAddress;
    private Integer ipPort;
    private String userName;
    private String password;
    private URL url;
    private Long timeOut;
    private Float speed;

    public BaseInfo() {
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Integer getIpPort() {
        return ipPort;
    }

    public void setIpPort(Integer ipPort) {
        this.ipPort = ipPort;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public Long getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(Long timeOut) {
        this.timeOut = timeOut;
    }

    public Float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
    }
}
