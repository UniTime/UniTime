/*
 * UniTime 3.4 - 3.5 (University Timetabling Application)
 * Copyright (C) 2013, UniTime LLC, and individual contributors
 * as indicated by the @authors tag.
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
 * You should have received a copy of the GNU General Public License along
 * with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
*/
package org.unitime.timetable.events;

import org.unitime.timetable.gwt.shared.EventInterface.SaveFilterDefaultRpcRequest;

import org.unitime.timetable.gwt.command.client.GwtRpcResponse;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.security.SessionContext;

/**
 * @author Tomas Muller
 */
@GwtRpcImplements(SaveFilterDefaultRpcRequest.class)
public class SaveFilterDefaultBackend implements GwtRpcImplementation<SaveFilterDefaultRpcRequest, GwtRpcResponse>{

	@Override
	public GwtRpcResponse execute(SaveFilterDefaultRpcRequest request, SessionContext context) {
		if (context.isAuthenticated())
			context.getUser().setProperty("Default[" + request.getName() + "]", request.getValue());
		
		return null;
	}

}
