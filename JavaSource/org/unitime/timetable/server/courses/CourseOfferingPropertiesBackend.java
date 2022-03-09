package org.unitime.timetable.server.courses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.hibernate.Query;
import org.hibernate.criterion.Order;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.defaults.ApplicationProperty;
import org.unitime.localization.messages.CourseMessages;
import org.unitime.timetable.defaults.UserProperty;
import org.unitime.timetable.events.EventAction.EventContext;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.AcademicSessionInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditFormatInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditTypeInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseCreditUnitTypeInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingPropertiesInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseOfferingPropertiesRequest;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.CourseTypeInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.DepartmentInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.InstructorInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.OfferingConsentTypeInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.OverrideTypeInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.ResponsibilityInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.SubjectAreaInterface;
import org.unitime.timetable.gwt.shared.CourseOfferingInterface.WaitListInterface;
import org.unitime.timetable.model.CourseCreditFormat;
import org.unitime.timetable.model.CourseCreditType;
import org.unitime.timetable.model.CourseCreditUnitType;
import org.unitime.timetable.model.CourseOffering;
import org.unitime.timetable.model.CourseType;
import org.unitime.timetable.model.Department;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.InstructionalOffering;
import org.unitime.timetable.model.OfferingConsentType;
import org.unitime.timetable.model.OverrideType;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.SubjectArea;
import org.unitime.timetable.model.TeachingResponsibility;
import org.unitime.timetable.model.comparators.CourseOfferingComparator;
import org.unitime.timetable.model.dao.CourseTypeDAO;
import org.unitime.timetable.model.dao.DepartmentDAO;
import org.unitime.timetable.model.dao.DepartmentalInstructorDAO;
import org.unitime.timetable.model.dao.OverrideTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SubjectAreaDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.UserAuthority;
import org.unitime.timetable.security.rights.Right;
import org.unitime.timetable.util.Constants;

/**
 * @author Alec Macleod
 */
@GwtRpcImplements(CourseOfferingPropertiesRequest.class)
public class CourseOfferingPropertiesBackend implements GwtRpcImplementation<CourseOfferingPropertiesRequest, CourseOfferingPropertiesInterface> {

	protected final static CourseMessages MSG = Localization.create(CourseMessages.class);
	Logger logger = java.util.logging.Logger.getLogger("UpdateCourseOfferingBackend");

	@Override
	public CourseOfferingPropertiesInterface execute(CourseOfferingPropertiesRequest request, SessionContext context) {
		
		if (request.hasSessionId())
			context = new EventContext(context, request.getSessionId());

		if (!context.hasPermission(Right.AddCourseOffering) && !context.hasPermission(Right.EditCourseOffering) && !context.hasPermission(Right.EditCourseOfferingCoordinators) && !context.hasPermission(Right.EditCourseOfferingNote)) {
			//If they don't have any of the permissions, reject based on any of them.
			context.checkPermission(Right.AddCourseOffering);
		}
		
		CourseOfferingPropertiesInterface response = new CourseOfferingPropertiesInterface();
		UserAuthority authority = null;
		
		if (context.getUser() != null) {
			Session session = SessionDAO.getInstance().get(request.hasSessionId() ? request.getSessionId() : context.getUser().getCurrentAcademicSessionId());
			response.setAcademicSession(new AcademicSessionInterface(session.getUniqueId(), session.getAcademicTerm() + " " + session.getAcademicYear()));
			authority = context.getUser().getCurrentAuthority();
			response.setWkEnrollDefault(session.getLastWeekToEnroll());
			response.setWkChangeDefault(session.getLastWeekToChange());
			response.setWkDropDefault(session.getLastWeekToDrop());
			response.setWeekStartDayOfWeek(Localization.getDateFormat("EEEE").format(session.getSessionBeginDateTime()));
		}
		
		List<CourseOffering> list = new ArrayList<CourseOffering>();
		int j = 0;
		Long tempSubjId = new Long(0);
		
		//Does this still work?
		for (SubjectArea subject: SubjectArea.getUserSubjectAreas(context.getUser())) {
			if (context.hasPermission(subject, Right.AddCourseOffering)) {
				SubjectAreaInterface subjectArea = new SubjectAreaInterface();
				subjectArea.setId(subject.getUniqueId());
				subjectArea.setAbbreviation(subject.getSubjectAreaAbbreviation());
				subjectArea.setLabel(subject.getTitle());
				response.addSubjectArea(subjectArea);
				if (j == 0) {
					tempSubjId = subject.getUniqueId();
				}
				
				j++;

			}
		}

		response.setPrefRowsAdded(Constants.PREF_ROWS_ADDED);
		
		for (CourseCreditFormat courseCreditFormat: CourseCreditFormat.getCourseCreditFormatList()) {
			CourseCreditFormatInterface courseCreditFormatObject = new CourseCreditFormatInterface();
			courseCreditFormatObject.setId(courseCreditFormat.getUniqueId());
			courseCreditFormatObject.setReference(courseCreditFormat.getReference());
			courseCreditFormatObject.setLabel(courseCreditFormat.getLabel());
			response.addCourseCreditFormat(courseCreditFormatObject);
		}
		
		for (CourseCreditType courseCreditType: CourseCreditType.getCourseCreditTypeList()) {
			CourseCreditTypeInterface courseCreditTypeObject = new CourseCreditTypeInterface();
			courseCreditTypeObject.setId(courseCreditType.getUniqueId());
			courseCreditTypeObject.setLabel(courseCreditType.getLabel());
			response.addCourseCreditType(courseCreditTypeObject);
		}
		
		for (CourseCreditUnitType courseCreditUnitType: CourseCreditUnitType.getCourseCreditUnitTypeList()) {
			CourseCreditUnitTypeInterface courseCreditUnitTypeObject = new CourseCreditUnitTypeInterface();
			courseCreditUnitTypeObject.setId(courseCreditUnitType.getUniqueId());
			courseCreditUnitTypeObject.setLabel(courseCreditUnitType.getLabel());
			response.addCourseCreditUnitType(courseCreditUnitTypeObject);
		}
		
		for (OfferingConsentType offeringConsentType: OfferingConsentType.getConsentTypeList()) {
			OfferingConsentTypeInterface offeringConsentTypeObject = new OfferingConsentTypeInterface();
			offeringConsentTypeObject.setId(offeringConsentType.getUniqueId());
			offeringConsentTypeObject.setLabel(offeringConsentType.getLabel());
			response.addOfferingConsentType(offeringConsentTypeObject);
		}
		
		for (TeachingResponsibility teachingResponsibility: TeachingResponsibility.getCoordinatorTeachingResponsibilities()) {
			ResponsibilityInterface responsibility = new ResponsibilityInterface();
			responsibility.setId(teachingResponsibility.getUniqueId());
			responsibility.setLabel(teachingResponsibility.getLabel());
			response.addResponsibility(responsibility);
		}

		if (request.getSubjAreaId() == null) {
			request.setSubjAreaId(tempSubjId);
		}

		if (request.getSubjAreaId() != null) {
			SubjectArea subjectArea = SubjectAreaDAO.getInstance().get(request.getSubjAreaId());

			StringBuffer queryClause = new StringBuffer("");
			
			if (subjectArea.getDepartment().getUniqueId() != null) {
				queryClause.append(" and i.department.uniqueId = " + subjectArea.getDepartment().getUniqueId());
			}
			
			String instructorNameFormat = UserProperty.NameFormat.get(context.getUser());
	        
	        Long acadSessionId = context.getUser().getCurrentAcademicSessionId();

			StringBuffer query = new StringBuffer();
			query.append("select distinct i from DepartmentalInstructor i ");
			query.append(" where i.department.session.uniqueId = :acadSessionId ");
			query.append(queryClause);
	        
	        DepartmentalInstructorDAO idao = new DepartmentalInstructorDAO();
			org.hibernate.Session hibSession = idao.getSession();

			Query q = hibSession.createQuery(query.toString());
			q.setFetchSize(5000);
			q.setCacheable(true);
			q.setLong("acadSessionId", acadSessionId);
	        
			List result = q.list();
	        Collections.sort(result);
		    for (Iterator i=result.iterator();i.hasNext();) {
	            DepartmentalInstructor di = (DepartmentalInstructor)i.next();
	            String name = di.getName(instructorNameFormat);
	            InstructorInterface instructorObject = new InstructorInterface();
	            instructorObject.setId(di.getUniqueId());
	            instructorObject.setLabel(name);
				response.addInstructor(instructorObject);
			}

		    //Funding department stuff
		    
		    HashMap<Long, String> fundingDeptMap = new HashMap<>();

		    Department subjectFundingDepartment = subjectArea.getFundingDept();
		    Department subjectDepartment = subjectArea.getDepartment();
		    if (subjectFundingDepartment != null) {		    	
		    	fundingDeptMap.put(subjectFundingDepartment.getUniqueId(), subjectFundingDepartment.getName());

		    	if (subjectFundingDepartment.getUniqueId() != subjectDepartment.getUniqueId()) {
			    	fundingDeptMap.put(subjectDepartment.getUniqueId(), subjectDepartment.getName());
		    	}
		    	
		    } else {
		    	fundingDeptMap.put(subjectDepartment.getUniqueId(), subjectDepartment.getName());
		    }
		    
		    if (request.getIsEdit()) {
		    	Long courseOfferingId = request.getCourseOfferingId();
		    	CourseOffering courseOffering = CourseOffering.findByUniqueId(courseOfferingId);
		    	InstructionalOffering instructionalOffering = courseOffering.getInstructionalOffering();
		    	Set<CourseOffering> courseOfferings = new HashSet<CourseOffering>();
		    	courseOfferings = instructionalOffering.getCourseOfferings();
		    	Iterator iterator = courseOfferings.iterator();
		    	while (iterator.hasNext()) {
		    		CourseOffering childCourseOffering = (CourseOffering) iterator.next();
		    		Department fundingDept = childCourseOffering.getSubjectArea().getFundingDept(); //TODO do we need to get effective if this is null?
		    		if (fundingDept != null) {
				    	fundingDeptMap.put(fundingDept.getUniqueId(), fundingDept.getName());
		    		}
		    	}
		    	
		    }
		    
		    //Get depts where funding dept flag is true
		    StringBuffer queryClause2 = new StringBuffer("");

			queryClause2.append(" and i.externalFundingDept = 'true'");

		    StringBuffer query2 = new StringBuffer();
		    query2.append("select distinct i from Department i ");
		    query2.append(" where i.session.uniqueId = :acadSessionId ");
		    query2.append(queryClause2);
	        
	        DepartmentDAO departmentDao = new DepartmentDAO();
	        org.hibernate.Session hibSession2 = departmentDao.getSession();

			Query q2 = hibSession2.createQuery(query2.toString());
			q2.setFetchSize(5000);
			q2.setCacheable(true);
			q2.setLong("acadSessionId", acadSessionId);
	        
			List result2 = q2.list();
	        Collections.sort(result2);
		    for (Iterator i=result2.iterator();i.hasNext();) {
	            Department dept = (Department)i.next();
				fundingDeptMap.put(dept.getUniqueId(), dept.getName());
			}
		    
		    if (!fundingDeptMap.isEmpty()) {
		    	for (Map.Entry<Long, String> entry : fundingDeptMap.entrySet()) {
		    	    DepartmentInterface fundingDepartmentObject = new DepartmentInterface();
			    	fundingDepartmentObject.setId(entry.getKey());
			    	fundingDepartmentObject.setLabel(entry.getValue());
			    	response.addFundingDepartment(fundingDepartmentObject);
		    	}
		    }
		}

		Boolean courseOfferingNumberUpperCase;
		if (ApplicationProperty.CourseOfferingNumberUpperCase.isTrue()){
			courseOfferingNumberUpperCase = true;
		} else {
			courseOfferingNumberUpperCase = false;
		}
		
		String courseNbrRegex = ApplicationProperty.CourseOfferingNumberPattern.value(); 
		String courseNbrInfo = ApplicationProperty.CourseOfferingNumberPatternInfo.value();
		Boolean courseOfferingNumberMustBeUnique;
		if (ApplicationProperty.CourseOfferingNumberMustBeUnique.isTrue()){
			courseOfferingNumberMustBeUnique = true;
			if (request.getCourseNumber() != null && request.getSubjAreaId() != null) {
				CourseOffering course = CourseOffering.findBySessionSubjAreaIdCourseNbr(response.getAcademicSession().getId(), request.getSubjAreaId(), request.getCourseNumber().toString());
				if (course != null) {
					response.setInstructionalOfferingId(course.getInstructionalOffering().getUniqueId().toString());
				} else {
					response.setInstructionalOfferingId(null);
				}
			}
		} else {
			courseOfferingNumberMustBeUnique = false;
		}
		
		response.setCourseNbrRegex(courseNbrRegex);
		response.setCourseNbrInfo(courseNbrInfo);
		response.setCourseOfferingMustBeUnique(courseOfferingNumberMustBeUnique);
		response.setCourseOfferingNumberUpperCase(courseOfferingNumberUpperCase);
		response.setCoursesFundingDepartmentsEnabled(ApplicationProperty.CoursesFundingDepartmentsEnabled.isTrue());

		Boolean allowAlternativeCourseOfferings = ApplicationProperty.StudentSchedulingAlternativeCourse.isTrue(); //true;
		response.setAllowAlternativeCourseOfferings(allowAlternativeCourseOfferings);
		
		String courseUrlProvider = ApplicationProperty.CustomizationCourseLink.value();
		response.setCourseUrlProvider(courseUrlProvider);
		
		response.setCanEditExternalIds(ApplicationProperty.CourseOfferingEditExternalIds.isTrue());
		response.setCanShowExternalIds(ApplicationProperty.CourseOfferingShowExternalIds.isTrue());
		
		List<CourseOffering> courseOfferingsDemand = setupCourseOfferings(context, new CourseFilter() {
			@Override
			public boolean accept(CourseOffering course) {
				return course.getDemand() != null && course.getDemand() > 0;
			}
		});

		for (CourseOffering co: courseOfferingsDemand) { //TODO
			CourseOfferingInterface courseOffering = new CourseOfferingInterface();
			courseOffering.setId(co.getUniqueId());
			courseOffering.setLabel(co.getCourseNameWithTitle());
			response.addCourseDemands(courseOffering);
		}

		if (allowAlternativeCourseOfferings) {
			List<CourseOffering> altCourseOfferings = setupCourseOfferings(context, new CourseFilter() {
				@Override
				public boolean accept(CourseOffering course) {
					return !course.getInstructionalOffering().isNotOffered(); //&& !course.equals(co); TODO
				}
			});

			for (CourseOffering co: altCourseOfferings) { //TODO
				CourseOfferingInterface courseOffering = new CourseOfferingInterface();
				courseOffering.setId(co.getUniqueId());
				//courseOffering.setAbbreviation(co.get);
				courseOffering.setLabel(co.getCourseNameWithTitle());
				response.addAltCourseOffering(courseOffering);
			}
		}

		for (CourseType courseType: CourseTypeDAO.getInstance().findAll(Order.asc("reference"))) {
			CourseTypeInterface courseTypeObject = new CourseTypeInterface();
			courseTypeObject.setId(courseType.getUniqueId());
			courseTypeObject.setLabel(courseType.getLabel());
			response.addCourseType(courseTypeObject);
		}
		
		//Override Types
		for (OverrideType overrideType: OverrideTypeDAO.getInstance().findAll()) {
			OverrideTypeInterface overrideTypeObject = new OverrideTypeInterface();
			overrideTypeObject.setId(overrideType.getUniqueId());
			overrideTypeObject.setName(overrideType.getLabel());
			overrideTypeObject.setReference(overrideType.getReference());
			response.addOverrideType(overrideTypeObject);
		}
		
		response.setWaitListDefault(ApplicationProperty.OfferingWaitListDefault.isTrue());
		
		WaitListInterface waitListDefault = new WaitListInterface();
		if (ApplicationProperty.OfferingWaitListDefault.isTrue()) {
			waitListDefault.setLabel(MSG.waitListDefaultEnabled());
			waitListDefault.setValue("");
			response.addWaitList(waitListDefault);
		} else {
			waitListDefault.setLabel(MSG.waitListDefaultDisabled());
			waitListDefault.setValue("");
			response.addWaitList(waitListDefault);
		}

		WaitListInterface waitListEnabled = new WaitListInterface();
		waitListEnabled.setLabel(MSG.waitListEnabled());
		waitListEnabled.setValue("true");
		response.addWaitList(waitListEnabled);
		
		WaitListInterface waitListDisabled = new WaitListInterface();
		waitListDisabled.setLabel(MSG.waitListDisabled());
		waitListDisabled.setValue("false");
		response.addWaitList(waitListDisabled);

		return response;
	}
	
	
	private List<CourseOffering> setupCourseOfferings(SessionContext context, CourseFilter filter) {
		List<CourseOffering> list = new ArrayList<CourseOffering>();
		for (SubjectArea subject: SubjectArea.getUserSubjectAreas(context.getUser())) {
			for (CourseOffering co: subject.getCourseOfferings()) {
				if (filter == null || filter.accept(co))
					list.add(co);
			}
		}
	    Collections.sort(list, new CourseOfferingComparator());
	    return list;
	}
	
	private static interface CourseFilter {
		public boolean accept(CourseOffering course);
	}
}
