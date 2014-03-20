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
package org.unitime.timetable.server.exams;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.HtmlUtils;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.gwt.client.sectioning.ExaminationEnrollmentTable.ExaminationScheduleRpcRequest;
import org.unitime.timetable.gwt.client.sectioning.ExaminationEnrollmentTable.ExaminationScheduleRpcResponse;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.EventInterface.ContactInterface;
import org.unitime.timetable.gwt.shared.EventInterface.RelatedObjectInterface;
import org.unitime.timetable.gwt.shared.EventInterface.ResourceInterface;
import org.unitime.timetable.gwt.shared.EventInterface.ResourceType;
import org.unitime.timetable.model.Class_;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Event;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamOwner;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.InstrOfferingConfig;
import org.unitime.timetable.model.dao.ExamDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.exam.ui.ExamAssignment;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo;
import org.unitime.timetable.solver.exam.ui.ExamRoomInfo;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.BackToBackConflict;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.DirectConflict;
import org.unitime.timetable.solver.exam.ui.ExamAssignmentInfo.MoreThanTwoADayConflict;
import org.unitime.timetable.solver.exam.ui.ExamInfo.ExamSectionInfo;
import org.unitime.timetable.solver.service.SolverService;

/**
 * @author Tomas Muller
 */
@GwtRpcImplements(ExaminationScheduleRpcRequest.class)
public class ExaminationScheduleBackend implements GwtRpcImplementation<ExaminationScheduleRpcRequest, ExaminationScheduleRpcResponse>{
	protected static GwtMessages MESSAGES = Localization.create(GwtMessages.class);

	@Autowired SolverService<ExamSolverProxy> examinationSolverService;
	
	@Override
	public ExaminationScheduleRpcResponse execute(ExaminationScheduleRpcRequest request, SessionContext context) {
		org.hibernate.Session hibSession = ExamDAO.getInstance().getSession();
		
		Exam exam = ExamDAO.getInstance().get(request.getExamId());
		
		context.checkPermission(exam, Right.ExaminationDetail);
		ExamSolverProxy proxy = examinationSolverService.getSolver();
		if (proxy != null && !exam.getExamType().getUniqueId().equals(proxy.getExamTypeId())) proxy = null;
		
		ExaminationScheduleRpcResponse response = new ExaminationScheduleRpcResponse();
		response.setExamType(exam.getExamType().getLabel());

		List<Object[]> exams = new ArrayList<Object[]>();
		exams.addAll(hibSession.createQuery(
				"select o, enrl.courseOffering from ExamOwner o, StudentClassEnrollment enrl inner join enrl.courseOffering co " +
				"where o.ownerType = :type and o.ownerId = co.uniqueId and enrl.student.uniqueId = :studentId and o.exam.examType.uniqueId = :examTypeId")
				.setInteger("type", ExamOwner.sOwnerTypeCourse)
				.setLong("studentId", request.getStudentId())
				.setLong("examTypeId", exam.getExamType().getUniqueId())
				.setCacheable(true).list());
		exams.addAll(hibSession.createQuery(
				"select o, enrl.courseOffering from ExamOwner o, StudentClassEnrollment enrl inner join enrl.courseOffering.instructionalOffering io " +
				"where o.ownerType = :type and o.ownerId = io.uniqueId and enrl.student.uniqueId = :studentId and o.exam.examType.uniqueId = :examTypeId")
				.setInteger("type", ExamOwner.sOwnerTypeOffering)
				.setLong("studentId", request.getStudentId())
				.setLong("examTypeId", exam.getExamType().getUniqueId())
				.setCacheable(true).list());
		exams.addAll(hibSession.createQuery(
				"select o, enrl.courseOffering from ExamOwner o, StudentClassEnrollment enrl inner join enrl.clazz.schedulingSubpart.instrOfferingConfig cfg " +
				"where o.ownerType = :type and o.ownerId = cfg.uniqueId and enrl.student.uniqueId = :studentId and o.exam.examType.uniqueId = :examTypeId")
				.setInteger("type", ExamOwner.sOwnerTypeConfig)
				.setLong("studentId", request.getStudentId())
				.setLong("examTypeId", exam.getExamType().getUniqueId())
				.setCacheable(true).list());
		exams.addAll(hibSession.createQuery(
				"select o, enrl.courseOffering from ExamOwner o, StudentClassEnrollment enrl inner join enrl.clazz c " +
				"where o.ownerType = :type and o.ownerId = c.uniqueId and enrl.student.uniqueId = :studentId and o.exam.examType.uniqueId = :examTypeId")
				.setInteger("type", ExamOwner.sOwnerTypeClass)
				.setLong("studentId", request.getStudentId())
				.setLong("examTypeId", exam.getExamType().getUniqueId())
				.setCacheable(true).list());
		for (Object[] o: exams) {
			ExamOwner owner = (ExamOwner)o[0];
			CourseOffering co = (CourseOffering)o[1];
			Exam x = owner.getExam();
			
			RelatedObjectInterface related = new RelatedObjectInterface();
    		related.setType(RelatedObjectInterface.RelatedObjectType.Examination);
    		related.setUniqueId(x.getUniqueId());
    		related.setName(x.getName() == null ? x.generateName() : x.getName());
    		related.addCourseName(co.getCourseName());
    		related.addCourseTitle(co.getTitle());
    		related.setInstruction(x.getExamType().getLabel());
    		related.setInstructionType(x.getExamType().getType());
    		if (owner.getOwnerType() == ExamOwner.sOwnerTypeClass) {
    			Class_ clazz = (Class_)owner.getOwnerObject();
    			related.setSectionNumber(clazz.getSectionNumberString(hibSession));
	    		related.setInstruction(clazz.getSchedulingSubpart().getItype().getAbbv());
        		if (clazz.getClassSuffix() != null) related.addExternalId(clazz.getClassSuffix());
    		} else if (owner.getOwnerType() == ExamOwner.sOwnerTypeConfig) {
    			InstrOfferingConfig config = (InstrOfferingConfig)owner.getOwnerObject();
				related.setSectionNumber("[" + config.getName() + "]");
				related.setInstruction(MESSAGES.colConfig());
    		} else if (owner.getOwnerType() == ExamOwner.sOwnerTypeCourse) {
    			related.setInstruction(MESSAGES.colCourse());
    		} else if (owner.getOwnerType() == ExamOwner.sOwnerTypeOffering) {
    			related.setInstruction(MESSAGES.colOffering());
    		}
			if (context != null && context.hasPermission(x, Right.ExaminationDetail))
				related.setDetailPage("examDetail.do?examId=" + x.getUniqueId());
    		for (DepartmentalInstructor i: x.getInstructors()) {
				ContactInterface instructor = new ContactInterface();
				instructor.setFirstName(i.getFirstName());
				instructor.setMiddleName(i.getMiddleName());
				instructor.setLastName(i.getLastName());
				instructor.setExternalId(i.getExternalUniqueId());
				instructor.setEmail(i.getEmail());
				related.addInstructor(instructor);
			}
    		
    		ExamAssignmentInfo assignment = null;
    		ExamPeriod period = null;
    		if (proxy != null) {
    			assignment = proxy.getAssignmentInfo(x.getUniqueId());
    			period = (assignment == null ? null : assignment.getPeriod());
    		} else {
    			assignment = new ExamAssignmentInfo(x, false);
    			period = x.getAssignedPeriod();
    		}
    		
			if (period != null) {
    			related.setDate(period.getStartDateLabel());
    			related.setDayOfYear(period.getDateOffset());
    			related.setStartSlot(period.getStartSlot());
    			related.setEndSlot(period.getEndSlot());
    			int printOffset = (x.getPrintOffset() == null ? 0 : x.getPrintOffset());
    			related.setTime(period.getStartTimeLabel(printOffset) + " - " + period.getEndTimeLabel(x.getLength(), printOffset));
    		}
			if (assignment != null && assignment.getRooms() != null) {
				for (ExamRoomInfo r: assignment.getRooms()) {
					ResourceInterface location = new ResourceInterface();
					location.setType(ResourceType.ROOM);
					location.setId(r.getLocationId());
					location.setName(r.getName());
					location.setSize(r.getCapacity());
					location.setRoomType(r.getLocation().getRoomTypeLabel());
					location.setBreakTime(r.getLocation().getEffectiveBreakTime());
					location.setMessage(r.getLocation().getEventMessage());
					related.addLocation(location);
				}
			}
			
			String conflicts = "";

			if (period != null) {
		        int nrTravelSlotsClassEvent = Integer.parseInt(ApplicationProperties.getProperty("tmtbl.exam.eventConflicts.travelTime.classEvent","6"));
		        int nrTravelSlotsCourseEvent = Integer.parseInt(ApplicationProperties.getProperty("tmtbl.exam.eventConflicts.travelTime.courseEvent","0"));

		        TreeSet<Event> events = new TreeSet<Event>();
		        
		        // class events
		        for (int t2 = 0; t2 < ExamOwner.sOwnerTypes.length; t2++) {
		        	events.addAll(hibSession.createQuery(
		        			"select distinct m1.event" +
		        			" from StudentClassEnrollment s1, ClassEvent e1 inner join e1.meetings m1, Exam e2 inner join e2.owners o2, StudentClassEnrollment s2" +
		        			" where e2.uniqueId = :examId and e1.clazz = s1.clazz and s1.student = s2.student and s1.student.uniqueId = :studentId" +
		        			ExaminationEnrollmentsBackend.where(t2, 2) + 
		        			" and m1.meetingDate = :meetingDate and m1.startPeriod < :endSlot and :startSlot < m1.stopPeriod")
		        			.setLong("examId", x.getUniqueId())
		        			.setLong("studentId", request.getStudentId())
		        			.setDate("meetingDate", period.getStartDate())
		        			.setInteger("startSlot", period.getStartSlot() - nrTravelSlotsClassEvent)
		        			.setInteger("endSlot", period.getEndSlot() + nrTravelSlotsClassEvent)
		        			.list());
		        }
		        
		    	// course events
		        for (int t1 = 0; t1 < ExamOwner.sOwnerTypes.length; t1++) {
		            for (int t2 = 0; t2 < ExamOwner.sOwnerTypes.length; t2++) {
		            	events.addAll(hibSession.createQuery(
		            			"select distinct m1.event" +
		            			" from StudentClassEnrollment s1, CourseEvent e1 inner join e1.meetings m1 inner join e1.relatedCourses o1, Exam e2 inner join e2.owners o2, StudentClassEnrollment s2" +
		            			" where e2.uniqueId = :examId and s1.student = s2.student and s1.student.uniqueId = :studentId" +
		            			ExaminationEnrollmentsBackend.where(t1, 1) + ExaminationEnrollmentsBackend.where(t2, 2) +
		            			" and m1.meetingDate = :meetingDate and m1.startPeriod < :endSlot and :startSlot < m1.stopPeriod and e1.reqAttendance = true and m1.approvalStatus = 1")
		            			.setLong("examId", x.getUniqueId())
		        			.setLong("studentId", request.getStudentId())
		            			.setDate("meetingDate", period.getStartDate())
		            			.setInteger("startSlot", period.getStartSlot() - nrTravelSlotsCourseEvent)
		            			.setInteger("endSlot", period.getEndSlot() + nrTravelSlotsCourseEvent)
		            			.list());
		            }
		        }
		        
				for (Event e: events)
					conflicts += (conflicts.isEmpty() ? "" : "<br>") +
							"<span class='dc' title='" + HtmlUtils.htmlEscape(e.getEventTypeAbbv() + " " + e.getEventName()) + "'>" + HtmlUtils.htmlEscape(e.getEventName()) + "</span>";
			}
			
			if (assignment != null) {
				for (DirectConflict conflict: assignment.getDirectConflicts()) {
		    		ExamAssignment other = conflict.getOtherExam();
		    		if (other != null && conflict.getStudents().contains(request.getStudentId())) {
		    			for (ExamSectionInfo section: other.getSections())
		    				if (section.getStudentIds().contains(request.getStudentId()))
		    					conflicts += (conflicts.isEmpty() ? "" : "<br>") +
		    					"<span class='dc' title='" + HtmlUtils.htmlEscape("Direct " + other.getExamName()) + "'>" + HtmlUtils.htmlEscape(section.getSubject() + " " + section.getCourseNbr()) + "</span>";
		    		}
				}
		    	for (BackToBackConflict conflict: assignment.getBackToBackConflicts()) {
		    		ExamAssignment other = conflict.getOtherExam();
		    		if (other != null && conflict.getStudents().contains(request.getStudentId())) {
		    			for (ExamSectionInfo section: other.getSections())
		    				if (section.getStudentIds().contains(request.getStudentId()))
		    					conflicts += (conflicts.isEmpty() ? "" : "<br>") +
		    					"<span class='b2b' title='" + HtmlUtils.htmlEscape("Back-To-Back " + other.getExamName()) + "'>" + HtmlUtils.htmlEscape(section.getSubject() + " " + section.getCourseNbr()) + "</span>";
		    		}
		    	}
		    	for (MoreThanTwoADayConflict conflict: assignment.getMoreThanTwoADaysConflicts()) {
		    		if (!conflict.getStudents().contains(request.getStudentId())) continue;

		    		String name = "", first = "", next = "";
		    		for (ExamAssignment other: conflict.getOtherExams()) {
		    			name += (name.isEmpty() ? "" : ", ") + other.getExamName();
		    			for (ExamSectionInfo section: other.getSections())
		    				if (section.getStudentIds().contains(request.getStudentId())) {
		    					String course = section.getSubject() + " " + section.getCourseNbr();
		    					if (first.isEmpty() || course.compareTo(first) < 0) first = course;
		    					if (owner.getCourse().getCourseName().compareTo(course) < 0 && (next.isEmpty() || course.compareTo(next) < 0)) next = course;
		    				}
		    		}
		    		
		    		conflicts += (conflicts.isEmpty() ? "" : "<br>") +
		    				"<span class='m2d' title='" + HtmlUtils.htmlEscape(">2 A Day " + name) + "'>" + HtmlUtils.htmlEscape(next.isEmpty() ? first : next) + "</span>";
		    	}
			}
			
			if (!conflicts.isEmpty())
				related.setConflicts(conflicts);
			
    		response.addExam(related);
		}
		
		return response;
	}

}
