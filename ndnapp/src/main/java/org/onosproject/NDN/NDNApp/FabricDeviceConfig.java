package org.onosproject.NDN.NDNApp;

import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;

public class FabricDeviceConfig extends Config<DeviceId>{
    public static final String CONFIG_KEY = "fabricDeviceConfig";
    private static final String MY_STATION_MAC = "myStationMac";
    private static final String IS_SPINE = "isSpine";

    @Override
    public boolean isValid() {
        return hasOnlyFields(MY_STATION_MAC, IS_SPINE) &&
                myStationMac() != null;
    }

    /**
     * 从交换机上得到mac地址
     * Gets the MAC address of the switch.
     *
     * 返回交换机的mac地址，如果交换机没有配置mac地址则返回空
     * @return MAC address of the switch. Or null if not configured.
     */
    public MacAddress myStationMac() {
        String mac = get(MY_STATION_MAC, null);
        return mac != null ? MacAddress.valueOf(mac) : null;
    }

    /**
     * 检查交换机是否为核心交换机
     * Checks if the switch is a spine switch.
     *
     * 当交换机为核心交换机时返回true，否则返回false，或者交换机没有被配置
     * @return true if the switch is a spine switch. false if the switch is not
     * a spine switch, or if the value is not configured.
     */
    public boolean isSpine() {
        String isSpine = get(IS_SPINE, null);
        return isSpine != null && Boolean.valueOf(isSpine);
    }


}
