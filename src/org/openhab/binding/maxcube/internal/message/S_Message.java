/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.maxcube.internal.message;


import java.util.logging.Logger;



/**
 * The S message contains information about Command execution results
 * 
 * @author Andreas Heil (info@aheil.de)
 * @author Bernd Michael Helm (bernd.helm at helmundwalter.de)
 * @author Marcel Verpaalen - OH2 version + parsing of the message
 * @since 1.6.0
 */
public final class S_Message extends Message {
	
	private int dutyCycle;
	private int freeMemorySlots;
	private boolean commandDiscarded = false;
	protected final Logger logger = Logger.getLogger(this.getClass().getName());
	
	public S_Message(String raw) {
		super(raw);
		String[] tokens = this.getPayload().split(Message.DELIMETER);
		if (tokens.length == 3){
			try{
				dutyCycle = Integer.parseInt(tokens[0],16);
				commandDiscarded = tokens[1] == "1";
				freeMemorySlots = Integer.parseInt(tokens[2],16);
			} catch(Exception e) {
				logger.fine("Exception occurred during parsing of S message: "+ e.getMessage()+" "+ e);
			}
		}else
		{
			logger.fine("Unexpected # of tolkens ("+tokens.length+") received in S message: "+this.getPayload());
		}
	}
	
	public int getDutyCycle() {
		return dutyCycle;
	}
	
	public int getFreeMemorySlots() {
		return freeMemorySlots;
	}
	
	public boolean isCommandDiscarded() {
		return commandDiscarded;
	}
	
	@Override
	public void debug(Logger logger) {
		logger.finer("=== S_Message === ");
		logger.finer("\tRAW : "+ this.getPayload());
		logger.finer("\tDutyCycle : "+ this.dutyCycle);
		logger.finer("\tCommand Discarded : "+ this.commandDiscarded);
		logger.finer("\tFreeMemorySlots : "+ this.freeMemorySlots);
	}
	
	@Override
	public MessageType getType() {
		return MessageType.S;
	}
	
}
