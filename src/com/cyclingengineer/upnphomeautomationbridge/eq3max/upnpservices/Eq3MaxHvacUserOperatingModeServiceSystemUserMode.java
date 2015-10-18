package com.cyclingengineer.upnphomeautomationbridge.eq3max.upnpservices;

import com.cyclingengineer.upnphomeautomationbridge.upnpservices.HvacUserOperatingModeServiceSystemUserMode;

public class Eq3MaxHvacUserOperatingModeServiceSystemUserMode extends
		HvacUserOperatingModeServiceSystemUserMode {

	@Override
	protected void nameUpdate(String setNameValue) {
		// do nothing
	}

	@Override
	protected void modeTargetUpdate(String setModeTargetValue) {
		// TODO do something when status update is requested
		modeStatus = setModeTargetValue;
	}

	@Override
	protected String modeStatusRequest() {
		// just return the status
		return modeStatus;
	}

	
}
