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
package org.unitime.timetable.security.permissions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.DepartmentStatusType;
import org.unitime.timetable.model.Event;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamType;
import org.unitime.timetable.model.Location;
import org.unitime.timetable.model.Meeting;
import org.unitime.timetable.model.RoomTypeOption;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.Solution;
import org.unitime.timetable.model.dao.ClassEventDAO;
import org.unitime.timetable.model.dao.ExamEventDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.UserAuthority;
import org.unitime.timetable.security.UserContext;
import org.unitime.timetable.security.UserQualifier;
import org.unitime.timetable.security.rights.Right;

public class EventPermissions {
	
	@PermissionForRight(Right.PersonalSchedule)
	public static class PersonalSchedule implements Permission<Session> {
		@Autowired PermissionSession permissionSession;

		@Override
		public boolean check(UserContext user, Session source) {
			return
				(permissionSession.check(user, source, DepartmentStatusType.Status.ReportClasses) && Solution.hasTimetable(source.getSessionId())) ||
				(permissionSession.check(user, source, DepartmentStatusType.Status.ReportExamsFinal) && Exam.hasTimetable(source.getUniqueId(), ExamType.sExamTypeFinal)) ||
				(permissionSession.check(user, source, DepartmentStatusType.Status.ReportExamsMidterm) && Exam.hasTimetable(source.getUniqueId(), ExamType.sExamTypeMidterm));
		}

		@Override
		public Class<Session> type() { return Session.class; }
		
	}
	
	@PermissionForRight(Right.PersonalScheduleLookup)
	public static class PersonalScheduleLookup extends PersonalSchedule {}
	
	protected static abstract class EventPermission<T> implements Permission<T> {
		@Autowired PermissionSession permissionSession;
		
		protected Date today() {
			Calendar cal = Calendar.getInstance(Localization.getJavaLocale());
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			return cal.getTime();
		}
		
		protected Date begin(Session session) {
			return session.getEventBeginDate();
		}
		
		protected Date end(Session session) {
			Calendar cal = Calendar.getInstance(Localization.getJavaLocale());
			cal.setTime(session.getEventEndDate());
			cal.add(Calendar.DAY_OF_YEAR, 1);
			return cal.getTime();
		}
		
		protected boolean isOutside(Date date, Session session) {
			return date == null || date.before(begin(session)) || !date.before(end(session));
		}
		
		protected boolean isPast(Date date) {
			return date == null || date.before(today());
		}
		
		protected List<Long> locations(Long sessionId, UserContext user) {
			String anyRequest = "";
			String deptRequest = "";
			String mgrRequest = "";
			for (RoomTypeOption.Status state: RoomTypeOption.Status.values()) {
				if (state.isAuthenticatedUsersCanRequestEvents())
					anyRequest += (anyRequest.isEmpty() ? "" : ", ") + state.ordinal();
				else {
					if (state.isDepartmentalUsersCanRequestEvents())
						deptRequest += (deptRequest.isEmpty() ? "" : ", ") + state.ordinal();
					if (state.isEventManagersCanRequestEvents())
						mgrRequest += (mgrRequest.isEmpty() ? "" : ", ") + state.ordinal();
				}
			}
			Set<Serializable> roleDeptIds = new HashSet<Serializable>(), mgrDeptIds = new HashSet<Serializable>();
			if (!user.getCurrentAuthority().hasRight(Right.DepartmentIndependent)) {
				for (UserAuthority a: user.getAuthorities()) {
					if (!sessionId.equals(a.getAcademicSession().getQualifierId())) continue;
					for (UserQualifier q: a.getQualifiers("Department")) {
						roleDeptIds.add(q.getQualifierId());
						if (a.hasRight(Right.EventMeetingApprove))
							mgrDeptIds.add(q.getQualifierId());
					}
				}
			}
			String roleDept = null, mgrDept = null;
			for (Serializable id: roleDeptIds)
				roleDept = (roleDept == null ? "" : roleDept + ",") + id;
			for (Serializable id: mgrDeptIds)
				mgrDept = (mgrDept == null ? "" : mgrDept + ",") + id;
			
			if (sessionId == null) return new ArrayList<Long>();
			
			return (List<Long>) SessionDAO.getInstance().getSession().createQuery(
					"select l.uniqueId " +
					"from Location l, RoomTypeOption o " +
					"where l.eventDepartment.allowEvents = true and o.roomType = l.roomType and o.department = l.eventDepartment and l.session.uniqueId = :sessionId and (" +
					"(l.eventStatus in (" + anyRequest + ") or (l.eventStatus is null and o.status in (" + anyRequest + ")))" +
					(user.getCurrentAuthority().hasRight(Right.DepartmentIndependent)
							? " or (l.eventStatus in (" + deptRequest + ") or (l.eventStatus is null and o.status in (" + deptRequest + ")))"
							: roleDept == null ? ""
							: " or ((l.eventStatus in (" + deptRequest + ") or (l.eventStatus is null and o.status in (" + deptRequest + "))) and o.department.uniqueId in (" + roleDept + "))"
					) +
					(user.getCurrentAuthority().hasRight(Right.DepartmentIndependent) && user.getCurrentAuthority().hasRight(Right.EventMeetingApprove)
							? " or (l.eventStatus in (" + mgrRequest + ") or (l.eventStatus is null and o.status in (" + mgrRequest + ")))"
							: mgrDept == null ? ""
							: " or ((l.eventStatus in (" + mgrRequest + ") or (l.eventStatus is null and o.status in (" + mgrRequest + "))) and o.department.uniqueId in (" + mgrDept + "))"
					) +
					")")
					.setLong("sessionId", sessionId).setCacheable(true).list();
		}
	}
	
	@PermissionForRight(Right.Events)
	public static class Events extends EventPermission<Session> {
		@Override
		public boolean check(UserContext user, Session source) {
			return source.getStatusType().canNoRoleReport() || (user.getCurrentAuthority().hasRight(Right.EventAnyLocation) || !locations(source.getUniqueId(), user).isEmpty());
		}
		
		@Override
		public Class<Session> type() { return Session.class; }
	}

	@PermissionForRight(Right.EventAddSpecial)
	public static class EventAddSpecial extends EventPermission<Session> {
		@Override
		public boolean check(UserContext user, Session source) {
			return (!isPast(end(source)) || user.getCurrentAuthority().hasRight(Right.EventEditPast)) &&
					(user.getCurrentAuthority().hasRight(Right.EventAnyLocation) || !locations(source.getUniqueId(), user).isEmpty());
		}
		
		@Override
		public Class<Session> type() { return Session.class; }
	}
	
	@PermissionForRight(Right.EventAddCourseRelated)
	public static class EventAddCourseRelated extends EventAddSpecial { }
	
	@PermissionForRight(Right.EventAddUnavailable)
	public static class EventAddUnavailable extends EventPermission<Session> {
		@Autowired Permission<Session> permissionEventAddSpecial;
		
		@Override
		public boolean check(UserContext user, Session source) {
			return user.getCurrentAuthority().hasRight(Right.EventLocationUnavailable) && permissionEventAddSpecial.check(user, source);
		}
		
		@Override
		public Class<Session> type() { return Session.class; }
	}

	@PermissionForRight(Right.EventDetail)
	public static class EventDetail implements Permission<Event> {
		@Autowired Permission<Class_> permissionClassDetail;
		@Autowired Permission<Exam> permissionExaminationDetail;
		
		@Override
		public boolean check(UserContext user, Event source) {
			// Owner can always see
			if (user.getExternalUserId().equals(source.getMainContact().getExternalUniqueId())) return true;
			
			// Event manager can see all events.
			// FIXME: Really?
			// if (user.getCurrentAuthority().hasRight(Right.EventLookupContact)) return true;
			
			switch (source.getEventType()) {
			case Event.sEventTypeClass:
				// Class event -- can see Class Detail page?
				return user.getCurrentAuthority().hasRight(Right.ClassDetail) && permissionClassDetail.check(user, ClassEventDAO.getInstance().get(source.getUniqueId()).getClazz());
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
				// Examination event -- can see ExaminationDetail page?
				return user.getCurrentAuthority().hasRight(Right.ExaminationDetail) && permissionExaminationDetail.check(user, ExamEventDAO.getInstance().get(source.getUniqueId()).getExam());
			default:
				// Event managers can see other events
				return user.getCurrentAuthority().hasRight(Right.EventLookupContact);
			}
		}
		
		@Override
		public Class<Event> type() { return Event.class; }

	}
	
	@PermissionForRight(Right.EventEdit)
	public static class EventEdit extends EventPermission<Event> {
		@Autowired PermissionSession permissionSession;
		@Autowired Permission<Date> permissionEventDate;
		
		@Override
		public boolean check(UserContext user, Event source) {
			switch (source.getEventType()) {
			case Event.sEventTypeClass:
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
				// Examination and class events cannot be edited just yet
				return false;
			case Event.sEventTypeUnavailable:
				if (!user.getCurrentAuthority().hasRight(Right.EventAddUnavailable)) return false;
				break;
			case Event.sEventTypeCourse:
				if (!user.getCurrentAuthority().hasRight(Right.EventAddCourseRelated)) return false;
				break;
			case Event.sEventTypeSpecial:
				if (!user.getCurrentAuthority().hasRight(Right.EventAddSpecial)) return false;
				break;
			}
			
			// Must be the owner or an event admin
			return user.getCurrentAuthority().hasRight(Right.EventLookupContact) || user.getExternalUserId().equals(source.getMainContact().getExternalUniqueId());
		}

		@Override
		public Class<Event> type() { return Event.class; }
	}
	
	@PermissionForRight(Right.EventDate)
	public static class EventDate extends EventPermission<Date> {

		@Override
		public boolean check(UserContext user, Date source) {
			// Must be inside of the academic session, and cannot be in the past (or must have the EventEditPast override)
			return (user.getCurrentAuthority().hasRight(Right.EventEditPast) || !isPast(source)) &&
					!isOutside(source, SessionDAO.getInstance().get(user.getCurrentAcademicSessionId()));
		}
		
		@Override
		public Class<Date> type() { return Date.class; }
	}
	
	@PermissionForRight(Right.EventLocation)
	public static class EventLocation extends EventPermission<Location> {
		@Override
		public boolean check(UserContext user, Location source) {
			// Must be within user's locations (or must have the EventAnyLocation override)
			return source == null || user.getCurrentAuthority().hasRight(Right.EventAnyLocation) || locations(user.getCurrentAcademicSessionId(), user).contains(source.getUniqueId());
		}
		
		@Override
		public Class<Location> type() { return Location.class; }
	}
	
	@PermissionForRight(Right.EventLocationApprove)
	public static class EventLocationApprove extends EventPermission<Location> {
		@Autowired Permission<Location> permissionEventLocation;
		@Autowired PermissionDepartment permissionDepartment;
		
		@Override
		public boolean check(UserContext user, Location source) {
			// Has the EventAnyLocation override? 
			if (user.getCurrentAuthority().hasRight(Right.EventAnyLocation)) return true;
			
			// Must be within user's locations
			if (!locations(user.getCurrentAcademicSessionId(), user).contains(source.getUniqueId())) return false;
			
			// Can manager approve? 
			if (!source.getRoomType().getOption(source.getEventDepartment()).getEventStatus().isEventManagersCanApprove()) return false;
			
			// Has event department?
			return permissionDepartment.check(user, source.getEventDepartment());
		}
		
		@Override
		public Class<Location> type() { return Location.class; }
	}
	
	@PermissionForRight(Right.EventLocationUnavailable)
	public static class EventLocationUnavailable extends EventPermission<Location> {
		@Autowired Permission<Location> permissionEventLocation;
		@Autowired PermissionDepartment permissionDepartment;
		
		@Override
		public boolean check(UserContext user, Location source) {
			// Has the EventAnyLocation override? 
			if (user.getCurrentAuthority().hasRight(Right.EventAnyLocation)) return true;

			// Must be within user's locations
			if (!locations(user.getCurrentAcademicSessionId(), user).contains(source.getUniqueId())) return false;

			// Can manager request?
			if (!source.getRoomType().getOption(source.getEventDepartment()).getEventStatus().isEventManagersCanRequestEvents()) return false;
			
			// Has event department?
			return permissionDepartment.check(user, source.getEventDepartment());
		}
		
		@Override
		public Class<Location> type() { return Location.class; }
	}

	@PermissionForRight(Right.EventLocationOverbook)
	public static class EventLocationOverbook extends EventPermission<Location> {
		@Autowired Permission<Location> permissionEventLocation;
		@Autowired PermissionDepartment permissionDepartment;
		
		@Override
		public boolean check(UserContext user, Location source) {
			// Has the EventAnyLocation override? 
			if (user.getCurrentAuthority().hasRight(Right.EventAnyLocation)) return true;

			// Must be within user's locations
			if (!locations(user.getCurrentAcademicSessionId(), user).contains(source.getUniqueId())) return false;

			// Can manager request?
			if (!source.getRoomType().getOption(source.getEventDepartment()).getEventStatus().isEventManagersCanRequestEvents()) return false;
			
			// Has event department?
			return permissionDepartment.check(user, source.getEventDepartment());
		}
		
		@Override
		public Class<Location> type() { return Location.class; }
	}
	
	@PermissionForRight(Right.EventMeetingEdit)
	public static class EventMeetingEdit extends EventPermission<Meeting> {
		@Autowired Permission<Event> permissionEventEdit;
		@Autowired Permission<Date> permissionEventDate;
		@Autowired Permission<Location> permissionEventLocation;

		@Override
		public boolean check(UserContext user, Meeting source) {
			// Only pending and approved meetings can be edited
			if (source.getStatus() != Meeting.Status.PENDING && source.getStatus() != Meeting.Status.APPROVED) return false;
			
			// Is the event editable?
			if (!permissionEventEdit.check(user, source.getEvent())) return false;
			
			// Is the date ok?
			if (!permissionEventDate.check(user, source.getMeetingDate())) return false;
			
			// Is the location ok?
			if (!permissionEventLocation.check(user, source.getLocation())) return false;
			
			return true;
		}
		
		@Override
		public Class<Meeting> type() { return Meeting.class; }
	}
	
	@PermissionForRight(Right.EventMeetingDelete)
	public static class EventMeetingDelete extends EventPermission<Meeting> {
		@Autowired Permission<Date> permissionEventDate;
		@Autowired Permission<Location> permissionEventLocation;

		@Override
		public boolean check(UserContext user, Meeting source) {
			switch (source.getEvent().getEventType()) {
			case Event.sEventTypeClass:
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
				// Examination and class events cannot be deleted through the event management
				return false;
			case Event.sEventTypeSpecial:
			case Event.sEventTypeCourse:
				// Only pending meetings can be deleted
				if (source.getStatus() != Meeting.Status.PENDING) return false;
				break;
			case Event.sEventTypeUnavailable:
				// Only approved meetings can be deleted
				if (source.getStatus() != Meeting.Status.APPROVED) return false;
				break;
			}
			
			// Is the date ok?
			if (!permissionEventDate.check(user, source.getMeetingDate())) return false;

			// Owner can delete
			if (user.getExternalUserId().equals(source.getEvent().getMainContact().getExternalUniqueId())) return true;
			
			// Otherwise check location too
			return permissionEventLocation.check(user, source.getLocation());
		}
		
		@Override
		public Class<Meeting> type() { return Meeting.class; }
	}
	
	@PermissionForRight(Right.EventMeetingCancel)
	public static class EventMeetingCancel extends EventPermission<Meeting> {
		@Autowired Permission<Date> permissionEventDate;
		@Autowired Permission<Location> permissionEventLocationApprove;

		@Override
		public boolean check(UserContext user, Meeting source) {
			switch (source.getEvent().getEventType()) {
			case Event.sEventTypeClass:
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
				// Examination and class events cannot be cancelled through the event management
				return false;
			case Event.sEventTypeSpecial:
			case Event.sEventTypeCourse:
				// Only pending meetings can be cancelled
				if (source.getStatus() != Meeting.Status.PENDING && source.getStatus() != Meeting.Status.APPROVED) return false;
				break;
			case Event.sEventTypeUnavailable:
				// Only approved meetings can be cancelled
				if (source.getStatus() != Meeting.Status.APPROVED) return false;
				break;
			}
			
			// Is the date ok?
			if (!permissionEventDate.check(user, source.getMeetingDate())) return false;

			// Owner can delete if date is ok
			if (user.getExternalUserId().equals(source.getEvent().getMainContact().getExternalUniqueId())) {
				if (permissionEventDate.check(user, source.getMeetingDate())) return true;
			}
			
			// Otherwise must be a manager
			if (!user.getCurrentAuthority().hasRight(Right.EventLookupContact)) return false;
			
			// Correct academic session?
			if (isOutside(source.getMeetingDate(), SessionDAO.getInstance().get(user.getCurrentAcademicSessionId()))) return false;
			
			// Is in the past?
			if (!user.getCurrentAuthority().hasRight(Right.EventApprovePast) && isPast(source.getMeetingDate())) return false;

			// Check the location
			return permissionEventLocationApprove.check(user, source.getLocation());
		}
		
		@Override
		public Class<Meeting> type() { return Meeting.class; }
	}
	
	@PermissionForRight(Right.EventMeetingApprove)
	public static class EventMeetingApprove extends EventPermission<Meeting> {
		@Autowired Permission<Location> permissionEventLocationApprove;
		
		@Override
		public boolean check(UserContext user, Meeting source) {
			// Only pending meetings can be approved
			if (source.getStatus() != Meeting.Status.PENDING) return false;
			
			// Following events are implicitly approved
			switch (source.getEvent().getEventType()) {
			case Event.sEventTypeClass:
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
			case Event.sEventTypeUnavailable:
				return false;
			}
			
			// Correct academic session?
			if (isOutside(source.getMeetingDate(), SessionDAO.getInstance().get(user.getCurrentAcademicSessionId()))) return false;
			
			// Is in the past?
			if (!user.getCurrentAuthority().hasRight(Right.EventApprovePast) && isPast(source.getMeetingDate())) return false;

			// Check the location
			return permissionEventLocationApprove.check(user, source.getLocation());
		}
		
		@Override
		public Class<Meeting> type() { return Meeting.class; }
	}
	
	@PermissionForRight(Right.EventMeetingInquire)
	public static class EventMeetingInquire extends EventPermission<Meeting> {
		@Autowired Permission<Location> permissionEventLocation;
		
		@Override
		public boolean check(UserContext user, Meeting source) {
			// Only pending and approved meetings can be inquired
			if (source.getStatus() != Meeting.Status.PENDING && source.getStatus() != Meeting.Status.APPROVED) return false;
			
			// Following events cannot be inquired
			switch (source.getEvent().getEventType()) {
			case Event.sEventTypeClass:
			case Event.sEventTypeFinalExam:
			case Event.sEventTypeMidtermExam:
				return false;
			}
			
			// Correct academic session?
			if (isOutside(source.getMeetingDate(), SessionDAO.getInstance().get(user.getCurrentAcademicSessionId()))) return false;
			
			// Is in the past?
			if (!user.getCurrentAuthority().hasRight(Right.EventApprovePast) && isPast(source.getMeetingDate())) return false;

			// Check the location
			return permissionEventLocation.check(user, source.getLocation());
		}
		
		@Override
		public Class<Meeting> type() { return Meeting.class; }
	}
	
}
