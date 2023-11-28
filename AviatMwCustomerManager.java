package com.mobinets.nps.customer.transmission.manufacture.aviat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.mobinets.nps.customer.CustomerTransmissionDefaultDataProvider;
import com.mobinets.nps.customer.transmission.manufacture.aviat.node.AviatNetworkElementsParser;
import com.mobinets.nps.customer.transmission.manufacture.aviat.nodeSlot.AviatmwNodeSlotsParser;
import com.mobinets.nps.customer.transmission.manufacture.aviat.trlinks.AviatTransmissionLinkParser;
import com.mobinets.nps.daemon.logging.OSSLogging;
import com.mobinets.nps.model.customer.data.element.ElementMwConfiguration;
import com.mobinets.nps.model.customer.data.element.ElementTransmissionLink;
import com.mobinets.nps.model.network.ElementAdditionalInfo;
import com.mobinets.nps.model.nodeinterfaces.NetworkElement;
import com.mobinets.nps.model.nodeinterfaces.NodeBoard;
import com.mobinets.nps.model.nodeinterfaces.NodeInterface;
import com.mobinets.nps.model.nodeinterfaces.NodeInterfaceType;
import com.mobinets.nps.model.nodeinterfaces.NodeSlot;
import com.mobinets.nps.model.nodeinterfaces.VirtualInterface;

public class AviatMwCustomerManager extends CustomerTransmissionDefaultDataProvider {

	private static final Log log = LogFactory.getLog(AviatMwCustomerManager.class);
	private String wataniyaPlanning;
	private AviatNetworkElementsParser aviatNetworkElementsParser;
	private AviatmwNodeSlotsParser aviatmwNodeSlotsParser;
	 
	
	 private AviatTransmissionLinkParser aviatTransmissionLinkParser;

	public void setAviatTransmissionLinkParser(AviatTransmissionLinkParser aviatTransmissionLinkParser) {
		this.aviatTransmissionLinkParser = aviatTransmissionLinkParser;
	}

	public void setAviatmwNodeSlotsParser(AviatmwNodeSlotsParser aviatmwNodeSlotsParser) {
		this.aviatmwNodeSlotsParser = aviatmwNodeSlotsParser;
	}

	public void setAviatNetworkElementsParser(AviatNetworkElementsParser aviatNetworkElementsParser) {
		this.aviatNetworkElementsParser = aviatNetworkElementsParser;
	}
	public void setWataniyaPlanning(String wataniyaPlanning) {
		this.wataniyaPlanning = wataniyaPlanning;
	}
 
	 
	@Override
	public List<NetworkElement> getNetworkElement() {
		log.debug("Parsing List of Network Element for Ericsson R3");
		return new ArrayList<NetworkElement>(aviatNetworkElementsParser.getMapOfNetworkElement().values());
	}
	
	@Override
	public List<NodeSlot> getNodeSlot() {
		long startTime = OSSLogging.setStartMethod(getClass(), "AviatMw::getNodeSlot");
		Map<String, NodeSlot> slotMap = new HashMap<>();
		Map<String, NodeSlot> results = new HashMap<String, NodeSlot>();
		List<NodeInterface> ni  = new ArrayList<NodeInterface>();
		Map<String,NodeInterface> nii  = new HashMap<String,NodeInterface>();

		if (aviatmwNodeSlotsParser != null && slotMap.isEmpty()) {
			slotMap.putAll(aviatmwNodeSlotsParser.getSlotsMaps());
			ni.addAll(aviatmwNodeSlotsParser.getEthernetMap().values());
			nii =aviatmwNodeSlotsParser.getLinktotalCpacitymap();
		}
		 
 
		addNodeInterfaces(slotMap, ni);
		updateNodeIntefaceCapacity(slotMap, nii);
//		for(NodeSlot slot :slotMap.values()){
//			results.put(slot.getId(), slot);
//		}
//		
//		removeDuplicatedBoards(results);
//
		OSSLogging.setEndMethod(getClass(), "AviatMw::getNodeSlot", startTime);
		return new ArrayList<>(slotMap.values());
		//return new ArrayList<>(slotMap.values());
	}

	private void updateNodeIntefaceCapacity(Map<String, NodeSlot> nodeSlotMap, Map<String,NodeInterface> nii) {
		for (NodeInterface nodeInterface : nii.values()) {
			String boardId = nodeInterface.getNodeBoardId();

			if (boardId == null)
				continue;

			Integer lastIndexOf_ = boardId.lastIndexOf("_");

			String slotId = boardId.substring(0, lastIndexOf_);
			NodeSlot nodeSlot = nodeSlotMap.get(slotId);

			if (nodeSlot == null)
				continue;

			for (NodeBoard nodeBoard : nodeSlot.getNodeBoards()) {
				if (nodeBoard.getId().equalsIgnoreCase(boardId)) {
					Collection<NodeInterface> listNodeInterface = nodeBoard.getNodeInterfaces();
					if (listNodeInterface == null) {
						listNodeInterface = new ArrayList<NodeInterface>();
						nodeBoard.setNodeInterfaces(listNodeInterface);
					}

					if (nodeInterface != null && listNodeInterface.contains(nodeInterface)) {
						for (NodeInterface physNodeInterface : listNodeInterface) {
							if (physNodeInterface.getId().equalsIgnoreCase(nodeInterface.getId())) {
								physNodeInterface.setTotalLinkCapacity((nodeInterface.getTotalLinkCapacity()));
								// physNodeInterface.setExternalCode(nodeInterface.getCapacity());
								// physNodeInterface.setExternalCode(nodeInterface.getTotalLinkCapacity());
								break;
							}
						}
					} else {
						nodeBoard.getNodeInterfaces().add(nodeInterface);
					}
				}
				System.out.println();
			}
		}
	}

	private void addNodeInterfaces(Map<String, NodeSlot> nodeSlotMap, List<NodeInterface> nodeInterfaces) {

		for (NodeInterface nodeInterface : nodeInterfaces) {
			String boardId = nodeInterface.getNodeBoardId();

			if (boardId == null)
				continue;

			Integer lastIndexOf_ = boardId.lastIndexOf("_");

			String slotId = boardId.substring(0, lastIndexOf_);
			NodeSlot nodeSlot = nodeSlotMap.get(slotId);

			if (nodeSlot == null)
				continue;

			for (NodeBoard nodeBoard : nodeSlot.getNodeBoards()) {
				if (nodeBoard.getId().equalsIgnoreCase(boardId)) {
					Collection<NodeInterface> listNodeInterface = nodeBoard.getNodeInterfaces();
					if (listNodeInterface == null) {
						listNodeInterface = new ArrayList<NodeInterface>();
						nodeBoard.setNodeInterfaces(listNodeInterface);
					}

					if (nodeInterface != null && listNodeInterface.contains(nodeInterface)) {
						for (NodeInterface physNodeInterface : listNodeInterface) {
							if (physNodeInterface.getId().equalsIgnoreCase(nodeInterface.getId())) {
								physNodeInterface.setSubInterfaces(nodeInterface.getSubInterfaces());
								physNodeInterface.setIpData(nodeInterface.getIpData());
								physNodeInterface.setInterfaceName(nodeInterface.getInterfaceName());
								physNodeInterface.setVlanId(nodeInterface.getVlanId());
								physNodeInterface.setVlanSetTrunk(nodeInterface.getVlanSetTrunk());
								physNodeInterface.setExternalCode(nodeInterface.getExternalCode());
								physNodeInterface.setPortSpeed(nodeInterface.getPortSpeed());
								physNodeInterface.setPortUtilization(nodeInterface.getPortUtilization());
								physNodeInterface.setPortStatus(nodeInterface.getPortStatus());
								physNodeInterface.setMinThroughtput(nodeInterface.getMinThroughtput());
								physNodeInterface.setPeakThroughput(nodeInterface.getPeakThroughput());
								// physNodeInterface.setExternalCode(nodeInterface.getCapacity());
								// physNodeInterface.setExternalCode(nodeInterface.getTotalLinkCapacity());
								break;
							}
						}
					} else {
						nodeBoard.getNodeInterfaces().add(nodeInterface);
					}
				}
				System.out.println();
			}
		}

	}
	/*@Override
	public List<NodeSlot> getNodeSlot() {
		if (wataniyaPlanning.equalsIgnoreCase("true"))
			return Collections.emptyList();
		log.debug("Parsing List of Node Slot for Aviat MW");
		try {
			 
			Map<String, NodeSlot> nodeSlots = new HashMap<String, NodeSlot>();
			if(nodeSlots.isEmpty()){
			 
			List<NodeInterface> ni = new ArrayList<NodeInterface>();
			ni.addAll(aviatmwNodeSlotsParser.getEthernetMap().values());
			 

			updateNodeIntefaceType(ni, mwConf);

			addNodeInterfaces(nodeSlots, ni);

		//	return new ArrayList<NodeSlot>(aviatmwNodeSlotsParser.getNodeSlotsElements());
			return new ArrayList<>(new HashSet<>(aviatmwNodeSlotsParser.getNodeSlotsElements()));
			}
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Error : ", e);
		}

		return new ArrayList<NodeSlot>();
	}*/
	
//	@Override
//	public List<ElementTransmissionLink> getTrsLinkList() {
//		if (wataniyaPlanning.equalsIgnoreCase("true"))
//			return Collections.emptyList();
//		log.debug("Parsing List of Transmission Link for Ericsson R3");
//		try {
//			return aviatTransmissionLinkParser.getElementTrsLinks();
//		} catch (IOException e) {
//			log.error("Error : ", e);
//		}
//		return Collections.emptyList();
//	}
	
	@Override
	public List<ElementTransmissionLink> getTrsLinkList() {
		if (wataniyaPlanning.equalsIgnoreCase("true"))
			return Collections.emptyList();
		List<ElementTransmissionLink> result = new ArrayList<>();
		if(aviatTransmissionLinkParser != null)
			try {
				result.addAll(aviatTransmissionLinkParser.getElementTrsLinks());
				wataniyaPlanning ="true";
			} catch (IOException e) {
				e.printStackTrace();
			}
		return result;
	}
	
	@Override
	public List<ElementMwConfiguration> getElementMwConfigurationList() {
		List<ElementMwConfiguration> result = new ArrayList<>();
		if(aviatTransmissionLinkParser != null)
			try {
				result.addAll(aviatTransmissionLinkParser.getLinkDataforMwConfig());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return result;
	}
	
	@Override
	public List<VirtualInterface> getVirtualInterfaces() {
		log.debug("Parsing List of Virtual Interfaces for Ericsson");
		List<VirtualInterface> list = aviatNetworkElementsParser.getVirtualInterfaces();

		return list;
	}
	
	@Override
	public List<ElementAdditionalInfo> getAddInfoList() {
		List<ElementAdditionalInfo> addInfos = new ArrayList<>();
		log.debug("Start Getting AdditionalInfo from TransmissionLinkParser");
		try {
			addInfos.addAll(aviatTransmissionLinkParser.getAdditionalInfos());
	 
		} catch (Exception e) {
			e.printStackTrace();
		}
 		return addInfos;
	}
	private void removeDuplicatedBoards(Map<String, NodeSlot> nodeSlotMap) {
		for(NodeSlot slot :nodeSlotMap.values()){
			try{
			Map<String, NodeBoard> bordsMap = new HashMap<>();
			for(NodeBoard board :slot.getNodeBoards()){
				bordsMap.put(board.getId(), board);
			}
			slot.getNodeBoards().clear();
			slot.getNodeBoards().addAll(bordsMap.values());
		}
		
		catch (Exception e) {
		}
		}
	}

}
 
 
//	private void addNodeInterfaces(Map<String, NodeSlot> nodeSlotMap, List<NodeInterface> nodeInterfaces) {
//
//		for (NodeInterface nodeInterface : nodeInterfaces) {
//			String boardId = nodeInterface.getNodeBoardId();
//
//			if (boardId == null)
//				continue;
//
//			Integer lastIndexOf_ = boardId.lastIndexOf("-");
//
//			String slotId = boardId.substring(0, lastIndexOf_);
//			NodeSlot nodeSlot = nodeSlotMap.get(slotId);
//
//			if (nodeSlot == null)
//				continue;
//
//			for (NodeBoard nodeBoard : nodeSlot.getNodeBoards()) {
//				if (nodeBoard.getId().equalsIgnoreCase(boardId)) {
//					Collection<NodeInterface> listNodeInterface = nodeBoard.getNodeInterfaces();
//					if (listNodeInterface == null) {
//						listNodeInterface = new ArrayList<NodeInterface>();
//						nodeBoard.setNodeInterfaces(listNodeInterface);
//					}
//
//					if (nodeInterface != null && listNodeInterface.contains(nodeInterface)) {
//						for (NodeInterface physNodeInterface : listNodeInterface) {
//							if (physNodeInterface.getId().equalsIgnoreCase(nodeInterface.getId())) {
//								physNodeInterface.setSubInterfaces(nodeInterface.getSubInterfaces());
//								physNodeInterface.setIpData(nodeInterface.getIpData());
//								physNodeInterface.setInterfaceName(nodeInterface.getInterfaceName());
//								physNodeInterface.setVlanId(nodeInterface.getVlanId());
//								physNodeInterface.setVlanSetTrunk(nodeInterface.getVlanSetTrunk());
//								physNodeInterface.setExternalCode(nodeInterface.getExternalCode());
//								physNodeInterface.setPortSpeed(nodeInterface.getPortSpeed());
//								physNodeInterface.setPortUtilization(nodeInterface.getPortUtilization());
//								physNodeInterface.setPortStatus(nodeInterface.getPortStatus());
//								physNodeInterface.setMinThroughtput(nodeInterface.getMinThroughtput());
//								physNodeInterface.setPeakThroughput(nodeInterface.getPeakThroughput());
//								// physNodeInterface.setExternalCode(nodeInterface.getCapacity());
//								// physNodeInterface.setExternalCode(nodeInterface.getTotalLinkCapacity());
//								break;
//							}
//						}
//					} else {
//						nodeBoard.getNodeInterfaces().add(nodeInterface);
//					}
//				}
//			}
//		}
//
//	}
//
 