package org.onvif.client;

import de.onvif.soap.OnvifDevice;
import de.onvif.utils.OnvifUtils;
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
        getDeviceMap.put("on-off", "0");
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


    /**
     * ContinuousMove 进行封装，第一个是默认移动速度和移动时间
     * 默认速度0.1 默认移动时间是1000毫秒
     */
    public static synchronized String executeContinuousMove(PTZ ptz, String profileToken, String direction)
            throws InterruptedException, DatatypeConfigurationException {
        return executeContinuousMove(ptz, profileToken, direction, DatatypeFactory.newInstance().newDuration(1000), 0.1f);
    }

    public static synchronized String executeContinuousMove(PTZ ptz, String profileToken, String direction, Long timeout, Float speedValue, Boolean boolen) throws InterruptedException, DatatypeConfigurationException {
        return executeContinuousMove(ptz, profileToken, direction, DatatypeFactory.newInstance().newDuration(timeout), speedValue);
    }

    public static synchronized String executeContinuousMove(PTZ ptz, String profileToken, String direction, Duration timeout, Float speedValue) throws InterruptedException {
        PTZSpeed speed = new PTZSpeed();
        Vector2D xy = new Vector2D();
        xy.setSpace("http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace");

        //ZOOM
        Vector1D zoom1D = new Vector1D();
        zoom1D.setSpace("http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace");
        switch (direction) {
            case "PTZ_CMD_LEFT":
                xy.setX(-(speedValue));
                xy.setY(0f);
                break;
            case "PTZ_CMD_RIGHT":
                xy.setX(speedValue);
                xy.setY(0f);
                break;
            case "PTZ_CMD_UP":
                xy.setX(0f);
                xy.setY(speedValue);
                break;
            case "PTZ_CMD_DOWN":
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
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "no support ptz";
        }

    }

    public static void main(String[] args) {
        BaseInfo baseInfo = new BaseInfo();
        baseInfo.setIpAddress("192.168.0.120");
        baseInfo.setUserName("admin");
        baseInfo.setPassword("HuaWei123");
        baseInfo.setSpeed(0.2f);
        baseInfo.setTimeOut(5000l);
        while (true) {
            try {
                Map<String, Object> map = new HashMap<>();
                if (getDeviceMaps.get(baseInfo.getIpAddress()) != null) {
                    map = getDeviceMaps.get(baseInfo.getIpAddress());
                } else {
                    map = getDevice(baseInfo);
                }
                if (null != map.get("on-off") && map.get("on-off").toString().equals("0")) {
                    continue;
                }
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_DOWN.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_UP.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_LEFT.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_RIGHT.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_ZOOM_IN.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
                executeContinuousMove((PTZ) map.get("ptz"), map.get("profileToken_0").toString(), DirectionEnum.PTZ_CMD_ZOOM_OUT.name(), baseInfo.getTimeOut(), baseInfo.getSpeed(), null);
                Thread.sleep(5000);
            } catch (InterruptedException interruptedException) {
                interruptedException.printStackTrace();
            } catch (DatatypeConfigurationException e) {
                e.printStackTrace();
            }
        }

    }
}
