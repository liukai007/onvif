package org.onvif.client;

import de.onvif.beans.DeviceInfo;
import de.onvif.soap.OnvifDevice;
import de.onvif.utils.OnvifUtils;

import java.io.IOException;
import java.lang.Object;
import java.math.BigDecimal;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.soap.SOAPException;

import org.onvif.ver10.device.wsdl.DeviceServiceCapabilities;
import org.onvif.ver10.events.wsdl.EventPortType;
import org.onvif.ver10.events.wsdl.GetEventProperties;
import org.onvif.ver10.events.wsdl.GetEventPropertiesResponse;
import org.onvif.ver10.media.wsdl.Media;
import org.onvif.ver10.schema.*;
import org.onvif.ver20.imaging.wsdl.ImagingPort;
import org.onvif.ver20.ptz.wsdl.Capabilities;
import org.onvif.ver20.ptz.wsdl.PTZ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Brad Lowe
 */
public class TestDevice {
    private static final Logger LOG = LoggerFactory.getLogger(TestDevice.class);

    public static String testCamera(OnvifCredentials creds) throws SOAPException, IOException {
        URL u =
                creds.getHost().startsWith("http")
                        ? new URL(creds.getHost())
                        : new URL("http://" + creds.getHost());
        return testCamera(u, creds.getUser(), creds.getPassword());
    }

    static String sep = "\n";

    // This method returns information about an initialized OnvifDevice.
    // This could throw an uncaught SOAP or other error on some cameras...
    // Would accept Pull Requests on printing out additional information about devices.
    public static String inspect(OnvifDevice device) {
        String out = "";
        DeviceInfo info = device.getDeviceInfo();
        out += "DeviceInfo:" + sep + "\t" + info + sep;
        DeviceServiceCapabilities caps = device.getDevice().getServiceCapabilities();
        String sysCaps = OnvifUtils.format(caps);
        sysCaps = sysCaps.replace("],", "],\t\n");

        out += "\tgetServiceCapabilities: " + sysCaps + sep;
        // out += "\tgetServiceCapabilities.getSystem: " + OnvifUtils.format(caps.getSystem()) + sep;

        Media media = device.getMedia();

        media.getVideoSources();
        List<Profile> profiles = media.getProfiles();
        out += "Media Profiles: " + profiles.size() + sep;
        for (Profile profile : profiles) {
            String profileToken = profile.getToken();
            String rtsp = device.getStreamUri(profileToken);
            out += "\tProfile: " + profile.getName() + " token=" + profile.getToken() + sep;
            out += "\t\tstream: " + rtsp + sep;
            out += "\t\tsnapshot: " + device.getSnapshotUri(profileToken) + sep;
            out += "\t\tdetails:" + OnvifUtils.format(profile) + sep;
        }

        try {
            List<VideoSource> videoSources = media.getVideoSources();
            out += "VideoSources: " + videoSources.size() + sep;
            for (VideoSource v : videoSources) out += "\t" + OnvifUtils.format(v) + sep;

            ImagingPort imaging = device.getImaging();
            if (imaging != null && videoSources.size() > 0) {
                String token = videoSources.get(0).getToken();

                out += "Imaging:" + token + sep;
                try {
                    org.onvif.ver20.imaging.wsdl.Capabilities image_caps = imaging.getServiceCapabilities();
                    out += "\tgetServiceCapabilities=" + OnvifUtils.format(image_caps) + sep;

                    if (token != null) {
                        out +=
                                "\tgetImagingSettings="
                                        + OnvifUtils.format(imaging.getImagingSettings(token))
                                        + sep;
                        out += "\tgetMoveOptions=" + OnvifUtils.format(imaging.getMoveOptions(token)) + sep;
                        out += "\tgetStatus=" + OnvifUtils.format(imaging.getStatus(token)) + sep;
                        out += "\tgetOptions=" + OnvifUtils.format(imaging.getOptions(token)) + sep;
                    }
                } catch (Throwable th) {
                    out += "Imaging unavailable:" + th.getMessage() + sep;
                }
            }
        } catch (Throwable th) {
            // this can fail if the device doesn't support video sources.
            out += "VideoSources: " + th.getMessage() + sep;
        }
        try {
            // This may throw a SoapFaultException with the message "This device does not support audio"
            List<AudioSource> audioSources = media.getAudioSources();
            out += "AudioSources: " + audioSources.size() + sep;
            for (AudioSource a : audioSources) out += "\t" + OnvifUtils.format(a) + sep;
        } catch (Throwable th) {
            out += "AudioSources Unavailable: " + th.getMessage() + sep;
        }

        try {
            EventPortType events = device.getEvents();
            if (events != null) {
                out += "Events:" + sep;
                out +=
                        "\tgetServiceCapabilities=" + OnvifUtils.format(events.getServiceCapabilities()) + sep;

                GetEventProperties getEventProperties = new GetEventProperties();
                GetEventPropertiesResponse getEventPropertiesResp =
                        events.getEventProperties(getEventProperties);
                out += "\tMessageContentFilterDialects:" + sep;
                for (String f : getEventPropertiesResp.getMessageContentFilterDialect())
                    out += ("\t\t" + f + sep);
                out += "\tTopicExpressionDialects:" + sep;
                for (String f : getEventPropertiesResp.getTopicExpressionDialect())
                    out += ("\t\t" + f + sep);

                out += "\tTopics:" + sep;
                StringBuffer tree = new StringBuffer();
                for (Object object : getEventPropertiesResp.getTopicSet().getAny()) {
                    Element e = (Element) object;
                    printTree(e, e.getNodeName(), tree);
                    // WsNotificationTest.printTree(e, e.getNodeName());
                }
                out += tree;
            }
        } catch (Throwable th) {
            out += "Events Unavailable: " + th.getMessage() + sep;
        }
        PTZ ptz = device.getPtz();
        if (ptz != null) {

            String profileToken = profiles.get(0).getToken();
            try {
                Capabilities ptz_caps = ptz.getServiceCapabilities();
                out += "PTZ:" + sep;
                out += "\tgetServiceCapabilities=" + OnvifUtils.format(ptz_caps) + sep;
                PTZStatus s = ptz.getStatus(profileToken);
                out += "\tgetStatus=" + OnvifUtils.format(s) + sep;
                // out += "ptz.getConfiguration=" + ptz.getConfiguration(profileToken) + sep;
                List<PTZPreset> presets = ptz.getPresets(profileToken);
                if (presets != null && !presets.isEmpty()) {
                    out += "\tPresets:" + presets.size() + sep;
                    for (PTZPreset p : presets) {
                        out += "\t\t" + OnvifUtils.format(p) + sep;

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
//                        ptz.absoluteMove(profileToken, position,speed );
//                        ptz.relativeMove(profileToken,position,speed);
//                        ptz.continuousMove(profileToken,speed);
                        executeContinuousMove(ptz, profileToken, "PTZ_CMD_LEFTUP", DatatypeFactory.newInstance().newDuration(2000));

                    }

                }
            } catch (Throwable th) {
                out += "PTZ: Unavailable" + th.getMessage() + sep;
            }
        }

        return out;
    }

    public static void printTree(Node node, String name, StringBuffer buffer) {

        if (node.hasChildNodes()) {
            NodeList nodes = node.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node n = nodes.item(i);
                printTree(n, name + " - " + n.getNodeName(), buffer);
            }
        } else {
            buffer.append("\t\t" + name + " - " + node.getNodeName() + "\n");
        }
    }

    public static String testCamera(URL url, String user, String password)
            throws SOAPException, IOException {
        LOG.info("Testing camera:" + url);
        OnvifDevice device = new OnvifDevice(url, user, password);
        return inspect(device);
    }

    public static void main(String[] args) {
//        String[] args1 = {"192.168.0.120", "admin", "HuaWei123"};
        String[] args1 = {"192.168.0.108", "admin", "budee123"};
        OnvifCredentials creds = GetTestDevice.getOnvifCredentials(args1);
        try {
            // OnvifDevice.setVerbose(true);
            String out = testCamera(creds);

            LOG.info("\n" + out + "\n");
        } catch (Throwable th) {
            LOG.error("Failed for " + creds, th);
            th.printStackTrace();
        }
    }

    /**
     *  八个方向（上、下、左、右、左上、左下、右上、右下），聚焦、放大、缩小等
     *  这在个过程中还包含对转动速度的控制或者放大缩小的速度控制
     */
    public static void executeContinuousMove(PTZ ptz,String profileToken,String direction, Duration timeout) throws InterruptedException {
        PTZSpeed speed = new PTZSpeed();
        Vector2D xy = new Vector2D();
        xy.setSpace("http://www.onvif.org/ver10/tptz/PanTiltSpaces/PositionGenericSpace");

        //ZOOM
        Vector1D zoom1D = new Vector1D();
        zoom1D.setSpace("http://www.onvif.org/ver10/tptz/ZoomSpaces/PositionGenericSpace");
        switch (direction){
            case  "PTZ_CMD_LEFT":
                xy.setX(-0.2f);
                xy.setY(0f);
                break;
            case  "PTZ_CMD_RIGHT":
                xy.setX(0.2f);
                xy.setY(0f);
                break;
            case  "PTZ_CMD_UP":
                xy.setX(0f);
                xy.setY(0.2f);
                break;
            case  "PTZ_CMD_DOWN":
                xy.setX(0f);
                xy.setY(-0.2f);
                break;
            case  "PTZ_CMD_LEFTUP":
                xy.setX(-0.2f);
                xy.setY(0.2f);
                break;
            case "PTZ_CMD_LEFTDOWN":
                xy.setX(-0.2f);
                xy.setY(-0.2f);
                break;
            case  "PTZ_CMD_RIGHTUP":
                xy.setX(0.2f);
                xy.setY(0.2f);
                break;
            case "PTZ_CMD_RIGHTDOWN":
                xy.setX(0.2f);
                xy.setY(-0.2f);
                break;
            case  "PTZ_CMD_ZOOM_IN":
                xy.setX(0f);
                xy.setY(0f);
                zoom1D.setX(0.2f);
                break;
                //缩小
            case  "PTZ_CMD_ZOOM_OUT":
                xy.setX(0f);
                xy.setY(0f);
                zoom1D.setX(-0.21f);
                break;
            default:
                break;
        }
        speed.setPanTilt(xy);
        speed.setZoom(zoom1D);
        ptz.continuousMove(profileToken,speed,timeout);
    }
}
