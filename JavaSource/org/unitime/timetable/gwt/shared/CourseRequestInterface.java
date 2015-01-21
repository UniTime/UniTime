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
package org.unitime.timetable.gwt.shared;

import java.io.Serializable;
import java.util.ArrayList;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * @author Tomas Muller
 */
public class CourseRequestInterface implements IsSerializable, Serializable {
	private static final long serialVersionUID = 1L;
	private Long iSessionId, iStudentId;
	private ArrayList<Request> iCourses = new ArrayList<Request>();
	private ArrayList<Request> iAlternatives = new ArrayList<Request>();
	private boolean iSaved = false;
	private boolean iNoChange = false;
	
	public CourseRequestInterface() {}

	public Long getAcademicSessionId() { return iSessionId; }
	public void setAcademicSessionId(Long sessionId) { iSessionId = sessionId; }
	
	public Long getStudentId() { return iStudentId; }
	public void setStudentId(Long studentId) { iStudentId = studentId; }
	
	public ArrayList<Request> getCourses() { return iCourses; }
	public ArrayList<Request> getAlternatives() { return iAlternatives; }
	
	public boolean isSaved() { return iSaved; }
	public void setSaved(boolean saved) { iSaved = saved; }
	
	public boolean isNoChange() { return iNoChange; }
	public void setNoChange(boolean noChange) { iNoChange = noChange; }
	
	public static class FreeTime implements IsSerializable, Serializable {
		private static final long serialVersionUID = 1L;
		private ArrayList<Integer> iDays = new ArrayList<Integer>();
		private int iStart;
		private int iLength;
		public FreeTime() {}
		
		public void addDay(int day) { iDays.add(day); }
		public ArrayList<Integer> getDays() { return iDays; }
		public String getDaysString(String[] shortDays, String separator) {
			if (iDays == null) return "";
			String ret = "";
			for (int day: iDays)
				ret += (ret.isEmpty() ? "" : separator) + shortDays[day];
			return ret;
		}
		
		public int getStart() { return iStart; }
		public void setStart(int startSlot) { iStart = startSlot; }
		public String getStartString(boolean useAmPm) {
	        int h = iStart / 12;
	        int m = 5 * (iStart % 12);
	        if (useAmPm)
	        	return (h > 12 ? h - 12 : h) + ":" + (m < 10 ? "0" : "") + m + (h == 24 ? "a" : h >= 12 ? "p" : "a");
	        else
				return h + ":" + (m < 10 ? "0" : "") + m;
		}

		
		public int getLength() { return iLength; }
		public void setLength(int length) { iLength = length; }
		public String getEndString(boolean useAmPm) {
			int h = (iStart + iLength) / 12;
			int m = 5 * ((iStart + iLength) % 12);
	        if (useAmPm)
	        	return (h > 12 ? h - 12 : h) + ":" + (m < 10 ? "0" : "") + m + (h == 24 ? "a" : h >= 12 ? "p" : "a");
	        else
				return h + ":" + (m < 10 ? "0" : "") + m;
		}
		
		public String toString(String[] shortDays, boolean useAmPm) {
			return getDaysString(shortDays, "") + " " + getStartString(useAmPm) + " - " + getEndString(useAmPm);
		}
		
		public String toString() {
			return "Free " + toString(new String[] {"M", "T", "W", "R", "F", "S", "U"}, true);
		}
		
		public String toAriaString(String[] longDays, boolean useAmPm) {
	        int h = iStart / 12;
	        int m = 5 * (iStart % 12);
	        String ret = getDaysString(longDays, " ") + " from ";
	        if (useAmPm)
	        	ret += (h > 12 ? h - 12 : h) + (m == 0 ? "" : (m < 10 ? " 0" : " ") + m) + (h == 24 ? " AM" : h >= 12 ? " PM" : " AM");
	        else
	        	ret += h + " " + (m < 10 ? "0" : "") + m;
	        h = (iStart + iLength) / 12;
			m = 5 * ((iStart + iLength) % 12);
			ret += " to ";
	        if (useAmPm)
	        	ret += (h > 12 ? h - 12 : h) + (m == 0 ? "" : (m < 10 ? " 0" : " ") + m) + (h == 24 ? " AM" : h >= 12 ? " PM" : " AM");
	        else
	        	ret += h + " " + (m < 10 ? "0" : "") + m;
	        return ret;  
		}
	}
	
	public static class Request implements IsSerializable, Serializable {
		private static final long serialVersionUID = 1L;
		private ArrayList<FreeTime> iRequestedFreeTime = null;
		private String iRequestedCourse = null;
		private String iFirstAlternative = null;
		private String iSecondAlternative = null;;
		private Boolean iWaitList = false;
		
		public Request() {}
		
		public String getRequestedCourse() { return iRequestedCourse; }
		public void setRequestedCourse(String requestedCourse) { iRequestedCourse = requestedCourse; }
		public boolean hasRequestedCourse() { return iRequestedCourse != null && !iRequestedCourse.isEmpty(); }

		public ArrayList<FreeTime> getRequestedFreeTime() { return iRequestedFreeTime; }
		public void addRequestedFreeTime(FreeTime ft) { 
			if (iRequestedFreeTime == null)
				iRequestedFreeTime = new ArrayList<FreeTime>();
			iRequestedFreeTime.add(ft);
		}
		public boolean hasRequestedFreeTime() { return iRequestedFreeTime != null && !iRequestedFreeTime.isEmpty(); }
		
		public String getFirstAlternative() { return iFirstAlternative; }
		public void setFirstAlternative(String firstAlternative) { iFirstAlternative = firstAlternative; }
		public boolean hasFirstAlternative() { return iFirstAlternative != null && !iFirstAlternative.isEmpty(); }
		
		public String getSecondAlternative() { return iSecondAlternative; }
		public void setSecondAlternative(String secondAlternative) { iSecondAlternative = secondAlternative; }
		public boolean hasSecondAlternative() { return iSecondAlternative != null && !iSecondAlternative.isEmpty(); }
		
		public boolean hasWaitList() { return iWaitList != null; }
		public boolean isWaitList() { return iWaitList != null && iWaitList.booleanValue(); }
		public void setWaitList(Boolean waitList) { iWaitList = waitList; }
		
		public String toString() {
			return (hasRequestedFreeTime() ? iRequestedFreeTime.toString() : hasRequestedCourse() ? iRequestedCourse : "-") +
				(hasFirstAlternative() ? ", " + iFirstAlternative : "") +
				(hasSecondAlternative() ? ", " + iSecondAlternative : "") +
				(isWaitList() ? " (w)" : "");
		}
	}
	
	public String toString() {
		String ret = "CourseRequests(student = " + iStudentId + ", session = " + iSessionId + ", requests = {";
		int idx = 1;
		for (Request r: iCourses)
			ret += "\n   " + (idx++) + ". " + r;
		idx = 1;
		for (Request r: iAlternatives)
			ret += "\n  A" + (idx++) + ". " + r;
		return ret + "\n})";
		
	}
}
