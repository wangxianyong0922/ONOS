package org.onosproject.NDN.NDNApp;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.onlab.util.SharedScheduledExecutors;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.*;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.group.Group;
import org.onosproject.net.group.GroupService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.pi.model.PiTableId;
import org.osgi.service.component.annotations.*;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiActionParamId;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiActionParam;
import org.onosproject.NDN.NDNPipeconf.PipeconfLoader;
import org.onosproject.NDN.NDNApp.FabricDeviceConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.onosproject.NDN.NDNApp.Utils;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.NDN.NDNApp.Utils.sleep;
import org.slf4j.Logger;

import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;
import static org.slf4j.LoggerFactory.getLogger;

// 将原项目中两个文件合并成一个
@Component(immediate = true,service = MyNDNApp.class)
public class MyNDNApp {
    // 将原文件中引入的变量直接在此处进行定义
    private static final int DEFAULT_FLOW_RULE_PRIORITY = 10;
    private static final int INITIAL_SETUP_DELAY = 2; // Seconds.
    private static final int CLEAN_UP_DELAY = 2000; // milliseconds
    private static final int DEFAULT_CLEAN_UP_RETRY_TIMES = 10;
    private static final int CPU_PORT_ID = 255;
    private static final int CPU_CLONE_SESSION_ID = 99;
    private static final String APP_NAME = "org.onosproject.NDN.NDNApp";

    //----------------------------------
    // 开启ONOS核心服务
    //
    //----------------------------------

    // ONOS核心服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    // ONOS pipeconf加载器服务对象（未被启用）
    /*@Reference(cardinality = ReferenceCardinality.MANDATORY)
    // Force activation of this component after the pipeconf has been registered.
    // 注册pipeconf后强制激活此组件。
    @SuppressWarnings("unused")
    protected PipeconfLoader pipeconfLoader;*/

    // ONOS网络配置注册服务对象，功能：网络配置的注册，比如我写了一种新的网络配置，那我要向这个服务注册一下
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry configRegistry;

    // ONOS组服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private GroupService groupService;

    // ONOS设备服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    // ONOS流规则服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    // ONOS组件配置服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private ComponentConfigService compCfgService;

    // 主控服务对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MastershipService mastershipService;

    // ONOS数据包服务对象
    /*@Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;*/

    // 新添加：组件配置服务
    /*@Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;*/

    /*// MainComponent类对象
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private MyNDNApp myNDNApp;*/


    // For the sake of simplicity and to facilitate reading logs, use a
    // single-thread executor to serialize all configuration tasks.
    // 为了简单和方便读取日志，请使用单线程执行器序列化所有配置任务。
    //private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private ExecutorService executorService;

    // 创建日志对象
    private static final Logger log = getLogger(MyNDNApp.class);

    // 网络配置监听器
    //private final InternalConfigListener cfgListener = new InternalConfigListener();

    //固定的出端口
    private static final int outPort = 3;

    private ApplicationId appId;

    // 这里创建了一个配置工厂对象，交换机可以使用该配置工厂中的配置模式（？）
    private final ConfigFactory<DeviceId, FabricDeviceConfig> fabricConfigFactory =
            new ConfigFactory<DeviceId, FabricDeviceConfig>(
                    SubjectFactories.DEVICE_SUBJECT_FACTORY, FabricDeviceConfig.class, FabricDeviceConfig.CONFIG_KEY) {
                @Override
                public FabricDeviceConfig createConfig() {
                    return new FabricDeviceConfig();
                }
            };


    @Activate
    protected void activate() {
        log.info("进入到MyNDNApp");
        executorService = newSingleThreadExecutor(groupedThreads("onos/app/wxy",
                "test output",
                log));
        appId = coreService.registerApplication(APP_NAME);
        // 等待从以前的执行中删除流和组。
        //waitPreviousCleanup();

        //---Test:从onos-netcfg命令中接收json文件并解析出name信息
        // 注册属性信息
        //componentConfigService.registerProperties(getClass());
        // 暂时关闭
        // 向配置服务注册监听器
        //configRegistry.addListener(cfgListener);
        // 注册配置工厂
        //factories.forEach(configRegistry::registerConfigFactory);
        // 注册配置信息（？）
        //cfgListener.reconfigureNetwork(configRegistry.getConfig(appId, NDNNameConfig.class));
        // 另一种形式的配置工厂注册
        //configRegistry.registerConfigFactory(fabricConfigFactory);
        // 注册侦听器以了解设备和主机事件。
        //deviceService.addListener(deviceListener);
        log.info("Started0");
        // 计划现有设备的设置。重新加载应用程序时需要。
        scheduleTask(this::setUpAllDevices, INITIAL_SETUP_DELAY);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        //componentConfigService.unregisterProperties(getClass(), false);
        //暂时关闭
        //configRegistry.unregisterConfigFactory(fabricConfigFactory);
        //configRegistry.removeListener(cfgListener);
        cleanUp();

        log.info("Stopped");
    }


    /**
     * 监听网络配置事件
     * */
    public class InternalConfigListener implements NetworkConfigListener {

        private void reconfigureNetwork(NDNNameConfig cfg) {
            if (cfg == null) {
                log.info("错误：cfg对象为空！");
                return;
            }
            if(cfg.one() != null){
                log.info("提示：提取到one的信息为--->{}",cfg.one());
                // 目前仅在这里对name信息进行处理
                ChangeName cn = new ChangeName();
                Map<String, List<String>> nameResult = cn.operationFile02(cfg.one());
                //TODO：下面要进行流表项生成了
                generateFibRulesByNameInformation(nameResult);

            }
            if(cfg.two() != null){
                log.info("提示：提取到two的信息为--->{}",cfg.two());
            }
            if(cfg.three() != null){
                log.info("提示：提取到three的信息为--->{}",cfg.three());
            }
        }

        @Override
        public void event(NetworkConfigEvent event) {
            if(event.type() == NetworkConfigEvent.Type.CONFIG_ADDED || event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED && event.configClass().equals(NDNNameConfig.class)){
                log.info("提示：触发网络配置事件");
                // 从网络配置事件中，按照指定配置文件方式读取其中的配置信息
                NDNNameConfig config = (NDNNameConfig)event.config().get();
                log.info("提示：通过事件的config对象直接获得配置的结果为--->{}",config.toString());
                // NDNNameConfig cfg = configRegistry.getConfig(appId,NDNNameConfig.class);
                reconfigureNetwork(config);


            }

        }

    }

    /**
     * 辅助方法：根据给定name的相关信息来生成流表项
     * @param 包含name信息的集合
     * */
    public void generateFibRulesByNameInformation(Map<String, List<String>> information){

        // 给当前网络拓扑中的每一个交换机都进行流表项的插入
        // 流规则往fib_table表中插入
        Map<String, List<String>> infor = information;
        final String tableId = "fib_table";
        Iterable<Device> availableDevices = deviceService.getAvailableDevices();
        Iterator<Device> iterator = availableDevices.iterator();
        while(iterator.hasNext()){

            // 获取设备信息
            Device next = iterator.next();
            DeviceId deviceId = next.id();
            // 取出name信息，进行流表项生成，注意，目前支持的最大name组件数量为5
            Iterator<Map.Entry<String, List<String>>> nameInfo_it = infor.entrySet().iterator();
            while(nameInfo_it.hasNext()){

                Map.Entry<String, List<String>> one = nameInfo_it.next();
                String name = one.getKey();
                // rule_info中包含的信息格式：index:%d compoents:%d iface:%d needed:%d mask:%s
                // 一个name可能对应多条rule information，都要做成流表项
                List<String> rule_info = one.getValue();
                for(String one_rule:rule_info){
                    // 切割信息，获得参数
                    // 索引
                    int index = 0;
                    // name组件数量
                    int compoents = 0;
                    // 出接口
                    int iface = 0;
                    // 需要生成的rule数量
                    int needed = 0;
                    // name的hash值和掩码
                    String mask = "";
                    // 数组中的元素为一条rule中的一个字段
                    log.info("提示：为了防止错误，这里输出rule-info的信息为--->{}",one_rule);
                    String[] infos = one_rule.split(" ");
                    for(int i = 0; i < infos.length; i++){
                        // 对字段进行再分割
                        String[] split = infos[i].split(":");
                        if(split[0].equals("index")){
                            int temp = Integer.parseInt(split[1]);
                            index = temp;
                        }
                        if(split[0].equals("compoents")){
                            int temp = Integer.parseInt(split[1]);
                            compoents = temp;
                        }
                        if(split[0].equals("iface")){
                            int temp = Integer.parseInt(split[1]);
                            iface = temp;
                        }
                        if(split[0].equals("needed")){
                            int temp = Integer.parseInt(split[1]);
                            needed = temp;
                        }
                        if(split[0].equals("mask")){
                            // 应该为0&&&0+0&&&0+0&&&0+0x95dd&&&0xffff的形式
                            String temp = split[1];
                            // 继续切割
                            String[] temp_in = temp.split("-");
                            // 取得非零值
                            for(String temp_in02:temp_in){
                                if(!temp_in02.equals("0&&&0")){
                                    // 得到目标值
                                    mask = temp_in02;
                                }

                            }

                        }
                    }

                    // TODO:目前不知道怎么把String的0x95dd转换成int的0x95dd
                    log.info("提示：进行mask中内容的验证--->{}",mask);
                    String[] nameHash_mask = mask.split("&&&");
                    // nameHash_mask[0]中数值为name的hash值
                    String nameHash = nameHash_mask[0];
                    // nameHash_mask[2]中的数值为mask值
                    String mask_now = nameHash_mask[1];
                    // 尝试1：转为byte数组形式---方式1---失败
                    byte[] nameHash_bytes = nameHash.getBytes(StandardCharsets.UTF_8);
                    byte[] mask_now_bytes = mask_now.getBytes(StandardCharsets.UTF_8);
                    // 尝试2：转为byte数组形式---方式2---失败
                    // 对hex字符串解码
                    Integer decode = Integer.decode(nameHash);
                    Integer decode1 = Integer.decode(mask_now);
                    // 解码后转bytes[]
                    byte[] bytes = Integer.toHexString(decode).getBytes(StandardCharsets.UTF_8);
                    byte[] bytes1 = Integer.toHexString(decode1).getBytes(StandardCharsets.UTF_8);
                    // 尝试3：直接解码为int---失败
                    Integer int_decode = Integer.decode(nameHash).intValue();
                    Integer int_decode1 = Integer.decode(mask_now);
                    // 尝试4：转为long---失败
                    /*long l = Long.parseLong(nameHash, 16);
                    long l1 = Long.parseLong(mask_now, 16);*/
                    // 尝试5：提取数值，转为hex，再提取byte
                    log.info("提示：为了防止报错，这里检测到的name hash值为--->{}",nameHash);
                    String substring = nameHash.substring(2);
                    int i = Integer.parseInt(substring, 16);
                    String s = Integer.toHexString(i);
                    byte[] bytes2 = s.getBytes(StandardCharsets.UTF_8);
                    String substring1 = mask_now.substring(2);
                    int i1 = Integer.parseInt(substring1, 16);
                    byte[] bytes3 = Integer.toHexString(i1).getBytes(StandardCharsets.UTF_8);

                    // 开始生成流表项
                    // 注意：这里创建了一个PiAction，action的名字为”set_egr“，action的参数为”egress_spec“
                    final PiAction setMcastGroupAction = PiAction.builder()
                            .withId(PiActionId.of("set_egr"))
                            .withParameter(new PiActionParam(PiActionParamId.of("egress_spec"), iface))
                            .build();

                    // 匹配字段01：name_metadata.components---值：index
                    // 匹配字段02：comp_metadata.c2---值：mask中包含，掩码：mask中包含
                    final PiCriterion ndnFibCriterion  = PiCriterion.builder()
                            .matchExact(PiMatchFieldId.of("name_metadata.components"), index)
                            .matchTernary(PiMatchFieldId.of("comp_metadata.c2"), bytes2, bytes3)
                            .build();

                    final FlowRule rule = Utils.buildFlowRule(
                            deviceId, appId, tableId,
                            ndnFibCriterion, setMcastGroupAction);

                    // 插入流规则
                    flowRuleService.applyFlowRules(rule);
                    log.info("提示：目前生产的rule信息为--->{}",rule.toString());
                }

            }

        }

    }

    /**
     * Sets up L2 bridging on all devices known by ONOS and for which this ONOS
     * node instance is currently master.
     * 在ONOS已知、且此ONOS节点实例当前是其主节点 的所有设备上 设置L2桥接。
     * <p>
     * This method is called at component activation.
     * 此方法在组件激活时调用。
     */
    private void setUpAllDevices() {
        deviceService.getAvailableDevices().forEach(device -> {
            log.info("*** step1 devices： {}...", device.id());
            if (mastershipService.isLocalMaster(device.id())) {
                log.info("*** step2 device is master Fib Table - Starting initial set up for {}...", device.id());
                // 如果设备有效可用且当前ONOS实例是其主控节点，则向该设备插入流规则
                //insertFibTableFlowRules(device.id());
            }
        });
    }

    /**
     * Returns the application ID.
     *
     * @return application ID
     */
    ApplicationId getAppId() {
        return appId;
    }

    /**
     * Returns the executor service managed by this component.
     *
     * @return executor service
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Schedules a task for the future using the executor service managed by
     * this component.
     * 使用此组件管理的executor服务为将来计划任务。（就是开启一个线程来处理给定的任务）
     *
     * @param task task runnable  （可执行的任务？）
     * @param delaySeconds delay in seconds  （秒级别的延迟）
     */
    public void scheduleTask(Runnable task, int delaySeconds) {
        SharedScheduledExecutors.newTimeout(
                () -> executorService.execute(task),
                delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Triggers clean up of flows and groups from this app, returns false if no
     * flows or groups were found, true otherwise.
     * 触发器清除此应用程序中的流和组，如果未找到流或组，则返回false，否则返回true。
     *
     * @return false if no flows or groups were found, true otherwise  （如果未找到流或组则为false，其他情况为true）
     */
    private boolean cleanUp() {
        // 得到指定app生成的所有流表项
        Collection<FlowRule> flows = Lists.newArrayList(
                flowRuleService.getFlowEntriesById(appId).iterator());

        // 得到设备+app下对应的组
        Collection<Group> groups = Lists.newArrayList();
        for (Device device : deviceService.getAvailableDevices()) {
            groupService.getGroups(device.id(), appId).forEach(groups::add);
        }

        // 两个均为空时，抛出异常
        if (flows.isEmpty() && groups.isEmpty()) {
            return false;
        }

        // 移除flows对象中包含的所有流表项（从所有设备上）
        flows.forEach(flowRuleService::removeFlowRules);
        if (!groups.isEmpty()) {
            // Wait for flows to be removed in case those depend on groups.
            // 等待流被移除，以防这些流依赖于组。
            sleep(1000);
            // 将设备从组中移除
            groups.forEach(g -> groupService.removeGroup(
                    g.deviceId(), g.appCookie(), g.appId()));
        }

        return true;
    }

    // 等待清理结束
    private void waitPreviousCleanup() {
        int retry = DEFAULT_CLEAN_UP_RETRY_TIMES;
        while (retry != 0) {

            if (!cleanUp()) {
                log.info("无需清理！");
                return;
            }

            log.info("Waiting to remove flows and groups from " +
                            "previous execution of {}...",
                    appId.name());

            sleep(CLEAN_UP_DELAY);

            --retry;
        }
        log.info("清理结束！");
    }

}
