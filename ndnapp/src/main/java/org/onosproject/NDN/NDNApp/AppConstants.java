package org.onosproject.NDN.NDNApp;

import org.onosproject.net.pi.model.PiPipeconfId;

public class AppConstants {
    // app名称
    public static final String APP_NAME = "org.onosproject.NDN.NDNApp";
    // pipeconf id，即该app依赖的pipeconf
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId("org.onosproject.NDN.NDNPipeconf");

    // 默认流规则优先级
    public static final int DEFAULT_FLOW_RULE_PRIORITY = 10;
    // 初始化设置等待时间
    public static final int INITIAL_SETUP_DELAY = 2; // Seconds.
    // 清除时间
    public static final int CLEAN_UP_DELAY = 2000; // milliseconds
    // 默认清理重试次数
    public static final int DEFAULT_CLEAN_UP_RETRY_TIMES = 10;

    // CPU_PORT_ID应该是通往控制器的端口
    public static final int CPU_PORT_ID = 255;
    public static final int CPU_CLONE_SESSION_ID = 99;
}
