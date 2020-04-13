/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.bridge;

import com.google.common.collect.ImmutableSet;
import org.onosproject.cfg.ComponentConfigService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;


import org.onosproject.core.ApplicationId;
import org.onosproject.net.packet.PacketService;
import org.onosproject.store.service.StorageService;
import org.onosproject.core.CoreService;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.net.DeviceId;
import org.onlab.packet.MacAddress;
import org.onosproject.net.PortNumber;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onlab.util.KryoNamespace;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.WallClockTimestamp;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onlab.packet.Ethernet;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import java.util.Map;
import java.util.HashMap;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    private EventuallyConsistentMap<DeviceId, Map<MacAddress, PortNumber>> table;
    private MyPacketProcessor processor = new MyPacketProcessor();

    @Activate
    protected void activate() {
        KryoNamespace.Builder metricSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(MultiValuedTimestamp.class);
        table =  storageService.<DeviceId, Map<MacAddress, PortNumber>>eventuallyConsistentMapBuilder()
                .withName("mac-table")
                .withSerializer(metricSerializer)
                .withTimestampProvider((key, metricsData) -> new
                        MultiValuedTimestamp<>(new WallClockTimestamp(), System.nanoTime()))
                .build();
        packetService.addProcessor(processor, PacketProcessor.director(2));
        appId = coreService.registerApplication("nctu.winlab.bridge");
        requestIntercepts();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();
        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class MyPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            
            if (context.isHandled()) {
                return;
            }
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            MacAddress sourceMac = ethPkt.getSourceMAC();
            MacAddress destinationMac = ethPkt.getDestinationMAC();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            PortNumber inPort = pkt.receivedFrom().port();
            
            inPacket(deviceId, sourceMac, inPort);
            
            Map <MacAddress, PortNumber> macAddressTable = table.get(deviceId);
            if(!macAddressTable.containsKey(destinationMac)) {
                packetOut(context, PortNumber.FLOOD);
            }else{
                PortNumber outPort = macAddressTable.get(destinationMac);
                packetOut(context, outPort);
                installRule(context, outPort);
            }
        }
    }
    private void inPacket(DeviceId deviceId, MacAddress macAddress, PortNumber portNumber) {
        if(table.containsKey(deviceId)) {
            Map <MacAddress, PortNumber> macAddressTable = table.get(deviceId);
            if(!macAddressTable.containsKey(macAddress)) {
                macAddressTable.put(macAddress, portNumber);
            }
        }else {
            Map <MacAddress, PortNumber> macAddressTable = new HashMap <MacAddress, PortNumber> ();
            macAddressTable.put(macAddress, portNumber);
            table.put(deviceId, macAddressTable);
        }
    }
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }
    private void installRule(PacketContext context, PortNumber portNumber) {
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthDst(inPkt.getDestinationMAC());
        selectorBuilder.matchEthSrc(inPkt.getSourceMAC());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
            .setOutput(portNumber)
            .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
            .withSelector(selectorBuilder.build())
            .withTreatment(treatment)
            .withPriority(10)
            .makeTemporary(10)
            .withFlag(ForwardingObjective.Flag.VERSATILE)
            .fromApp(appId)
            .add();
        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);
    }
}
