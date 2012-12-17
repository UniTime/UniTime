/*
 * UniTime 3.4 (University Timetabling Application)
 * Copyright (C) 2012, UniTime LLC, and individual contributors
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
package org.unitime.timetable.gwt.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.model.Event;
import org.unitime.timetable.model.EventNote;
import org.unitime.timetable.model.dao.EventDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.context.HttpSessionContext;
import org.unitime.timetable.security.rights.Right;

public class UploadServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_MAX_SIZE = 4096 * 1024;
	
	public static final String SESSION_LAST_FILE = "LAST_FILE";
	
	protected SessionContext getSessionContext() {
		return HttpSessionContext.getSessionContext(getServletContext());
	}
	
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Long eventId = (request.getParameter("event") == null ? null : Long.parseLong(request.getParameter("event")));
		String fileName = request.getParameter("name");
		Long noteId = (request.getParameter("note") == null ? null : Long.valueOf(request.getParameter("note")));
		
		if (eventId != null) {
			getSessionContext().checkPermissionAnyAuthority(Long.valueOf(eventId), "Event", Right.EventDetail);
			Event event = EventDAO.getInstance().get(eventId);
			TreeSet<EventNote> notes = new TreeSet<EventNote>();
			for (EventNote note: event.getNotes()) {
				if (note.getAttachedName() == null || note.getAttachedName().isEmpty()) continue;
				if (fileName != null) {
					if (fileName.equals(note.getAttachedName()) && (noteId == null || noteId.equals(note.getUniqueId()))) notes.add(note);
				} else if (noteId != null) {
					if (noteId.equals(note.getUniqueId())) notes.add(note);
				} else {
					notes.add(note);
				}
			}
			if (!notes.isEmpty()) {
				EventNote note = notes.last();
				
				response.setContentType(note.getAttachedContentType());
				response.setHeader( "Content-Disposition", "attachment; filename=\"" + note.getAttachedName() + "\"" );
		        OutputStream out = response.getOutputStream();
		        out.write(note.getAttachedFile());
		        out.flush();
		        out.close();
		        
		        return;
			}
		}
		
		throw new ServletException("Nothing to download.");
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			String maxSizeProperty = ApplicationProperties.getProperty("unitime.upload.max_size_in_bytes");
			int maxSize = (maxSizeProperty == null ? DEFAULT_MAX_SIZE : Integer.parseInt(maxSizeProperty));
			
			ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory(maxSize, ApplicationProperties.getTempFolder()));
			upload.setSizeMax(maxSize);
			
			List<FileItem> files = (List<FileItem>)upload.parseRequest(request);
			
			String message = null;
			if (files.size() == 1) {
				FileItem file = files.get(0);
				if (file.getSize() <= 0) {
					request.getSession().removeAttribute(SESSION_LAST_FILE);
					message = "No file is selected.";
				} else {
					request.getSession().setAttribute(SESSION_LAST_FILE, file);
					message = "File " + file.getName() + " (" + file.getSize() + " bytes) selected.";
				}
			} else {
				request.getSession().removeAttribute(SESSION_LAST_FILE);
				message = "No file is selected.";
			}
			
			response.setContentType("text/html; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();
			out.print(message);
			out.flush();
			out.close();
		} catch (FileUploadException e) {
			response.setContentType("text/html; charset=UTF-8");
			response.setCharacterEncoding("UTF-8");
			PrintWriter out = response.getWriter();
			out.print("ERROR:Upload failed: " + e.getMessage());
			out.flush();
			out.close();
		}
	}
}
