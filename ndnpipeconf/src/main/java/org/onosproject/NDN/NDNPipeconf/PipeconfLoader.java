package org.onosproject.NDN.NDNPipeconf;

import org.onosproject.driver.pipeline.DefaultSingleTablePipeline;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.device.PortStatisticsDiscovery;
import org.onosproject.net.driver.DriverAdminService;
import org.onosproject.net.driver.DriverProvider;
import org.onosproject.net.pi.model.*;
import org.onosproject.net.pi.service.PiPipeconfService;
import org.onosproject.p4runtime.model.P4InfoParser;
import org.onosproject.p4runtime.model.P4InfoParserException;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;

import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.BMV2_JSON;
import static org.onosproject.net.pi.model.PiPipeconf.ExtensionType.P4_INFO_TEXT;

//import static org.onosproject.NDN.NDNApp.AppConstants.PIPECONF_ID;

@Component(immediate = true)
public final class PipeconfLoader {

    private final Logger log = LoggerFactory.getLogger(getClass());
    public static final PiPipeconfId PIPECONF_ID = new PiPipeconfId("wxyNDNApp-ndnpipeconf");
    //private static final String P4INFO_PATH = "/p4info.txt";
    //private static final String BMV2_JSON_PATH = "/bmv2.json";

    //private static final URL P4INFO_URL = PipeconfLoader.class.getResource("/p4info.txt");
    //private static final URL BMV2_JSON_URL = PipeconfLoader.class.getResource("/bmv2.json");

    private static final URL P4INFO_URL = PipeconfLoader.class.getResource("/ndn_router-p4-16.p4.p4info.txt");
    private static final URL BMV2_JSON_URL = PipeconfLoader.class.getResource("/ndn_router-p4-16.json");

    //private static final URL P4INFO_URL = PipeconfLoader.class.getResource("/wxymain.p4.p4info.txt");
    //private static final URL BMV2_JSON_URL = PipeconfLoader.class.getResource("/wxymain.json");
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private PiPipeconfService pipeconfService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DriverAdminService driverAdminService;

    @Activate
    public void activate() {
        log.info(".....222start register " + PIPECONF_ID);
        /*// Registers the pipeconf at component activation.
        if (pipeconfService.getPipeconf(PIPECONF_ID).isPresent()) {
            // Remove first if already registered, to support reloading of the
            // pipeconf during the tutorial.
            // 如果已经注册，请先删除，以支持在教程中重新加载pipeconf。
            pipeconfService.unregister(PIPECONF_ID);
        }
        removePipeconfDrivers();*/
        try {
            log.info(".....4444start register " + PIPECONF_ID);
            pipeconfService.register(buildPipeconf());
        } catch (P4InfoParserException e) {
            log.error("Unable to register " + PIPECONF_ID, e);
        }
    }

    @Deactivate
    public void deactivate() {
        // Do nothing.
    }

/*    private PiPipeconf buildPipeconf() throws P4InfoParserException {

        final URL p4InfoUrl = PipeconfLoader.class.getResource(P4INFO_PATH);
        final URL bmv2JsonUrlUrl = PipeconfLoader.class.getResource(BMV2_JSON_PATH);
        final PiPipelineModel pipelineModel = P4InfoParser.parse(p4InfoUrl);

        return DefaultPiPipeconf.builder()
                .withId(PIPECONF_ID)
      a          .withPipelineModel(pipelineModel)
                .addBehaviour(PiPipelineInterpreter.class, InterpreterImpl.class)
                .addBehaviour(Pipeliner.class, DefaultSingleTablePipeline.class)
                .addExtension(P4_INFO_TEXT, p4InfoUrl)
                .addExtension(BMV2_JSON, bmv2JsonUrlUrl)
                .build();
    }*/

    private PiPipeconf buildPipeconf() throws P4InfoParserException {

        //解析指定位置的P4Info文件
        final PiPipelineModel pipelineModel = P4InfoParser.parse(P4INFO_URL);

        return DefaultPiPipeconf.builder()
                .withId(PIPECONF_ID)
                .withPipelineModel(pipelineModel)
                .addBehaviour(PiPipelineInterpreter.class, InterpreterImpl.class)
                .addBehaviour(Pipeliner.class, DefaultSingleTablePipeline.class)
                .addExtension(P4_INFO_TEXT, P4INFO_URL)
                .addExtension(BMV2_JSON, BMV2_JSON_URL)
                .build();
    }

    private void removePipeconfDrivers() {
        List<DriverProvider> driverProvidersToRemove = driverAdminService
                .getProviders().stream()
                .filter(p -> p.getDrivers().stream()
                        .anyMatch(d -> d.name().endsWith(PIPECONF_ID.id())))
                .collect(Collectors.toList());

        if (driverProvidersToRemove.isEmpty()) {
            return;
        }

        log.info("Found {} outdated drivers for pipeconf '{}', removing...",
                driverProvidersToRemove.size(), PIPECONF_ID);

        driverProvidersToRemove.forEach(driverAdminService::unregisterProvider);
    }

}
