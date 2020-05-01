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
package nctu.winlab.pathapp;

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

import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyGraph;
import java.util.Set;
import org.onosproject.net.topology.TopologyVertex;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import java.util.LinkedList; 
import java.util.Queue; 
import org.onosproject.net.device.DeviceService;
import java.util.List;
import org.onosproject.net.Port;
import org.onosproject.net.link.LinkService;
import org.onosproject.net.Link;
import java.util.*;
import org.onlab.packet.IpAddress;
import org.onosproject.net.HostId;
/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent{

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId appId;
    private int tmp = 0;
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

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LinkService linkService;

    private MyPacketProcessor processor = new MyPacketProcessor();

    @Activate
    protected void activate() {
        KryoNamespace.Builder metricSerializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(MultiValuedTimestamp.class);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        appId = coreService.registerApplication("nctu.winlab.pathapp");
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

            PortNumber inPort = pkt.receivedFrom().port();
            if(tmp == 0){
                tmp = tmp + 1;
            }else if (tmp == 1) {
                inPacket(context);
                tmp = 0;
            }
        }
    }
    private void inPacket(PacketContext context) {
            DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
            //log.info("tmp:" + tmp);
            log.info("Packet-in from device " + deviceId.toString());
            findPath(context);
    }
    private void findPath(PacketContext context) {
            MacAddress sourceMac =  context.inPacket().parsed().getSourceMAC();
            MacAddress destinationMac =  context.inPacket().parsed().getDestinationMAC();

            Topology topo = topologyService.currentTopology();
            TopologyGraph graph = topologyService.getGraph(topo);
            Set<TopologyVertex> allVertexes = graph.getVertexes();
            Set<TopologyEdge> allEdges = graph.getEdges();
            TopologyVertex sourceVertex = allVertexes.iterator().next();
            TopologyVertex sourceVertex2 = allVertexes.iterator().next();
            TopologyVertex destinationVertex = allVertexes.iterator().next();
            Boolean findSourceVertex = false;
            Boolean findDestinationVertex = false;
            for(TopologyVertex v : allVertexes) {
                Set<Host> allConnectedHosts = hostService.getConnectedHosts(v.deviceId());
                for(Host h : allConnectedHosts) {
                    if(h.mac().equals(sourceMac)) {
                        sourceVertex = v;
                        sourceVertex2 = v;
                        findSourceVertex = true;
                        //log.info("Find source vertex");
                    }
                    if(h.mac().equals(destinationMac)) {
                        destinationVertex = v;
                        findDestinationVertex = true;
                        //log.info("Find des vertex");
                    }
                }
            }

            if(findSourceVertex && findDestinationVertex){
                
            }else{
                //log.info("return");
                return;
            }

            //log.info(sourceMac + " " + destinationMac);
            //log.info(sourceVertex2 + " " + destinationVertex);
            if(sourceVertex2.deviceId().equals(destinationVertex.deviceId())){
                //log.info("Same switch!");
                Vector<IpAddress> sourceIp = new Vector<IpAddress>();
                Vector<IpAddress> destinationIp = new Vector<IpAddress>();
                Vector<HostId> hostIdVector = new Vector<HostId>();
                Map <DeviceId, PortNumber> m1 = new HashMap <DeviceId, PortNumber> ();
                Map <DeviceId, PortNumber> m2 = new HashMap <DeviceId, PortNumber> ();
                for(Host h: hostService.getHostsByMac(sourceMac)){
                    m1.put(destinationVertex.deviceId(), h.location().port());
                    for(IpAddress ip: h.ipAddresses()){
                        sourceIp.addElement(ip);
                        hostIdVector.addElement(h.id());
                    }
                }
                for(Host h: hostService.getHostsByMac(destinationMac)){
                    m2.put(destinationVertex.deviceId(), h.location().port());
                    for(IpAddress ip: h.ipAddresses()){
                        destinationIp.addElement(ip);
                        hostIdVector.addElement(h.id());
                    }
                }
                if(hostIdVector.size() == 2){
                    log.info("Start to install path from " + hostIdVector.get(0) + " to " + hostIdVector.get(1));
                }
                if(sourceIp.size() != 0 && destinationIp.size() != 0){
                    log.info("install rule " + destinationVertex.deviceId());
                    installRule(context, destinationVertex.deviceId(), m1.get(destinationVertex.deviceId()), sourceIp.get(0), destinationIp.get(0), false);
                    installRule(context, destinationVertex.deviceId(), m2.get(destinationVertex.deviceId()), sourceIp.get(0), destinationIp.get(0), true);
                }
                return;
            }
            Queue<TopologyVertex> vertexQueue = new LinkedList<>();
            Map<TopologyVertex, Integer> vertexColor = new HashMap<>();
            Map<TopologyVertex, Integer> vertexDistance = new HashMap<>();
            Map<TopologyVertex, TopologyVertex> vertexPredecessor = new HashMap<>();
            for(TopologyVertex v : allVertexes) {
                vertexColor.put(v, 0);
                vertexDistance.put(v, 99999999);
            }
            for(TopologyVertex v : allVertexes) {
                if (vertexColor.get(sourceVertex) == 0){
                    vertexColor.put(sourceVertex, 1);
                    vertexDistance.put(sourceVertex, 0);
                    vertexQueue.add(sourceVertex);
                    while(!vertexQueue.isEmpty()) {
                        TopologyVertex u = vertexQueue.element();
                        Set<TopologyEdge> test = graph.getEdgesFrom(u);
                        for(TopologyEdge t : test) {
                            //log.info("Find path：" + u.toString() + ":" + t.toString());
                            if(vertexColor.get(t.dst()) == 0) {
                                vertexColor.put(t.dst(), 1);
                                vertexDistance.put(t.dst(), vertexDistance.get(u) + 1);
                                vertexPredecessor.put(t.dst(), u);
                                vertexQueue.add(t.dst());
                            }
                        }
                        vertexQueue.poll();
                        vertexColor.put(u, 2);
                    }
                }
                sourceVertex = v;
            }

            for (Object key : vertexPredecessor.keySet()) {
                //log.info(key + " : " + vertexPredecessor.get(key));
            }
            TopologyVertex ans = destinationVertex;
            Vector<Map <DeviceId, PortNumber>> srcSwitchPortVector = new Vector<Map <DeviceId, PortNumber>>(); 
            Vector<Map <DeviceId, PortNumber>> dstSwitchPortVector = new Vector<Map <DeviceId, PortNumber>>(); 
            Vector<IpAddress> sourceIp = new Vector<IpAddress>();
            Vector<IpAddress> destinationIp = new Vector<IpAddress>();
            Vector<HostId> hostIdVector = new Vector<HostId>();
            while(vertexPredecessor.get(ans) != null){
                Set<Link> t1 = linkService.getDeviceEgressLinks(ans.deviceId());
                for( Link l : t1) {
                    if(l.dst().deviceId().equals(vertexPredecessor.get(ans).deviceId())){

                        Map <DeviceId, PortNumber> m1 = new HashMap <DeviceId, PortNumber> ();
                        m1.put(ans.deviceId(), l.src().port());
                        //log.info("dst: " + ans.deviceId().toString() + " " + l.src().port().toString());
                        dstSwitchPortVector.addElement(m1);
                        //log.info("TEST:" + l.dst().deviceId() + " "+ sourceVertex2.deviceId());
                        if(l.dst().deviceId().equals(sourceVertex2.deviceId())) {
                            for(Host h: hostService.getHostsByMac(sourceMac)){
                                Map <DeviceId, PortNumber> m2 = new HashMap <DeviceId, PortNumber> ();
                                m2.put(sourceVertex2.deviceId(), h.location().port());
                                //log.info("TEST:" + sourceVertex2.deviceId() + " "+  h.ipAddresses());
                                for(IpAddress ip: h.ipAddresses()){
                                    sourceIp.addElement(ip);
                                    hostIdVector.addElement(h.id());
                                    //log.info("TEST sourceIp:" + sourceIp.get(0));
                                }
                                //log.info("dst: " + sourceVertex.deviceId().toString() + " " + h.location().port().toString());
                                dstSwitchPortVector.addElement(m2);
                            }
                        }

                        if(ans.deviceId().equals(destinationVertex.deviceId())) {
                            for(Host h: hostService.getHostsByMac(destinationMac)){
                                Map <DeviceId, PortNumber> m2 = new HashMap <DeviceId, PortNumber> ();
                                m2.put(destinationVertex.deviceId(), h.location().port());
                                for(IpAddress ip: h.ipAddresses()){
                                    destinationIp.addElement(ip);
                                    hostIdVector.addElement(h.id());
                                    //log.info("TEST destinationIp:" + destinationIp.get(0));
                                    //log.info("TEST destinationIp:" + h.id() + " " + h.ipAddresses());
                                }
                                //log.info("src: " + destinationVertex.deviceId().toString() + " " + h.location().port().toString());
                                srcSwitchPortVector.addElement(m2);
                            }
                        }

                        Map <DeviceId, PortNumber> m2 = new HashMap <DeviceId, PortNumber> ();
                        m2.put(l.dst().deviceId(), l.dst().port());
                        //log.info("src: " + l.dst().deviceId().toString() + " " + l.dst().port().toString());
                        srcSwitchPortVector.addElement(m2);
                    }
                }
                ans = vertexPredecessor.get(ans);
            }
            if(hostIdVector.size() == 2){
                log.info("Start to install path from " + hostIdVector.get(0) + " to " + hostIdVector.get(1));
            }
            //log.info(srcSwitchPortVector.size() + " " + dstSwitchPortVector.size());
            //log.info(sourceIp.get(0) + " " + destinationIp.get(0));
            for(int i = 0; i < srcSwitchPortVector.size(); i++){
                Map <DeviceId, PortNumber> tmp = srcSwitchPortVector.get(i);
                for(DeviceId key : tmp.keySet()){
                    if(sourceIp.size() != 0 && destinationIp.size() != 0){
                        log.info("install rule " + key);
                        installRule(context, key, tmp.get(key), sourceIp.get(0), destinationIp.get(0), true);
                    }
                }
            }
            for(int i = dstSwitchPortVector.size()-1; i >= 0; i--){
                Map <DeviceId, PortNumber> tmp = dstSwitchPortVector.get(i);
                for(DeviceId key : tmp.keySet()){
                    if(sourceIp.size() != 0 && destinationIp.size() != 0){
                        //log.info("det install rule " + key + " " + tmp.get(key));
                        installRule(context, key, tmp.get(key), sourceIp.get(0), destinationIp.get(0), false);
                    }
                }
            }
    }
    private void installRule(PacketContext context, DeviceId deviceId, PortNumber portNumber,IpAddress sourceIp,IpAddress destinationIp, Boolean direction) {
        //log.info(sourceIp.toString() + " " + destinationIp.toString());
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(inPkt.getEtherType());
        if(direction){
            selectorBuilder.matchIPDst(destinationIp.toIpPrefix());
            selectorBuilder.matchIPSrc(sourceIp.toIpPrefix());
        }
        else{
            selectorBuilder.matchIPDst(sourceIp.toIpPrefix());
            selectorBuilder.matchIPSrc(destinationIp.toIpPrefix());
        }
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
            .setOutput(portNumber)
            .build();
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
            .withSelector(selectorBuilder.build())
            .withTreatment(treatment)
            .withPriority(10)
            .makeTemporary(30)
            .withFlag(ForwardingObjective.Flag.VERSATILE)
            .fromApp(appId)
            .add();
        flowObjectiveService.forward(deviceId, forwardingObjective);
    }
}