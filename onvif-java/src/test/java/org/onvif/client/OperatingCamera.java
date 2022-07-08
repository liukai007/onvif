package org.onvif.client;

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
    private static Map<String, Object> getDeviceMap;
    private static URL url;
    private static String ipAddress;
    private static Integer ipPort;
    private static String userName;
    private static String password;


    /**
     * 第一步验证权限
     */
    public static Map<String, Object> getDevice(String[] args) throws IOException, SOAPException {
        OnvifCredentials onvifCredentials = GetTestDevice.getOnvifCredentials(args);
        getDeviceMap = testCamera(onvifCredentials);
        return getDeviceMap;
    }

    public static Map<String, Object> testCamera(OnvifCredentials creds) throws SOAPException, IOException {
        ipAddress = creds.getHost();
        URL u =
                creds.getHost().startsWith("http")
                        ? new URL(creds.getHost())
                        : new URL("http://" + creds.getHost());
        url = u;
        userName = creds.getUser();
        password = creds.getPassword();
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
    public static synchronized void executeContinuousMove(String direction)
            throws InterruptedException, DatatypeConfigurationException {
        executeContinuousMove(direction, DatatypeFactory.newInstance().newDuration(1000), 0.1f);
    }

    public static synchronized void executeContinuousMove(String direction, Long timeout, Float speedValue, Boolean boolen) throws InterruptedException, DatatypeConfigurationException {
        executeContinuousMove(direction, DatatypeFactory.newInstance().newDuration(timeout), speedValue);
    }

    public static synchronized void executeContinuousMove(String direction, Duration timeout, Float speedValue) throws InterruptedException {
        PTZ ptz = (PTZ) getDeviceMap.get("ptz");
        String profileToken = getDeviceMap.get("profileToken_0").toString();
        if (ptz == null || StringUtils.isBlank(profileToken)) {
            try {
                getDeviceMap = testCamera(url, userName, password);
            } catch (SOAPException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
        ptz.continuousMove(profileToken, speed, timeout);
    }

    public static void main(String[] args) {
        String[] args1 = {"192.168.0.120", "admin", "HuaWei123"};
        try {
            Map<String, Object> map = getDevice(args1);
            executeContinuousMove(DirectionEnum.PTZ_CMD_DOWN.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);
            executeContinuousMove(DirectionEnum.PTZ_CMD_UP.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);
            executeContinuousMove(DirectionEnum.PTZ_CMD_LEFT.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);
            executeContinuousMove(DirectionEnum.PTZ_CMD_RIGHT.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);
            executeContinuousMove(DirectionEnum.PTZ_CMD_ZOOM_IN.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);
            executeContinuousMove(DirectionEnum.PTZ_CMD_ZOOM_OUT.name(), 5000l, 0.2f, null);
            Thread.sleep(5000);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (SOAPException e) {
            e.printStackTrace();
        } catch (InterruptedException interruptedException) {
            interruptedException.printStackTrace();
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }
    }
}
