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
package org.unitime.timetable.onlinesectioning.custom.purdue;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.cpsolver.ifs.util.ToolBox;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.restlet.Client;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.ClientResource;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.gwt.shared.OnlineSectioningInterface.EligibilityCheck;
import org.unitime.timetable.gwt.shared.OnlineSectioningInterface.EligibilityCheck.EligibilityFlag;
import org.unitime.timetable.gwt.shared.SectioningException;
import org.unitime.timetable.onlinesectioning.AcademicSessionInfo;
import org.unitime.timetable.onlinesectioning.OnlineSectioningHelper;
import org.unitime.timetable.onlinesectioning.OnlineSectioningLog;
import org.unitime.timetable.onlinesectioning.OnlineSectioningServer;
import org.unitime.timetable.onlinesectioning.custom.StudentEnrollmentProvider;
import org.unitime.timetable.onlinesectioning.model.XCourse;
import org.unitime.timetable.onlinesectioning.model.XCourseRequest;
import org.unitime.timetable.onlinesectioning.model.XEnrollment;
import org.unitime.timetable.onlinesectioning.model.XOffering;
import org.unitime.timetable.onlinesectioning.model.XRequest;
import org.unitime.timetable.onlinesectioning.model.XSection;
import org.unitime.timetable.onlinesectioning.model.XStudent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonReader;

public class XEStudentEnrollment implements StudentEnrollmentProvider {
	private static Logger sLog = Logger.getLogger(XEStudentEnrollment.class);
	
	private String iBannerApiUrl = ApplicationProperties.getProperty("banner.xe.site");
	private String iBannerApiUser = ApplicationProperties.getProperty("banner.xe.user");
	private String iBannerApiPassword = ApplicationProperties.getProperty("banner.xe.password");
	
	private Client iClient;
	
	public XEStudentEnrollment() {
		List<Protocol> protocols = new ArrayList<Protocol>();
		protocols.add(Protocol.HTTP);
		protocols.add(Protocol.HTTPS);
		iClient = new Client(protocols);
	}
	
	private String getBannerTerm(AcademicSessionInfo session) {
		if (session.getTerm().toLowerCase().startsWith("spr")) return session.getYear() + "20";
		if (session.getTerm().toLowerCase().startsWith("sum")) return session.getYear() + "30";
		if (session.getTerm().toLowerCase().startsWith("fal"))
			return String.valueOf(Integer.parseInt(session.getYear()) + 1) + "10";
		return session.getYear() + session.getTerm().toLowerCase();
	}
	
	private Gson getGson() {
		return new GsonBuilder()
			.registerTypeAdapter(DateTime.class, new JsonSerializer<DateTime>() {
				@Override
				public JsonElement serialize(DateTime src, Type typeOfSrc, JsonSerializationContext context) {
					return new JsonPrimitive(src.toString("yyyy-MM-dd'T'HH:mm:ss'Z'"));
				}
			})
			.registerTypeAdapter(DateTime.class, new JsonDeserializer<DateTime>() {
				@Override
				public DateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
					return new DateTime(json.getAsJsonPrimitive().getAsString(), DateTimeZone.UTC);
				}
			})
			// .setPrettyPrinting()
			.create();
	}
	
	@Override
	public void checkEligibility(OnlineSectioningServer server, OnlineSectioningHelper helper, EligibilityCheck check, XStudent student) throws SectioningException {
		// Cannot enroll -> no additional check is needed
		if (!check.hasFlag(EligibilityFlag.CAN_ENROLL)) return;

		ClientResource resource = null;
		try {
			String pin = (helper.getUser().hasExtension(OnlineSectioningLog.UserEntity.pin) ? helper.getUser().getExtension(OnlineSectioningLog.UserEntity.pin) : null);
			AcademicSessionInfo session = server.getAcademicSession();
			helper.debug("Checking eligility for " + student.getName() + " (term: " + getBannerTerm(session) + ", id:" + student.getExternalId() + ", pin:" + pin + ")");
			
			// First, check student registration status
			resource = new ClientResource(iBannerApiUrl);
			resource.setNext(iClient);
			resource.addQueryParameter("term", getBannerTerm(session));
			resource.addQueryParameter("bannerId", student.getExternalId());
			helper.getAction().addOptionBuilder().setKey("term").setValue(getBannerTerm(session));
			if (pin != null && !pin.isEmpty()) {
				resource.addQueryParameter("altPin", pin);
				helper.getAction().addOptionBuilder().setKey("pin").setValue(pin);
			}
			resource.setChallengeResponse(ChallengeScheme.HTTP_BASIC, iBannerApiUser, iBannerApiPassword);
			
			resource.get(MediaType.APPLICATION_JSON);
			
			// Check status, memorize enrolled sections
			Gson gson = getGson();
			List<XEInterface.RegisterResponse> current = gson.fromJson(new JsonReader(resource.getResponseEntity().getReader()), XEInterface.RegisterResponse.TYPE);
			helper.getAction().addOptionBuilder().setKey("response").setValue(gson.toJson(current));
			helper.debug("Current registration: " + gson.toJson(current));
			if (current == null || current.isEmpty() || !current.get(0).validStudent) {
				String reason = null;
				if (current != null && current.size() > 0 && current.get(0).failureReasons != null) {
					for (String m: current.get(0).failureReasons) {
						if ("Your PIN is invalid.".equals(m)) {
							check.setFlag(EligibilityFlag.PIN_REQUIRED, true);
							if (pin == null || pin.isEmpty()) return;
						}
						if (reason == null)
							reason = m;
						else
							reason += "<br>" + m;
					}
				}
				check.setMessage(reason == null ? "Failed to check student registration eligility." : reason);
				check.setFlag(EligibilityFlag.CAN_ENROLL, false);
			} else {
				// Check enrollments
				OnlineSectioningLog.Enrollment.Builder stored = OnlineSectioningLog.Enrollment.newBuilder();
				stored.setType(OnlineSectioningLog.Enrollment.EnrollmentType.STORED);
				Set<String> sectionExternalIds = new HashSet<String>();
				for (XRequest request: student.getRequests()) {
					helper.getAction().addRequest(OnlineSectioningHelper.toProto(request));
					if (request instanceof XCourseRequest) {
						XCourseRequest r = (XCourseRequest)request;
						XEnrollment e = r.getEnrollment();
						if (e == null) continue;
						XOffering offering = server.getOffering(e.getOfferingId());
						for (XSection section: offering.getSections(e)) {
							stored.addSection(OnlineSectioningHelper.toProto(section, e));
							String extId = section.getExternalId(e.getCourseId());
							if (extId != null)
								sectionExternalIds.add(extId);
						}
					}
				}
				helper.getAction().addEnrollment(stored);
				OnlineSectioningLog.Enrollment.Builder external = OnlineSectioningLog.Enrollment.newBuilder();
				external.setType(OnlineSectioningLog.Enrollment.EnrollmentType.EXTERNAL);
				String added = "";
				if (current.get(0).registrations != null)
					for (XEInterface.Registration reg: current.get(0).registrations) {
						if ("R".equals(reg.statusIndicator)) {
							if (!sectionExternalIds.remove(reg.courseReferenceNumber))
								added += (added.isEmpty() ? "" : ", ") + reg.courseReferenceNumber;
							OnlineSectioningLog.Section.Builder section = external.addSectionBuilder()
								.setClazz(OnlineSectioningLog.Entity.newBuilder().setName(reg.courseReferenceNumber))
								.setCourse(OnlineSectioningLog.Entity.newBuilder().setName(reg.subject + " " + reg.courseNumber))
								.setSubpart(OnlineSectioningLog.Entity.newBuilder().setName(reg.scheduleType));
							if (reg.registrationStatusDate != null)
								section.setTimeStamp(reg.registrationStatusDate.getMillis());
						}
					}
				helper.getAction().addEnrollment(external);
				String removed = "";
				for (String s: sectionExternalIds)
					removed += (removed.isEmpty() ? "" : ", ") + s;
				if (!added.isEmpty() || !removed.isEmpty()) {
					check.setMessage("UniTime enrollment data are not synchronized with Banner enrollment data, please try again later" +
							" (" + (removed.isEmpty() ? "added " + added : added.isEmpty() ? "dropped " + removed : "added " + added + ", dropped " + removed) + ")");
					// check.setFlag(EligibilityFlag.CAN_ENROLL, false);
				}
			}
		} catch (SectioningException e) {
			helper.info("Banner eligibility failed: " + e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			helper.info("Banner eligibility failed: " + e.getMessage(), e);
			throw new SectioningException(e.getMessage(), e);
		} finally {
			if (resource != null) resource.release();
		}
	}
	
	@Override
	public List<EnrolledSection> getEnrollment(OnlineSectioningServer server, OnlineSectioningHelper helper, XStudent student) throws SectioningException {
		ClientResource resource = null;
		try {
			String pin = (helper.getUser().hasExtension(OnlineSectioningLog.UserEntity.pin) ? helper.getUser().getExtension(OnlineSectioningLog.UserEntity.pin) : null);
			AcademicSessionInfo session = server.getAcademicSession();
			helper.debug("Retrieving enrollment for " + student.getName() + " (term: " + getBannerTerm(session) + ", id:" + student.getExternalId() + ", pin:" + pin + ")");
			
			// First, check student registration status
			resource = new ClientResource(iBannerApiUrl);
			resource.setNext(iClient);
			resource.addQueryParameter("term", getBannerTerm(session));
			resource.addQueryParameter("bannerId", student.getExternalId());
			helper.getAction().addOptionBuilder().setKey("term").setValue(getBannerTerm(session));
			if (pin != null && !pin.isEmpty()) {
				resource.addQueryParameter("altPin", pin);
				helper.getAction().addOptionBuilder().setKey("pin").setValue(pin);
			}
			resource.setChallengeResponse(ChallengeScheme.HTTP_BASIC, iBannerApiUser, iBannerApiPassword);
			
			resource.get(MediaType.APPLICATION_JSON);
			
			// Check status, memorize enrolled sections
			Gson gson = getGson();
			List<XEInterface.RegisterResponse> current = gson.fromJson(new JsonReader(resource.getResponseEntity().getReader()), XEInterface.RegisterResponse.TYPE);
			helper.getAction().addOptionBuilder().setKey("response").setValue(gson.toJson(current));
			helper.debug("Current registration: " + gson.toJson(current));
			if (current == null || current.isEmpty() || !current.get(0).validStudent) {
				String reason = null;
				if (current != null && current.size() > 0 && current.get(0).failureReasons != null) {
					for (String m: current.get(0).failureReasons) {
						if (reason == null)
							reason = m;
						else
							reason += "\n" + m;
					}
				}
				throw new SectioningException(reason == null ? "Failed to check student registration status." : reason);
			}
			List<EnrolledSection> registered = new ArrayList<EnrolledSection>();
			OnlineSectioningLog.Enrollment.Builder external = OnlineSectioningLog.Enrollment.newBuilder();
			external.setType(OnlineSectioningLog.Enrollment.EnrollmentType.EXTERNAL);
			if (current.get(0).registrations != null)
				for (XEInterface.Registration reg: current.get(0).registrations) {
					if ("R".equals(reg.statusIndicator)) {
						registered.add(new EnrolledSection(reg.subject, reg.courseNumber, reg.courseReferenceNumber));
						OnlineSectioningLog.Section.Builder section = external.addSectionBuilder()
							.setClazz(OnlineSectioningLog.Entity.newBuilder().setName(reg.courseReferenceNumber))
							.setCourse(OnlineSectioningLog.Entity.newBuilder().setName(reg.subject + " " + reg.courseNumber))
							.setSubpart(OnlineSectioningLog.Entity.newBuilder().setName(reg.scheduleType));
						if (reg.registrationStatusDate != null)
							section.setTimeStamp(reg.registrationStatusDate.getMillis());
					}
				}
			helper.getAction().addEnrollment(external);
			
			helper.debug("Return: " + registered);
			return registered;
		} catch (SectioningException e) {
			helper.warn("Failed to retrieve student registration data from Banner: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			helper.error("Failed to retrieve student registration data from Banner: " + e.getMessage(), e);
			throw new SectioningException(e.getMessage(), e);
		} finally {
			if (resource != null) resource.release();
		}
	}

	@Override
	public List<EnrollmentFailure> enroll(OnlineSectioningServer server, OnlineSectioningHelper helper, XStudent student, Map<XCourse, List<XSection>> enrollments) throws SectioningException {
		if (enrollments == null || enrollments.isEmpty())
			return new ArrayList<StudentEnrollmentProvider.EnrollmentFailure>();
		
		ClientResource resource = null;
		try {
			String pin = (helper.getUser().hasExtension(OnlineSectioningLog.UserEntity.pin) ? helper.getUser().getExtension(OnlineSectioningLog.UserEntity.pin) : null);
			AcademicSessionInfo session = server.getAcademicSession();
			sLog.info("Enrolling " + student.getName() + " to " + enrollments + " (term: " + getBannerTerm(session) + ", id:" + student.getExternalId() + ", pin:" + pin + ")");
			
			// First, check student registration status
			resource = new ClientResource(iBannerApiUrl);
			resource.setNext(iClient);
			resource.addQueryParameter("term", getBannerTerm(session));
			resource.addQueryParameter("bannerId", student.getExternalId());
			helper.getAction().addOptionBuilder().setKey("term").setValue(getBannerTerm(session));
			if (pin != null && !pin.isEmpty()) {
				resource.addQueryParameter("altPin", pin);
				helper.getAction().addOptionBuilder().setKey("pin").setValue(pin);
			}
			resource.setChallengeResponse(ChallengeScheme.HTTP_BASIC, iBannerApiUser, iBannerApiPassword);
			
			resource.get(MediaType.APPLICATION_JSON);
			
			// Check status, memorize enrolled sections
			Gson gson = getGson();
			List<XEInterface.RegisterResponse> current = gson.fromJson(new JsonReader(resource.getResponseEntity().getReader()), XEInterface.RegisterResponse.TYPE);
			if (current == null || current.isEmpty() || !current.get(0).validStudent) {
				String reason = null;
				if (current != null && current.size() > 0 && current.get(0).failureReasons != null) {
					for (String m: current.get(0).failureReasons) {
						if (reason == null)
							reason = m;
						else
							reason += "\n" + m;
					}
				}
				throw new SectioningException(reason == null ? "Failed to check student registration status." : reason);
			}
			Set<String> registered = new HashSet<String>();
			helper.getAction().addOptionBuilder().setKey("original").setValue(gson.toJson(current));
			sLog.info("Current registration: " + gson.toJson(current));
			if (current.get(0).registrations != null)
				for (XEInterface.Registration reg: current.get(0).registrations) {
					if ("R".equals(reg.statusIndicator))
						registered.add(reg.courseReferenceNumber);
				}
			
			// Next, try to enroll student into the given courses
			Map<String, XSection> id2section = new HashMap<String, XSection>();
			Map<String, XCourse> id2course = new HashMap<String, XCourse>();
			XEInterface.RegisterRequest req = new XEInterface.RegisterRequest(getBannerTerm(session), student.getExternalId(), pin);
			for (Map.Entry<XCourse, List<XSection>> entry: enrollments.entrySet()) {
				XCourse course = entry.getKey();
				for (XSection section: entry.getValue()) {
					String id = section.getExternalId(course.getCourseId());
					registered.remove(id);
					req.add(id);
					id2section.put(id, section);
					id2course.put(id, course);
				}
			}
			for (String id: registered)
				req.drop(id);
			
			sLog.info("Request: " + gson.toJson(req));
			helper.getAction().addOptionBuilder().setKey("request").setValue(gson.toJson(req));
			
			resource.post(new JsonRepresentation(gson.toJson(req)));
			
			// Finally, check the response
			XEInterface.RegisterResponse response = gson.fromJson(new JsonReader(resource.getResponseEntity().getReader()), XEInterface.RegisterResponse.class);
			sLog.info("Response: " + gson.toJson(response));
			helper.getAction().addOptionBuilder().setKey("response").setValue(gson.toJson(response));
			if (response == null || !response.validStudent) {
				String reason = null;
				if (current != null && current.size() > 0 && current.get(0).failureReasons != null) {
					for (String m: current.get(0).failureReasons) {
						if (reason == null)
							reason = m;
						else
							reason += "\n" + m;
					}
				}
				throw new SectioningException(reason == null ? "Failed to enroll student." : reason);
			}
			
			
			List<EnrollmentFailure> fails = new ArrayList<EnrollmentFailure>();
			Set<String> checked = new HashSet<String>();
			if (response.registrations != null) {
				OnlineSectioningLog.Enrollment.Builder external = OnlineSectioningLog.Enrollment.newBuilder();
				external.setType(OnlineSectioningLog.Enrollment.EnrollmentType.EXTERNAL);
				for (XEInterface.Registration reg: response.registrations) {
					String id = reg.courseReferenceNumber;
					checked.add(id);
					if ("R".equals(reg.statusIndicator)) {
						// skip successfully registered enrollments
						external.addSection(external.addSectionBuilder()
								.setClazz(OnlineSectioningLog.Entity.newBuilder().setName(reg.courseReferenceNumber))
								.setCourse(OnlineSectioningLog.Entity.newBuilder().setName(reg.subject + " " + reg.courseNumber))
								.setSubpart(OnlineSectioningLog.Entity.newBuilder().setName(reg.scheduleType)));
						continue;
					}
					if ("D".equals(reg.statusIndicator)) {
						// skip deleted enrollments
						continue;
					}
					XCourse course = id2course.get(id);
					XSection section = id2section.get(id);
					String error = null;
					if (reg.crnErrors != null)
						for (XEInterface.CrnError e: reg.crnErrors) {
							if (error == null)
								error = e.message;
							else
								error += "\n" + e.message;
						}
					fails.add(new EnrollmentFailure(course, section, error == null ? "Enrollment failed." : error, "RW".equals(reg.courseRegistrationStatus) || "RE".equals(reg.courseRegistrationStatus)));
					if ("RW".equals(reg.courseRegistrationStatus) || "RE".equals(reg.courseRegistrationStatus))
						external.addSection(external.addSectionBuilder()
								.setClazz(OnlineSectioningLog.Entity.newBuilder().setName(reg.courseReferenceNumber))
								.setCourse(OnlineSectioningLog.Entity.newBuilder().setName(reg.subject + " " + reg.courseNumber))
								.setSubpart(OnlineSectioningLog.Entity.newBuilder().setName(reg.scheduleType)));
				}
				helper.getAction().addEnrollment(external);
			}
			if (response.failedRegistrations != null) {
				Set<String> error = new TreeSet<String>();
				for (XEInterface.FailedRegistration reg: response.failedRegistrations) {
					if (reg.failedCRN != null) {
						String id = reg.failedCRN;
						XCourse course = id2course.get(id);
						XSection section = id2section.get(id);
						fails.add(new EnrollmentFailure(course, section, reg.failure == null ? "Enrollment failed." : reg.failure, false));
						checked.add(id);
					} else {
						if (reg.failure != null)
							error.add(reg.failure);
					}
				}
				String em = null;
				for (String m: error) {
					if (em == null)
						em = m;
					else
						em += ", " + m;
				}
				for (Map.Entry<XCourse, List<XSection>> entry: enrollments.entrySet()) {
					XCourse course = entry.getKey();
					for (XSection section: entry.getValue()) {
						String id = section.getExternalId(course.getCourseId());
						if (!checked.contains(id))
							fails.add(new EnrollmentFailure(course, section, em == null ? "Enrollment failed." : em, false));
					}
				}
			}
			
			sLog.info("Return: " + fails);
			if (!fails.isEmpty())
				helper.getAction().addOptionBuilder().setKey("message").setValue(fails.toString());
			return fails;
		} catch (SectioningException e) {
			sLog.error("Banner enrollment failed: " + e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			sLog.error("Banner enrollment failed: " + e.getMessage(), e);
			throw new SectioningException(e.getMessage(), e);
		} finally {
			if (resource != null) resource.release();
		}
	}

	@Override
	public void dispose() {
		try {
			iClient.stop();
		} catch (Exception e) {
			sLog.error(e.getMessage(), e);
		}
	}
	
	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		try {
			ToolBox.configureLogging();

			// XStudentId student = new XStudentId(-1l, "PURDUE001", "Purdue Student");
			
			Map<Long, List<XSection>> enrollments = new HashMap<Long, List<XSection>>();
			List<XSection> sections = new ArrayList<XSection>();
			sections.add(new XSection("20151"));
			sections.add(new XSection("20153"));
			sections.add(new XSection("20108"));
			sections.add(new XSection("20164"));
			sections.add(new XSection("20222"));
			enrollments.put(1l, sections);
			
			XEStudentEnrollment xe = new XEStudentEnrollment();
			/*
			xe.enroll(
					new AcademicSessionInfo(-1l, "2014", "Fall", "M"),
					student, 
					"111111",
					enrollments);
			xe.enroll(
					new AcademicSessionInfo(-1l, "2014", "Fall", "M"),
					student, 
					"111111",
					new HashMap<Long, List<XSection>>());
					*/
			
			ClientResource resource = new ClientResource(xe.iBannerApiUrl);
			resource.setNext(xe.iClient);
			resource.addQueryParameter("term", "201510");
			resource.addQueryParameter("bannerId", "PURDUE001");
			resource.addQueryParameter("altPin", "111111");
			resource.setChallengeResponse(ChallengeScheme.HTTP_BASIC, xe.iBannerApiUser, xe.iBannerApiPassword);
			resource.get(MediaType.APPLICATION_JSON);
			
			System.out.println(resource.getResponseEntity().getText());
			
			xe.dispose();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isAllowWaitListing() {
		return false;
	}
}
