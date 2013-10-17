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
package org.unitime.commons.hibernate.connection;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.sf.cpsolver.ifs.util.ToolBox;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;

/**
 * @author Tomas Muller
 */
public class LoggingDBCPConnectionProvider extends DBCPConnectionProvider {
	private static final long serialVersionUID = 1L;
	private static final Log sLog = LogFactory.getLog(LoggingDBCPConnectionProvider.class);
	private static final DecimalFormat sDF = new DecimalFormat("#,##0.00");
	private List<Lease> iLeases = new ArrayList<Lease>();
	private LeaseLogger iLogger = null;
	
	@Override
	public Connection getConnection() throws SQLException {
		Connection connection = super.getConnection();
		synchronized (iLeases) {
			iLeases.add(new Lease(connection));
		}
		return connection;
	}
	
	@Override
	public void closeConnection(Connection connection) throws SQLException {
		synchronized (iLeases) {
			for (Iterator<Lease> i = iLeases.iterator(); i.hasNext(); ) {
				Lease lease = i.next();
				if (lease.getConnection().equals(connection))
					i.remove();
			}
		}
		super.closeConnection(connection);
	}
	
	@Override
	public void configure(Properties props) throws HibernateException {
		super.configure(props);
		iLogger = new LeaseLogger();
		iLogger.start();
	}
	
	@Override
	public void close() throws HibernateException {
		super.close();
		iLogger.interrupt();
	}
	
	public static class Lease {
		private Connection iConnection;
		private Thread iThread;
		private StackTraceElement[] iTrace;
		private long iTimeStamp;
		
		public Lease(Connection connection) {
			iConnection = connection;
			iThread = Thread.currentThread();
			iTrace = Thread.currentThread().getStackTrace();
			iTimeStamp = System.currentTimeMillis();
		}
		
		public Connection getConnection() {
			return iConnection;
		}
		
		public boolean equals(Object o) {
			if (o == null) return false;
			if (o instanceof Connection)
				return getConnection().equals(o);
			if (o instanceof Lease)
				return getConnection().equals(((Lease)o).getConnection());
			return false;
		}
		
		public double getLeaseTime() {
			return (System.currentTimeMillis() - iTimeStamp) / 1000.0;
		}
		
		public Thread.State getState() {
			return iThread.getState();
		}
		
		public String getName() {
			return iThread.getName();
		}
		
		public String getStackTrace() {
			int first = 0;
			for (int i = 3; i < iTrace.length; i++)
				if (iTrace[i].getClassName().startsWith("org.unitime.") && !iTrace[i].getClassName().endsWith("._BaseRootDAO")) { first = i; break; }
			StringBuffer ret = new StringBuffer();
			for (int i = first; i < iTrace.length; i++)
				ret.append("\n  " + iTrace[i]);
			return ret.toString();
		}
		
		public String toString() {
			StackTraceElement trace = null;
			for (int i = 3; i < iTrace.length; i++)
				if (iTrace[i].getClassName().startsWith("org.unitime.") && !iTrace[i].getClassName().endsWith("._BaseRootDAO")) { trace = iTrace[i]; break; }
			return sDF.format(getLeaseTime()) + " " + getState() + " " + getName() + " " + trace;
		}
	}
	
	public class LeaseLogger extends Thread {
		private boolean iActive = true;
		
		public LeaseLogger() {
			super("DBCP:Logger");
			setDaemon(true);
		}
		
		@Override
		public void run() {
			sLog.info("Database connection pool logging is enabled.");
			while (iActive) {
				try {
					try {
						sleep(60000);
					} catch (InterruptedException e) {}
					synchronized (iLeases) {
						List<Lease> suspicious = new ArrayList<Lease>();
						for (Lease lease: iLeases)
							if (lease.getLeaseTime() > 60.0 || lease.getState() == State.TERMINATED)
								suspicious.add(lease);
						if (!suspicious.isEmpty())
							sLog.info("Suspicious leases:" + ToolBox.col2string(iLeases, 2));
						for (Lease lease: suspicious)
							if (lease.getState() == State.TERMINATED) {
								sLog.fatal("Releasing connection of a terminated thread " + lease.getName() + "." + lease.getStackTrace());
								closeConnection(lease.getConnection());
							}
					}
				} catch (Exception e) {
					sLog.warn("Logging failed: " + e.getMessage(), e);
				}
			}
		}
		
		@Override
		public void interrupt() {
			iActive = false;
			super.interrupt();
			try { join(); } catch (InterruptedException e) {}
		}
	}

}
