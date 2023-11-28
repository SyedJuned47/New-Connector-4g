package com.mobinets.nps.customer.transmission.manufacture.aviat.node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.supercsv.io.CsvListReader;
import org.supercsv.prefs.CsvPreference;

import com.mobinets.nep.model.autoimporter.ErrorAlarm;
import com.mobinets.nep.npt.NptConstants;
import com.mobinets.nps.customer.transmission.common.CommonConfig;
import com.mobinets.nps.customer.transmission.common.FilesFilterHandler;
import com.mobinets.nps.customer.transmission.common.IpMatcher;
import com.mobinets.nps.customer.transmission.common.ManufactureFixer;
import com.mobinets.nps.customer.transmission.common.NodesMatchingParser;
import com.mobinets.nps.customer.transmission.common.SiteMatchingFounder;
import com.mobinets.nps.customer.transmission.common.TransmissionCommon;
import com.mobinets.nps.customer.transmission.externalC.airtel.common.MW_EmsNameIP_Matcher;
import com.mobinets.nps.customer.transmission.manufacture.common.ConnectorUtility;
import com.mobinets.nps.customer.transmission.manufacture.ericsson.r3.common.R3NEIDFounder;
import com.mobinets.nps.daemon.common.NodeContainer;
import com.mobinets.nps.daemon.csv.AbstractFileCsvParser;
import com.mobinets.nps.model.ipaddress.IPDataV6;
import com.mobinets.nps.model.nodeinterfaces.NetworkElement;
import com.mobinets.nps.model.nodeinterfaces.VirtualInterface;
import com.mobinets.nps.model.nodeinterfaces.VirtualInterface.VirtualInterfaceName;

public class AviatNetworkElementsParser extends AbstractFileCsvParser<NetworkElement> {

	private static final Log log = LogFactory.getLog(AviatNetworkElementsParser.class);
	private static final Log logErr = LogFactory.getLog("NETWORK_ELEMENT_ERROR_LOGGER");

	private Map<String, NetworkElement> mapRes;
	private Map<String, NetworkElement> elementsByName;
	private Map<String, String> ipByElementId;
	List<String> easthubList = new ArrayList<>();
	List<String> northhubList = new ArrayList<>();
	List<String> southhubList = new ArrayList<>();
	List<String> virtualIntefacesIdList = new ArrayList<>();
	List<String> westhubList = new ArrayList<>();
	List<String> neIdList = new ArrayList<>();
	private Map<String, String> circleIdMap = new HashMap<String,String>();
	 
	public Map<String, String> getCircleIdMap() {
		return circleIdMap;
	}

	public List<String> getNeIdList() {
		return neIdList;
	}


	private Map<String, NetworkElement> elementsByObjectId;
	private List<VirtualInterface> virtualInterfaces;
	// parsers output from CDL files
	private Set<String> cdlMapRes;
	// parsers output from SOEM file
	private Set<String> soemMapRes;
	// parsersoutput from MINILINK file
	private Map<String, NetworkElement> miniLinkMapRes;
	 
	private CommonConfig r3Config;
	private R3NEIDFounder r3NeIdFounder;
	private NodesMatchingParser nodesMatcher;
	private SiteMatchingFounder siteMatchFounder;
	private ManufactureFixer siteNameFixer;
	private MW_EmsNameIP_Matcher mW_EmsNameIP_Matcher;
	private IpMatcher ipMatcher;
	
	
	public void setSiteMatchFounder(SiteMatchingFounder siteMatchFounder) {
		this.siteMatchFounder = siteMatchFounder;
	}
	
	public ManufactureFixer getSiteNameFixer() {
		return siteNameFixer;
	}

	public void setNodesMatcher(NodesMatchingParser nodesMatcher) {
		this.nodesMatcher = nodesMatcher;
	}

	public void setR3Config(CommonConfig r3Config) {
		this.r3Config = r3Config;
	}

	public void setR3NeIdFounder(R3NEIDFounder r3NeIdFounder) {
		this.r3NeIdFounder = r3NeIdFounder;
	}

	public void setSiteNameFixer(ManufactureFixer siteNameFixer) {
		this.siteNameFixer = siteNameFixer;
	}

	
	public void setmW_EmsNameIP_Matcher(MW_EmsNameIP_Matcher mW_EmsNameIP_Matcher) {
		this.mW_EmsNameIP_Matcher = mW_EmsNameIP_Matcher;
	}
	
	public void setIpMatcher(IpMatcher ipMatcher) {
		this.ipMatcher = ipMatcher;
	}

	/**
	 * @return
	 */
	private void parseAviatMwDumps() {
		log.debug("Begin of Aviat Network Elements parsing from MW Files");
		try {

			if (null == mapRes)
				mapRes = new HashMap<String, NetworkElement>();
			if (null == elementsByName)
				elementsByName = new HashMap<String, NetworkElement>();
			if (null == elementsByObjectId)
				elementsByObjectId = new HashMap<String, NetworkElement>();

			if (null == virtualInterfaces)
				virtualInterfaces = new ArrayList<VirtualInterface>();

			if (soemMapRes == null)
				soemMapRes = new HashSet<String>();
	
			if(ipByElementId==null)
				ipByElementId = new HashMap<String, String>();
			
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
			
			
			addHeaderToParse("Name");
			addHeaderToParse("IP Address");
			addHeaderToParse("Device Type");
			
			for (int i =0;i<aviatFiles.size();i++) {
				File file = aviatFiles.get(i);
			
				if (!file.getName().contains("INVENTORY_REPORT"))
					continue;
				
				String hub = StringUtils.substringAfterLast(file.toString(), "MW");
				hub = StringUtils.substringBefore(hub, "_");
				hub = hub.replaceAll("AVIATNMS", "").trim();
				hub = StringUtils.substring(hub, 1);
				if(hub.equals("EAST")){
					
					 
					easthubList.add("AS");
					easthubList.add("NE");
					easthubList.add("UE");
					easthubList.add("BR");
					easthubList.add("OR");
					easthubList.add("WB");
				}
                if(hub.equals("NORTH")){
					
					 
					northhubList.add("DL");
					northhubList.add("HP");
					northhubList.add("JK");
					northhubList.add("PB");
					northhubList.add("UW");
					northhubList.add("HR");
				}
                if(hub.equals("SOUTH")){
	
	                
	               southhubList.add("AP");
	               southhubList.add("CN");
	               southhubList.add("KK");
	               southhubList.add("TN");
	               southhubList.add("KL");
                  }
                if(hub.equals("WEST")){
	
	              westhubList.add("RJ");
	              westhubList.add("GJ");
	              westhubList.add("MP");
	              westhubList.add("KO");
	              westhubList.add("MH");
	              westhubList.add("MU");
                }
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
						String address = row.get(headerIndexOf("IP Address")).trim();
						String ip = address;
						
						if(ip.contains("2401:4900:0:8016:0:800:0:8a3"))
						System.out.println();
						String circle1 = StringUtils.substring(neId, 0, 2);
						 if(circle1.contains("DL")){
							createNode(row, model, circle1, neName, neId, ip, file);}
						
						/*if(hub.equals("SOUTH") && southhubList.contains(circle1)){
							createNode(row, model, circle1, neName, neId, ip, file);
						}
						if(hub.equals("WEST") && westhubList.contains(circle1)){
							createNode(row, model, circle1, neName, neId, ip, file);
						}
						if(hub.equals("NORTH") && northhubList.contains(circle1)){
							createNode(row, model, circle1, neName, neId, ip, file);
						}*/}
						 catch (Exception e) {
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
			}catch (Exception e) {
			log.error("Error : ", e);
		}
	}			

	private void createNode(List<String> row, String model, String circle1, String neName, String neId, String ip, File file) {
		
		if(neName.contains("CQS01.2"))
		{
			neName=TransmissionCommon.getStringByGroup(neName,"(?ims)^(\\w+\\d+)(\\.)\\d+",1);
		}
		String siteId = StringUtils.substringBefore(neName, "-");
		siteId = StringUtils.substring(siteId,4).trim();
		siteId = siteId+"_"+circle1;
	 
		if (null == neId || "".equals(neId))
			return;
		
		neName = StringUtils.substringBefore(neName, "-");
		neName = neName+"_"+ip;
		String temporaryIp = ip;
		ip = ip+"_"+circle1;
		
		if(ip.contains("2401:4900:0:8016:0:800:0:8a3_RJ"))
			System.out.println();
		 
		
		NetworkElement ele = new NetworkElement();
		ele.setId(ip);
		ele.setNeId(ip);
		ele.setName(neName);
		ele.setExternalCode(ip);
		ele.setObjectId(ip);
		ele.setNeModel(model);
		ele.setManufacturerName("Aviat");
		ele.setXpicAbility(true);
		ele.setNeType(TransmissionCommon.IDU);
		ele.setSite(siteId);
		
		String subnetMask =  "128";

		if (siteNameFixer.isAlfa3xImport())
			soemMapRes.add(ele.getNeId());

		if (!mapRes.containsKey(neId)) {
			if (elementsByName.get(ele.getName().toLowerCase()) != null && ip.equals(ipByElementId.get(elementsByName.get(ele.getName().toLowerCase()).getId()))) {
				ErrorAlarm error = new ErrorAlarm();
				error.setMessage("Duplicated Name: " + ele.getName() + " with different external code: " + ele.getNeId() + " and " + elementsByName.get(ele.getName().toLowerCase()).getNeId());
				error.setAdditionalInfo("File Path : " + file.getPath());
				error.setNeType(NptConstants.IDU);
				error.setType(ErrorAlarm.NETWORK_ERROR);
				NodeContainer.addErrorToList(error);
				ele.setName(ele.getName() + "_" + ele.getNeId());
				ele.setObjectId(ele.getNeId());
			}
			
			mapRes.put(ele.getNeId(), ele);
			neIdList.add(temporaryIp);
			circleIdMap.put(temporaryIp,circle1);
			ipByElementId.put(neId, ip);
			if (ip != null && !ip.isEmpty()) {
				String virtualInterfaceId ="";
				if(ip.contains(".")){
					subnetMask = "255.255.255.255";
				 virtualInterfaceId = ip+"-"+temporaryIp+"-"+"255.255.255.255";
				}
				else{
				 virtualInterfaceId = ip+"-"+temporaryIp+"_"+"128";
				}
				if(!virtualIntefacesIdList.contains(virtualInterfaceId)){
				 
				
				VirtualInterface vi = new VirtualInterface();
				vi.setInterfaceName(VirtualInterfaceName.MANAGEMENT1.toString());
				vi.setId(virtualInterfaceId);
				vi.setNodeId(ip);
				if(!ip.contains(":")){
				vi.setIpData(NodeContainer.getIpDataForName(ip + "_" + temporaryIp, temporaryIp, subnetMask));
				}
				if(!ip.contains(".")){
				IPDataV6 ipDatav6 = NodeContainer.getIpDataV6ForNameNokia(ip, virtualInterfaceId,
					"128", VirtualInterfaceName.MANAGEMENT1.toString());
				vi.setIpDataV6(ipDatav6);
				}
				
			//	vi.setIpDataV6(ipDatav6);
				virtualInterfaces.add(vi);
				virtualIntefacesIdList.add(ip+"-"+temporaryIp+"_"+"128");
				}
				
			}
			
			elementsByName.put(ele.getName().toLowerCase(), ele);
			elementsByObjectId.put(ele.getObjectId(), ele);
		}
	}
 
	/**
	 * @return
	 */

	public Map<String, NetworkElement> getMapOfNetworkElement() {
		if(mapRes==null)
			this.parseAviatMwDumps();
		return mapRes;
	}
	
 

	/**
	 * @return
	 */
	public List<VirtualInterface> getVirtualInterfaces() {
		if (virtualInterfaces == null || virtualInterfaces.size() == 0)
			this.getMapOfNetworkElement();
		return virtualInterfaces;
	}
	
	public void clearVirtualInterfaces(){
		virtualInterfaces = null;
	}
	
	public void clearElements(){	
		mapRes = null;
		elementsByName = null;
		elementsByObjectId = null;
		virtualInterfaces = null;
		cdlMapRes = null;
		soemMapRes = null;
		miniLinkMapRes = null;
       
	}


}
