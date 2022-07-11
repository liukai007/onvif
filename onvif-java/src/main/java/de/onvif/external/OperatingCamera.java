package de.onvif.external;

import de.onvif.beans.DeviceInfo;
import de.onvif.soap.OnvifDevice;
import de.onvif.utils.OnvifUtils;
import org.apache.commons.lang3.StringUtils;
import org.onvif.ver10.device.wsdl.DeviceServiceCapabilities;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.schema.*;
import org.onvif.ver20.ptz.wsdl.Capabilities;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.soap.SOAPException;
import java.io.IOException;
import java.lang.Object;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @类名: OperatingCamera
 * @描述: 把要使用的到的简单功能进行封装，方便项目使用，比如ptz的控制，和视频流URL
 * @作者: LiuKai
 * @版本: 1.0
 * @创建时间: 2022/7/8 9:14
 * @修改历史: （列表如下）
 * 时间    修改人   修改原因  修改内容
 */
public class OperatingCamera {
    private static String onvifDevice = "onvifDevice";
    private static String onLineOrOffLine = "on-off";
    private static String ptzSupport = "ptzSupport";
    private static final Logger logger = LoggerFactory.getLogger(OperatingCamera.class);
    private static Map<String, Map<String, Object>> getDeviceMaps = new ConcurrentHashMap<>();


    /**
     * 第一步验证权限
     */
    public static Map<String, Object> getDevice(BaseInfo baseInfo) {
        OnvifCredentials onvifCredentials = GetTestDevice.getOnvifCredentials(baseInfo);
        try {
            return testCamera(onvifCredentials);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Map<String, Object> getDeviceMap = new HashMap<>();
        getDeviceMap.put(onLineOrOffLine, "0");
        return getDeviceMap;
    }

    public static Map<String, Object> testCamera(OnvifCredentials creds) throws SOAPException, IOException {
        URL u =
                creds.getHost().startsWith("http")
                        ? new URL(creds.getHost())
                        : new URL("http://" + creds.getHost());
        Map<String, Object> getDeviceMap = testCamera(u, creds.getUser(), creds.getPassword());
        getDeviceMap.put("on-off", "1");
        getDeviceMaps.put(creds.getHost(), getDeviceMap);
        return testCamera(u, creds.getUser(), creds.getPassword());
    }

    public static Map<String, Object> testCamera(URL url, String user, String password)
            throws SOAPException, IOException {
        logger.info("Testing camera:" + url);
        OnvifDevice device = new OnvifDevice(url, user, password);
        return inspect(device);
    }

    public static Map<String, Object> inspect(OnvifDevice device) {
        Map<String, Object> map = new HashMap<>();
        map.put("on-off", "1");
        map.put("onvifDevice", device);
        DeviceServiceCapabilities caps = device.getDevice().getServiceCapabilities();
        Media media = device.getMedia();
        media.getVideoSources();
        List<Profile> profiles = media.getProfiles();
        if (!profiles.isEmpty()) {
            for (int i = 0; i < profiles.size(); i++) {
                Profile profile = profiles.get(i);
                String profileToken = profiles.get(i).getToken();
                String rtsp = device.getStreamUri(profileToken);
                logger.info("Profile" + profile.getName() + " token=" + profile.getToken());
                logger.info("stream: " + rtsp);
                logger.info("snapshot: " + device.getSnapshotUri(profileToken));
                logger.info("details:" + OnvifUtils.format(profile));

                map.put("profileToken_" + i, profile.getToken());
                map.put("stream_" + i, rtsp);
            }
        }


        PTZ ptz = device.getPtz();
        if (ptz != null) {
            String profileToken = profiles.get(0).getToken();
            try {
                Capabilities ptz_caps = ptz.getServiceCapabilities();
                logger.info("PTZ:");
                logger.info("getServiceCapabilities=" + OnvifUtils.format(ptz_caps));
                PTZStatus s = ptz.getStatus(profileToken);
                logger.info("getStatus=" + OnvifUtils.format(s));
            } catch (Throwable th) {
                logger.info("PTZ: Unavailable" + th.getMessage());
            }
            map.put("ptz", ptz);
        }

        return map;
    }


    public static synchronized Map<String, Object> executeContinuousMove(BaseInfo baseInfo, String direction) throws DatatypeConfigurationException {
        Map<String, Object> map = new HashMap<>();
        if (getDeviceMaps.get(baseInfo.getIpAddress()) != null) {
            map = getDeviceMaps.get(baseInfo.getIpAddress());
        } else {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) == null) {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) != null) {
            OnvifDevice device = (OnvifDevice) map.get(onvifDevice);
            DeviceInfo deviceInfo = device.getDeviceInfo();
            if (StringUtils.isNoneBlank(deviceInfo.toString())) {
                map.put(onLineOrOffLine, "1");
            } else {
                map.put(onLineOrOffLine, "0");
            }
        } else {
            map.put(onLineOrOffLine, "0");
        }

        try {
            return executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), direction, baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
            Map<String, Object> map1 = new HashMap<>();
            map1.put(ptzSupport, "noSupport");
            return map1;
        }
    }

    /**
     * ContinuousMove 进行封装，第一个是默认移动速度和移动时间
     * 默认速度0.1 默认移动时间是1000毫秒
     */
    public static synchronized Map<String, Object> executeContinuousMove(PTZ ptz, String profileToken, String direction)
            throws InterruptedException, DatatypeConfigurationException {
        return executeContinuousMove(ptz, profileToken, direction, DatatypeFactory.newInstance().newDuration(1000), 0.1f);
    }

    public static synchronized Map<String, Object> executeContinuousMove(PTZ ptz, String profileToken, String direction, Long timeout, Float speedValue, Boolean boolen) throws InterruptedException, DatatypeConfigurationException {
        return executeContinuousMove(ptz, profileToken, direction, DatatypeFactory.newInstance().newDuration(timeout), speedValue);
    }

    public static synchronized Map<String, Object> executeContinuousMove(PTZ ptz, String profileToken, String direction, Duration timeout, Float speedValue) throws InterruptedException {
        Map<String, Object> map = new HashMap<>();
        PTZSpeed speed = new PTZSpeed();
        Vector2D xy = new Vector2D();
        xy.setSpace("http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace");

        //ZOOM
        Vector1D zoom1D = new Vector1D();
        zoom1D.setSpace("http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace");
        switch (direction) {
            case "PTZ_CMD_LEFT":
                logger.info("摄像头向左");
                xy.setX(-(speedValue));
                xy.setY(0f);
                break;
            case "PTZ_CMD_RIGHT":
                logger.info("摄像头向右");
                xy.setX(speedValue);
                xy.setY(0f);
                break;
            case "PTZ_CMD_UP":
                logger.info("摄像头向上");
                xy.setX(0f);
                xy.setY(speedValue);
                break;
            case "PTZ_CMD_DOWN":
                logger.info("摄像头向下");
                xy.setX(0f);
                xy.setY(-(speedValue));
                break;
            case "PTZ_CMD_LEFTUP":
                xy.setX(-(speedValue));
                xy.setY(speedValue);
                break;
            case "PTZ_CMD_LEFTDOWN":
                xy.setX(-(speedValue));
                xy.setY(-(speedValue));
                break;
            case "PTZ_CMD_RIGHTUP":
                xy.setX(speedValue);
                xy.setY(speedValue);
                break;
            case "PTZ_CMD_RIGHTDOWN":
                xy.setX(speedValue);
                xy.setY(-(speedValue));
                break;
            case "PTZ_CMD_ZOOM_IN":
                xy.setX(0f);
                xy.setY(0f);
                zoom1D.setX(speedValue);
                break;
            //缩小
            case "PTZ_CMD_ZOOM_OUT":
                xy.setX(0f);
                xy.setY(0f);
                zoom1D.setX(-(speedValue));
                break;
            default:
                break;
        }
        speed.setPanTilt(xy);
        speed.setZoom(zoom1D);
        try {
            ptz.continuousMove(profileToken, speed, timeout);
            map.put(ptzSupport, "support");
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            map.put(ptzSupport, "noSupport");
            return map;
        }

    }

    //复位键，默认第一个预设就是复位
    public static Map<String, Object> getReset(BaseInfo baseInfo) {
        Map<String, Object> map = new HashMap<>();
        if (getDeviceMaps.get(baseInfo.getIpAddress()) != null) {
            map = getDeviceMaps.get(baseInfo.getIpAddress());
        } else {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) == null) {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) != null) {
            OnvifDevice device = (OnvifDevice) map.get(onvifDevice);
            PTZ ptz = device.getPtz();
            String profileToken = map.get("profileToken_0").toString();
            List<PTZPreset> presets = ptz.getPresets(profileToken);
            if (ptz != null) {
                map.put(onLineOrOffLine, "1");
                if (presets != null && !presets.isEmpty()) {
                    if (!presets.isEmpty()) {
                        PTZVector position = new PTZVector();
                        Vector1D vector1D = new Vector1D();
                        vector1D.setX(1);
                        vector1D.setSpace("http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace");
                        position.setZoom(vector1D);
                        Vector2D vector2D = new Vector2D();
                        vector2D.setX(1);
                        vector2D.setY(1);
                        vector2D.setSpace("http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace");
                        position.setPanTilt(vector2D);
                        PTZSpeed speed = new PTZSpeed();
                        speed.setPanTilt(vector2D);
                        speed.setZoom(vector1D);
                        ptz.gotoPreset(profileToken, presets.get(0).getToken(), speed);
                    }

                }
            } else {
                map.put(onLineOrOffLine, "0");
            }
        }
        return map;
    }


    public static Map<String, Object> getOnLineOrOffLine(BaseInfo baseInfo) {
        Map<String, Object> map = new HashMap<>();
        if (getDeviceMaps.get(baseInfo.getIpAddress()) != null) {
            map = getDeviceMaps.get(baseInfo.getIpAddress());
        } else {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) == null) {
            map = getDevice(baseInfo);
        }
        if (map.get(onvifDevice) != null) {
            OnvifDevice device = (OnvifDevice) map.get(onvifDevice);
            DeviceInfo deviceInfo = device.getDeviceInfo();
            if (StringUtils.isNoneBlank(deviceInfo.toString())) {
                map.put(onLineOrOffLine, "1");
            } else {
                map.put(onLineOrOffLine, "0");
            }
        } else {
            map.put(onLineOrOffLine, "0");
        }
        return map;
    }

    public static void main(String[] args) throws DatatypeConfigurationException {
        BaseInfo baseInfo = new BaseInfo();
        baseInfo.setIpAddress("192.168.2.139");
        baseInfo.setUserName("admin");
        baseInfo.setPassword("HuaWei123");
        baseInfo.setSpeed(0.2f);
        baseInfo.setTimeOut(5000l);
        System.out.println(getOnLineOrOffLine(baseInfo).toString());
        System.out.println(getOnLineOrOffLine(baseInfo).toString());
        System.out.println(getOnLineOrOffLine(baseInfo).toString());
        getReset(baseInfo);
        while (true) {
            System.out.println(executeContinuousMove(baseInfo, DirectionEnum.PTZ_CMD_RIGHT.name()).toString());
        }
    }
}
