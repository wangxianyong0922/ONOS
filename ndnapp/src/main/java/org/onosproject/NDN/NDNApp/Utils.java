package org.onosproject.NDN.NDNApp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.criteria.PiCriterion;
import org.onosproject.net.group.*;
import org.onosproject.net.pi.model.PiActionProfileId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiGroupKey;
import org.onosproject.net.pi.runtime.PiTableAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
//import static org.onosproject.NDN.NDNApp.AppConstants.DEFAULT_FLOW_RULE_PRIORITY;
import static org.onosproject.net.group.DefaultGroupBucket.createAllGroupBucket;
import static org.onosproject.net.group.DefaultGroupBucket.createCloneGroupBucket;


public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);
    public static final int DEFAULT_FLOW_RULE_PRIORITY = 10;

    /**
     * 从名字上看，该方法的作用是创建一个多播组
     * 根据指定appid、设备id、组id和端口信息创建一个组播组
     * */
    public static GroupDescription buildMulticastGroup(
            ApplicationId appId,
            DeviceId deviceId,
            int groupId,
            Collection<PortNumber> ports) {
        return buildReplicationGroup(appId, deviceId, groupId, ports, false);
    }

    /**
     * 从名字上看，是通过给定参数复制一个组播组
     * */
    public static GroupDescription buildCloneGroup(
            ApplicationId appId,
            DeviceId deviceId,
            int groupId,
            Collection<PortNumber> ports) {
        return buildReplicationGroup(appId, deviceId, groupId, ports, true);
    }

    /**
     * 根据给定参数，创建一个组播组
     * @param appId ：应用id
     * @param deviceId ：设备id
     * @param groupId ：组id
     * @param isClone ：是否是克隆组播组的标识位
     * @param ports ：端口
     * */
    private static GroupDescription buildReplicationGroup(
            ApplicationId appId,
            DeviceId deviceId,
            int groupId,
            Collection<PortNumber> ports,
            boolean isClone) {

        /**
         * 检查：设备、app和端口是否为空
         * 为空则返回异常，且当前方法结束
         * */
        checkNotNull(deviceId);
        checkNotNull(appId);
        checkArgument(!ports.isEmpty());

        /**
         * GroupKey：是一个接口，里面只有一个byte[]类型返回值的抽象方法key()；
         * DefaultGroupKey：是一个GroupKey接口的实现类，其带参构造方法的作用为检查给定key数值是否为空，部不为空则赋值给DefaultGroupKey类的全局变量key
         * TODO：key是什么意思？是xx的唯一标识吗？xx是什么？名字还是设备？
         * */
        final GroupKey groupKey = new DefaultGroupKey(
                ByteBuffer.allocate(4).putInt(groupId).array());

        /**
         * 第一次map：将port对象转换为了TrafficTreatment对象
         * 第二次map：将TrafficTreatment对象转换为了GroupBucket对象
         * GroupBucket：直译过来就是“组桶”，难道里面存放的都是组对象？GroupBucket是一个接口，里面有很多抽象方法
         * TODO：为什么把端口对象PortNumber转换为组桶对象GroupBucket？
         * */
        final List<GroupBucket> bucketList = ports.stream()
                .map(p -> DefaultTrafficTreatment.builder()
                        .setOutput(p).build())
                .map(t -> isClone ? createCloneGroupBucket(t)
                        : createAllGroupBucket(t))
                .collect(Collectors.toList());

        /**
         * @return：返回一个组描述对象，里面包含：设备id、组描述类型、该组的接口列表（？）以及组标识，组id和app id
         * */
        return new DefaultGroupDescription(
                deviceId,
                isClone ? GroupDescription.Type.CLONE : GroupDescription.Type.ALL,
                new GroupBuckets(bucketList),
                groupKey, groupId, appId);
    }

    /**
     * 通过给定参数创建一条流规则
     * @param appId ：要创建此流规则的app的id
     * @param switchId ：此流规则要被插入的设备的id
     * @param tableId ：此流规则要被插入的表的id
     * @param piCriterion ：构成此流规则的pi规则
     * @param piAction ：构成此流规则的pi动作
     * 可以肯定的是，创建的流规则是针对一个具体的p4交换机，因为直接用的pi来进行创建
     * */
    public static FlowRule buildFlowRule(DeviceId switchId, ApplicationId appId,
                                         String tableId, PiCriterion piCriterion,
                                         PiTableAction piAction) {
        /*log.info("---------PiTableId.of(tableId)获取到" +
                "的fib_table表的id为：{}，但在p4info中该表的id为：{}---------",PiTableId.of(tableId),"33575563");*/
        return DefaultFlowRule.builder()
                .forDevice(switchId)
                .forTable(PiTableId.of(tableId))
                .fromApp(appId)
                .withPriority(DEFAULT_FLOW_RULE_PRIORITY)
                .makePermanent()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchPi(piCriterion).build())
                .withTreatment(DefaultTrafficTreatment.builder()
                        .piTableAction(piAction).build())
                .build();
    }

    /**
     * 用于测试的辅助类：用于不带匹配域的流表项的生成
     * */
    public static FlowRule buildFlowRuleWithoutCriterion(DeviceId switchId, ApplicationId appId,
                                         String tableId, PiTableAction piAction) {
        /*log.info("---------PiTableId.of(tableId)获取到" +
                "的fib_table表的id为：{}，但在p4info中该表的id为：{}---------",PiTableId.of(tableId),"33575563");*/
        return DefaultFlowRule.builder()
                .forDevice(switchId)
                .forTable(PiTableId.of(tableId))
                .fromApp(appId)
                .withPriority(DEFAULT_FLOW_RULE_PRIORITY)
                .makePermanent()
                .withTreatment(DefaultTrafficTreatment.builder()
                        .piTableAction(piAction).build())
                .build();
    }

    /**
     * 该方法用于“生成选择组”
     * TODO：什么是选择组？为什么要生成它？
     * */
    public static GroupDescription buildSelectGroup(DeviceId deviceId,
                                                    String tableId,
                                                    String actionProfileId,
                                                    int groupId,
                                                    Collection<PiAction> actions,
                                                    ApplicationId appId) {

        /**
         * PiGroupKey：根据给定参数创建一个pi组关键词，参数为：pi表id+pi动作id+组id
         * */
        final GroupKey groupKey = new PiGroupKey(
                PiTableId.of(tableId), PiActionProfileId.of(actionProfileId), groupId);
        /**
         * 第一次map：将pi action转换为TrafficTreament对象
         * 第二次map：将TrafficTreament对象转换为了GroupBucket对象
         * TODO：为什么要进行这种转换？
         * */
        final List<GroupBucket> buckets = actions.stream()
                .map(action -> DefaultTrafficTreatment.builder()
                        .piTableAction(action).build())
                .map(DefaultGroupBucket::createSelectGroupBucket)
                .collect(Collectors.toList());
        /**
         * 返回：一个组描述对象，通过指定参数构建
         * */
        return new DefaultGroupDescription(
                deviceId,
                GroupDescription.Type.SELECT,
                new GroupBuckets(buckets),
                groupKey,
                groupId,
                appId);
    }

    /**
     * 通过给定参数，暂缓进程运行
     * */
    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.error("Interrupted!", e);
            Thread.currentThread().interrupt();
        }
    }
}
