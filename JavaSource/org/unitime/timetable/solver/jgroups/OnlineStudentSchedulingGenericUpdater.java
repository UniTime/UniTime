/*
 * UniTime 3.5 (University Timetabling Application)
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
package org.unitime.timetable.solver.jgroups;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.ToolBox;

import org.apache.log4j.Logger;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.unitime.timetable.ApplicationProperties;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;

public class OnlineStudentSchedulingGenericUpdater extends Thread {
	private Logger iLog;
	private long iSleepTimeInSeconds = 5;
	private boolean iRun = true;
	
	private JChannel iChannel;
	private OnlineStudentSchedulingContainerRemote iContainer;
	private LockService iLockService;
	private Lock iMasterLock;
	private AtomicBoolean iMaster = new AtomicBoolean(false);
	private MasterAcquiringThread iMasterThread;

	public OnlineStudentSchedulingGenericUpdater(JChannel channel, OnlineStudentSchedulingContainerRemote container) {
		super();
		iChannel = channel;
		iContainer = container;
		iLockService = new LockService(channel);
		setDaemon(true);
		setName("Updater[generic]");
		iSleepTimeInSeconds = Long.parseLong(ApplicationProperties.getProperty("unitime.sectioning.queue.loadInterval", "300"));
		iLog = Logger.getLogger(OnlineStudentSchedulingGenericUpdater.class + ".updater[generic]"); 
	}
	
	@Override
	public void run() {
		iMasterThread = new MasterAcquiringThread();
		iMasterThread.start();

		try {
			iLog.info("Generic updater started.");
			while (iRun) {
				try {
					sleep(iSleepTimeInSeconds * 1000);
				} catch (InterruptedException e) {}
				if (iRun)
					checkForNewServers();
			}
			iLog.info("Generic updater stopped.");
		} catch (Exception e) {
			iLog.error("Generic updater failed, " + e.getMessage(), e);
		}
		
		iMasterThread.interrupt();
		if (iMaster.get())
			iMasterLock.unlock();
	}
	
	public void stopUpdating() {
		iRun = false;
		if (iMaster.get())
			iMasterLock.unlock();
		interrupt();
	}
	
	public void checkForNewServers() {
		if (!iMaster.get()) return;
		Lock lock = iLockService.getLock("updater[generic].check");
		lock.lock();
		org.hibernate.Session hibSession = SessionDAO.getInstance().getSession();
		try {
			String year = ApplicationProperties.getProperty("unitime.enrollment.year");
			String term = ApplicationProperties.getProperty("unitime.enrollment.term");
			String campus = ApplicationProperties.getProperty("unitime.enrollment.campus");
			
			Set<String> solvers = new HashSet<String>();
			try {
				RspList<Set<String>> ret = iContainer.getDispatcher().callRemoteMethods(
						null, "getSolvers", new Object[] {}, new Class[] {}, SolverServerImplementation.sAllResponses);
				for (Rsp<Set<String>> rsp : ret) {
					solvers.addAll(rsp.getValue());
				}
			} catch (Exception e) {
				iLog.error("Failed to retrieve servers: " + e.getMessage(), e);
				return;
			}
			
			for (Iterator<Session> i = SessionDAO.getInstance().findAll(hibSession).iterator(); i.hasNext(); ) {
				Session session = i.next();
				
				if (solvers.contains(session.getUniqueId().toString())) continue;
				
				if (year != null && !year.equals(session.getAcademicYear())) continue;
				if (term != null && !term.equals(session.getAcademicTerm())) continue;
				if (campus != null && !campus.equals(session.getAcademicInitiative())) continue;
				
				if (session.getStatusType().isTestSession()) continue;
				if (!session.getStatusType().canSectionAssistStudents() && !session.getStatusType().canOnlineSectionStudents()) continue;
				
				int nrSolutions = ((Number)hibSession.createQuery(
						"select count(s) from Solution s where s.owner.session.uniqueId=:sessionId")
						.setLong("sessionId", session.getUniqueId()).uniqueResult()).intValue();
				if (nrSolutions == 0) continue;
				
				try {
					iContainer.getDispatcher().callRemoteMethod(
							ToolBox.random(iChannel.getView().getMembers()),
							"createRemoteSolver", new Object[] { session.getUniqueId().toString(), null, iChannel.getAddress() },
							new Class[] { String.class, DataProperties.class, Address.class },
							SolverServerImplementation.sFirstResponse);
				} catch (Exception e) {
					iLog.fatal("Unable to upadte session " + session.getAcademicTerm() + " " + session.getAcademicYear() + " (" + session.getAcademicInitiative() + "), reason: "+ e.getMessage(), e);
				}
			}
		} finally {
			hibSession.close();
			lock.unlock();
		}
	}

	private class MasterAcquiringThread extends Thread {
		MasterAcquiringThread() {
			setName("Updater[generic]:AcquiringMasterLock");
			setDaemon(true);
		}
		
		@Override
		public void run() {
			iMasterLock = iLockService.getLock("updater[generic].master");
			try {
				iMasterLock.lockInterruptibly();
				iMaster.set(true);
				iLog.info("I am the coordinator!");
			} catch (InterruptedException e) {}
		}
	}
}
