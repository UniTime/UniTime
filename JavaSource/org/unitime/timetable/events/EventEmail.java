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
package org.unitime.timetable.events;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

import javax.activation.DataSource;
import javax.mail.MessagingException;

import org.apache.commons.fileupload.FileItem;
import org.unitime.commons.Email;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.export.events.EventsExportEventsToICal;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.server.UploadServlet;
import org.unitime.timetable.gwt.shared.EventInterface;
import org.unitime.timetable.gwt.shared.EventInterface.ContactInterface;
import org.unitime.timetable.gwt.shared.EventInterface.MeetingInterface;
import org.unitime.timetable.gwt.shared.EventInterface.MultiMeetingInterface;
import org.unitime.timetable.gwt.shared.EventInterface.SaveOrApproveEventRpcRequest;
import org.unitime.timetable.gwt.shared.EventInterface.SaveOrApproveEventRpcResponse;
import org.unitime.timetable.model.Event;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.util.Constants;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

public class EventEmail {
	protected static GwtMessages MESSAGES = Localization.create(GwtMessages.class);
	protected static GwtConstants CONSTANTS = Localization.create(GwtConstants.class);
	protected static Map<Long, String> sMessageId = new Hashtable<Long, String>();
	
	private SaveOrApproveEventRpcRequest iRequest = null;
	private SaveOrApproveEventRpcResponse iResponse = null; 
	
	public EventEmail(SaveOrApproveEventRpcRequest request, SaveOrApproveEventRpcResponse response) {
		iRequest = request; iResponse = response;
	}
	
	public EventEmail() {}
	
	public SaveOrApproveEventRpcRequest request() { return iRequest; }
	public SaveOrApproveEventRpcResponse response() { return iResponse; }
	
	public void send(SessionContext context) throws UnsupportedEncodingException, MessagingException {
		try {
			if (!request().isEmailConfirmation()) return;
			
			if (!"true".equals(ApplicationProperties.getProperty("unitime.email.confirm.event", ApplicationProperties.getProperty("tmtbl.event.confirmationEmail","true")))) {
				response().info(MESSAGES.emailDisabled());
				return;
	        }
			
			Email email = Email.createEmail();
			if (event().hasContact() && event().getContact().getEmail() != null)
				email.addRecipient(event().getContact().getEmail(), event().getContact().getName(MESSAGES));
			if (event().hasAdditionalContacts()) {
				for (ContactInterface contact: event().getAdditionalContacts()) {
					if (contact.getEmail() != null)
						email.addRecipient(contact.getEmail(), contact.getName(MESSAGES));
				}
			}
			if (event().hasSponsor() && event().getSponsor().hasEmail())
				email.addRecipientCC(event().getSponsor().getEmail(), event().getSponsor().getName());
			if (event().hasEmail()) {
				String suffix = ApplicationProperties.getProperty("unitime.email.event.suffix", null);
				for (String address: event().getEmail().split("\n")) {
					if (!address.trim().isEmpty()) {
						if (suffix != null && address.indexOf('@') < 0)
							email.addRecipientCC(address.trim() + suffix, null);
						else
							email.addRecipientCC(address.trim(), null);
					}
				}
			}
			
			email.setSubject(event().getName() + " (" + event().getType().getName(CONSTANTS) + ")");
			
			if (context.isAuthenticated() && context.getUser().getEmail() != null) {
				email.setReplyTo(context.getUser().getEmail(), context.getUser().getName());
			} else {
				email.setReplyTo(event().getContact().getEmail(), event().getContact().getName(MESSAGES));
			}
			
			final FileItem file = (FileItem)context.getAttribute(UploadServlet.SESSION_LAST_FILE);
			if (file != null) {
				email.addAttachement(new DataSource() {
					@Override
					public OutputStream getOutputStream() throws IOException {
						throw new IOException("No output stream.");
					}
					@Override
					public String getName() {
						return file.getName();
					}
					@Override
					public InputStream getInputStream() throws IOException {
						return file.getInputStream();
					}
					@Override
					public String getContentType() {
						return file.getContentType();
					}
				});
			}
			
			final String ical = icalendar();
			if (ical != null) {
				email.addAttachement(new DataSource() {
					@Override
					public OutputStream getOutputStream() throws IOException {
						throw new IOException("No output stream.");
					}
					@Override
					public String getName() {
						return "event.ics";
					}
					@Override
					public InputStream getInputStream() throws IOException {
						return new ByteArrayInputStream(ical.getBytes("UTF-8"));
					}
					@Override
					public String getContentType() {
						return "text/calendar; charset=UTF-8";
					}
				});
			}

			email.setHTML(message());
			
			Long eventId = (response().hasEventWithId() ? response().getEvent().getId() : request().getEvent().getId());
			if (eventId != null) {
				String messageId = sMessageId.get(eventId);
				if (messageId != null)
					email.setInReplyTo(messageId);
			}
			
			email.send();
			
			if (eventId != null) {
				String messageId = email.getMessageId();
				if (messageId != null)
					sMessageId.put(eventId, messageId);
			}
			
			response().info(MESSAGES.infoConfirmationEmailSent(event().hasContact() ? event().getContact().getName(MESSAGES) : "?"));
		} catch (Exception e) {
			response().error(MESSAGES.failedToSendConfirmationEmail(e.getMessage()));
			e.printStackTrace();
		}
	}
	
	private EventInterface event() {
		return response().getEvent() != null ? response().getEvent() : request().getEvent();
	}
	
	private String subject() {
		switch (request().getOperation()) {
		case CREATE: 
			return MESSAGES.emailSubjectCreate(event().getName());
		case UPDATE:
			return MESSAGES.emailSubjectUpdate(event().getName());
		case DELETE:
			return MESSAGES.emailSubjectDelete(event().getName());
		case APPROVE:
			return MESSAGES.emailSubjectApprove(event().getName());
		case INQUIRE:
			return MESSAGES.emailSubjectInquire(event().getName());
		case REJECT:
			return MESSAGES.emailSubjectReject(event().getName());
		case CANCEL:
			return MESSAGES.emailSubjectCancel(event().getName());
		default:
			return MESSAGES.emailSubjectUpdate(event().getName());
		}
	}
	
	private String message() throws IOException, TemplateException {
		Configuration cfg = new Configuration();
		cfg.setClassForTemplateLoading(EventEmail.class, "");
		cfg.setLocale(Localization.getJavaLocale());
		cfg.setOutputEncoding("utf-8");
		Template template = cfg.getTemplate("confirmation.ftl");
		Map<String, Object> input = new HashMap<String, Object>();
		input.put("msg", MESSAGES);
		input.put("const", CONSTANTS);
		input.put("subject", subject());
		input.put("event", event());
		input.put("operation", request().getOperation() == null ? "NONE" : request().getOperation().name());
		if (response().hasCreatedMeetings())
			input.put("created", EventInterface.getMultiMeetings(response().getCreatedMeetings(), true));
		if (response().hasDeletedMeetings())
			input.put("deleted", EventInterface.getMultiMeetings(response().getDeletedMeetings(), true));
		if (response().hasCancelledMeetings())
			input.put("cancelled", EventInterface.getMultiMeetings(response().getCancelledMeetings(), true));
		if (response().hasUpdatedMeetings())
			input.put("updated", EventInterface.getMultiMeetings(response().getUpdatedMeetings(), true));
		if (request().hasMessage())
			input.put("message", request().getMessage());
		if (request().getEvent().getId() != null) {
			if (event().hasMeetings())
				input.put("meetings", EventInterface.getMultiMeetings(event().getMeetings(), true));
			else
				input.put("meetings", new TreeSet<MultiMeetingInterface>());
		}
		input.put("version", MESSAGES.pageVersion(Constants.getVersion(), Constants.getReleaseDate()));
		input.put("ts", new Date());
		
		StringWriter s = new StringWriter();
		template.process(input, new PrintWriter(s));
		s.flush(); s.close();

		return s.toString();
	}

	public String icalendar() throws IOException {
		StringWriter buffer = new StringWriter();
		PrintWriter out = new PrintWriter(buffer);

		out.println("BEGIN:VCALENDAR");
        out.println("VERSION:2.0");
        out.println("CALSCALE:GREGORIAN");
        out.println("METHOD:PUBLISH");
        out.println("X-WR-CALNAME:" + event().getName());
        out.println("X-WR-TIMEZONE:"+TimeZone.getDefault().getID());
        out.println("PRODID:-//UniTime " + Constants.getVersion() + "/Events Calendar//NONSGML v1.0//EN");
        
        boolean exp = new EventsExportEventsToICal().print(
        		out,
        		response().hasEventWithId() && response().getEvent().hasMeetings() ? response().getEvent() : request().getEvent(),
        		response().hasEventWithId() && response().getEvent().hasMeetings() ? null : EventsExportEventsToICal.ICalendarStatus.CANCELLED);
        
		out.println("END:VCALENDAR");
		
		out.flush(); out.close();
		return (exp ? buffer.getBuffer().toString() : null);		
	}
	
	public static void eventExpired(Event cancelledEvent, Set<Meeting> cancelledMeetings) throws Exception {
		if (!"true".equals(ApplicationProperties.getProperty("unitime.email.confirm.event", ApplicationProperties.getProperty("tmtbl.event.confirmationEmail","true"))))
			return;

		Session session = cancelledEvent.getSession();
		if (session == null)
			for (Meeting m: cancelledEvent.getMeetings())
				if (m.getLocation() != null) { session = m.getLocation().getSession(); break; }
		EventInterface event = EventDetailBackend.getEventDetail(session, cancelledEvent, null);
		TreeSet<MeetingInterface> meetings = new TreeSet<EventInterface.MeetingInterface>();
		for (Meeting metting: cancelledMeetings)
			for (MeetingInterface m: event.getMeetings())
				if (m.getId().equals(metting.getUniqueId())) meetings.add(m);
		
		Email email = Email.createEmail();
		if (event.hasContact() && event.getContact().getEmail() != null)
			email.addRecipient(event.getContact().getEmail(), event.getContact().getName(MESSAGES));
		if (event.hasAdditionalContacts()) {
			for (ContactInterface contact: event.getAdditionalContacts()) {
				if (contact.getEmail() != null)
					email.addRecipient(contact.getEmail(), contact.getName(MESSAGES));
			}
		}
		if (event.hasSponsor() && event.getSponsor().hasEmail())
			email.addRecipientCC(event.getSponsor().getEmail(), event.getSponsor().getName());
		if (event.hasEmail()) {
			String suffix = ApplicationProperties.getProperty("unitime.email.event.suffix", null);
			for (String address: event.getEmail().split("\n")) {
				if (!address.trim().isEmpty()) {
					if (suffix != null && address.indexOf('@') < 0)
						email.addRecipientCC(address.trim() + suffix, null);
					else
						email.addRecipientCC(address.trim(), null);
				}
			}
		}
		email.setSubject(event.getName() + " (" + event.getType().getName(CONSTANTS) + ")");
		
		Configuration cfg = new Configuration();
		cfg.setClassForTemplateLoading(EventEmail.class, "");
		cfg.setLocale(Localization.getJavaLocale());
		cfg.setOutputEncoding("utf-8");
		Template template = cfg.getTemplate("confirmation.ftl");
		Map<String, Object> input = new HashMap<String, Object>();
		input.put("msg", MESSAGES);
		input.put("const", CONSTANTS);
		input.put("subject", MESSAGES.emailSubjectExpired(event.getName()));
		input.put("event", event);
		input.put("operation", SaveOrApproveEventRpcRequest.Operation.CANCEL);
		input.put("updated", EventInterface.getMultiMeetings(meetings, true));
		input.put("message", MESSAGES.noteEventExpired());
		input.put("meetings", EventInterface.getMultiMeetings(event.getMeetings(), true));
		input.put("version", MESSAGES.pageVersion(Constants.getVersion(), Constants.getReleaseDate()));
		input.put("ts", new Date());
		
		StringWriter buffer = new StringWriter();
		template.process(input, new PrintWriter(buffer));
		buffer.flush(); buffer.close();

		email.setHTML(buffer.toString());
		
		String messageId = sMessageId.get(event.getId());
		if (messageId != null)
			email.setInReplyTo(messageId);
		
		email.send();
		
		if (email.getMessageId() != null)
			sMessageId.put(event.getId(), email.getMessageId());
	}
}
