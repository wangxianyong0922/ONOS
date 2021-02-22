package org.onosproject.NDN.NDNPipeconf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.onlab.packet.DeserializationException;
import org.onlab.packet.Ethernet;
import org.onlab.util.ImmutableByteSequence;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.pi.model.PiMatchFieldId;
import org.onosproject.net.pi.model.PiPacketMetadataId;
import org.onosproject.net.pi.model.PiPipelineInterpreter;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.onosproject.net.pi.runtime.PiPacketMetadata;
import org.onosproject.net.pi.runtime.PiPacketOperation;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.onlab.util.ImmutableByteSequence.copyFrom;
import static org.onosproject.net.PortNumber.CONTROLLER;
import static org.onosproject.net.PortNumber.FLOOD;
import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.flow.instructions.Instructions.OutputInstruction;
import static org.onosproject.net.pi.model.PiPacketOperationType.PACKET_OUT;
//import static org.onosproject.NDN.NDNApp.AppConstants.CPU_PORT_ID;

public class InterpreterImpl extends AbstractHandlerBehaviour implements PiPipelineInterpreter{

    // From v1model.p4
    private static final int V1MODEL_PORT_BITWIDTH = 9;

    // wxy insert TABLE_L2_FWD_ID
    /*private static final PiTableId TABLE_L2_FWD_ID =
            PiTableId.of("ingress.t_l2_fwd");
*/
    //这个名为TABLE_MAP的map中只有一个值:<0,TABLE_L2_FWD_ID>
    /*private static final Map<Integer, PiTableId> TABLE_MAP =
            new ImmutableMap.Builder<Integer, PiTableId>()
                    .put(0, TABLE_L2_FWD_ID)
                    .build();*/

    // From P4Info.
    private static final Map<Criterion.Type, String> CRITERION_MAP =
            new ImmutableMap.Builder<Criterion.Type, String>()
                    .put(Criterion.Type.IN_PORT, "standard_metadata.ingress_port")
                    .put(Criterion.Type.ETH_DST, "hdr.ethernet.dstAddr")
                    .put(Criterion.Type.ETH_SRC, "hdr.ethernet.srcAddr")
                    .put(Criterion.Type.ETH_TYPE, "hdr.ethernet.etherType")
                    .build();

    /**
     * Returns a collection of PI packet operations populated with metadata
     * specific for this pipeconf and equivalent to the given ONOS
     * OutboundPacket instance.
     * 返回一个PI数据包操作的集合，其中填充了此pipeconf的特定元数据，相当于给定的ONOS OutboundPacket实例。
     * 这里是由ONOS出站数据包抽象转换为具体的pi数据包操作，相当于高层抽象到底层具体操作的一次转换
     *
     * @param packet ONOS OutboundPacket  ONOS的出站数据包
     * @return collection of PI packet operations  一个pi数据包操作对象
     * @throws PiInterpreterException if the packet treatments cannot be
     *                                executed by this pipeline  如果对数据包的处理操作在该流水线中不能被实现，则抛出异常
     */
    @Override
    public Collection<PiPacketOperation> mapOutboundPacket(OutboundPacket packet)
            throws PiInterpreterException {
        TrafficTreatment treatment = packet.treatment();

        // Packet-out in main.p4 supports only setting the output port,
        // i.e. we only understand OUTPUT instructions.
        // ONOS中对数据包的Packet-out中，main.p4只能支持设置出端口---当然是设置数据包的出端口
        // 我们只能理解OUPUT指令
        // 下面的操作，就是将OutboundPacket中的指令拿出来对比，是OUTPUT的指令就转换为OutputInstruction对象并保存下来
        // 这一步，是用来获得packet-out数据包中的指令
        List<OutputInstruction> outInstructions = treatment
                .allInstructions()
                .stream()
                .filter(i -> i.type().equals(OUTPUT))
                .map(i -> (OutputInstruction) i)
                .collect(toList());

        if (treatment.allInstructions().size() != outInstructions.size()) {
            // There are other instructions that are not of type OUTPUT.
            // 如果有指令不是OUTPUT类型的，由于无法进行转换（运行main.p4的交换机不支持），所以抛出异常
            throw new PiInterpreterException("Treatment not supported: " + treatment);
        }

        /**
         * 根据数据包处理指令创建PI数据包操作对象
         * 其中经过了几次判断：
         * 第一次判断：该指令指定的输出端口是否为逻辑端口同时转发行为不是泛洪？是则抛出异常，否则继续运行
         * 第二次判断：数据包的转发行为是否为泛洪？是则获取交换机端口信息，并为每个端口创建一个该数据包
         * 第三次（分支，无判断）：为给定的输出指令创建一个数据包
         * */
        // 这一步，是将指令转换为数据包的pi操作对象
        ImmutableList.Builder<PiPacketOperation> builder = ImmutableList.builder();
        for (OutputInstruction outInst : outInstructions) {
            if (outInst.port().isLogical() && !outInst.port().equals(FLOOD)) {
                throw new PiInterpreterException(format(
                        "Packet-out on logical port '%s' not supported",
                        outInst.port()));
            } else if (outInst.port().equals(FLOOD)) {
                // To emulate flooding, we create a packet-out operation for
                // each switch port.
                // 针对泛洪，就是给每一个端口创建一个packet-out操作
                final DeviceService deviceService = handler().get(DeviceService.class);
                // 从当前交换机上拿到端口
                for (Port port : deviceService.getPorts(packet.sendThrough())) {
                    builder.add(buildPacketOut(packet.data(), port.number().toLong()));
                }
            } else {
                // Create only one packet-out for the given OUTPUT instruction.
                builder.add(buildPacketOut(packet.data(), outInst.port().toLong()));
            }
        }
        //返回的这个对象，将指定交换机对数据包的具体端口转发
        return builder.build();
    }

    /**
     * Builds a pipeconf-specific packet-out instance with the given payload and
     * egress port.
     *
     * 使用给定的payload和egress port构建特定于pipeconf的packet_out实例。
     * 该方法最终会给数据包增加有效载荷部分和元数据（出口）部分
     *
     * @param pktData    packet payload（数据包的有效载荷）
     * @param portNumber egress port（数据包的出端口）
     * @return packet-out
     * @throws PiInterpreterException if packet-out cannot be built
     */
    private PiPacketOperation buildPacketOut(ByteBuffer pktData, long portNumber)
            throws PiInterpreterException {

        // Make sure port number can fit in v1model port metadata bitwidth.
        // 确保端口号可以适合v1model端口元数据位宽度。
        final ImmutableByteSequence portBytes;
        try {
            // 这里要对端口号进行一个转换
            portBytes = copyFrom(portNumber).fit(V1MODEL_PORT_BITWIDTH);
        } catch (ImmutableByteSequence.ByteSequenceTrimException e) {
            // 抛出异常：端口号太大了
            throw new PiInterpreterException(format(
                    "Port number %d too big, %s", portNumber, e.getMessage()));
        }

        // Create metadata instance for egress port.
        // 为egress port创建元数据实例。
        // *** TODO EXERCISE 4: modify metadata names to match P4 program  （修改元数据名称以匹配P4程序）
        // ---- START SOLUTION ----
        // 这个应该是对应p4文件中的元数据中的出接口
        final String outPortMetadataName = "standard_metadata.egress_port";;
        // ---- END SOLUTION ----
        final PiPacketMetadata outPortMetadata = PiPacketMetadata.builder()
                .withId(PiPacketMetadataId.of(outPortMetadataName))
                .withValue(portBytes)
                .build();

        // Build packet out.
        // 这个packet-out中包含一的出端口元数据（P4元数据出端口字段和端口号的映射）以及有效载荷
        return PiPacketOperation.builder()
                .withType(PACKET_OUT)
                .withData(copyFrom(pktData))
                .withMetadata(outPortMetadata)
                .build();
    }

    /**
     * Returns an ONS InboundPacket equivalent to the given pipeconf-specific
     * packet-in operation.
     * 返回一个ONS InboundPacket，相当于操作中给定的pipeconf特定数据包。
     * 该方法是一个从底层抽象到高层抽象的转换，是将PI数据包操作对象转换为更高层的ONOS数据包操作对象
     * 就是packet_in--->对于交换机传上来的packet_in数据包，在这里进行转换，转换成ONOS能够认识的抽象
     *
     * @param packetIn packet operation（由P4交换机传递而来）
     * @param deviceId ID of the device that originated the packet-in（产生packet_in数据包的交换机id）
     * @return inbound packet
     * @throws PiInterpreterException if the packet operation cannot be mapped
     *                                to an inbound packet（如果数据包的操作不能转换为inboundpacket，就抛出异常）
     */
    @Override
    public InboundPacket mapInboundPacket(PiPacketOperation packetIn, DeviceId deviceId)
            throws PiInterpreterException {

        // Find the ingress_port metadata.
        // 找到入端口元数据（数据包进入交换机的那个端口）
        // *** TODO EXERCISE 4: modify metadata names to match P4Info
        // ---- START SOLUTION ----
        // 获取P4中元数据内包含的packet-in端口
        final String inportMetadataName = "standard_metadata.ingress_port";
        // ---- END SOLUTION ----
        // 找到该数据包在此交换机上的入端口
        Optional<PiPacketMetadata> inportMetadata = packetIn.metadatas()
                .stream()
                .filter(meta -> meta.id().id().equals(inportMetadataName))
                .findFirst();

        //入端口不存在就抛出异常
        if (!inportMetadata.isPresent()) {
            throw new PiInterpreterException(format(
                    "Missing metadata '%s' in packet-in received from '%s': %s",
                    inportMetadataName, deviceId, packetIn));
        }

        // Build ONOS InboundPacket instance with the given ingress port.
        // 通过给定的入端口创建ONOS InboundPacket实例。

        // 1. Parse packet-in object into Ethernet packet instance.
        // 1. 将packet-in对象解析为以太网包实例。
        final byte[] payloadBytes = packetIn.data().asArray();
        final ByteBuffer rawData = ByteBuffer.wrap(payloadBytes);
        final Ethernet ethPkt;
        try {
            ethPkt = Ethernet.deserializer().deserialize(
                    payloadBytes, 0, packetIn.data().size());
        } catch (DeserializationException dex) {
            throw new PiInterpreterException(dex.getMessage());
        }

        // 2. Get ingress port
        // 2. 得到入端口
        final ImmutableByteSequence portBytes = inportMetadata.get().value();
        final short portNum = portBytes.asReadOnlyBuffer().getShort();
        final ConnectPoint receivedFrom = new ConnectPoint(
                deviceId, PortNumber.portNumber(portNum));

        // 返回由指定信息构建的InboundPacket
        return new DefaultInboundPacket(receivedFrom, ethPkt, rawData);
    }

    @Override
    public Optional<Integer> mapLogicalPortNumber(PortNumber port) {
        if (CONTROLLER.equals(port)) {
            //return Optional.of(CPU_PORT_ID);
            return Optional.of(255);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PiMatchFieldId> mapCriterionType(Criterion.Type type) {
        if (CRITERION_MAP.containsKey(type)) {
            return Optional.of(PiMatchFieldId.of(CRITERION_MAP.get(type)));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public PiAction mapTreatment(TrafficTreatment treatment, PiTableId piTableId)
            throws PiInterpreterException {
        throw new PiInterpreterException("Treatment mapping not supported");
    }

    @Override
    public Optional<PiTableId> mapFlowRuleTableId(int flowRuleTableId) {
        // wxy update
        //return Optional.ofNullable(TABLE_MAP.get(flowRuleTableId));
        return Optional.empty();
    }

}
