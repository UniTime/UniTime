/*
 * UniTime 3.2 (University Timetabling Application)
 * Copyright (C) 2008 - 2010, UniTime LLC, and individual contributors
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
package org.unitime.timetable.form;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.unitime.timetable.model.Exam;
import org.unitime.timetable.model.ExamPeriod;
import org.unitime.timetable.model.Session;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.solver.WebSolver;
import org.unitime.timetable.solver.exam.ExamSolverProxy;
import org.unitime.timetable.util.ComboBoxLookup;
import org.unitime.timetable.webutil.timegrid.ExamGridTable;

public class ExamGridForm extends ActionForm {
	private static final long serialVersionUID = 1429431006186003906L;
	private Long iSessionId;
    private TreeSet[] iPeriods;
    private int iSessionBeginWeek;
    private Date iSessionBeginDate;
    private Date iExamBeginDate;
    private boolean iShowSections = false;
    
    private String iOp = null;
    private int iExamType = Exam.sExamTypeFinal;
    private int[] iDate;
    private int[] iStartTime;
    private int[] iEndTime;
    private int iResource = ExamGridTable.sResourceRoom;
    private int iBackground = ExamGridTable.sBgNone;
    private String iFilter = null;
    private int iDispMode = ExamGridTable.sDispModePerWeekVertical;
    private int iOrder = ExamGridTable.sOrderByNameAsc;
    private boolean iBgPreferences = false;
    private boolean iHasMidtermExams = false;
    
    public int getDate(int examType) { return iDate[examType]; }
    public void setDate(int examType, int date) { iDate[examType] = date; }
    public boolean isAllDates(int examType) { return iDate[examType] == Integer.MIN_VALUE; }
    public int getStartTime(int examType) { return iStartTime[examType]; }
    public void setStartTime(int examType, int startTime) { iStartTime[examType] = startTime; }
    public int getEndTime(int examType) { return iEndTime[examType]; }
    public void setEndTime(int examType, int endTime) { iEndTime[examType] = endTime; }
    public int getResource() { return iResource; }
    public void setResource(int resource) { iResource = resource; }
    public int getBackground() { return iBackground; }
    public void setBackground(int background) { iBackground = background; }
    public String getFilter() { return iFilter; }
    public void setFilter(String filter) { iFilter = filter; }
    public int getDispMode() { return iDispMode; }
    public void setDispMode(int dispMode) { iDispMode = dispMode; }
    public int getOrder() { return iOrder; }
    public void setOrder(int order) { iOrder = order; }
    public boolean getBgPreferences() { return iBgPreferences; }
    public void setBgPreferences(boolean bgPreferences) { iBgPreferences = bgPreferences; }
    public String getOp() { return iOp; }
    public void setOp(String op) { iOp = op; }
    
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        iDate = new int[Exam.sExamTypes.length];
        iStartTime = new int[Exam.sExamTypes.length];
        iEndTime = new int[Exam.sExamTypes.length];
        for (int i=0;i<Exam.sExamTypes.length;i++)
        	iDate[i] = iStartTime[i] = iEndTime[i] = -1;
        iResource = ExamGridTable.sResourceRoom;
        iBackground = ExamGridTable.sBgNone;
        iFilter = null;
        iDispMode = ExamGridTable.sDispModePerWeekVertical;
        iOrder = ExamGridTable.sOrderByNameAsc;
        iBgPreferences = false;
        iOp = null;
        iShowSections = false;
		iExamType = Exam.sExamTypeFinal;
		try {
			ExamSolverProxy solver = WebSolver.getExamSolver(request.getSession());
			if (solver!=null)
				iExamType = solver.getProperties().getPropertyInt("Exam.Type", iExamType);
		} catch (Exception e) {}
    }
    
    public Long getSessionId() { return iSessionId; }
    public Date getExamBeginDate() { return iExamBeginDate; }
    public TreeSet getPeriods(int examType) { return iPeriods[examType]; }
    
    public void load(SessionContext context) throws Exception {
    	iHasMidtermExams = Exam.hasMidtermExams(context.getUser().getCurrentAcademicSessionId());
        Session session = SessionDAO.getInstance().get(context.getUser().getCurrentAcademicSessionId());
        iSessionId = session.getUniqueId();
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.setTime(session.getSessionBeginDateTime());
        iSessionBeginWeek = cal.get(Calendar.WEEK_OF_YEAR);
        iSessionBeginDate = session.getSessionBeginDateTime();
        iExamBeginDate = session.getExamBeginDate();
        iPeriods = new TreeSet[Exam.sExamTypes.length];
        for (int i=0;i<Exam.sExamTypes.length;i++) {
        	iPeriods[i] = ExamPeriod.findAll(session.getUniqueId(), i);
        	setDate(i, Integer.parseInt(context.getUser().getProperty("ExamGrid.date."+i, String.valueOf(Integer.MIN_VALUE))));
        	setStartTime(i, Integer.parseInt(context.getUser().getProperty("ExamGrid.start."+i, String.valueOf(getFirstStart(i)))));
        	setEndTime(i, Integer.parseInt(context.getUser().getProperty("ExamGrid.end."+i, String.valueOf(getLastEnd(i)))));
        }
        setResource(Integer.parseInt(context.getUser().getProperty("ExamGrid.resource", String.valueOf(ExamGridTable.sResourceRoom))));
        setBackground(Integer.parseInt(context.getUser().getProperty("ExamGrid.background", String.valueOf(ExamGridTable.sBgNone))));
        setFilter(context.getUser().getProperty("ExamGrid.filter"));
        setDispMode(Integer.parseInt(context.getUser().getProperty("ExamGrid.dispMode", String.valueOf(ExamGridTable.sDispModePerWeekVertical))));
        setOrder(Integer.parseInt(context.getUser().getProperty("ExamGrid.order", String.valueOf(ExamGridTable.sOrderByNameAsc))));
        setBgPreferences("1".equals(context.getUser().getProperty("ExamGrid.bgPref", "0")));
        setExamType(context.getAttribute("Exam.Type") == null ? iExamType : (Integer)context.getAttribute("Exam.Type"));
        setShowSections("1".equals(context.getUser().getProperty("ExamReport.showSections", "1")));
    }
    
    public void save(SessionContext context) throws Exception {
    	for (int i=0;i<Exam.sExamTypes.length;i++) {
    		context.getUser().setProperty("ExamGrid.date."+i, String.valueOf(getDate(i)));
    		context.getUser().setProperty("ExamGrid.start."+i, String.valueOf(getStartTime(i)));
    		context.getUser().setProperty("ExamGrid.end."+i, String.valueOf(getEndTime(i)));
    	}
        context.getUser().setProperty("ExamGrid.resource", String.valueOf(getResource()));
        context.getUser().setProperty("ExamGrid.background", String.valueOf(getBackground()));
        context.getUser().setProperty("ExamGrid.filter", String.valueOf(getFilter()));
        context.getUser().setProperty("ExamGrid.dispMode", String.valueOf(getDispMode()));
        context.getUser().setProperty("ExamGrid.order", String.valueOf(getOrder()));
        context.getUser().setProperty("ExamGrid.bgPref", getBgPreferences() ? "1" : "0");
        context.setAttribute("Exam.Type", getExamType());
        context.getUser().setProperty("ExamReport.showSections", getShowSections() ? "1" : "0");
    }

    public Vector<ComboBoxLookup> getDates(int examType) {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        ret.addElement(new ComboBoxLookup("All Dates", String.valueOf(Integer.MIN_VALUE)));
        HashSet added = new HashSet();
        SimpleDateFormat df = new SimpleDateFormat("MM/dd");
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            Calendar cal = Calendar.getInstance(Locale.US);
            cal.setTime(period.getStartDate());
            int week = 1;
            while (cal.getTime().after(iSessionBeginDate) && cal.get(Calendar.WEEK_OF_YEAR) != iSessionBeginWeek) {
            	cal.add(Calendar.DAY_OF_YEAR, -7); week ++;
            }
            while (cal.getTime().before(iSessionBeginDate) && cal.get(Calendar.WEEK_OF_YEAR) != iSessionBeginWeek) {
            	cal.add(Calendar.DAY_OF_WEEK, 7); week --;
            }
            cal.setTime(period.getStartDate());
            if (added.add(1000+week)) {
                while (cal.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY) cal.add(Calendar.DAY_OF_YEAR, -1);
                String first = df.format(cal.getTime());
                while (cal.get(Calendar.DAY_OF_WEEK)!=Calendar.SUNDAY) cal.add(Calendar.DAY_OF_YEAR, 1);
                String end = df.format(cal.getTime());
                ret.addElement(new ComboBoxLookup(
                        "Week "+week+" ("+first+" - "+end+")",
                        String.valueOf(1000+week)));
            }
        }
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            if (added.add(period.getDateOffset())) {
                ret.addElement(new ComboBoxLookup(ExamGridTable.sDF.format(period.getStartDate()),period.getDateOffset().toString()));
            }
        }
        return ret;
    }
    
    public int getFirstDate(int examType) {
        int startDate = Integer.MAX_VALUE;
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            startDate = Math.min(startDate, period.getDateOffset());
        }
        return startDate;
    }
    
    public int getLastDate(int examType) {
        int endDate = Integer.MIN_VALUE;
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
        	ExamPeriod period = (ExamPeriod)i.next();
            endDate = Math.max(endDate, period.getDateOffset());
        }
        return endDate;
    }

    
    public int getFirstStart(int examType) {
        int startSlot = -1;
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            if (startSlot<0) startSlot = period.getStartSlot();
            else startSlot = Math.min(startSlot, period.getStartSlot());
        }
        return startSlot;
    }
    
    public int getLastEnd(int examType) {
        int endSlot = -1;
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            if (endSlot<0) endSlot = period.getEndSlot();
            else endSlot = Math.max(endSlot, period.getEndSlot());
        }
        return endSlot;
    }

    public Vector<ComboBoxLookup> getStartTimes(int examType) {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        HashSet added = new HashSet();
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            if (added.add(period.getStartSlot())) {
                ret.addElement(new ComboBoxLookup(period.getStartTimeLabel(), period.getStartSlot().toString()));
            }
        }
        return ret;
    }
    
    public Vector<ComboBoxLookup> getEndTimes(int examType) {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        HashSet added = new HashSet();
        for (Iterator i=iPeriods[examType].iterator();i.hasNext();) {
            ExamPeriod period = (ExamPeriod)i.next();
            if (added.add(period.getEndSlot())) {
                ret.addElement(new ComboBoxLookup(period.getEndTimeLabel(), String.valueOf(period.getEndSlot())));
            }
        }
        return ret;
    }
    
    public Vector<ComboBoxLookup> getResources() {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        for (int i=0;i<ExamGridTable.sResources.length;i++)
            ret.addElement(new ComboBoxLookup(ExamGridTable.sResources[i], String.valueOf(i)));
        return ret;
    }

    public Vector<ComboBoxLookup> getBackgrounds() {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        for (int i=0;i<ExamGridTable.sBackgrounds.length;i++)
            ret.addElement(new ComboBoxLookup(ExamGridTable.sBackgrounds[i], String.valueOf(i)));
        return ret;
    }

    public Vector<ComboBoxLookup> getDispModes() {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        for (int i=0;i<ExamGridTable.sDispModes.length;i++)
            ret.addElement(new ComboBoxLookup(ExamGridTable.sDispModes[i], String.valueOf(i)));
        return ret;
    }

    public Vector<ComboBoxLookup> getOrders() {
        Vector<ComboBoxLookup> ret = new Vector<ComboBoxLookup>();
        for (int i=0;i<ExamGridTable.sOrders.length;i++)
            ret.addElement(new ComboBoxLookup(ExamGridTable.sOrders[i], String.valueOf(i)));
        return ret;
    }

    public int getExamType() { return iExamType; }
    public void setExamType(int type) { iExamType = type; }
    public Collection getExamTypes() {
    	Vector ret = new Vector(Exam.sExamTypes.length);
        for (int i=0;i<Exam.sExamTypes.length;i++) {
            if (i==Exam.sExamTypeMidterm && !iHasMidtermExams) continue;
            ret.add(new ComboBoxLookup(Exam.sExamTypes[i], String.valueOf(i)));
        }
    	return ret;
    }
    
    public int getSessionBeginWeek() { return iSessionBeginWeek; }
    public Date getSessionBeginDate() { return iSessionBeginDate; }
    public boolean getShowSections() { return iShowSections; }
    public void setShowSections(boolean showSections) { iShowSections = showSections; }
}
