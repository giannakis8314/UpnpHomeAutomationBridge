package com.cyclingengineer.upnphomeautomationbridge.upnpdevices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fourthline.cling.binding.LocalServiceBindingException;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;

public class HvacZoneThermostatDevice extends UpnpDevice {

	protected String deviceIdentity; 
	protected String friendlyName;
	protected String manufacturerName; 
	protected String modelName;
	protected String modelDescription; 
	protected String modelNumber;
	protected Class<?> zoneUserModeServiceClass;
	
	
	public HvacZoneThermostatDevice(String deviceIdentity, 
			String friendlyName, 
			String manufacturerName, 
			String modelName, 
			String modelDescription, 
			String modelNumber, 
			Class<?> zoneUserModeServiceClass) {
		super();
		this.deviceIdentity = deviceIdentity; 
		this.friendlyName = friendlyName;
		this.manufacturerName = manufacturerName; 
		this.modelName = modelName;
		this.modelDescription = modelDescription; 
		this.modelNumber = modelNumber;
		this.zoneUserModeServiceClass = zoneUserModeServiceClass;
	}
	
	public LocalDevice createDevice( ) 
			throws ValidationException,
			LocalServiceBindingException, 
			IOException {

		DeviceIdentity identity = new DeviceIdentity(
				UDN.uniqueSystemIdentifier(deviceIdentity));

		DeviceType type = new UDADeviceType("HVAC_ZoneThermostat", 1);

		DeviceDetails details = new DeviceDetails(friendlyName,
				new ManufacturerDetails(manufacturerName), new ModelDetails(
						modelName, modelDescription, modelNumber));

		/*Icon icon = new Icon("image/png", 48, 48, 8, getClass().getResource(
				"icon.png"));*/

		// we have to have a ZoneUserMode HVAC_OperatingMode service
		LocalService zoneUserModeLocalService = 
				new AnnotationLocalServiceBinder().read(zoneUserModeServiceClass);

		zoneUserModeLocalService.setManager(
				new DefaultServiceManager(
						zoneUserModeLocalService, zoneUserModeServiceClass
				)
		);

		serviceList.add(zoneUserModeLocalService);
		LocalService[] serviceArray = new LocalService[ serviceList.size() ];
		serviceList.toArray(serviceArray);
		
		// create embedded devices
		ArrayList<LocalDevice> embeddedLocalDeviceList = new ArrayList<LocalDevice>();
		for (UpnpDevice d : embeddedDevicesList) {
			embeddedLocalDeviceList.add(d.createDevice());		
		}
		LocalDevice[] embeddedDeviceArray = new LocalDevice[ embeddedDevicesList.size() ];
		embeddedLocalDeviceList.toArray(embeddedDeviceArray);
		
		return new LocalDevice(identity, type, details, /*icon,*/
				serviceArray,embeddedDeviceArray);
	}
	
}
