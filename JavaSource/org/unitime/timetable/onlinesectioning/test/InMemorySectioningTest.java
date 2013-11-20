/*
 * UniTime 3.2 - 3.5 (University Timetabling Application)
 * Copyright (C) 2011 - 2013, UniTime LLC, and individual contributors
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
package org.unitime.timetable.onlinesectioning.test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XReservationType;
import org.unitime.timetable.onlinesectioning.reports.OnlineSectioningReport.Counter;
import org.unitime.timetable.onlinesectioning.solver.OnlineSectioningSelection;
import org.unitime.timetable.onlinesectioning.solver.StudentSchedulingAssistantWeights;
import org.unitime.timetable.onlinesectioning.solver.SuggestionSelection;
import org.unitime.timetable.onlinesectioning.solver.SuggestionsBranchAndBound;
import org.unitime.timetable.onlinesectioning.solver.multicriteria.MultiCriteriaBranchAndBoundSelection;
import org.unitime.timetable.onlinesectioning.solver.multicriteria.MultiCriteriaBranchAndBoundSuggestions;

import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.DistanceMetric;
import net.sf.cpsolver.ifs.util.JProf;
import net.sf.cpsolver.ifs.util.ToolBox;
import net.sf.cpsolver.studentsct.StudentPreferencePenalties;
import net.sf.cpsolver.studentsct.StudentSectioningModel;
import net.sf.cpsolver.studentsct.StudentSectioningXMLLoader;
import net.sf.cpsolver.studentsct.StudentSectioningXMLSaver;
import net.sf.cpsolver.studentsct.constraint.LinkedSections;
import net.sf.cpsolver.studentsct.extension.DistanceConflict;
import net.sf.cpsolver.studentsct.extension.TimeOverlapsCounter;
import net.sf.cpsolver.studentsct.heuristics.selection.BranchBoundSelection.BranchBoundNeighbour;
import net.sf.cpsolver.studentsct.model.Config;
import net.sf.cpsolver.studentsct.model.Course;
import net.sf.cpsolver.studentsct.model.CourseRequest;
import net.sf.cpsolver.studentsct.model.Enrollment;
import net.sf.cpsolver.studentsct.model.FreeTimeRequest;
import net.sf.cpsolver.studentsct.model.Offering;
import net.sf.cpsolver.studentsct.model.Request;
import net.sf.cpsolver.studentsct.model.Section;
import net.sf.cpsolver.studentsct.model.Student;
import net.sf.cpsolver.studentsct.model.Subpart;
import net.sf.cpsolver.studentsct.reservation.CourseReservation;
import net.sf.cpsolver.studentsct.reservation.Reservation;

/**
 * @author Tomas Muller
 */
public class InMemorySectioningTest {
	public static Logger sLog = Logger.getLogger(InMemorySectioningTest.class);
	
	private StudentSectioningModel iModel;
	private boolean iSuggestions = false;
	
	private Map<String, Counter> iCounters = new HashMap<String, Counter>();
	
	public InMemorySectioningTest(DataProperties config) {
		iModel = new TestModel(config);
		iModel.setDistanceConflict(new DistanceConflict(new DistanceMetric(iModel.getProperties()), iModel.getProperties()));
		iModel.addModelListener(iModel.getDistanceConflict());
		iModel.setTimeOverlaps(new TimeOverlapsCounter(null, iModel.getProperties()));
		iModel.addModelListener(iModel.getTimeOverlaps());
		iModel.setStudentWeights(new StudentSchedulingAssistantWeights(iModel.getProperties()));
		iSuggestions = "true".equals(System.getProperty("suggestions", iSuggestions ? "true" : "false"));
		
		sLog.info("Using " + (config.getPropertyBoolean("StudentWeights.MultiCriteria", true) ? "multi-criteria ": "") +
				(config.getPropertyBoolean("StudentWeights.PriorityWeighting", true) ? "priority" : "equal") + " weighting model" +
				" with " + config.getPropertyInt("Neighbour.BranchAndBoundTimeout", 1000) +" ms time limit.");

	}
	
	public StudentSectioningModel model() { return iModel; }
	
	public void inc(String name, double value) {
		synchronized (iCounters) {
			Counter c = iCounters.get(name);
			if (c == null) {
				c = new Counter();
				iCounters.put(name, c);
			}
			c.inc(value);
		}
	}
	
	public void inc(String name) {
		inc(name, 1.0);
	}
	
	protected Course clone(Course course, long studentId, Student originalStudent, Map<Long, Section> classTable) {
		Offering clonedOffering = new Offering(course.getOffering().getId(), course.getOffering().getName());
		int courseLimit = course.getLimit();
		if (courseLimit >= 0) {
			courseLimit -= course.getEnrollments().size();
			if (courseLimit < 0) courseLimit = 0;
			for (Iterator<Enrollment> i = course.getEnrollments().iterator(); i.hasNext();) {
				Enrollment enrollment = i.next();
				if (enrollment.getStudent().getId() == studentId) { courseLimit++; break; }
			}
		}
		Course clonedCourse = new Course(course.getId(), course.getSubjectArea(), course.getCourseNumber(), clonedOffering, courseLimit, course.getProjected());
		clonedCourse.setNote(course.getNote());
		Hashtable<Config, Config> configs = new Hashtable<Config, Config>();
		Hashtable<Subpart, Subpart> subparts = new Hashtable<Subpart, Subpart>();
		Hashtable<Section, Section> sections = new Hashtable<Section, Section>();
		for (Iterator<Config> e = course.getOffering().getConfigs().iterator(); e.hasNext();) {
			Config config = e.next();
			int configLimit = config.getLimit();
			if (configLimit >= 0) {
				configLimit -= config.getEnrollments().size();
				if (configLimit < 0) configLimit = 0;
				for (Iterator<Enrollment> i = config.getEnrollments().iterator(); i.hasNext();) {
					Enrollment enrollment = i.next();
					if (enrollment.getStudent().getId() == studentId) { configLimit++; break; }
				}
			}
			Config clonedConfig = new Config(config.getId(), configLimit, config.getName(), clonedOffering);
			configs.put(config, clonedConfig);
			for (Iterator<Subpart> f = config.getSubparts().iterator(); f.hasNext();) {
				Subpart subpart = f.next();
				Subpart clonedSubpart = new Subpart(subpart.getId(), subpart.getInstructionalType(), subpart.getName(), clonedConfig,
						(subpart.getParent() == null ? null: subparts.get(subpart.getParent())));
				clonedSubpart.setAllowOverlap(subpart.isAllowOverlap());
				clonedSubpart.setCredit(subpart.getCredit());
				subparts.put(subpart, clonedSubpart);
				for (Iterator<Section> g = subpart.getSections().iterator(); g.hasNext();) {
					Section section = g.next();
					int limit = section.getLimit();
					if (limit >= 0) {
						// limited section, deduct enrollments
						limit -= section.getEnrollments().size();
						if (limit < 0) limit = 0; // over-enrolled, but not unlimited
						if (studentId >= 0)
							for (Enrollment enrollment: section.getEnrollments())
								if (enrollment.getStudent().getId() == studentId) { limit++; break; }
					}
					Section clonedSection = new Section(section.getId(), limit,
							section.getName(course.getId()), clonedSubpart, section.getPlacement(),
							section.getChoice().getInstructorIds(), section.getChoice().getInstructorNames(),
							(section.getParent() == null ? null : sections.get(section.getParent())));
					clonedSection.setName(-1l, section.getName(-1l));
					clonedSection.setNote(section.getNote());
					clonedSection.setSpaceExpected(section.getSpaceExpected());
					clonedSection.setSpaceHeld(section.getSpaceHeld());
			        if (section.getIgnoreConflictWithSectionIds() != null)
			        	for (Long id: section.getIgnoreConflictWithSectionIds())
			        		clonedSection.addIgnoreConflictWith(id);
			        if (limit > 0) {
			        	double available = Math.round(section.getSpaceExpected() - limit);
						clonedSection.setPenalty(available / section.getLimit());
			        }
					sections.put(section, clonedSection);
					classTable.put(section.getId(), clonedSection);
				}
			}
		}
		if (course.getOffering().hasReservations()) {
			for (Reservation reservation: course.getOffering().getReservations()) {
				int reservationLimit = (int)Math.round(reservation.getLimit());
				if (reservationLimit >= 0) {
					reservationLimit -= reservation.getEnrollments().size();
					if (reservationLimit < 0) reservationLimit = 0;
					for (Iterator<Enrollment> i = reservation.getEnrollments().iterator(); i.hasNext();) {
						Enrollment enrollment = i.next();
						if (enrollment.getStudent().getId() == studentId) { reservationLimit++; break; }
					}
					if (reservationLimit <= 0) continue;
				}
				boolean applicable = originalStudent != null && reservation.isApplicable(originalStudent);
				if (reservation instanceof CourseReservation)
					applicable = (course.getId() == ((CourseReservation)reservation).getCourse().getId());
				if (reservation instanceof net.sf.cpsolver.studentsct.reservation.DummyReservation) {
					// Ignore by reservation only flag (dummy reservation) when the student is already enrolled in the course
					for (Enrollment enrollment: course.getEnrollments())
						if (enrollment.getStudent().getId() == studentId) { applicable = true; break; }
				}
				Reservation clonedReservation = new XOffering.SimpleReservation(XReservationType.Dummy, reservation.getId(), clonedOffering,
						reservation.getPriority(), reservation.canAssignOverLimit(), reservationLimit, 
						applicable, reservation.mustBeUsed(), reservation.isAllowOverlap(), reservation.isExpired());
				for (Config config: reservation.getConfigs())
					clonedReservation.addConfig(configs.get(config));
				for (Map.Entry<Subpart, Set<Section>> entry: reservation.getSections().entrySet()) {
					Set<Section> clonedSections = new HashSet<Section>();
					for (Section section: entry.getValue())
						clonedSections.add(sections.get(section));
					clonedReservation.getSections().put(
							subparts.get(entry.getKey()),
							clonedSections);
				}
			}
		}
		return clonedCourse;
	}
	
	protected Request addRequest(Student student, Student original, Request request, Map<Long, Section> classTable) {
		if (request instanceof FreeTimeRequest) {
			return new FreeTimeRequest(student.getRequests().size() + 1, student.getRequests().size(), request.isAlternative(), student, ((FreeTimeRequest) request).getTime());
		} else if (request instanceof CourseRequest) {
			List<Course> courses = new ArrayList<Course>();
			for (Course course: ((CourseRequest) request).getCourses())
				courses.add(clone(course, student.getId(), original, classTable));
			CourseRequest clonnedRequest = new CourseRequest(student.getRequests().size() + 1, student.getRequests().size(), request.isAlternative(), student, courses, ((CourseRequest) request).isWaitlist(), null);
			for (Request originalRequest: original.getRequests()) {
				Enrollment originalEnrollment = originalRequest.getAssignment();
				for (Course clonnedCourse: clonnedRequest.getCourses()) {
					if (!clonnedCourse.getOffering().hasReservations()) continue;
					if (originalEnrollment != null && clonnedCourse.equals(originalEnrollment.getCourse())) {
						boolean needReservation = clonnedCourse.getOffering().getUnreservedSpace(clonnedRequest) < 1.0;
						if (!needReservation) {
							boolean configChecked = false;
							for (Section originalSection: originalEnrollment.getSections()) {
								Section clonnedSection = classTable.get(originalSection.getId()); 
								if (clonnedSection.getUnreservedSpace(clonnedRequest) < 1.0) { needReservation = true; break; }
								if (!configChecked && clonnedSection.getSubpart().getConfig().getUnreservedSpace(clonnedRequest) < 1.0) { needReservation = true; break; }
								configChecked = true;
							}
						}
						if (needReservation) {
							Reservation reservation = new XOffering.SimpleReservation(XReservationType.Dummy, -original.getId(), clonnedCourse.getOffering(), 5, false, 1, true, false, false, true);
							for (Section originalSection: originalEnrollment.getSections())
								reservation.addSection(classTable.get(originalSection.getId()));
						}
						break;
					}
				}
			}	
			return clonnedRequest;
		} else {
			return null;
		}
	}
	
	public boolean section(Student original) {
		StudentSectioningModel model = new TestModel(iModel.getProperties());
		Student student = new Student(original.getId());
		Hashtable<CourseRequest, Set<Section>> preferredSectionsForCourse = new Hashtable<CourseRequest, Set<Section>>();
		Map<Long, Section> classTable = new HashMap<Long, Section>();
		
		synchronized (iModel) {
			for (Request request: original.getRequests()) {
				Request clonnedRequest = addRequest(student, original, request, classTable);
				if (request.getAssignment() != null && request.getAssignment().isCourseRequest()) {
					Set<Section> sections = new HashSet<Section>();
					for (Section section: request.getAssignment().getSections())
						sections.add(classTable.get(section.getId()));
					preferredSectionsForCourse.put((CourseRequest)clonnedRequest, sections);
				}
			}
		}
		
		model.addStudent(student);
		model.setDistanceConflict(new DistanceConflict(iModel.getDistanceConflict().getDistanceMetric(), model.getProperties()));
		model.setTimeOverlaps(new TimeOverlapsCounter(null, model.getProperties()));
		for (LinkedSections link: iModel.getLinkedSections()) {
			List<Section> sections = new ArrayList<Section>();
			for (Offering offering: link.getOfferings())
				for (Subpart subpart: link.getSubparts(offering))
					for (Section section: link.getSections(subpart)) {
						Section x = classTable.get(section.getId());
						if (x != null) sections.add(x);
					}
			if (sections.size() >= 2)
				model.addLinkedSections(sections);
		}
		OnlineSectioningSelection selection = null;
		if (model.getProperties().getPropertyBoolean("StudentWeights.MultiCriteria", true)) {
			selection = new MultiCriteriaBranchAndBoundSelection(iModel.getProperties());
		} else {
			selection = new SuggestionSelection(model.getProperties());
		}
		
		selection.setModel(model);
		selection.setPreferredSections(preferredSectionsForCourse);
		selection.setRequiredSections(new Hashtable<CourseRequest, Set<Section>>());
		selection.setRequiredFreeTimes(new HashSet<FreeTimeRequest>());
		
		long t0 = JProf.currentTimeMillis();
		BranchBoundNeighbour neighbour = selection.select(student);
		long time = JProf.currentTimeMillis() - t0;
		inc("[C] CPU Time", time);
		if (neighbour == null) {
			inc("[F] Failure");
		} else {
			if (iSuggestions) {
				StudentPreferencePenalties penalties = new StudentPreferencePenalties(StudentPreferencePenalties.sDistTypePreference);
				int maxOverExpected = 0;
				int assigned = 0;
				double penalty = 0.0;
				Hashtable<CourseRequest, Set<Section>> enrollments = new Hashtable<CourseRequest, Set<Section>>();
				List<RequestSectionPair> pairs = new ArrayList<InMemorySectioningTest.RequestSectionPair>();
				
				for (int i = 0; i < neighbour.getAssignment().length; i++) {
					Enrollment enrl = neighbour.getAssignment()[i];
					if (enrl != null && enrl.isCourseRequest() && enrl.getAssignments() != null) {
						assigned ++;
						for (Section section: enrl.getSections()) {
							if (section.getPenalty() >= 0) maxOverExpected ++;
							pairs.add(new RequestSectionPair(enrl.variable(), section));
						}
						enrollments.put((CourseRequest) enrl.variable(), enrl.getSections());
						penalty += penalties.getPenalty(enrl);
					}
				}
				penalty /= assigned;
				inc("[S] Initial Penalty", penalty);
				for (int i = 0; i < pairs.size(); i++) {
					RequestSectionPair pair = pairs.get(i);
					SuggestionsBranchAndBound suggestionBaB = null;
					if (model.getProperties().getPropertyBoolean("StudentWeights.MultiCriteria", true)) {
						suggestionBaB = new MultiCriteriaBranchAndBoundSuggestions(
								model.getProperties(), student,
								new Hashtable<CourseRequest, Set<Section>>(), new HashSet<FreeTimeRequest>(), enrollments,
								pair.getRequest(), pair.getSection(),
								null, null, maxOverExpected,
								iModel.getProperties().getPropertyBoolean("StudentWeights.PriorityWeighting", true));
					} else {
						suggestionBaB = new SuggestionsBranchAndBound(model.getProperties(), student,
								new Hashtable<CourseRequest, Set<Section>>(), new HashSet<FreeTimeRequest>(), enrollments,
								pair.getRequest(), pair.getSection(),
								null, null, maxOverExpected);
					}
					
					long x0 = JProf.currentTimeMillis();
					TreeSet<SuggestionsBranchAndBound.Suggestion> suggestions = suggestionBaB.computeSuggestions();
					inc("[S] Suggestion CPU Time", JProf.currentTimeMillis() - x0);

					SuggestionsBranchAndBound.Suggestion best = null;
					for (SuggestionsBranchAndBound.Suggestion suggestion: suggestions) {
						int a = 0;
						double p = 0.0;
						for (int j = 0; j < suggestion.getEnrollments().length; j++) {
							Enrollment e = suggestion.getEnrollments()[j];
							if (e != null && e.isCourseRequest() && e.getAssignments() != null) {
								p += penalties.getPenalty(e);
								a ++;
							}
						}
						p /= a;
						if (a > assigned || (assigned == a && p < penalty)) {
							best = suggestion;
						}
					}
					if (best != null) {
						Enrollment[] e = best.getEnrollments();
						for (int j = 0; j < e.length; j++)
							if (e[j] != null && e[j].getAssignments() == null) e[j] = null;
						neighbour = new BranchBoundNeighbour(student, best.getValue(), e);
						assigned = 0; penalty = 0.0;
						enrollments.clear(); pairs.clear();
						for (int j = 0; j < neighbour.getAssignment().length; j++) {
							Enrollment enrl = neighbour.getAssignment()[j];
							if (enrl != null && enrl.isCourseRequest() && enrl.getAssignments() != null) {
								assigned ++;
								for (Section section: enrl.getSections())
									pairs.add(new RequestSectionPair(enrl.variable(), section));
								enrollments.put((CourseRequest) enrl.variable(), enrl.getSections());
								penalty += penalties.getPenalty(enrl);
							}
						}
						penalty /= assigned;
						inc("[S] Improved Penalty", penalty);
					}
				}
				inc("[S] Final Penalty", penalty);
			}
			
			List<Enrollment> enrollments = new ArrayList<Enrollment>();
			i: for (int i = 0; i < neighbour.getAssignment().length; i++) {
				Request request = original.getRequests().get(i);
				Enrollment clonnedEnrollment = neighbour.getAssignment()[i];
				if (clonnedEnrollment != null && clonnedEnrollment.getAssignments() != null) {
					if (request instanceof FreeTimeRequest) {
						enrollments.add(((FreeTimeRequest)request).createEnrollment());
					} else {
						for (Course course: ((CourseRequest)request).getCourses())
							if (course.getId() == clonnedEnrollment.getCourse().getId())
								for (Config config: course.getOffering().getConfigs())
									if (config.getId() == clonnedEnrollment.getConfig().getId()) {
										Set<Section> assignments = new HashSet<Section>();
										for (Subpart subpart: config.getSubparts())
											for (Section section: subpart.getSections()) {
												if (clonnedEnrollment.getSections().contains(section)) {
													assignments.add(section);
												}
											}
										Reservation reservation = null;
										if (clonnedEnrollment.getReservation() != null) {
											for (Reservation r: course.getOffering().getReservations())
												if (r.getId() == clonnedEnrollment.getReservation().getId()) { reservation = r; break; }
										}
										enrollments.add(new Enrollment(request, clonnedEnrollment.getPriority(), course, config, assignments, reservation));
										continue i;
									}
					}
				}
			}
			synchronized (iModel) {
				for (Request r: original.getRequests()) {
                	r.setInitialAssignment(r.getAssignment());
                	if (r.getAssignment() != null) updateSpace(r.getAssignment(), true);
				}
				for (Request r: original.getRequests())
					if (r.getAssignment() != null) r.unassign(0);
				boolean fail = false;
				for (Enrollment enrl: enrollments) {
					if (iModel.conflictValues(enrl).isEmpty()) {
						enrl.variable().assign(0, enrl);
					} else {
						fail = true; break;
					}
				}
				if (fail) {
					for (Request r: original.getRequests())
						if (r.getAssignment() != null) updateSpace(r.getAssignment(), true);
					for (Request r: original.getRequests())
						if (r.getAssignment() != null) r.unassign(0);
					for (Request r: original.getRequests())
						if (r.getInitialAssignment() != null) r.assign(0, r.getInitialAssignment());
					for (Request r: original.getRequests())
						if (r.getAssignment() != null) updateSpace(r.getAssignment(), false);
				} else {
					for (Enrollment enrl: enrollments)
						updateSpace(enrl, false);
				}
				if (fail) return false;
			}
			neighbour.assign(0);
			int a = 0, u = 0, np = 0, zp = 0, pp = 0, cp = 0;
			double p = 0.0;
			for (Request r: student.getRequests()) {
				if (r instanceof CourseRequest) {
					if (r.getAssignment() != null) {
						for (Section s: r.getAssignment().getSections()) {
							if (s.getPenalty() < 0.0) np ++;
							if (s.getPenalty() == 0.0) zp ++;
							if (s.getPenalty() > 0.0) pp++;
							if (s.getLimit() > 0) {
								p += s.getPenalty(); cp ++;
							}
						}
						a++;
					} else {
						u++;
					}
				}
			}
			inc("[S] Student");
			if (a > 0)
				inc("[A] Assigned", a);
			if (u > 0)
				inc("[A] Not Assigned", u);
			inc("[V] Value", neighbour.value());
			if (zp > 0)
				inc("[P] Zero penalty", zp);
			if (np > 0)
				inc("[P] Negative penalty", np);
			if (pp > 0)
				inc("[P] Positive penalty", pp);
			if (cp > 0)
				inc("[P] Average penalty", p / cp);
		}
		inc("[T0] Time <10ms", time < 10 ? 1 : 0);
		inc("[T1] Time <100ms", time < 100 ? 1 : 0);
		inc("[T2] Time <250ms", time < 250 ? 1 : 0);
		inc("[T3] Time <500ms", time < 500 ? 1 : 0);
		inc("[T4] Time <1s", time < 1000 ? 1 : 0);
		inc("[T5] Time >=1s", time >= 1000 ? 1 : 0);
		return true;
	}
	
	public static void updateSpace(Enrollment enrollment, boolean increment) {
    	if (enrollment == null || !enrollment.isCourseRequest()) return;
        for (Section section : enrollment.getSections())
            section.setSpaceHeld(section.getSpaceHeld() + (increment ? 1.0 : -1.0));
        List<Enrollment> feasibleEnrollments = new ArrayList<Enrollment>();
        for (Enrollment enrl : enrollment.getRequest().values()) {
        	if (!enrl.getCourse().equals(enrollment.getCourse())) continue;
            boolean overlaps = false;
            for (Request otherRequest : enrollment.getRequest().getStudent().getRequests()) {
                if (otherRequest.equals(enrollment.getRequest()) || !(otherRequest instanceof CourseRequest))
                    continue;
                Enrollment otherErollment = otherRequest.getAssignment();
                if (otherErollment == null)
                    continue;
                if (enrl.isOverlapping(otherErollment)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps)
                feasibleEnrollments.add(enrl);
        }
        double change = 1.0 / feasibleEnrollments.size();
        for (Enrollment feasibleEnrollment : feasibleEnrollments)
            for (Section section : feasibleEnrollment.getSections())
                section.setSpaceExpected(section.getSpaceExpected() + (increment ? +change : -change));
    }
	
	public void run() {
        sLog.info("Input: " + ToolBox.dict2string(model().getExtendedInfo(), 2));

        List<Student> students = new ArrayList<Student>(model().getStudents());
        Collections.shuffle(students);
        
        Iterator<Student> iterator = students.iterator();
        int nrThreads = Integer.parseInt(System.getProperty("nrConcurrent", "10"));
        List<Executor> executors = new ArrayList<InMemorySectioningTest.Executor>();
        for (int i = 0; i < nrThreads; i++) {
        	Executor executor = new Executor(iterator);
        	executor.start();
        	executors.add(executor);
        }

        long t0 = System.currentTimeMillis();
        while (iterator.hasNext()) {
        	try {
        		Thread.sleep(60000);
        	} catch (InterruptedException e) {}
        	long time = System.currentTimeMillis() - t0;
        	synchronized (iModel) {
        		sLog.info("Progress [" + (time / 60000) + "m]: " + ToolBox.dict2string(iModel.getExtendedInfo(), 2));	
			}
        }
        
        for (Executor executor: executors) {
        	try {
        		executor.join();
        	} catch (InterruptedException e) {}
        }
        
        sLog.info("Output: " + ToolBox.dict2string(model().getExtendedInfo(), 2));
	}
	
    public class Executor extends Thread {
		private Iterator<Student> iStudents = null;
		
		public Executor(Iterator<Student> students) {
			iStudents = students;
		}
		
		@Override
		public void run() {
			try {
				for (;;) {
					Student student = iStudents.next();
					int attempt = 1;
					while (!section(student)) {
						sLog.warn(attempt + ". attempt failed for " + student.getId());
						inc("[F] Failed attempt", attempt);
						attempt ++;
						if (attempt == 11) break;			
					}
					if (attempt > 10)
						inc("[F] Failed enrollment (all 10 attempts)");
				}
			} catch (NoSuchElementException e) {}
		}
		
	}
	
	public class TestModel extends StudentSectioningModel {
		public TestModel(DataProperties config) {
			super(config);
		}

		@Override
		public Map<String,String> getExtendedInfo() {
			Map<String, String> ret = super.getExtendedInfo();
			for (Map.Entry<String, Counter> e: iCounters.entrySet())
				ret.put(e.getKey(), e.getValue().toString());
			ret.put("Weighting model",
					(model().getProperties().getPropertyBoolean("StudentWeights.MultiCriteria", true) ? "multi-criteria ": "") +
					(model().getProperties().getPropertyBoolean("StudentWeights.PriorityWeighting", true) ? "priority" : "equal"));
			ret.put("B&B time limit", model().getProperties().getPropertyInt("Neighbour.BranchAndBoundTimeout", 1000) +" ms");
			if (iSuggestions) {
				ret.put("Suggestion time limit", model().getProperties().getPropertyInt("Suggestions.Timeout", 1000) +" ms");
			}
			return ret;
		}
	}
	
	public static class RequestSectionPair {
		private Request iRequest;
		private Section iSection;
		RequestSectionPair (Request request, Section section) {
			iRequest = request; iSection = section;
		}
		public Request getRequest() { return iRequest; }
		public Section getSection() { return iSection; }
	}
	
	public static void main(String[] args) {
		try {
			// System.setProperty("jprof", "cpu");
			BasicConfigurator.configure();
			ToolBox.setSeed(Long.valueOf(System.getProperty("seed", "13031978")));
			
            DataProperties cfg = new DataProperties();
			cfg.setProperty("Neighbour.BranchAndBoundTimeout", "5000");
			cfg.setProperty("Suggestions.Timeout", "1000");
			cfg.setProperty("Extensions.Classes", DistanceConflict.class.getName() + ";" + TimeOverlapsCounter.class.getName());
			cfg.setProperty("StudentWeights.Class",  StudentSchedulingAssistantWeights.class.getName());
			cfg.setProperty("StudentWeights.PriorityWeighting", "true");
			cfg.setProperty("StudentWeights.LeftoverSpread", "true");
			cfg.setProperty("StudentWeights.BalancingFactor", "0.0");
			cfg.setProperty("Reservation.CanAssignOverTheLimit", "true");
			cfg.setProperty("Distances.Ellipsoid", DistanceMetric.Ellipsoid.WGS84.name());
			cfg.setProperty("StudentWeights.MultiCriteria", "true");
			cfg.setProperty("CourseRequest.SameTimePrecise", "true");
			
            cfg.setProperty("log4j.rootLogger", "INFO, A1");
            cfg.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
            cfg.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
            cfg.setProperty("log4j.appender.A1.layout.ConversionPattern","%-5p %c{2}: %m%n");
            cfg.setProperty("log4j.logger.org.hibernate","INFO");
            cfg.setProperty("log4j.logger.org.hibernate.cfg","WARN");
            cfg.setProperty("log4j.logger.org.hibernate.cache.EhCacheProvider","ERROR");
            cfg.setProperty("log4j.logger.org.unitime.commons.hibernate","INFO");
            cfg.setProperty("log4j.logger.net","INFO");
            
            cfg.putAll(System.getProperties());

            PropertyConfigurator.configure(cfg);

            InMemorySectioningTest test = new InMemorySectioningTest(cfg);
            
            File input = new File(args[0]);
            StudentSectioningXMLLoader loader = new StudentSectioningXMLLoader(test.model());
            loader.setInputFile(input);
            loader.load();
            
            test.run();
            
            Solver<Request, Enrollment> s = new Solver<Request, Enrollment>(cfg);
            s.setInitalSolution(test.model());
            StudentSectioningXMLSaver saver = new StudentSectioningXMLSaver(s);
            File output = new File(input.getParentFile(), input.getName().substring(0, input.getName().lastIndexOf('.')) + "-" + cfg.getProperty("run", "r0") + ".xml");
            saver.save(output);
		} catch (Exception e) {
			sLog.error("Test failed: " + e.getMessage(), e);
		}
	}
}
