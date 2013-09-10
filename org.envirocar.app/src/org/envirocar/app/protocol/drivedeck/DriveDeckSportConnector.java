/* 
 * enviroCar 2013
 * Copyright (C) 2013  
 * Martin Dueren, Jakob Moellers, Gerald Pape, Christopher Stephan
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 * 
 */
package org.envirocar.app.protocol.drivedeck;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.envirocar.app.commands.CommonCommand;
import org.envirocar.app.logging.Logger;
import org.envirocar.app.protocol.AbstractOBDConnector;

public class DriveDeckSportConnector extends AbstractOBDConnector {

	private static final Logger logger = Logger.getLogger(DriveDeckSportConnector.class);
	private static final char CARRIAGE_RETURN = '\r';
	private static final int SLEEP_TIME = 25;
	private static final int TIMEOUT = 5000;
	
	@Override
	public void preInitialization(final InputStream in, final OutputStream out)
			throws IOException {
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			logger.warn(e.getMessage(), e);
		}
		
		logger.info("Sending out initial CR.");
		out.write(CARRIAGE_RETURN);
		out.flush();
		
		waitForInitialResponses(in);
	}
	
	private void waitForInitialResponses(InputStream in) throws IOException {
		logger.info("waiting for initial responses...");
		int i;
		while ((i = in.read()) <= 0) {
			try {
				int tries = 0;
				while (in.available() <= 0) {
					if (tries++ * SLEEP_TIME > TIMEOUT) {
						logger.info("could not receive anything from DriveDeck adapter.");
						return;
					}
					
					Thread.sleep(getSleepTime());
				}
			} catch (InterruptedException e) {
				logger.warn(e.getMessage(), e);
			}	
		}
		
		byte[] buffer = new byte[1024];
		int index = 0;
		do {
			buffer[index++] = (byte) i; 
		} while ((i = in.read()) > 0);
		
		logger.info("Received result: "+ new String(buffer));
	}

	@Override
	public void runCommand(CommonCommand cmd, InputStream in, OutputStream out)
			throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<CommonCommand> getInitializationCommands() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean supportsDevice(String deviceName) {
		return deviceName.contains("DRIVEDECK") && deviceName.contains("W4");
	}

	@Override
	public void processInitializationCommand(CommonCommand cmd) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean connectionVerified() {
		// TODO Auto-generated method stub
		return false;
	}

}
