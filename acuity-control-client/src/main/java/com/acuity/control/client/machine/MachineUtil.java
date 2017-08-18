package com.acuity.control.client.machine;


import com.acuity.db.domain.vertex.impl.machine.MachineUpdate;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;

/**
 * Created by Zachary Herridge on 8/14/2017.
 */
public class MachineUtil {


    public static MachineUpdate buildMachineState() {
        MachineUpdate machineUpdate = new MachineUpdate();

        HashMap<String, Object> properties = new HashMap<>();
        System.getProperties().keySet().forEach(key -> properties.put(String.valueOf(key), System.getProperty(String.valueOf(key))));

        return machineUpdate;
    }


    public static float getCPUUsage() {
        OperatingSystemMXBean operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        int availableProcessors = operatingSystemMXBean.getAvailableProcessors();
        long prevUpTime = runtimeMXBean.getUptime();
        long prevProcessCpuTime = operatingSystemMXBean.getProcessCpuTime();
        double cpuUsage;
        try {
            Thread.sleep(500);
        } catch (Exception ignored) {
        }
        operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long upTime = runtimeMXBean.getUptime();
        long processCpuTime = operatingSystemMXBean.getProcessCpuTime();
        long elapsedCpu = processCpuTime - prevProcessCpuTime;
        long elapsedTime = upTime - prevUpTime;
        return Math.min(99F, elapsedCpu / (elapsedTime * 10000F * availableProcessors));
    }
}