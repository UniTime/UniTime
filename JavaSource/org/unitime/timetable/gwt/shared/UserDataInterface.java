/*
 * UniTime 3.5 (University Timetabling Application)
 * Copyright (C) 2014, UniTime LLC, and individual contributors
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
package org.unitime.timetable.gwt.shared;

import java.util.ArrayList;
import java.util.HashMap;

import org.unitime.timetable.gwt.command.client.GwtRpcRequest;
import org.unitime.timetable.gwt.command.client.GwtRpcResponse;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseNull;

/**
 * @author Tomas Muller
 */
public class UserDataInterface extends HashMap<String, String> implements GwtRpcResponse {
	private static final long serialVersionUID = 1L;

	public static class GetUserDataRpcRequest extends ArrayList<String> implements GwtRpcRequest<UserDataInterface> {
		private static final long serialVersionUID = 1L;
	}
	
	public static class SetUserDataRpcRequest extends UserDataInterface implements GwtRpcRequest<GwtRpcResponseNull> {
		private static final long serialVersionUID = 1L;
	}
}
