package com.mobinets.nps.customer.transmission.manufacture.aviat.nodeSlot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.mobinets.nps.customer.transmission.common.BoardTypeMatchingParser;
import com.mobinets.nps.customer.transmission.common.CommonConfig;
import com.mobinets.nps.customer.transmission.common.FilesFilterHandler;
import com.mobinets.nps.customer.transmission.common.TransmissionCommon;
import com.mobinets.nps.customer.transmission.common.utilties.CsvHandler;
import com.mobinets.nps.customer.transmission.manufacture.aviat.node.AviatNetworkElementsParser;
import com.mobinets.nps.customer.transmission.manufacture.aviat.trlinks.AviatTransmissionLinkParser;
import com.mobinets.nps.customer.transmission.manufacture.common.ConnectorUtility;
import com.mobinets.nps.customer.transmission.manufacture.ericsson.r3.common.R3NEIDFounder;
import com.mobinets.nps.daemon.common.NodeContainer;
import com.mobinets.nps.daemon.csv.AbstractFileCsvParser;
import com.mobinets.nps.model.nodeinterfaces.NetworkElement;
import com.mobinets.nps.model.nodeinterfaces.NodeBoard;
import com.mobinets.nps.model.nodeinterfaces.NodeCabinet;
import com.mobinets.nps.model.nodeinterfaces.NodeInterface;
import com.mobinets.nps.model.nodeinterfaces.NodeShelf;
import com.mobinets.nps.model.nodeinterfaces.NodeSlot;

public class AviatmwNodeSlotsParser extends AbstractFileCsvParser<NodeSlot>implements CsvHandler {

	private static final Log log = LogFactory.getLog(AviatmwNodeSlotsParser.class);
	private static final Log logErr = LogFactory.getLog("NODE_SLOT_ERROR_LOGGER");

	private CommonConfig r3Config;
	private BoardTypeMatchingParser boardTypeMatching;
	private R3NEIDFounder r3NeIdFounder;

	private Map<String, NodeSlot> nodeSlots = null;
    private Map<String, NodeInterface> ethernetMap;
    private Map<String,NodeInterface> linktotalCpacitymap = new HashMap<>();
    public Map<String, NodeInterface> getLinktotalCpacitymap() {
		return linktotalCpacitymap;
	}

	private Map<String, String> circleIdmap;
    private List<String> ethernetMapInterfacesList = new ArrayList<>();
    private List<String> boardIdList = new ArrayList<>();
    private List<String> nodeInterfaceList = new ArrayList<>();
    private Map<String, NodeSlot> nodeInterfaceIdNodeboardMap = new HashMap<String, NodeSlot>();
    private AviatTransmissionLinkParser aviatTransmissionLinkParser;
	
   
	public void setAviatTransmissionLinkParser(AviatTransmissionLinkParser aviatTransmissionLinkParser) {
		this.aviatTransmissionLinkParser = aviatTransmissionLinkParser;
	}

	private boolean isParsed = false;
     
    
	public Map<String, NodeInterface> getEthernetMap() {
		return ethernetMap;
	}

	private Map<String, NodeBoard> duplicateBoards = null;
	private Map<String, NetworkElement> networkEleMap = new HashMap<String, NetworkElement>();
	/*private Map<String,String > nodeOneInterfacemapfromLinkReport;
	private Map<String,String > nodeTwoInterfacemapfromLinkReport;*/
	private Map<String, String> mmuSlotByNeMap = new HashMap<String, String>();
	private Map<String, List<NodeInterface>> tranceiverMap = new HashMap<String, List<NodeInterface>>(); 
	private Map<String,NodeInterface> nodeInterfaceMap =  new HashMap<String, NodeInterface>();  
	Map<String, String> npuSlotMap ;
	private Map<String, NodeCabinet> cabinets = new HashMap<String, NodeCabinet>(); 
	private Map<String,NodeInterface> config_EthernerMap  = new HashMap<String, NodeInterface>(); 
	private Map<String, NodeShelf> shelves = new HashMap<String, NodeShelf>(); 
	private Map<String, NodeSlot> slotsMap = new HashMap<String,NodeSlot>();
	private HashMap<String,NodeBoard> boardMap = new HashMap<>();
	Map<String, String> fauSlotMap ;
	
	AviatNetworkElementsParser aviatNetworkElementsParser;

	public void setAviatNetworkElementsParser(AviatNetworkElementsParser aviatNetworkElementsParser) {
		this.aviatNetworkElementsParser = aviatNetworkElementsParser;
	}

	public void setR3NeIdFounder(R3NEIDFounder r3NeIdFounder) {
		this.r3NeIdFounder = r3NeIdFounder;
	}

	public void setR3Config(CommonConfig r3Config) {
		this.r3Config = r3Config;
	}

	public void setBoardTypeMatching(BoardTypeMatchingParser boardTypeMatching) {
		this.boardTypeMatching = boardTypeMatching;
	}
	public void init(){
		ethernetMap = new HashMap<>(); 
	}
	
//	public List<String> getTrinkIdList() {
//		List<String> mwConfig = new ArrayList<>();
//		if (linktotalCpacitymap == null)
//		for(NodeInterface key : linktotalCpacitymap.values()){
//			String cap = key.getTotalLinkCapacity();
//			String id = key.getId();
//			String interfaceId = id+"/"+cap;
//			mwConfig.add(interfaceId);
//		}
//		return mwConfig;
//	}
	 
	/**
	 * 
	 */
	private void parseNodeSlotsFromAviatInventoryFile() {
	 init();
	    parsingInterfaceFromTrlinkReport();
		parsingInterfacefromEthernetReport();
		 
		log.debug("Begin of Ericsson Node Slots parsing From Soem Inventory File ...");

		if (null == duplicateBoards)
			duplicateBoards = new HashMap<String, NodeBoard>();

		if (null == nodeSlots)
			nodeSlots = new HashMap<String, NodeSlot>();
		
		String cabinetIndex = "1", ruIndex ="1", shelfIndex = "1" , indexOnSlot ="0";
		int portIdx = 0;
		String cabinetType = "" , shelfType = "", boardType = "", moduleType = "", moduleTypes ="";
		String manufacture = "Aviat", serialNumber = "", productNo ="", version = "",partNumber ="";
		String slotIndex = ""; String nodeId_ip = "",moduleName = "",ethernetCap="",ip="",interfaceId="";
		String path = r3Config.getProperty("aviat.mw.dumps");

		if (null == path) {
			log.error("Missing attribute (aviat.mw.dumps) in manufacture-config.xml.");
			return;
		}

		File folder = new File(path);

		if (!folder.exists()) {
			logErr.error("Folder (" + path + ") not found");
			log.error("Folder (" + path + ") not found");
			return;
		}
		
		List<File> aviatFiles = new ArrayList<>();
		ConnectorUtility.listofFiles(path, aviatFiles, new FilesFilterHandler.CsvFiles());
		
		clearHeaders();
		addHeaderToParse("Name");
		addHeaderToParse("IP Address");
		addHeaderToParse("Device Type");
		addHeaderToParse("Part Number");
		addHeaderToParse("Serial Number");
		addHeaderToParse("Hardware Revision");
		addHeaderToParse("Module Type");
		addHeaderToParse("Module Name");
		 
		
		
		
		for (int i =0;i<aviatFiles.size();i++) {
			File file = aviatFiles.get(i);
		
			if (!file.getName().contains("INVENTORY_REPORT"))
				continue;
			
			String hub = StringUtils.substringAfterLast(file.toString(), "MW");
			hub = StringUtils.substringBefore(hub, "_");
			hub = hub.replaceAll("AVIATNMS", "").trim();
			hub = StringUtils.substring(hub, 1);
		 
			try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {

	            // Skip the first two lines
	            for (int i1 = 0; i1 < 2; i1++) {
	                bufferedReader.readLine();
	            }
			CsvListReader csvReader = new CsvListReader(bufferedReader, CsvPreference.EXCEL_PREFERENCE);
			
			try {
				 
				final String[] header = csvReader.getCSVHeader(true);
				boolean isOK = fillHeaderIndex(header);

				if (!isOK) {
					logErr.error("Error data in header (ID, Type, Address, NEName) .. for file " + file.getPath());
					continue;
				}
			
				List<String> row = new ArrayList<String>();
				while ((row = csvReader.read()) != null) {
					try{
					String neId = row.get(headerIndexOf("Name"));
					String neName = row.get(headerIndexOf("Name")).trim();
					String model = row.get(headerIndexOf("Device Type"));
					moduleName = row.get(headerIndexOf("Module Name")); 
					ip = row.get(headerIndexOf("IP Address")).trim();
					serialNumber = row.get(headerIndexOf("Serial Number")).trim();
					productNo = row.get(headerIndexOf("Part Number")).trim();
					moduleTypes =  row.get(headerIndexOf("Module Type")).trim();
					version = row.get(headerIndexOf("Hardware Revision")).trim();
					boolean isTransceiver = false;
					
					String circle1 = StringUtils.substring(neId, 0, 2);
					nodeId_ip = ip+"_"+circle1;
					
					if(!circle1.contains("DL"))
						continue;
					
					if(nodeId_ip.contains("2401:4900:0:8016:0:1800:1:c465_DL"))
					System.out.println();
					
				     String cabinetIds = nodeId_ip + "_" + cabinetIndex;
						String shelfIds = cabinetIds + "_" + shelfIndex;
					 
						if(moduleTypes.contains("Pluggable SFP Module"))
							isTransceiver = true;
							
						//Rule for IndexOnSlot and BoardType
						if(moduleTypes.contains("Radio Module") && moduleName.contains("mmwRFModule1/1")){
							slotIndex = "0";
							model = "mmwRFModule";
							
							
							String slotId = shelfIds + "_" + slotIndex;
							
							NodeCabinet nodeCabinet = createNodeCabinet(cabinetIds,cabinetIndex, cabinetType,model,manufacture,nodeId_ip);
							NodeShelf nodeShelf = createNodeShelf(shelfIds,shelfIndex, nodeCabinet,shelfType,manufacture,model);
							
							NodeSlot nodeSlot = createNodeSlot(nodeId_ip, slotId, slotIndex, nodeShelf);

							String boardIds = slotId + "_" + indexOnSlot;
							
							if(boardIds.contains("2401:4900:0:8016:0:1800:1:e102_DL_1_1_1_0_1"))
								System.out.println();
							
							NodeBoard nodeBoard = boardMap.get(boardIds);
							String bId = "";
						//	String strIndexOnSlot = String.valueOf(indexOnSlot);
							
							if(boardIdList.contains(boardIds))
								continue;
							
//							if(boardIdList.contains(boardIds))
//							{
//								String lastIndex = StringUtils.substringAfterLast(boardIds,"_");
//								int intindex = Integer.valueOf(lastIndex);
//								intindex++;
//								String stringindex = String.valueOf(intindex);
//							    bId = StringUtils.substringBeforeLast(boardIds, "_");
//							    boardIds = bId+"_"+stringindex;
//								
//							}
						 
							 
							nodeBoard = createNodeBoard(nodeId_ip, slotId, indexOnSlot, boardIds,
										TransmissionCommon.convertStringToInteger(indexOnSlot), moduleTypes,
										model,nodeSlot,moduleName,serialNumber,version,productNo,manufacture,slotIndex);
				
							boardMap.put(boardIds, nodeBoard);
	                       nodeSlot.getNodeBoards().add(nodeBoard);
						   slotsMap.put(nodeSlot.getId(), nodeSlot);
						   
							
						}
						
						else if(moduleTypes.contains("WTM4800 Terminal") && moduleName.contains("Terminal1")){
							slotIndex = "1";
						   model = "WTM4800 Terminal";
						   
						   
						   String slotId = shelfIds + "_" + slotIndex;
							
							NodeCabinet nodeCabinet = createNodeCabinet(cabinetIds,cabinetIndex, cabinetType,model,manufacture,nodeId_ip);
							NodeShelf nodeShelf = createNodeShelf(shelfIds,shelfIndex, nodeCabinet,shelfType,manufacture,model);
							
							NodeSlot nodeSlot = createNodeSlot(nodeId_ip, slotId, slotIndex, nodeShelf);

							String boardIds = slotId + "_" + indexOnSlot;
							if(boardIds.contains("2401:4900:0:8016:0:1800:1:e102_DL_1_1_1_0"))
								System.out.println();

							NodeBoard nodeBoard = boardMap.get(boardIds);
							String bId = "";
						//	String strIndexOnSlot = String.valueOf(indexOnSlot);
							
							if(boardIdList.contains(boardIds))
							continue;
								
							
						 
							 
							nodeBoard = createNodeBoard(nodeId_ip, slotId, indexOnSlot, boardIds,
										TransmissionCommon.convertStringToInteger(indexOnSlot), moduleTypes,
										model,nodeSlot,moduleName,serialNumber,version,productNo,manufacture,slotIndex);
				
							boardMap.put(boardIds, nodeBoard);
	                       nodeSlot.getNodeBoards().add(nodeBoard);
						   slotsMap.put(nodeSlot.getId(), nodeSlot);
						   
						}	else if(moduleTypes.contains("Pluggable SFP Module"))
						{
							 String description ="Transceiver";
			            	   
			            	   String slotIndexforInterface = "1";
			            	   String indexOnSlotForInterface = "0";
			            	   int portIdxforInterface = 0 ;
			            	   if(moduleName.contains("SFP+Module1/1")){
			            		   portIdxforInterface = 1;
							 }
							   else if(moduleName.contains("SFP+Module1/2")){
								   portIdxforInterface = 2;
							   }
							   
							   interfaceId = nodeId_ip+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndexforInterface+"_"+indexOnSlotForInterface+"_"+portIdxforInterface;
							
							   String BoardId = nodeId_ip+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndexforInterface+"_"+indexOnSlotForInterface;
							   String sfpType = "SFP+Module";
							 
								List<NodeInterface> nodeInterfaces = new ArrayList<>();
								if (isTransceiver){
									nodeInterfaces.add(createTransceiver(interfaceId, portIdxforInterface, sfpType, serialNumber, partNumber));
									if(tranceiverMap.containsKey(BoardId)){
										tranceiverMap.get(BoardId).addAll(nodeInterfaces);
									}
									else{
										tranceiverMap.put(BoardId, nodeInterfaces);
									}
									
									continue;
								}
							
						}
						
						/*String slotId = shelfIds + "_" + slotIndex;
						
						NodeCabinet nodeCabinet = createNodeCabinet(cabinetIds, cabinetType,model,manufacture);
						NodeShelf nodeShelf = createNodeShelf(shelfIds,shelfIndex, nodeCabinet,shelfType,manufacture,model);
						
						NodeSlot nodeSlot = createNodeSlot(nodeId_ip, slotId, slotIndex, nodeShelf);

						String boardIds = slotId + "_" + indexOnSlot;
						
						NodeBoard nodeBoard = boardMap.get(boardIds);
						String bId = "";
					//	String strIndexOnSlot = String.valueOf(indexOnSlot);
						
						if(boardIdList.contains(boardIds))
						{
							String lastIndex = StringUtils.substringAfterLast(boardIds,"_");
							int intindex = Integer.valueOf(lastIndex);
							intindex++;
							String stringindex = String.valueOf(intindex);
						    bId = StringUtils.substringBeforeLast(boardIds, "_");
							bId = bId+"_"+stringindex;
							
						}
					 
						 
						nodeBoard = createNodeBoard(nodeId_ip, slotId, indexOnSlot, bId,
									TransmissionCommon.convertStringToInteger(indexOnSlot), moduleTypes,
									model,nodeSlot,moduleName,serialNumber,version,productNo,manufacture,slotIndex);
			
						boardMap.put(boardIds, nodeBoard);
                       nodeSlot.getNodeBoards().add(nodeBoard);
					   slotsMap.put(nodeSlot.getId(), nodeSlot);*/
                       
		               /*if(moduleTypes.contains("Pluggable SFP Module")){
		            	   
		            	   String description ="Transceiver";
		            	   
		            	   String slotIndexforInterface = "1";
		            	   
		            	   if(moduleName.contains("SFP+Module1/1")){
							   portIdx = 1;
						 }
						   else if(moduleName.contains("SFP+Module1/2")){
							   portIdx = 2;
						   }
						   
						   interfaceId = nodeId_ip+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndexforInterface+"_"+indexOnSlot+"_"+portIdx;
						
						   if(interfaceId.contains("2401:4900:0:8016:0:1800:1:c586_DL_1_1_1_0_1"))
							   System.out.println();
						 
						   
							//Interface created from invenotry 
						   
						   if(nodeInterfaceList.contains(interfaceId))
							   continue;
						
					      NodeInterface nodeInterface = createNodeInterface(interfaceId,portIdx,moduleName,serialNumber,partNumber,description,boardIds," "," "," "," ",moduleTypes);
					      nodeBoard.getNodeInterfaces().add(nodeInterface);
						  nodeSlot.getNodeBoards().add(nodeBoard);
						   slotsMap.put(nodeSlot.getId(), nodeSlot);
						   nodeInterfaceIdNodeboardMap.put(interfaceId,nodeSlot);
						 
						   
		               }*/
							 
					} catch (Exception e) {
						log.error("Error : ", e);
					}
				}			
				csvReader.close();
			}	catch (Exception e) {
				log.error("Error : ", e);
			}	
		}catch(Exception e){
			e.printStackTrace();
			log.error("Error: ",e);
		}

	} 
 
       log.debug("End of Ericsson Node Slots parsing From Soem Inventory File ...");
       
       createTranceivers();
	//	createNodeInterfaceFromEthernetmap(slotsMap,ethernetMap); 
      
	} 
 

private void parsingInterfaceFromTrlinkReport() {

	log.debug("Start creating Aviat MW Transmission links");
	initlink();

	String path = r3Config.getProperty("aviat.mw.dumps");

	if (null == path) {
		log.error("Missing path (aviat.mw.dumps) in context file.");
		return;
	}
	File folder = new File(path);
	if (!folder.exists()) {
		log.error("Folder (" + path + ") not found");
		return;
	}
	
	String cabinetIndex = "1",shelfIndex = "1" , indexOnSlot ="0", slotIndex = "0", portIndex ="1";
	String interfaceId1="", interfaceId2 ="";
	String site1 ="", site2="";
	String externalCode ="",capacity = "",linkType ="",linkModulation="",typeofBand="";
	
	 
	if (!folder.exists()) {
		logErr.error("Folder (" + path + ") not found");
		log.error("Folder (" + path + ") not found");
		return;
	}
	
	List<File> aviatFiles = new ArrayList<>();
	ConnectorUtility.listofFiles(path, aviatFiles, new FilesFilterHandler.CsvFiles());
	
	
	clearHeaders();
	addHeaderToParse("Site A IP");
	addHeaderToParse("Site Z IP");
	addHeaderToParse("Site A Name");
	addHeaderToParse("Site Z Name");
	addHeaderToParse("Site A Maximum Configured Capacity");
	addHeaderToParse("Site Z Maximum Configured Capacity");
	addHeaderToParse("Site A Current Modulation");
	addHeaderToParse("Site Z Current Modulation");
	addHeaderToParse("ATPC Status");
	addHeaderToParse("Site A Tx Freq (MHz)");
	addHeaderToParse("Site Z Tx Freq (MHz)");
	addHeaderToParse("Site A Max Configured Modulation");
	addHeaderToParse("Site Z Max Configured Modulation");
	addHeaderToParse("Site A Min Configured Modulation");
	addHeaderToParse("Site Z Min Configured Modulation");
	addHeaderToParse("Site A Max RSL Last 24h");
	addHeaderToParse("Site Z Max RSL Last 24h");
	addHeaderToParse("Site A Min RSL Last 24h");
	addHeaderToParse("Site Z Min RSL Last 24h");
	addHeaderToParse("Site A Max Tx Power Last 24h");
	addHeaderToParse("Site Z Max Tx Power Last 24h");
	addHeaderToParse("Site Z Max Tx Power Last 24h");
	
	
	
	 

	for (int i =0;i<aviatFiles.size();i++) {
		File file = aviatFiles.get(i);
	
		if (!file.getName().contains("LINK_REPORT"))
			continue;
		
		 
		try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {

            // Skip the first two lines
            for (int i1 = 0; i1 < 2; i1++) {
                bufferedReader.readLine();
            }
		CsvListReader csvReader = new CsvListReader(bufferedReader, CsvPreference.EXCEL_PREFERENCE);
		
		try {
			 
			final String[] header = csvReader.getCSVHeader(true);
			boolean isOK = fillHeaderIndex(header);

			if (!isOK) {
				logErr.error("Error data in header (ID, Type, Address, NEName) .. for file " + file.getPath());
				continue;
			}
		
			List<String> row = new ArrayList<String>();
			while ((row = csvReader.read()) != null) {
				try{
				String node1 = row.get(headerIndexOf("Site A IP"));
				String node2 = row.get(headerIndexOf("Site Z IP"));
				String name1 = row.get(headerIndexOf("Site A Name"));
				String name2 = row.get(headerIndexOf("Site Z Name"));
				String siteA_MaxCapacity = row.get(headerIndexOf("Site A Maximum Configured Capacity"));
				String siteB_MaxCapacity = row.get(headerIndexOf("Site Z Maximum Configured Capacity"));
	 
				String circl1  = name1.substring(0, Math.min(name1.length(), 2));
				String circl2  = name2.substring(0, Math.min(name2.length(), 2));
			/*	
				 
				 String circle1 = circleIdmap.get(node1);
				 String circle2 = circleIdmap.get(node2);
		         */
				 if(!circl1.contains("DL") && !circl2.contains("DL"))
					 continue;
				 String s1 = StringUtils.substring(site1, 4) + "_" + StringUtils.substring(site1, 0, 2);
				 String s2 = StringUtils.substring(site2, 4) + "_" + StringUtils.substring(site2, 0, 2);
				 
					String external1 = node1 + "_"+circl1+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex + "_" + indexOnSlot + "_"
							+ portIndex;
					String external2 = node2 + "_"+circl2+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex +"_" + indexOnSlot + "_"
							+ portIndex;
					
					String capacity1 = external1+"_"+siteA_MaxCapacity;
					String capacity2 = external2+"_"+siteB_MaxCapacity;
					
					String boardId1 = StringUtils.substringBeforeLast(external1, "_");
					String boardId2 = StringUtils.substringBeforeLast(external2, "_");
					
					if(external1 != null && !external1.isEmpty()){
						NodeInterface interface1 = new NodeInterface();
						interface1.setId(external1);
						interface1.setNodeBoardId(boardId1);
						interface1.setTotalLinkCapacity(siteA_MaxCapacity);
						linktotalCpacitymap.put(external1,interface1);
					}
					if(external2 != null && !external2.isEmpty()){
						NodeInterface interface2 = new NodeInterface();
						interface2.setId(external1);
						interface2.setNodeBoardId(boardId2);
						interface2.setTotalLinkCapacity(siteB_MaxCapacity);
						linktotalCpacitymap.put(external2,interface2);
					}
					
				}catch (Exception e) {
			log.error("Error : ", e);
		}	
			}
	}catch(Exception e){
		e.printStackTrace();
		log.error("Error: ",e);
	}

} 
catch(Exception e){
e.printStackTrace();
log.error("Error: ",e);
}
   log.debug("End of parsing  Interfaces from Trlink Report respective to TotalLinkCapacity ...");
		}
  
} 
		
private void initlink() {
	
 
	circleIdmap =  aviatNetworkElementsParser.getCircleIdMap();
}

private void createTranceivers() {
		for(NodeSlot slot:slotsMap.values()){
			for(NodeBoard board:slot.getNodeBoards()){
				if(tranceiverMap.containsKey(board.getId())){
					board.getNodeInterfaces().addAll(tranceiverMap.get(board.getId()));
				}
			}
		}
		
	}


private NodeInterface createTransceiver(String interfaceId, Integer portIndex, String typeOfUnit, String serial,
			String partNumber) {
		NodeInterface nodeInterface = new NodeInterface();
		nodeInterface.setId(interfaceId);
		nodeInterface.setInterfaceIndex(portIndex);
//		nodeInterface.setTransceiverDesc(typeOfUnit);
		nodeInterface.setSfpdescription(typeOfUnit);
		nodeInterface.setDescription("Transceiver");
		nodeInterface.setPartnumber(partNumber);
		nodeInterface.setSerial(serial);
		// nodeInterface.setSfpdescription(description);
		return nodeInterface;
	}

private void createNodeInterfaceFromEthernetmap(Map<String, NodeSlot> slotsMap2,
		Map<String, NodeInterface> ethernetMap2) {
	 
	
	
	 for(NodeInterface interfacefromEthernet : ethernetMap2.values()){
    	 try{
    	 String pSpeed =  interfacefromEthernet.getPortSpeed();
		 String pStatus = interfacefromEthernet.getPortStatus();
		 String eCapacity = interfacefromEthernet.getEthernetCapacity();
		 String interFaceNames = interfacefromEthernet.getInterfaceName();
	 
    	 
    	 String interfaceIdfromEthernet = interfacefromEthernet.getId();
    	 if(nodeInterfaceList.contains(interfaceIdfromEthernet)){
    		 
    		 if(nodeInterfaceIdNodeboardMap.containsKey(interfaceIdfromEthernet)){
    			 
    			NodeSlot slots = nodeInterfaceIdNodeboardMap.get(interfaceIdfromEthernet);
    			
    			for(NodeBoard nodeboard  : slots.getNodeBoards()){
    				
    				for(NodeInterface nodeinterface : nodeboard.getNodeInterfaces()){
    					
    					nodeinterface.setId(interfaceIdfromEthernet);
    					nodeinterface.setInterfaceName(interFaceNames);
    					nodeinterface.setPortSpeed(pSpeed);
    					nodeinterface.setPortStatus(pStatus);
    					nodeinterface.setEthernetCapacity(eCapacity);
    					nodeboard.getNodeInterfaces().add(nodeinterface);
    					 slots.getNodeBoards().add(nodeboard);
	   					   slotsMap.put(slots.getId(), slots);
    					
    				}
    				
    			
    				
    			}
    		 }
    		 
    	 }
    	 
    	 
    	 
     }
	 catch(Exception e){
		 log.error("Error");
	 }
	 }
}
	//////////
	
	
	
	
	
	
	
	
	
//	
//	for (NodeSlot slot : slotsMap2.values()) {
//		try {
//			Map<String, NodeBoard> boardMap = new HashMap<String, NodeBoard>();
//			for (NodeBoard board : slot.getNodeBoards()) {
//				String boardId = board.getId();
//				for(NodeInterface interfaces : board.getNodeInterfaces()){
//					
//					String interfaceIdfromTransreciever = interfaces.getId();
//					if(interfaceIdfromTransreciever == null || interfaceIdfromTransreciever.isEmpty())
//						System.out.println();
//						
//					
//				     for(NodeInterface interfacefromEthernet : ethernetMap2.values()){
//				    	 
//				    	 String pSpeed =  interfacefromEthernet.getPortSpeed();
//			    		 String pStatus = interfacefromEthernet.getPortStatus();
//			    		 String eCapacity = interfacefromEthernet.getEthernetCapacity();
//			    		 String interFaceNames = interfacefromEthernet.getInterfaceName();
//				    	 
//				    	 String interfaceIdfromEthernet = interfacefromEthernet.getId();
//				    	 if(nodeInterfaceList.contains(interfaceIdfromEthernet)){
//				    		 
//				    		 if(nodeInterfaceIdNodeboardMap.containsKey(interfaceIdfromEthernet)){
//				    			 
//				    			NodeSlot slots = nodeInterfaceIdNodeboardMap.get(interfaceIdfromEthernet);
//				    			
//				    			for(NodeBoard nodeboard  : slots.getNodeBoards()){
//				    				
//				    				for(NodeInterface nodeinterface : nodeboard.getNodeInterfaces()){
//				    					
//				    					nodeinterface.setPortSpeed(pSpeed);
//				    					nodeinterface.setPortStatus(pStatus);
//				    					nodeinterface.setEthernetCapacity(eCapacity);
//				    					 board.getNodeInterfaces().add(nodeinterface);
//							    		 slot.getNodeBoards().add(board);
//					   					   slotsMap.put(slot.getId(), slot);
//				    					
//				    				}
//				    				
//				    			
//				    				
//				    			}
//				    		 }
//				    		 
//				    	 }
//				    	 if(interfaceIdfromTransreciever == null || interfaceIdfromTransreciever.isEmpty())
//				    	 {
//				    		 String pSpeed =  interfacefromEthernet.getPortSpeed();
//				    		 String pStatus = interfacefromEthernet.getPortStatus();
//				    		 String eCapacity = interfacefromEthernet.getEthernetCapacity();
//				    		 String interFaceNames = interfacefromEthernet.getInterfaceName();
//				    		 
//                            NodeInterface nodeInterface = new NodeInterface();
//				    		 
//                             nodeInterface.setId(interfaceIdfromEthernet);
//				    		 nodeInterface.setPortSpeed(pSpeed);
//				    		 nodeInterface.setPortStatus(pStatus);
//				    		 nodeInterface.setEthernetCapacity(eCapacity);
//				    		 board.getNodeInterfaces().add(nodeInterface);
//				    		 slot.getNodeBoards().add(board);
//		   					   slotsMap.put(slot.getId(), slot);
//				    		 
//				    	 }
//				    	 else  if(interfaceIdfromTransreciever.equals(interfaceIdfromEthernet)){
//				    		 
//				    		 String pSpeed =  interfacefromEthernet.getPortSpeed();
//				    		 String pStatus = interfacefromEthernet.getPortStatus();
//				    		 String eCapacity = interfacefromEthernet.getEthernetCapacity();
//				    		 String interFaceNames = interfacefromEthernet.getInterfaceName();
//				    		 
//				    		 NodeInterface nodeInterface = new NodeInterface();
//				    		 
//				    		 nodeInterface.setPortSpeed(pSpeed);
//				    		 nodeInterface.setPortStatus(pStatus);
//				    		 nodeInterface.setEthernetCapacity(eCapacity);
//				    		 board.getNodeInterfaces().add(nodeInterface);
//				    		 slot.getNodeBoards().add(board);
//		   					   slotsMap.put(slot.getId(), slot);
//				    	 }
//				    	 
//				     }
//				}
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//	}
	
 
	private void parsingInterfacefromEthernetReport() {
		parsingOlnymoduleFromConfiguration();
		 
			log.debug("Start Paring Aviat Configuration Report for Interface Name for Mapping With Ethernet Report");
			init();

			String path = r3Config.getProperty("aviat.mw.dumps");

			if (null == path) {
				log.error("Missing path (aviat.mw.dumps) in context file.");
				return;
			}
			File folder = new File(path);
			if (!folder.exists()) {
				log.error("Folder (" + path + ") not found");
				return;
			}
			
			String cabinetIndex = "1",shelfIndex = "1" , indexOnSlot ="0";
			String interfaceId1="", interfaceId2 ="";
			String site1 ="", site2="";
			String externalCode ="",capacity = "",linkType ="",linkModulation="",interfaceid="";
			String portSpeed ="",ethernetCapcity="";
		    String portStatus="";
			
			 
			if (!folder.exists()) {
				logErr.error("Folder (" + path + ") not found");
				log.error("Folder (" + path + ") not found");
				return;
			}
			
			List<File> aviatFiles = new ArrayList<>();
			ConnectorUtility.listofFiles(path, aviatFiles, new FilesFilterHandler.CsvFiles());
			
			
			clearHeaders();
			addHeaderToParse("Interface");
			addHeaderToParse("In Mbps (Mbps)");
			addHeaderToParse("Out Mbps (Mbps)");
			addHeaderToParse("Max In Utilization (%)");
			addHeaderToParse("Max Out Utilization (%)");
			
		 

			for (int i =0;i<aviatFiles.size();i++) {
				File file = aviatFiles.get(i);
			
				if (!file.getName().contains("ETHERNET_REPORT"))
					continue;
				
				 
				try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {

		            // Skip the first two lines
		            for (int i1 = 0; i1 < 2; i1++) {
		                bufferedReader.readLine();
		            }
				CsvListReader csvReader = new CsvListReader(bufferedReader, CsvPreference.EXCEL_PREFERENCE);
				
				try {
					 
					final String[] header = csvReader.getCSVHeader(true);
					boolean isOK = fillHeaderIndex(header);

					if (!isOK) {
						logErr.error("Error data in header (Interface Name, Mpbs) .. for file " + file.getPath());
						continue;
					}
				
					List<String> row = new ArrayList<String>();
					while ((row = csvReader.read()) != null) {
						try{
						String interfaceName = row.get(headerIndexOf("Interface"));
						String Inutilization = row.get(headerIndexOf("Max In Utilization (%)"));  
						String Outuitlization = row.get(headerIndexOf("Max Out Utilization (%)"));  
						//utilization rule
						
						double inutilization  = Double.valueOf(Inutilization);
						double oututilization = Double.valueOf(Outuitlization);
						
						double maxInOutUtilization = Math.max(inutilization, oututilization);
						String portUtilization = String.valueOf(maxInOutUtilization);
						String peakThroughOut = portUtilization;
						
						
						//ethernetcapcity-rule
						String inMpbs =  row.get(headerIndexOf("In Mbps (Mbps)")); 
						String outMpbs =  row.get(headerIndexOf("Out Mbps (Mbps)")); 
				        
						if(inMpbs.isEmpty() &&  outMpbs.isEmpty()){
							ethernetCapcity =" ";
						}
						else{
						double inMb = Double.valueOf(inMpbs);
						double outMb= Double.valueOf(outMpbs);
						
						double maxEthernetCapacity = Math.max(inMb, outMb);
						ethernetCapcity = String.valueOf(maxEthernetCapacity);
							}
						String name = StringUtils.substringBefore(interfaceName, " ").trim();
						String circle = name.substring(0, Math.min(name.length(), 2));
						String type = StringUtils.substringAfter(interfaceName, " ").trim();
						
						if(!circle.contains("DL"))
							continue;
						
						 if(name.contains("DLMW836-DLMW13096"))
							 System.out.println();
						if(config_EthernerMap.keySet().contains(interfaceName)){
							
							NodeInterface configData = config_EthernerMap.get(interfaceName);
							 portSpeed = configData.getPortSpeed();
							 portStatus = configData.getPortStatus();
							 String ip = configData.getId();
							   
								 if(type.contentEquals("GigabitEthernet1/1") || type.contentEquals("GigabitEthernet1/2") || type.contentEquals("TenGigE1/2") 
								 ||type.contentEquals("TenGigE1/1")){
								 if(type.contentEquals("GigabitEthernet1/1")){
										String slotIndex="1";
										String portIndex= "3";
										String portType = "GETH";
									
									 
									interfaceid = ip+"_"+circle+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex+"_"+indexOnSlot+"_"+portIndex;
									
									
								}
									if(type.contentEquals("GigabitEthernet1/2")){
										String slotIndex="1";
										String portIndex= "4";
										String portType = "GETH";
									
									 
									interfaceid = ip+"_"+circle+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex+"_"+indexOnSlot+"_"+portIndex;
									
									
								}
									if(type.contentEquals("TenGigE1/2")){
										String slotIndex="1";
										String portIndex= "2";
										String portType = "TENGETH";
									
									 
									interfaceid = ip+"_"+circle+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex+"_"+indexOnSlot+"_"+portIndex;
									
									
								}
									if(type.contentEquals("TenGigE1/1")){
										String slotIndex="1";
										String portIndex= "1";
										String portType = "TENGETH";
									
									 
									interfaceid = ip+"_"+circle+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex+"_"+indexOnSlot+"_"+portIndex;
									
									
								}
								 }
								 else if(type.contains("Radio1")){

							    	    String slotIndex="0";
										String portIndex= "1";
										String portType = "GETH";
									
									 
									interfaceid = ip+"_"+circle+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndex+"_"+indexOnSlot+"_"+portIndex;
								 
									 
								 }
								
						}
						else {
							System.out.println();
							continue;
							 
						}
						
						String nodeBoardId = StringUtils.substringBeforeLast(interfaceid, "_");
					    
						if(type.contains("Radio1")){
							
							NodeInterface interfaces2 = new NodeInterface();
							interfaces2.setId(interfaceid);
							interfaces2.setNodeBoardId(nodeBoardId);
							interfaces2.setEthernetCapacity(ethernetCapcity);	
							ethernetMap.put(interfaceid, interfaces2);
							ethernetMapInterfacesList.add(interfaceid);
						}
						else{		
							
							if(type.contains("L1LA1"))
								continue;
							
						NodeInterface interfaces2 = new NodeInterface();
						interfaces2.setId(interfaceid);
						interfaces2.setPortSpeed(portSpeed);
						interfaces2.setPortStatus(portStatus);
						interfaces2.setNodeBoardId(nodeBoardId);
						interfaces2.setInterfaceName(type);				
						ethernetMap.put(interfaceid, interfaces2);
						ethernetMapInterfacesList.add(interfaceid);
						}
						}catch (Exception e) {
					log.error("Error : ", e);
				}	
					}
			}catch(Exception e){
				e.printStackTrace();
				log.error("Error: ",e);
			}

		} 
		catch(Exception e){
		e.printStackTrace();
		log.error("Error: ",e);
		}
	       log.debug("End of parsing Module Type name on the basis of IP from Configuration file...");
				}
		
	}
	
	private void parsingOlnymoduleFromConfiguration() {
		log.debug("Start Paring Aviat MW Cofiguration for Module Type");
		//init();

		String path = r3Config.getProperty("aviat.mw.dumps");

		if (null == path) {
			log.error("Missing path (aviat.mw.dumps) in context file.");
			return;
		}
		File folder = new File(path);
		if (!folder.exists()) {
			log.error("Folder (" + path + ") not found");
			return;
		}
 
		if (!folder.exists()) {
			logErr.error("Folder (" + path + ") not found");
			log.error("Folder (" + path + ") not found");
			return;
		}
		
		List<File> aviatFiles = new ArrayList<>();
		ConnectorUtility.listofFiles(path, aviatFiles, new FilesFilterHandler.CsvFiles());
		
		
		clearHeaders();
		addHeaderToParse("Management IP Address");
		addHeaderToParse("Name"); 
		addHeaderToParse("Operational Status");
		addHeaderToParse("Port Speed (Mbps)");
		addHeaderToParse("Module Name");
		 

		for (int i =0;i<aviatFiles.size();i++) {
			File file = aviatFiles.get(i);
		
			if (!file.getName().contains("CONFIGURATION_REPORT"))
				continue;
			
			 
			try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {

	            // Skip the first two lines
	            for (int i1 = 0; i1 < 2; i1++) {
	                bufferedReader.readLine();
	            }
			CsvListReader csvReader = new CsvListReader(bufferedReader, CsvPreference.EXCEL_PREFERENCE);
			
			try {
				 
				final String[] header = csvReader.getCSVHeader(true);
				boolean isOK = fillHeaderIndex(header);

				if (!isOK) {
					logErr.error("Error data in header (IP Management, Module Name) .. for file " + file.getPath());
					continue;
				}
			
				List<String> row = new ArrayList<String>();
				while ((row = csvReader.read()) != null) {
					try{
					String ip = row.get(headerIndexOf("Management IP Address"));
					String name = row.get(headerIndexOf("Name"));
					String circle = name.substring(0, Math.min(name.length(), 2));
					String moduelName = row.get(headerIndexOf("Module Name"));
					
					String nameofConfig = name+" "+moduelName;
					if(!circle.contains("DL"))
						continue;
					
					if(name.contains("DLMW836-DLMW13096"))
						System.out.println();
					String portSpeed = row.get(headerIndexOf("Port Speed (Mbps)"));
					String portStatus =  row.get(headerIndexOf("Operational Status"));
					
					NodeInterface interfacs = new NodeInterface();
					interfacs.setId(ip);
					interfacs.setInterfaceName(name);
					interfacs.setPortSpeed(portSpeed);
					interfacs.setPortStatus(portStatus);
					config_EthernerMap.put(nameofConfig,interfacs);
									
								/*	createMwConfig(external1,external2,externalCode,node1,node2,site1,site2,external1,external2,externalCode,trLinkCapacity
											,totalLinkCapacity,linkModulation,atpc_mode);*/			
					}catch (Exception e) {
				log.error("Error : ", e);
			}	
				}
		}catch(Exception e){
			e.printStackTrace();
			log.error("Error: ",e);
		}

	} 
	catch(Exception e){
	e.printStackTrace();
	log.error("Error: ",e);
	}
       log.debug("End of parsing Module Type name on the basis of IP from Configuration file...");
			}
		
	}

	private NodeBoard createNodeBoard(String nodeId_ip, String slotId, String indexOnSlot, String boardIds,
			Integer convertStringToInteger, String moduleTypes, String model, NodeSlot nodeSlot,String moduleName,String boardSerialNumber,String version,
			String partNumber,String manufactureName,String slotIndex) {
		
		String indexOnslot =  StringUtils.substringAfterLast(boardIds, "_");
		NodeBoard board=new NodeBoard();
		
		//String boardId=TransmissionCommon.concatenateStrings("_", boardIds, slotIndex);
	 	board.setId(boardIds);
	 	board.setExternalCode(boardIds);
		board.setBoardIndexOnSlot(Integer.parseInt(indexOnslot));
		board.setSlotId(slotId);
		board.setBoardSerialNumber(boardSerialNumber);
		board.setBoardTypeCode(NodeContainer.getBoardDictionnaryForName(model));
		board.setVersion(version);
		board.setPartNumber(partNumber);
		board.setManufacturerName(manufactureName);
		board.setModel(model);
		boardIdList.add(boardIds);
		return board;
	}

	private NodeSlot createNodeSlot(String nodeId_ip, String slotId, String indexOnSlot, NodeShelf nodeShelf) {
		 
		Integer intSlot = Integer.valueOf(indexOnSlot);
		NodeSlot nodeSlot = slotsMap.get(slotId);
		if (nodeSlot == null) {
			nodeSlot = new NodeSlot();
			nodeSlot.setId(slotId);
			nodeSlot.setNodeId(nodeId_ip);
			nodeSlot.setSlotIndex(intSlot);
			nodeSlot.setShelf(nodeShelf);

			slotsMap.put(slotId, nodeSlot);
		}

		return nodeSlot;
	}
 

	private NodeShelf createNodeShelf(String shelfIds, String shelfIndex, NodeCabinet nodeCabinet, String shelfType,
			String manufacture, String model) {
		 
Integer intShelf = Integer.valueOf(shelfIndex);
		
   
		 
		NodeShelf nodeShelf = new NodeShelf();
			
		    nodeShelf.setId(shelfIds);
			nodeShelf.setModel(shelfType);
			nodeShelf.setStartRu(1);
			nodeShelf.setShelfIndex(intShelf);
			nodeShelf.setModel(shelfType);
			nodeShelf.setShelfType(NodeContainer.getNodeShelfTypeForName(shelfType));
			nodeShelf.setCabinet(nodeCabinet);
			nodeShelf.setManufacturerName(manufacture);
			NodeContainer.getNodeShelfForName(shelfIds, nodeShelf);
//			NodeContainer.getNodeShelfForName(nodeShelf.getId(), nodeShelf.getShelfIndex(), nodeShelf.getModel());
			shelves.put(shelfIds, nodeShelf);
		
		return nodeShelf;
	}

	private NodeCabinet createNodeCabinet(String cabinetIds, String cabinetIndex, String cabinetType, String model, String manufacture,String nodeId_ip) {
 
		 int intCabinetIndex = Integer.valueOf(cabinetIndex);
		    NodeCabinet nodeCabinet = new NodeCabinet();
			nodeCabinet.setId(cabinetIds);
			nodeCabinet.setModel(cabinetType);
			nodeCabinet.setNodeId(nodeId_ip);
			nodeCabinet.setCabinetIndex(intCabinetIndex);
			nodeCabinet.setNodeType(cabinetType);
			nodeCabinet.setCabinetType(NodeContainer.getNodeCabinetTypeForName(cabinetType));
			nodeCabinet.setCabinetIndex(1);
			nodeCabinet.setManufacturerName(manufacture);
			nodeCabinet = NodeContainer.getNodeCabinetForName(cabinetIds, nodeCabinet);
			//NodeContainer.getNodeCabinetForName(nodeCabinet.getId(), nodeCabinet.getCabinetIndex(),
				//	nodeCabinet.getModel());
			cabinets.put(cabinetIds, nodeCabinet);
		  
		return nodeCabinet;

	}

	 
	private NodeInterface createNodeInterface(String interfaceId, Integer portIndex, String typeOfUnit, String serial,
			String partNumber, String description,String nodeBoardId,String totalLinkCapacity,String ethernetCapacity,String portSpeed,String portStatus,String interfaceName) {

		NodeInterface nodeInterface = new NodeInterface();
		nodeInterface.setId(interfaceId);
		nodeInterface.setInterfaceIndex(portIndex);
		nodeInterface.setSfpdescription(typeOfUnit);
		nodeInterface.setDescription(description);
		nodeInterface.setPartnumber(partNumber);
		nodeInterface.setSerial(serial);
		nodeInterface.setTotalLinkCapacity(totalLinkCapacity);
		nodeInterface.setEthernetCapacity(ethernetCapacity);
		nodeInterface.setNodeBoardId(nodeBoardId);	
		nodeInterface.setPortSpeed(portSpeed);
		nodeInterface.setPortStatus(portStatus);
		nodeInterface.setInterfaceName(interfaceName);
		// nodeInterface.setSfpdescription(description);
		nodeInterfaceList.add(interfaceId);
		return nodeInterface;
	}
	
	/**
	 * 
	 */
	 

	/**
	 * @return
	 * @throws IOException
	 */
	/*public List<NodeSlot> getNodeSlotsElements() throws IOException {
		log.debug("Get All Node Slots Elements");

		if (slotsMap.isEmpty() ||  slotsMap == null) {
			 parseNodeSlotsFromAviatInventoryFile();
			 
		 }

 

		return  new ArrayList<NodeSlot>(slotsMap.values());
	}*/
	
	public Map<String, NodeSlot> getSlotsMaps() {
		if (!isParsed  || slotsMap.isEmpty())
			parseNodeSlotsFromAviatInventoryFile();
		
		
		//removeNodeInterfaceDuplication();
		return slotsMap;
	}

	/**
	 * @return
	 */
 
	public void clrearElements(){
		
		nodeSlots = null;
		duplicateBoards = null;
		networkEleMap = null;
	 
	}
	
	private void removeNodeInterfaceDuplication() {
		for(NodeSlot nodeSlot : slotsMap.values()) {
			
			try{
				Map<String,NodeBoard> map = new HashMap<>();
				
				for(NodeBoard board : nodeSlot.getNodeBoards()) {
					map.put(board.getId(), board);
				}
				
				nodeSlot.getNodeBoards().clear();
				nodeSlot.setNodeBoards(map.values());
			}
			catch(Exception e){
				
			}
			
			for(NodeBoard board : nodeSlot.getNodeBoards()) {
							
				try{
				Map<String,NodeInterface> map = new HashMap<>();
				for(NodeInterface nodeInterface : board.getNodeInterfaces()) {
					map.put(nodeInterface.getId(), nodeInterface);
				}
				board.getNodeInterfaces().clear();
				board.setNodeInterfaces(map.values());
				}
				catch (Exception e) {
					// TODO: handle exception
				}
			}
		}
		
	}
	
}