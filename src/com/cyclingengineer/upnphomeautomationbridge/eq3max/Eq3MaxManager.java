package com.cyclingengineer.upnphomeautomationbridge.eq3max;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fourthline.cling.UpnpService;
import org.openhab.binding.maxcube.internal.MaxCubeDiscover;
import org.openhab.binding.maxcube.internal.Utils;
import org.openhab.binding.maxcube.internal.exceptions.IncompleteMessageException;
import org.openhab.binding.maxcube.internal.exceptions.IncorrectMultilineIndexException;
import org.openhab.binding.maxcube.internal.exceptions.MessageIsWaitingException;
import org.openhab.binding.maxcube.internal.exceptions.NoMessageAvailableException;
import org.openhab.binding.maxcube.internal.exceptions.UnprocessableMessageException;
import org.openhab.binding.maxcube.internal.exceptions.UnsupportedMessageTypeException;
import org.openhab.binding.maxcube.internal.message.C_Message;
import org.openhab.binding.maxcube.internal.message.Configuration;
import org.openhab.binding.maxcube.internal.message.Device;
import org.openhab.binding.maxcube.internal.message.DeviceInformation;
import org.openhab.binding.maxcube.internal.message.L_Message;
import org.openhab.binding.maxcube.internal.message.M_Message;
import org.openhab.binding.maxcube.internal.message.Message;
import org.openhab.binding.maxcube.internal.message.MessageProcessor;
import org.openhab.binding.maxcube.internal.message.MessageType;
import org.openhab.binding.maxcube.internal.message.RoomInformation;

import com.cyclingengineer.upnphomeautomationbridge.eq3max.internals.Room;
import com.cyclingengineer.upnphomeautomationbridge.eq3max.upnpdevices.Eq3RoomHvacZoneThermostatDevice;

/**
 * This is the top level class for the EQ-3 MAX! UPNP bridge
 * @author Paul Hampson (cyclingengineer)
 */
public class Eq3MaxManager implements Runnable {
	
	private UpnpService upnpService;
	private String cubeIp = "";
	private int cubePort = 62910;
	
	private Socket cubeSocket = null;
	private BufferedReader cubeReader = null;
	private OutputStreamWriter cubeWriter = null;
	
	private MessageProcessor messageProcessor = new MessageProcessor();
	
	private ArrayList<Configuration> configurations = new ArrayList<Configuration>();
	private ArrayList<Device> devices = new ArrayList<Device>();
	private ArrayList<Room> rooms = new ArrayList<Room>();
	
	protected final Logger logger = Logger.getLogger(this.getClass().getName());
	
	public Eq3MaxManager(UpnpService upnpService) {
		this.upnpService = upnpService;	
		
	}
	
	private void openCubeConnection() throws UnknownHostException, IOException {
		if (cubeSocket == null) {
			cubeSocket = new Socket(cubeIp, cubePort);
			cubeSocket.setSoTimeout(2000);
			logger.info("Established connection to MAX! cube at "+cubeIp+":"+cubePort);
			cubeReader = new BufferedReader(new InputStreamReader(cubeSocket.getInputStream()));
			cubeWriter = new OutputStreamWriter(cubeSocket.getOutputStream());
		}
	}
	
	private void closeCubeConnection() throws IOException {
		if (cubeSocket != null) {			
			cubeSocket.close();
			logger.info("Closed connection to MAX! cube");
			cubeReader = null;
			cubeWriter = null;
			cubeSocket = null;
		}
	}
	
	public void run() {
		// MAX! cube discovery
		if (cubeIp.isEmpty()) {
			cubeIp = MaxCubeDiscover.discoverIp();
		}
		if (cubeIp == null)
		{
			logger.severe("Exiting as unable to find MAX! cube IP");
			return;
		} 
		
		logger.info("Found cube at "+cubeIp);		
		try {
			openCubeConnection();
		} catch (UnknownHostException e) {
			logger.severe("Unable to connect to cube (UnknownHostException): "+e.getMessage());
			return;
		} catch (IOException e) { 
			logger.severe("Unable to connect to cube (IOException): "+e.getMessage());
			return;
		}
		
		logger.info("Successfully connected to cube at "+cubeIp);	
		
		// Find all available devices
		boolean processMessages = true;
		while (processMessages) {
			String rawLine = null;
			try {
				rawLine = cubeReader.readLine();
			} catch (Exception e) {
				logger.severe("Error reading data from cube: "+e.getMessage());
				processMessages = false;
				continue;
			}
			
			logger.info("Read: "+rawLine);
			
			if (rawLine == null) {
				// run out of data
				processMessages = false;
				continue;
			}
			
			Message message = null;
			try {
				this.messageProcessor.addReceivedLine(rawLine);
				if (this.messageProcessor.isMessageAvailable()) {
					message = this.messageProcessor.pull();
					logger.info("New MAX! message available");
				} else {
					continue;
				}
				
				if (message != null) {
					logger.getParent().setLevel(Level.FINEST);
					message.debug(logger);

					// process messages
					switch (message.getType())
					{
						case M: // device and room configuration
						{
							M_Message msg = (M_Message) message;
							// process rooms
							for (RoomInformation ri : msg.rooms) {
								Room existingRoom = null;
								for (Room r : rooms) {
									if (r.getRoomId() == ri.getPosition()) {
										existingRoom = r;
										break;
									}
								}
								// no existing room found - add one
								if (existingRoom == null) {
									logger.info("Adding room "+ri.getName());
									rooms.add(new Room(ri.getName(), ri.getPosition()));
								}									
							}
							
							// process devices
							for (DeviceInformation di : msg.devices) {
								
								// generate device to room mapping							
								Room parentRoom = null;
								for (Room r : rooms) {
									if (r.getRoomId() == di.getRoomId())
										parentRoom = r;
								}
								if (parentRoom == null) {
									// this would be odd... TODO handle orphaned device?
									logger.warning("Found orphaned device "+di.getName());
									continue;
								} else {
									logger.info("Adding "+di.getName()+" to "+parentRoom.getRoomName());
									parentRoom.addDevice(di.getSerialNumber());
								}
								
								// setup configurations
								Configuration c = null;
								for (Configuration conf : configurations) {
									if (conf.getSerialNumber().equalsIgnoreCase(di.getSerialNumber())) {
										c = conf;
										break;
									}
								}

								if (c != null) {
									configurations.remove(c);
								}

								c = Configuration.create(di);
								configurations.add(c);

								c.setRoomId(di.getRoomId());
							}							
							break;
						}
						
						case C: // device state information
						{
							Configuration c = null;
							for (Configuration conf : configurations) {
								if (conf.getSerialNumber().equalsIgnoreCase(((C_Message) message).getSerialNumber())) {
									c = conf;
									break;
								}
							}

							if (c == null) {
								configurations.add(Configuration.create(message));
							} else {
								c.setValues((C_Message) message);
							}
							break;
						}
						// TODO other message types...
						case L: 
						{
							((L_Message) message).updateDevices(devices, configurations);
							
							logger.info(""+devices.size()+" devices found." );
							
							// the L message is the last one, while the reader
							// would hang trying to read a new line and
							// eventually the
							// cube will fail to establish
							// new connections for some time
							processMessages = false;
							break;
						}
						
						default:
							// do nothing
							break;
					}
				}
			}  catch (IncorrectMultilineIndexException ex) {
				logger.info("Incorrect MAX!Cube multiline message detected. Stopping processing and continue with next Line.");
				this.messageProcessor.reset();
			} catch (NoMessageAvailableException ex) {
				logger.info("Could not process MAX!Cube message. Stopping processing and continue with next Line.");
				this.messageProcessor.reset();
			} catch (IncompleteMessageException ex) {
				logger.info("Error while parsing MAX!Cube multiline message. Stopping processing, and continue with next Line.");
				this.messageProcessor.reset();
			} catch (UnprocessableMessageException ex) {
				logger.info("Error while parsing MAX!Cube message. Stopping processing, and continue with next Line.");
				this.messageProcessor.reset();
			} catch (UnsupportedMessageTypeException ex) {
				logger.info("Unsupported MAX!Cube message detected. Ignoring and continue with next Line.");
				this.messageProcessor.reset();
			} catch (MessageIsWaitingException ex) {
				logger.info("There was and unhandled message waiting. Ignoring and continue with next Line.");
				this.messageProcessor.reset();
			} catch (Exception e) {
				logger.info("Failed to process message received by MAX! protocol.");
				logger.fine(Utils.getStackTrace(e));
				this.messageProcessor.reset();
			}
		}
		
		try {
			closeCubeConnection();
		} catch (IOException e) {
			logger.warning("Failed to close cube socket");
			logger.fine(Utils.getStackTrace(e));
		}
		
		// TODO build Upnp device(s) based on data received from MAX! cube 
		
		// TODO setup Upnp service
		// example:
		try {			
            
			//example device
            Eq3RoomHvacZoneThermostatDevice thermostat = new Eq3RoomHvacZoneThermostatDevice("Room Thermostat 1", "RT1 Friendly Name", "Manuf", "Model", "A lovely room thermostat", "v1");

            // add thermostat
            this.upnpService.getRegistry().addDevice(
            		thermostat.createDevice()
            );

        } catch (Exception ex) {
            System.err.println("Exception occured: " + ex);
            ex.printStackTrace(System.err);
            System.exit(1);
        }
		
		// TODO request data from max cube & start update loop
		
	}

}
