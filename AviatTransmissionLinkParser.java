package com.mobinets.nps.customer.transmission.manufacture.aviat.trlinks;

import java.io.BufferedReader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.mobinets.nep.model.autoimporter.MWConfiguration.AtpcModeEnum;
import com.mobinets.nps.customer.transmission.common.CommonConfig;
import com.mobinets.nps.customer.transmission.common.FilesFilterHandler;
import com.mobinets.nps.customer.transmission.common.IpMatcher;
import com.mobinets.nps.customer.transmission.common.TransmissionCommon;
import com.mobinets.nps.customer.transmission.common.TypeMatchingParser;
import com.mobinets.nps.customer.transmission.manufacture.aviat.node.AviatNetworkElementsParser;
import com.mobinets.nps.customer.transmission.manufacture.common.AdditionalInfoUtilities;
import com.mobinets.nps.customer.transmission.manufacture.common.ConnectorUtility;
import com.mobinets.nps.customer.transmission.manufacture.ericsson.r3.node.R3NetworkElementsParser;
import com.mobinets.nps.customer.transmission.manufacture.ericsson.r3.nodeSlot.R3NodeSlotsParser;
import com.mobinets.nps.daemon.csv.AbstractFileCsvParser;
import com.mobinets.nps.model.customer.data.element.ElementMwConfiguration;
import com.mobinets.nps.model.customer.data.element.ElementTransmissionLink;
import com.mobinets.nps.model.network.ElementAdditionalInfo;
import com.mobinets.nps.model.network.ElementAdditionalInfo.ElementType;
import com.mobinets.nps.model.nodeinterfaces.NetworkElement;
import com.mobinets.nps.model.nodeinterfaces.NodeInterface;
import com.mobinets.nps.model.nodeinterfaces.NodeSlot;

public class AviatTransmissionLinkParser extends AbstractFileCsvParser<ElementTransmissionLink> {

	private static final Log log = LogFactory.getLog(AviatTransmissionLinkParser.class);
	private static final Log logErr = LogFactory.getLog("TRLINK_ERROR_LOGGER");

	private CommonConfig r3Config;
	private TypeMatchingParser typeMatching;
	private R3NetworkElementsParser r3NetworkElementsParser;
	private R3NodeSlotsParser r3NodeSlotsParser;
	List<LinkPart> linkParts = new ArrayList<>();
	private Map<String, NodeSlot> nodeSlotMap;
	private Set<String> exterCodeSet;
	private Map<String, Integer> sameIdMap;
	private Map<String, String> farEndTerminalMapError;
	private Map<String, NetworkElement> networkEleMap;
	private Map<String, String> circleIdmap;
	private Map<String, NetworkElement> elementByName;
	private Map<String, NodeInterface> nodeInterfacesMap;
	private Map<String,List<String>>txFrquencyListMap;
	private List<String>tempList;
	private List<String> nodeIdList;
	private Map<String, ElementTransmissionLink> linksMap; 
	private Map<String, String> tcapacitymap = new HashMap<>();
	 
	
	
	public List<ElementMwConfiguration> getLinkDataforMwConfig() throws IOException {
		List<ElementMwConfiguration> mwConfig = new ArrayList<>();
		if (elementMwConfigurationMap == null)
			parserLinks();
		mwConfig.addAll(elementMwConfigurationMap.values());
		return mwConfig;
	}
	

	private Map<String, ElementMwConfiguration> elementMwConfigurationMap;
	private List<ElementAdditionalInfo> additionalInfos =  new ArrayList<>();
	private List<String> AggregationList;
	private Map<String, NetworkElement>linkconfigurationMap;
	private Map<String, List<LinkPart>> linkPartByCircleMap = new ConcurrentHashMap<String, List<LinkPart>>();
	Map<String, Node> nodeByCirleMap = new HashMap<String, Node>();
	Map<String, List<String>> node2AdditionalInfoByInstanceRF1ByCircleMap = new HashMap<>();
	private Matcher numMatcher;
	private AviatNetworkElementsParser aviatNetworkElementsParser;

	 
	public void setAviatNetworkElementsParser(AviatNetworkElementsParser aviatNetworkElementsParser) {
		this.aviatNetworkElementsParser = aviatNetworkElementsParser;
	}

	private Map<String, String> radioLinkMap = new HashMap<>();
	private Map<String, String> radioLinkMaxRxMap = new HashMap<>();
	private Map<String, String> radioLink2Map = new HashMap<>();
	private Map<String, String> radioLinkMaxRx2Map = new HashMap<>();
	private Map<String, String> radioLinkTXMap = new HashMap<>();
	private Map<String, String> channelSpacingmap = new HashMap<>(); 
	private Map<String, String> radioLinkTXpart2Map = new HashMap<>();
	private Map<String, String> nameTomoduleName = new HashMap<>(); 
	private Map<String, String> bandMap = new HashMap<>();
	Map<String, Node> nodeMap = new HashMap<String, Node>();
	private IpMatcher ipMatcher;
	Set<String> usedInterfaces = new HashSet<>();


	public void setIpMatcher(IpMatcher ipMatcher) {
		this.ipMatcher = ipMatcher;
	}


	public void setTypeMatching(TypeMatchingParser typeMatching) {
		this.typeMatching = typeMatching;
	}

	public void setR3Config(CommonConfig r3Config) {
		this.r3Config = r3Config;
	}

	public void setR3NetworkElementsParser(R3NetworkElementsParser r3NetworkElementsParser) {
		this.r3NetworkElementsParser = r3NetworkElementsParser;
	}

	public void setR3NodeSlotsParser(R3NodeSlotsParser r3NodeSlotsParser) {
		this.r3NodeSlotsParser = r3NodeSlotsParser;
	}

	private Pattern numPattern = Pattern.compile("\\d*");

	private void init() {
		linksMap = new HashMap<>();
		elementMwConfigurationMap = new HashMap<>();
		networkEleMap = aviatNetworkElementsParser.getMapOfNetworkElement();
		circleIdmap = aviatNetworkElementsParser.getCircleIdMap();
		nodeIdList = aviatNetworkElementsParser.getNeIdList();
		nodeInterfacesMap = new HashMap<String, NodeInterface>();
		tempList = new ArrayList<>();
		linkconfigurationMap = new HashMap<>();
		AggregationList = new ArrayList<>();
		txFrquencyListMap = new HashMap<>();
		 
		

		nodeSlotMap = new HashMap<String, NodeSlot>();
		try {
			for (NodeSlot slot : r3NodeSlotsParser.getNodeSlotsElements(networkEleMap)) {
				nodeSlotMap.put(slot.getId(), slot);
			}
		} catch (Exception e) {
		}

	}

 	/*public void parserLinks() {
		log.debug("Start creating Aviat MW Transmission links");
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
        
		
		fillRadioLinktMap(path);
		aggregation(path);

		
		List<File> soemFiles = new ArrayList<>();
		ConnectorUtility.listofFiles(path, soemFiles, new FilesFilterHandler.SoemConfigMLTNFiles());
		Utilities.sortFilesByDate(soemFiles, FilesFilterHandler.mmu2b_c_, R3CustomerManager.flexibleDateParser);
		String txFreq = "";
		String rxFreq = "";

		Set<String> folderSet = new HashSet<String>();
		for (int i = 0; i < soemFiles.size(); i++) {

			List<LinkPart> linkParts = new ArrayList<>();
			Map<String, List<String>> node2AdditionalInfoByInstanceRF1Map = new HashMap<>();
			clearHeaders();
			addHeaderToParse("NEID");
			addHeaderToParse("Terminal_ID");
			addHeaderToParse("Far_End_ID");
			addHeaderToParse("Instance_RF1");
			addHeaderToParse("Instance_RF2");
			addHeaderToParse("Protection_Mode_Admin_Status");
			addHeaderToParse("Max_Capacity");
			addHeaderToParse("Capacity");
			addHeaderToParse("FarEndNEIP");
			addHeaderToParse("FarEndNESlot");
			addHeaderToParse("tx_freq");
			addHeaderToParse("rx_freq");
			addHeaderToParse("E1_Number");

			addHeaderToParse("ATPC_Capability_Ra1");
			addHeaderToParse("ATPC_Capability_Ra2");
			addHeaderToParse("Modulation_Capability_Ra1");
			addHeaderToParse("Modulation_Capability_Ra2");
			addHeaderToParse("Packet_Link_Capacity");
			addHeaderToParse("Channel_Spacing");
			addHeaderToParse("Max_Modulation");
			addHeaderToParse("Modulation");
			addHeaderToParse("Traffic_Type");// link type
			addHeaderToParse("Freq_Band_Ra1");
			addHeaderToParse("Selected_Output_Power_RF1");
			boolean isSecondHeader = false;

			File file = soemFiles.get(i);

			if (!file.isFile())
				continue;

			String parent = file.getParentFile().getAbsolutePath();
			String oss = file.getName().split("_")[0];
			oss = oss + parent;
			if (folderSet.contains(oss))
				continue;

			try {
				ICsvListReader inFile = new CsvListReader(new FileReader(file), CsvPreference.EXCEL_PREFERENCE);
				String ossIp = TransmissionCommon.getStringByGroup(file.getAbsolutePath(),
						"(\\d+\\.\\d+\\.\\d+\\.\\d+)", 1);
				String circle = ipMatcher.getName(ossIp);
				final String[] header = inFile.getCSVHeader(true);
				boolean isOK = fillHeaderIndex(header);
				if (!isOK) {

					clearHeaders();
					addHeaderToParse("NEID");
					addHeaderToParse("Terminal_ID");
					addHeaderToParse("Far_End_ID");
					addHeaderToParse("Instance_RF1");
					addHeaderToParse("Instance_RF2");
					addHeaderToParse("Protection_Mode_Admin_Status");
					addHeaderToParse("Max_Capacity");
					addHeaderToParse("Capacity");
					addHeaderToParse("FarEndNEIP");
					addHeaderToParse("FarEndNESlot");
					addHeaderToParse("Base_TX_Frequency_RF1");
					addHeaderToParse("Base_RX_Frequency_RF1");
					addHeaderToParse("E1_Number");

					addHeaderToParse("ATPC_Capability_Ra1");
					addHeaderToParse("ATPC_Capability_Ra2");
					addHeaderToParse("Modulation_Capability_Ra1");
					addHeaderToParse("Modulation_Capability_Ra2");
					addHeaderToParse("Packet_Link_Capacity");
					addHeaderToParse("Channel_Spacing");
					addHeaderToParse("Max_Modulation");
					addHeaderToParse("Modulation");
					addHeaderToParse("Traffic_Type");
					addHeaderToParse("Freq_Band_Ra1");
					addHeaderToParse("Selected_Output_Power_RF1");

					isOK = fillHeaderIndex(header);
					isSecondHeader = true;

					if (!isOK) {
						log.error("Error with parsing header of file " + file.getPath());
						continue;
					}
				}

				log.debug("Start parsing mmu2b file " + file.getAbsolutePath());

				List<String> row = new ArrayList<String>();
				String tx_freq = "";
				String rx_freq = "";
								
				while ((row = inFile.read()) != null) {

					try {
						folderSet.add(oss);
						String neId = row.get(headerIndexOf("NEID")).trim();
						String terminalId = row.get(headerIndexOf("Terminal_ID")).trim();
						String farEndId = row.get(headerIndexOf("Far_End_ID")).trim();
						String instanceRf1 = row.get(headerIndexOf("Instance_RF1")).trim();
						String instanceRf2 = row.get(headerIndexOf("Instance_RF2")).trim();

						String protectionModeAdminStatus = row.get(headerIndexOf("Protection_Mode_Admin_Status"))
								.trim();
						String type = headerIndexOf("Max_Capacity") == -1 ? ""
								: row.get(headerIndexOf("Max_Capacity")).trim();
						String farEndNESlot = row.get(headerIndexOf("FarEndNESlot")).trim();
						String farEndNEIP = row.get(headerIndexOf("FarEndNEIP")).trim();
						String e1Number = row.get(headerIndexOf("E1_Number")).trim();
						String capacity = row.get(headerIndexOf("Capacity")).trim();
						String channel_Spacing = row.get(headerIndexOf("Channel_Spacing")).trim();
						String max_Modulation = row.get(headerIndexOf("Max_Modulation")).trim();
						String freqBandRa = row.get(headerIndexOf("Freq_Band_Ra1")).trim();
						String selectedPower = row.get(headerIndexOf("Selected_Output_Power_RF1")).trim();
						// new rule for modulation

						String modulation = row.get(headerIndexOf("Max_Modulation")).trim();
						if (modulation.equals("Adaptive Modulation not Supported")
								|| modulation.equals("Adaptive Modulation not Enabled")
								|| modulation.equalsIgnoreCase("none")) {

							modulation = row.get(headerIndexOf("Modulation")).trim();
						}
						
						String atpcCapRa1 = row.get(headerIndexOf("ATPC_Capability_Ra1")).trim();
						String atpcCapRa2 = row.get(headerIndexOf("ATPC_Capability_Ra2")).trim();
						String modCapRa1 = row.get(headerIndexOf("Modulation_Capability_Ra1")).trim();
						String modCapRa2 = row.get(headerIndexOf("Modulation_Capability_Ra2")).trim();
						String packetLinkCapacity = row.get(headerIndexOf("Packet_Link_Capacity")).trim();
						String link_type = row.get(headerIndexOf("Traffic_Type"));
						if (isSecondHeader) {
							tx_freq = row.get(headerIndexOf("Base_TX_Frequency_RF1")).trim();
							rx_freq = row.get(headerIndexOf("Base_RX_Frequency_RF1")).trim();
						} else {
							tx_freq = row.get(headerIndexOf("tx_freq")).trim();
							rx_freq = row.get(headerIndexOf("rx_freq")).trim();
						}

						if (neId == null || neId.isEmpty())
							continue;
						
						txFreq = tx_freq;
						rxFreq = rx_freq;

						neId = r3NetworkElementsParser.getSiteNameFixer().fixEricssonMwNodeId(ossIp, neId);

						if (atpcCapRa1 != null && !atpcCapRa1.isEmpty())
							AdditionalInfoUtilities.writeCsv(TransmissionCommon.createAdditionalInformation(neId,
									ElementType.IDU, "ATPC_Capability_Ra1", "ATPC_Capability_Ra1", atpcCapRa1));
						if (atpcCapRa2 != null && !atpcCapRa2.isEmpty())
							AdditionalInfoUtilities.writeCsv(TransmissionCommon.createAdditionalInformation(neId,
									ElementType.IDU, "ATPC_Capability_Ra2", "ATPC_Capability_Ra2", atpcCapRa2));
						if (modCapRa1 != null && !modCapRa1.isEmpty())
							AdditionalInfoUtilities
									.writeCsv(TransmissionCommon.createAdditionalInformation(neId, ElementType.IDU,
											"Modulation_Capability_Ra1", "Modulation_Capability_Ra1", modCapRa1));
						if (modCapRa2 != null && !modCapRa2.isEmpty())
							AdditionalInfoUtilities
									.writeCsv(TransmissionCommon.createAdditionalInformation(neId, ElementType.IDU,
											"Modulation_Capability_Ra2", "Modulation_Capability_Ra2", modCapRa2));

						numMatcher = numPattern.matcher(type);
						if (numMatcher.find() && !numMatcher.group().isEmpty()) {
							type = numMatcher.group() + " Mbps";
						} else {
							type = row.get(headerIndexOf("Capacity")).trim();
						}

						String farEndNESlot2 = null;

						if (farEndNESlot.contains("+")) {
							String[] values = farEndNESlot.split("\\+");
							farEndNESlot = values[0];
							farEndNESlot2 = values[1];
						}

						LinkPart linkPart = new LinkPart();
						linkPart.neId = neId;
						linkPart.terminaId = terminalId;
						linkPart.farEndId = farEndId;
						linkPart.instanceRf1 = instanceRf1;
						linkPart.instanceRf2 = instanceRf2;
						linkPart.protectionModeAdminStatus = protectionModeAdminStatus;
						linkPart.type = type;
						linkPart.farEndNESlot = farEndNESlot;
						linkPart.farEndNEIP = farEndNEIP;
						linkPart.txFreq = tx_freq;
						linkPart.rxFreq = rx_freq;
						linkPart.capacity = capacity;
						linkPart.e1Number = e1Number;
						linkPart.packetLinkCapacity = packetLinkCapacity;
						linkPart.channel_Spacing = channel_Spacing;
						linkPart.max_Modulation = max_Modulation;
						linkPart.modulation = modulation;
						linkPart.link_type = link_type;
						linkPart.circle = circle;
						linkPart.ossIp = ossIp;
						linkPart.farEndNESlot2 = farEndNESlot2;
						linkPart.farEndNESlot2 = farEndNESlot2;
						linkPart.freqBandRa = freqBandRa;
						linkPart.selectedPower = selectedPower;
						linkParts.add(linkPart);

						List<String> values = new ArrayList<>();
						values.add(max_Modulation);
						values.add(modulation);
						node2AdditionalInfoByInstanceRF1Map.put(neId + instanceRf1, values);
						node2AdditionalInfoByInstanceRF1ByCircleMap.put(neId + instanceRf1 + "_" + circle, values);

						if (circle != null && linkPartByCircleMap.containsKey(circle)) {
							linkPartByCircleMap.get(circle).add(linkPart);
						} else if (circle != null) {
							List<LinkPart> parts = new ArrayList<LinkPart>();
							parts.add(linkPart);
							linkPartByCircleMap.put(circle, parts);
						}

					} catch (Exception e) {
						log.error("Error: ", e);
					}

				}
				inFile.close();
				createOssLinks(parent, ossIp, linkParts, node2AdditionalInfoByInstanceRF1Map, circle,tx_freq,rx_freq);
				log.debug("End parsing mmu2b file " + file.getAbsolutePath());
			} catch (Exception e) {
				log.error("Error while parsing the file : " + file.getAbsolutePath(), e);
			}
		}
		createLinksInterOss(txFreq,rxFreq);
		log.debug("End creating mmu2b links");
	}*/
 	

	public List<ElementTransmissionLink> getElementTrsLinks() throws IOException {
		List<ElementTransmissionLink> trLinkRes = new ArrayList<>();
		if (linksMap == null)
			parserLinks();
		trLinkRes.addAll(linksMap.values());

		// trLinkRes.addAll(parseTrLinksFromCdl());
		// trLinkRes.addAll(parseTransmissionLinks());
		//trLinkRes.addAll(getTrLinksFromMiniLinkEFiles().values());

		return trLinkRes;
	}

	/*public List<ElementMwConfiguration> getMwConfig() {
		List<ElementMwConfiguration> mwConfig = new ArrayList<>();
		if (elementMwConfigurationMap == null)
			moduleFromConfiguration();

		mwConfig.addAll(elementMwConfigurationMap.values());

		return mwConfig;
	}*/
	
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
		
		String cabinetIndex = "1",shelfIndex = "1" , indexOnSlot ="0", slotIndex = "0", portIndex ="1";
		String interfaceId1="", interfaceId2 ="";
		String site1 ="", site2="";
		String externalCode ="",capacity = "",linkType ="",linkModulation="";
		
		 
		if (!folder.exists()) {
			logErr.error("Folder (" + path + ") not found");
			log.error("Folder (" + path + ") not found");
			return;
		}
		
		List<File> aviatFiles = new ArrayList<>();
		ConnectorUtility.listofFiles(path, aviatFiles, new FilesFilterHandler.CsvFiles());
		
		
		clearHeaders();
		addHeaderToParse("Module Name");
		addHeaderToParse("Management IP Address");
		addHeaderToParse("Name"); 
		addHeaderToParse("Channel Separation (kHz)");

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
					String moduleName = row.get(headerIndexOf("Module Name"));
					String channelSpacing = row.get(headerIndexOf("Channel Separation (kHz)"));
					
					if(moduleName.contains("mmwCarrier1/1")){
						String ip_spacing = ip+"_"+channelSpacing;
						channelSpacingmap.put(name,ip_spacing);
					}
				 
			        if(!moduleName.contains("L1LA1"))
			        	continue;
					bandMap.put(ip, moduleName);
									
							 		
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

 

	public List<ElementAdditionalInfo> getAdditionalInfos() {
		if (additionalInfos.isEmpty())
			parserLinks();
		return additionalInfos;
	}
 

	public void clearElements() {
		nodeSlotMap = null;
		exterCodeSet = null;
		sameIdMap = null;
		farEndTerminalMapError = null;
		networkEleMap = null;
		elementByName = null;
		nodeInterfacesMap = null;
		linksMap = null;
	}

	public void clearAdditionalInfos() {
		additionalInfos = null;
	}

 

 	
 	private void parserLinks() {
 		parsingOlnymoduleFromConfiguration();
		log.debug("Start creating Aviat MW Transmission links");
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
					/*String siteA_Max_ConfigCap = row.get(headerIndexOf("Site A Maximum Configured Capacity"));   
					String siteB_Max_ConfigCap = row.get(headerIndexOf("Site A Maximum Configured Capacity"));*/
					String siteA_CurrentModulation = row.get(headerIndexOf("Site A Current Modulation")); 
					String siteZ_CurrentModulation = row.get(headerIndexOf("Site Z Current Modulation")); 
					String atpc_mode = row.get(headerIndexOf("ATPC Status"));  
					String txfreq = row.get(headerIndexOf("Site A Tx Freq (MHz)"));  
					String rxfreq = row.get(headerIndexOf("Site Z Tx Freq (MHz)"));  
					String highest_orderschemeNode1 = row.get(headerIndexOf("Site A Max Configured Modulation"));
					String highest_orderschemeNode2 = row.get(headerIndexOf("Site Z Max Configured Modulation"));
					String lowest_orderschemeNode1 = row.get(headerIndexOf("Site A Min Configured Modulation"));
					String lowest_orderschemeNode2 = row.get(headerIndexOf("Site Z Min Configured Modulation"));
					String node1_Max_RxLevel = row.get(headerIndexOf("Site A Max RSL Last 24h"));
					String node2_Max_RxLevel = row.get(headerIndexOf("Site Z Max RSL Last 24h"));
					String node1_Min_RxLevel = row.get(headerIndexOf("Site A Min RSL Last 24h"));
					String node2_Min_RxLevel = row.get(headerIndexOf("Site Z Min RSL Last 24h"));
					String node1_Max_TxPower = row.get(headerIndexOf("Site A Max Tx Power Last 24h"));
					String node2_Max_TxPower = row.get(headerIndexOf("Site Z Max Tx Power Last 24h"));
					String circl1  = name1.substring(0, Math.min(name1.length(), 2));
					String circl2  = name2.substring(0, Math.min(name2.length(), 2));
					
					//nodeId_ip+"_"+cabinetIndex+"_"+shelfIndex+"_"+slotIndexforInterface+"_"+indexOnSlot+"_"+portIdx;
					 
					
					if(siteA_CurrentModulation.equals("qam-")  || !siteA_CurrentModulation.isEmpty() ){
						linkModulation = siteA_CurrentModulation;
					}
					else if(siteZ_CurrentModulation.equals("qam-")  || !siteZ_CurrentModulation.isEmpty()){
						linkModulation = siteZ_CurrentModulation;
					}
					else{
						linkModulation ="";
					}
					
					
					double maxCapacity1 = Double.parseDouble(siteA_MaxCapacity);
					double maxCapacity2 = Double.parseDouble(siteB_MaxCapacity);

					double maxCapacity = Math.max(maxCapacity1, maxCapacity2);

					String trLinkCapacity = String.valueOf(maxCapacity);
					String totalLinkCapacity =  trLinkCapacity;
			 
					 
					
					if(nodeIdList.contains(node1))
					site1 = row.get(headerIndexOf("Site A Name"));
					
					if(nodeIdList.contains(node1))
				    site2 = row.get(headerIndexOf("Site Z Name"));
					
					if(node1.contains("2401:4900:0:8016:0:1800:1:c626") || node2.contains("2401:4900:0:8016:0:1800:1:c626"))
						System.out.println();
					 
					 String circle1 = circleIdmap.get(node1);
					 String circle2 = circleIdmap.get(node2);
			         
					 if(!circle1.contains("DL") && !circle2.contains("DL"))
						 continue;
					 String s1 = StringUtils.substring(site1, 4) + "_" + StringUtils.substring(site1, 0, 2);
					 String s2 = StringUtils.substring(site2, 4) + "_" + StringUtils.substring(site2, 0, 2);
					 
						String external1 = node1 + "_"+circle1+"-"+cabinetIndex+"-"+shelfIndex+"-"+slotIndex + "-" + indexOnSlot + "-"
								+ portIndex;
						String external2 = node2 + "_"+circle2+"-"+cabinetIndex+"-"+shelfIndex+"-"+slotIndex +"-" + indexOnSlot + "-"
								+ portIndex;
						
				
						 externalCode = TransmissionCommon.getKey(external1, external2, "_");
						
					 if (!externalCode.contains("null")) {
							if (!usedInterfaces.contains(external1) && !usedInterfaces.contains(external2)) {
								ElementTransmissionLink trLink = new ElementTransmissionLink();
								trLink.setManufacturer(TransmissionCommon.ERICSSON);
								trLink.setId(externalCode);
								trLink.setName(externalCode);
								trLink.setTrLinkCapacity(trLinkCapacity);
								trLink.setSlotIndex1(slotIndex);
								trLink.setSlotIndex2(slotIndex);
								trLink.setInterfaceIndex1(portIndex);
								trLink.setInterfaceIndex2(slotIndex);
								trLink.setBoardIndex1(indexOnSlot);
								trLink.setBoardIndex2(indexOnSlot);
								trLink.setSite1(s1);
								trLink.setSite2(s2);
								trLink.setNode1(node1+"_"+circle1);
								trLink.setNode2(node2+"_"+circle2);
								if(linkType.isEmpty()){
									
								String typeofLink_node1 = bandMap.get(node1);
								String typeofLink_node2 = bandMap.get(node2);
									
									if(bandMap.containsKey(node1) && typeofLink_node1.contains("L1LA1")){
									 
											linkType = "EBAND_SDB";	}
									else if(bandMap.containsKey(node2) && typeofLink_node2.contains("L1LA1")){
										 
										linkType = "EBAND_SDB";	}
										else{
											linkType = "EBAND_SA";
										}
									 
									
										
								}
								
								trLink.setType(linkType);
                                linkType = "";
								trLink.setExternalCode(externalCode);
								linksMap.put(externalCode, trLink);
								
								   if(highest_orderschemeNode1!= null && !highest_orderschemeNode1.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Highest-Order AM Scheme for Node 1", highest_orderschemeNode1);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(highest_orderschemeNode2!= null && !highest_orderschemeNode2.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Highest-Order AM Scheme for Node 2", highest_orderschemeNode2);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(lowest_orderschemeNode1!= null && !lowest_orderschemeNode1.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Lowest-Order AM Scheme for Node 1", lowest_orderschemeNode2);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(lowest_orderschemeNode2!= null && !lowest_orderschemeNode2.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Lowest-Order AM Scheme for Node 2", lowest_orderschemeNode2);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   //Max_MinLevel
								   
								   if(node1_Max_RxLevel!= null && !node1_Max_RxLevel.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node1 Max Rx Level (Last 24 hours) [dBm]", node1_Max_RxLevel);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(node2_Max_RxLevel!= null && !node2_Max_RxLevel.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node 2 Max Rx Level (Last 24 hours) [dBm]", node2_Max_RxLevel);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(node1_Min_RxLevel!= null && !node1_Min_RxLevel.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node 1 Min Rx Level (Last 24 hours) [dBm", node1_Min_RxLevel);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(node2_Min_RxLevel!= null && !node2_Min_RxLevel.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node 2 Min Rx Level (Last 24 hours) [dBm]", node2_Min_RxLevel);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   //PowerTX
								   
								   
								   if(node1_Max_TxPower!= null && !node1_Max_TxPower.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node 1 Max Tx Level (Last 24 hours) [dBm]", node1_Max_TxPower);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   if(node2_Max_TxPower!= null && !node2_Max_TxPower.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
		    	    					externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Node 2 Max Tx Level (Last 24 hours) [dBm]", node2_Max_TxPower);
		    	    					AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    	    					additionalInfos.add(elementAdditionalInfo);
		    						}
								   
								   
								   //totallinkCapacity 
								   
		    		               if(trLinkCapacity!=null && !trLinkCapacity.isEmpty()){
		    							ElementAdditionalInfo elementAdditionalInfo=TransmissionCommon.createAdditionalInformation(
		    									externalCode,ElementType.TrLink,"MW LINK CONFIGURATION","totalLinnk Capacity (Kbps)",trLinkCapacity);
		    						            AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
		    						            additionalInfos.add(elementAdditionalInfo);
		    						}
		    		              
		    
								
						
								createMwConfig(external1,external2,externalCode,node1,node2,site1,site2,trLinkCapacity
										,totalLinkCapacity,linkModulation,atpc_mode,txfreq,rxfreq,s1,s2);
								
							}
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
       log.debug("End of Ericsson Node Slots parsing From Soem Inventory File ...");
			}
      
	} 

	 
	private void createMwConfig(String external1, String external2, String externalCode, String node1, String node2,
		String site1, String site2, String trLinkCapacity,
		String totalLinkCapacity, String linkModulation, String atpc_mode,String txfrequency,String rxfrequency,String s1, String s2) {
		
        String SitA_SiteB = site1+"-"+site2;
//		String circl1  = site1.substring(0, Math.min(site1.length(), 2));
//		String circl2  = site2.substring(0, Math.min(site2.length(), 2));
		
//		String cabinetIndex = "1", shelfIndex= "1", slotIndex1="0",IndexOnSlot ="0", portIndex="1",slotIndex2="1";	
		
//		String id1 = node1+"-"+circl1+"-"+cabinetIndex+"-"+shelfIndex+"-"+slotIndex1+"-"+IndexOnSlot+"-"+portIndex;
//		String id2 = node1+"-"+circl2+"-"+cabinetIndex+"-"+shelfIndex+"-"+slotIndex2+"-"+IndexOnSlot+"-"+portIndex;
//		 
//		String linkId = id1+"_"+id2;
		
		
		double txfreq = Double.valueOf(txfrequency);
		double rxfreq = Double.valueOf(rxfrequency);
		
	   double freqBand = txfreq-rxfreq;
	  
	   String fqBand = String.valueOf(freqBand);
	   
	   if(fqBand.contains("10000")){
		   fqBand="80";
	   }
	   
	   
	   double fband = Double.valueOf(fqBand);
	  
	   String aggregationiId = s1+"-"+s2+"_"+"Aviat"+"_"+fqBand;
	   String masterLinkid = "false";
	   String linkConfiguration ="";
	   
	   ElementMwConfiguration mwConfig = new ElementMwConfiguration();
	   
	   mwConfig.setAggrgationLinkId(aggregationiId);
	   
	   if(atpc_mode.contains("TRUE")){
	   mwConfig.setAtpcMode(AtpcModeEnum.ON);}
	   if(atpc_mode.contains("FALSE")){
	   mwConfig.setAtpcMode(AtpcModeEnum.OFF);}
	   mwConfig.setCapacity(totalLinkCapacity);
	   mwConfig.setMasterLink(masterLinkid);
	   mwConfig.setModulation(linkModulation);
	   mwConfig.setFreqBand(fband);
	   mwConfig.setFreqChannelRx(rxfreq);
	   mwConfig.setFreqChannelTx(txfreq);
	   mwConfig.setTrLinkId(externalCode);
	   if(linkConfiguration.isEmpty()){
		   
		    String typeofLink_node1 = bandMap.get(node1);
			String typeofLink_node2 = bandMap.get(node2);
				
				if(bandMap.containsKey(node1) && typeofLink_node1.contains("L1LA1")){
				 
					linkConfiguration = "1+0 (SDB-Eband)";	}
				else if(bandMap.containsKey(node2) && typeofLink_node2.contains("L1LA1")){
					 
					linkConfiguration = "1+0 (SDB-Eband)";	}
					else{
						linkConfiguration = "1+0 (SA-Eband)";
					}
		   
	   }
	   mwConfig.setLinkConfiguration(linkConfiguration);
	   elementMwConfigurationMap.put(externalCode,mwConfig);
	   
	   if(channelSpacingmap.containsKey(SitA_SiteB)){
		   
		   String address = channelSpacingmap.get(SitA_SiteB);
		   String nodeiptoMatch = StringUtils.substringBefore(address, "_");
		   if(node1.contains(nodeiptoMatch) || node2.contains(nodeiptoMatch)){
			   String channel_Spacing = StringUtils.substringAfter(address, "_");  
			   int chanspac = Integer.valueOf(channel_Spacing);
			   chanspac  = chanspac/1000;
			   String Spacing = String.valueOf(chanspac);
			   
			   ElementAdditionalInfo elementAdditionalInfo = TransmissionCommon.createAdditionalInformation(
						externalCode, ElementType.TrLink, "MW LINK CONFIGURATION", "Channel spacing", Spacing);
						AdditionalInfoUtilities.writeCsv(elementAdditionalInfo);
						additionalInfos.add(elementAdditionalInfo);
						
		   }
		    
	   }
	  	  
}


	/*private void createMwConfig(String external1, String external2, String externalCode, String node1, String node2,
			String site1, String site2, String external12, String external22, String externalCode2,
			String trLinkCapacity, String totalLinkCapacity, String linkModulation, String atpc_mode) {
		
		log.debug("Start creating Aviat MW Transmission links");
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
		
		String cabinetIndex = "1",shelfIndex = "1" , indexOnSlot ="0", slotIndex = "0", portIndex ="1";
		String interfaceId1="", interfaceId2 ="";
		String site1 ="", site2="";
		String externalCode ="",capacity = "",linkType ="",linkModulation="";
		
		 
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
					String siteA_MaxCapacity = row.get(headerIndexOf("Site A Maximum Configured Capacity"));
					String siteB_MaxCapacity = row.get(headerIndexOf("Site Z Maximum Configured Capacity"));
					String siteA_Max_ConfigCap = row.get(headerIndexOf("Site A Maximum Configured Capacity"));   
					String siteB_Max_ConfigCap = row.get(headerIndexOf("Site A Maximum Configured Capacity"));
					String siteA_CurrentModulation = row.get(headerIndexOf("Site A Current Modulation")); 
					String siteZ_CurrentModulation = row.get(headerIndexOf("Site Z Current Modulation")); 
					String atpc_mode = row.get(headerIndexOf("ATPC Status"));  
					}catch (Exception e) {
						e.printStackTrace();
						log.error(e);
				}
				}
		try {

			double freqRx, freqTx, freq, bandWidth;
			Double power;
			boolean isOneOfDataFilled = false;
			try {
				freqRx = Double.valueOf(frequencyChannelRx).doubleValue();
				isOneOfDataFilled = true;
			} catch (Exception e) {
				freqRx = -1;
			}

			try {
				freqTx = Double.valueOf(frequencyChannelTx);
				isOneOfDataFilled = true;
			} catch (Exception e) {
				freqTx = -1;
			}

			try {
				freq = Double.valueOf(freqBandRa).doubleValue() * 1000;
				isOneOfDataFilled = true;
			} catch (Exception e) {
				freq = -1;
			}

			try {
				power = Double.valueOf(selectedPower);
				isOneOfDataFilled = true;
			} catch (Exception e) {
				power = null;
			}

			try {
				bandWidth = Double.valueOf(bandwidthStr).doubleValue();
				isOneOfDataFilled = true;
			} catch (Exception e) {
				bandWidth = -1;
			}

			if (!isOneOfDataFilled && capacity.isEmpty() && atpcMode.isEmpty() && protectionMode.isEmpty())
				return; // ignore it because all data are empty

			protectionMode = protectionMode.replaceAll("(?i)hot", "").trim();

			if (!elementMwConfigurationMap.containsKey(externalCode)) {
				ElementMwConfiguration mwconfig = new ElementMwConfiguration();
				mwconfig.setTrLinkId(externalCode);

				mwconfig.setCapacity(capacity);
				mwconfig.setFreqChannelRx(freqRx);
				mwconfig.setFreqChannelTx(freqTx);
				mwconfig.setFreqBand(freq);
				mwconfig.setPower(power);
				mwconfig.setProtectionMode(PathMWConfiguration.getMWConfiguration(protectionMode));
				mwconfig.setBandwidth(bandWidth);
				mwconfig.setAtpcMode(AtpcModeEnum.valueOfAtpcMode(atpcMode));
				mwconfig.setParity(ParityEnum.NONE);
				mwconfig.setImporterConnector(ImporterConnector.Ericsson_R3);
				mwconfig.setDumpType(DumpType.MLE);
				elementMwConfigurationMap.put(externalCode, mwconfig);
			}

		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
		}
			}catch (Exception e) {
				e.printStackTrace();
				log.error(e);
			}

	}*/
	
		 
	public static class LinkPart {
		public String selectedPower;
		public String freqBandRa;
		public String link_type;
		public String modulation;
		public String max_Modulation;
		public String packetLinkCapacity;
		public String neId;
		public String terminaId;
		public String farEndId;
		public String instanceRf1;
		public String instanceRf2;
		public String protectionModeAdminStatus;
		public String type;
		public String farEndNEIP;
		public String farEndNESlot;
		public String farEndNESlot2;
		public String txFreq;
		public String rxFreq;
		public String capacity;
		public String e1Number;
		public String channel_Spacing;
		public String circle;
		public String ossIp;
	}

	public static class Node {
		public String id;
		public String name;
		public String address;
	}

}
