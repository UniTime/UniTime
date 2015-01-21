/*
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * The Apereo Foundation licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
*/
package org.unitime.timetable.server.menu;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.TreeSet;

import org.cpsolver.ifs.util.DataProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.form.ListSolutionsForm;
import org.unitime.timetable.gwt.command.server.GwtRpcImplementation;
import org.unitime.timetable.gwt.command.server.GwtRpcImplements;
import org.unitime.timetable.gwt.command.server.GwtRpcLogging;
import org.unitime.timetable.gwt.command.server.GwtRpcLogging.Level;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.MenuInterface.InfoPairInterface;
import org.unitime.timetable.gwt.shared.MenuInterface.SolverInfoInterface;
import org.unitime.timetable.gwt.shared.MenuInterface.SolverInfoRpcRequest;
import org.unitime.timetable.model.SolverGroup;
import org.unitime.timetable.model.TimetableManager;
import org.unitime.timetable.model.dao.ExamTypeDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.SolverGroupDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.solver.SolverProxy;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.solver.service.SolverService;
import org.unitime.timetable.solver.studentsct.StudentSolverProxy;

/**
 * @author Tomas Muller
 */
@GwtRpcImplements(SolverInfoRpcRequest.class)
@GwtRpcLogging(Level.DISABLED)
public class SolverInfoBackend implements GwtRpcImplementation<SolverInfoRpcRequest, SolverInfoInterface> {
	protected static GwtMessages MESSAGES = Localization.create(GwtMessages.class);

	@Autowired SolverService<SolverProxy> courseTimetablingSolverService;
	@Autowired SolverService<ExamSolverProxy> examinationSolverService;
	@Autowired SolverService<StudentSolverProxy> studentSectioningSolverService;

	@Override
	public SolverInfoInterface execute(SolverInfoRpcRequest request, SessionContext context) {
		SolverInfoInterface ret = new SolverInfoInterface();
		
		org.hibernate.Session hibSession = SessionDAO.getInstance().getSession();
		try {
			
			SolverProxy solver = courseTimetablingSolverService.getSolver();
			ExamSolverProxy examSolver = examinationSolverService.getSolver();
			StudentSolverProxy studentSolver = studentSectioningSolverService.getSolver();
			
			Map progress = (studentSolver!=null?studentSolver.getProgress():examSolver!=null?examSolver.getProgress():solver!=null?solver.getProgress():null);
			if (progress == null) return null;
			
			DataProperties properties = (studentSolver!=null?studentSolver.getProperties():examSolver!=null?examSolver.getProperties():solver.getProperties());
			String progressStatus = (String)progress.get("STATUS");
			String progressPhase = (String)progress.get("PHASE");
			long progressCur = ((Long)progress.get("PROGRESS")).longValue();
			long progressMax = ((Long)progress.get("MAX_PROGRESS")).longValue();
			String version = (String)progress.get("VERSION");
			if (version==null || "-1".equals(version)) version = "N/A";
			double progressPercent = 100.0*((double)(progressCur<progressMax?progressCur:progressMax))/((double)progressMax);
			String runnerName = getName(properties.getProperty("General.OwnerPuid","N/A"));
			Long[] solverGroupId = properties.getPropertyLongArry("General.SolverGroupId",null);
			String ownerName = "";
			if (solverGroupId!=null) {
				for (int i=0;i<solverGroupId.length;i++) {
					if (i>0) ownerName += " & ";
					ownerName += getName((new SolverGroupDAO()).get(solverGroupId[i]));
				}
			}
			if (examSolver!=null) ownerName = ExamTypeDAO.getInstance().get(examSolver.getExamTypeId()).getLabel();
			if (ownerName==null || ownerName.length()==0)
				ownerName = "N/A";
			if (ownerName.equals("N/A"))
				ownerName = runnerName;
			if (runnerName.equals("N/A"))
				runnerName = ownerName;
			if (!ownerName.equals(runnerName))
				ownerName = runnerName+" as "+ownerName;
			if (ownerName.length() > 50)
				ownerName = ownerName.substring(0,47) + "...";

			ret.setType(studentSolver != null ? MESSAGES.solverStudent() : examSolver != null ? MESSAGES.solverExamination() : MESSAGES.solverCourse());
			ret.addPair(MESSAGES.fieldType(), ret.getType());
			ret.setUrl(studentSolver != null ? "studentSolver.do" : examSolver != null ? "examSolver.do": "solver.do");
			ret.addPair(MESSAGES.fieldSolver(), progressStatus);
			ret.setSolver(progressStatus);
			ret.addPair(MESSAGES.fieldPhase(), progressPhase);
			if (progressMax>0)
				ret.addPair(MESSAGES.fieldProgress(), (progressCur<progressMax?progressCur:progressMax) + " of " + progressMax + " (" + new DecimalFormat("0.0").format(progressPercent) + "%)");
			ret.addPair(MESSAGES.fieldOwner(), ownerName);
			ret.addPair(MESSAGES.fieldHost(), (studentSolver!=null?studentSolver.getHost():examSolver!=null?examSolver.getHost():solver.getHost()));
			ret.addPair(MESSAGES.fieldSession(), SessionDAO.getInstance().get(properties.getPropertyLong("General.SessionId",null)).getLabel());
			InfoPairInterface p = ret.addPair(MESSAGES.fieldVersion(), version);
			
			if (request.isIncludeSolutionInfo()) {
				Map<String,String> info = null;
				try {
					if (studentSolver != null) {
						info = studentSolver.statusSolutionInfo();
					} else if (examSolver != null) {
						info = examSolver.statusSolutionInfo();
					} else if (solver != null) {
						info = solver.statusSolutionInfo();
					}
				} catch (Exception e) {}
				
				if (info != null && !info.isEmpty()) {
					p.setSeparator(true);
					TreeSet<String> keys = new TreeSet<String>(new ListSolutionsForm.InfoComparator());
					keys.addAll(info.keySet());
					for (String key: keys)
						ret.addPair(key, (String)info.get(key));
				}
			}
	 		
		} finally {
			hibSession.close();
		}
		return ret;
	}
	
	private String getName(String puid) {
		return getName(TimetableManager.findByExternalId(puid));
	}

	private String getName(TimetableManager mgr) {
		if (mgr==null) return null;
		return mgr.getShortName();
	}

	private String getName(SolverGroup gr) {
		if (gr==null) return null;
		return gr.getAbbv();
	}

}
