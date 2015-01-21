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
package org.unitime.timetable.model;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.unitime.timetable.model.base.BaseSavedHQL;
import org.unitime.timetable.model.dao.SavedHQLDAO;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.security.UserContext;

/**
 * @author Tomas Muller, Stephanie Schluttenhofer
 */
public class SavedHQL extends BaseSavedHQL {
	private static final long serialVersionUID = 2532519378106863655L;

	public SavedHQL() {
		super();
	}
	
	public static enum Flag {
		APPEARANCE_COURSES("Appearance: Courses", "courses"),
		APPEARANCE_EXAMS("Appearance: Examinations", "exams"),
		APPEARANCE_SECTIONING("Appearance: Student Sectioning", "sectioning"),
		APPEARANCE_EVENTS("Appearance: Events", "events"),
		APPEARANCE_ADMINISTRATION("Appearance: Administration", "administration"),
		ADMIN_ONLY("Restrictions: Administrator Only")
		;
		private String iDescription;
		private String iAppearance;
		Flag(String desc, String appearance) { iDescription = desc; iAppearance = appearance; }
		Flag(String desc) { this(desc, null); }
		public int flag() { return 1 << ordinal(); }
		public boolean isSet(int type) { return (type & flag()) != 0; }
		public String description() { return iDescription; }
		public String getAppearance() { return iAppearance; }
	}
	
	private static interface OptionImplementation {
		public Map<Long, String> getValues(UserContext user);
		
	}
	
	public static enum Option {
		SESSION("Academic Session", false, false, new OptionImplementation() {
			@Override
			public Map<Long, String> getValues(UserContext user) {
				Long sessionId = user.getCurrentAcademicSessionId();
				Session session = (sessionId == null ? null : SessionDAO.getInstance().get(sessionId));
				if (session == null) return null;
				Map<Long, String> ret = new Hashtable<Long, String>();
				ret.put(session.getUniqueId(), session.getLabel());
				return ret;
			}
		}),
		DEPARTMENT("Department", true, false, new OptionImplementation() {
			@Override
			public Map<Long, String> getValues(UserContext user) {
				Long sessionId = user.getCurrentAcademicSessionId();
				Session session = (sessionId == null ? null : SessionDAO.getInstance().get(sessionId));
				if (session == null) return null;
				TimetableManager manager = TimetableManager.findByExternalId(user.getExternalUserId());
				if (manager == null) return null;
				Map<Long, String> ret = new Hashtable<Long, String>();
				for (Department d: Department.getUserDepartments(user))
					ret.put(d.getUniqueId(), d.htmlLabel());
				return ret;
			}
		}),
		DEPARTMENTS("Departments", true, true, DEPARTMENT.iImplementation),
		SUBJECT("Subject Area", true, false, new OptionImplementation() {
			@Override
			public Map<Long, String> getValues(UserContext user) {
				Map<Long, String> ret = new Hashtable<Long, String>();
				try {
					for (SubjectArea s: SubjectArea.getUserSubjectAreas(user)) {
						ret.put(s.getUniqueId(), s.getSubjectAreaAbbreviation());
					}
				} catch (Exception e) { return null; }
				return ret;
			}
		}),
		SUBJECTS("Subject Areas", true, true, SUBJECT.iImplementation),
		BUILDING("Building", true, false, new OptionImplementation() {
			@Override
			public Map<Long, String> getValues(UserContext user) {
				Long sessionId = user.getCurrentAcademicSessionId();
				Session session = (sessionId == null ? null : SessionDAO.getInstance().get(sessionId));
				TimetableManager manager = TimetableManager.findByExternalId(user.getExternalUserId());
				if (manager == null) return null;
				Map<Long, String> ret = new Hashtable<Long, String>();
				for (Building b: (List<Building>)Building.findAll(session.getUniqueId()))
					ret.put(b.getUniqueId(), b.getAbbrName());
				return ret;
			}
		}),
		BUILDINGS("Buildings", true, true, BUILDING.iImplementation),
		ROOM("Room", true, false, new OptionImplementation() {
			@Override
			public Map<Long, String> getValues(UserContext user) {
				Long sessionId = user.getCurrentAcademicSessionId();
				Session session = (sessionId == null ? null : SessionDAO.getInstance().get(sessionId));
				TimetableManager manager = TimetableManager.findByExternalId(user.getExternalUserId());
				if (manager == null) return null;
				Map<Long, String> ret = new Hashtable<Long, String>();
				for (Room r: (List<Room>)Room.findAllRooms(session.getUniqueId())){
					ret.put(r.getUniqueId(), r.getLabel());
				}
				return ret;
			}
		}),
		ROOMS("Rooms", true, true, ROOM.iImplementation)
		;
		
		String iName;
		OptionImplementation iImplementation;
		boolean iAllowSelection, iMultiSelect;
		Option(String name, boolean allowSelection, boolean multiSelect ,OptionImplementation impl) {
			iName = name;
			iAllowSelection = allowSelection; iMultiSelect = multiSelect;
			iImplementation = impl;
		}
		
		public String text() { return iName; }
		public boolean allowSingleSelection() { return iAllowSelection; }
		public boolean allowMultiSelection() { return iAllowSelection && iMultiSelect; }
		public Map<Long, String> values(UserContext user) { return iImplementation.getValues(user); }
	}
	
	public static void main(String args[]) {
		for (Flag f: Flag.values()) {
			System.out.println(f.name() + ": " + f.flag() + " (" + f.isSet(0xFF) + ")");
		}
	}
	
	public boolean isSet(Flag f) {
		return getType() != null && f.isSet(getType());
	}
	
	public void set(Flag f) {
		if (!isSet(f))
			setType((getType() == null ? 0 : getType()) + f.flag());
	}
	
	public void clear(Flag f) {
		if (isSet(f)) setType(getType() - f.flag());
	}
	
	public static List<SavedHQL> listAll(org.hibernate.Session hibSession, Flag appearance, boolean admin) {
		if (admin) {
			return (List<SavedHQL>)(hibSession == null ? SavedHQLDAO.getInstance().getSession() : hibSession).createQuery(
					"from SavedHQL q where bit_and(q.type, :flag) > 0 order by q.name")
					.setInteger("flag", appearance.flag())
					.setCacheable(true).list();
		} else {
			return (List<SavedHQL>)(hibSession == null ? SavedHQLDAO.getInstance().getSession() : hibSession).createQuery(
					"from SavedHQL q where bit_and(q.type, :flag) > 0 and bit_and(q.type, :admin) = 0 order by q.name")
					.setInteger("flag", appearance.flag())
					.setInteger("admin", Flag.ADMIN_ONLY.flag())
					.setCacheable(true).list();
		}
	}
	
	public static boolean hasQueries(Flag appearance, boolean admin) {
		if (admin) {
			return ((Number)SavedHQLDAO.getInstance().getSession().createQuery(
					"select count(q) from SavedHQL q where bit_and(q.type, :flag) > 0")
					.setInteger("flag", appearance.flag())
					.setCacheable(true).uniqueResult()).intValue() > 0;
		} else {
			return ((Number)SavedHQLDAO.getInstance().getSession().createQuery(
					"select count(q) from SavedHQL q where bit_and(q.type, :flag) > 0 and bit_and(q.type, :admin) = 0")
					.setInteger("flag", appearance.flag())
					.setInteger("admin", Flag.ADMIN_ONLY.flag())
					.setCacheable(true).uniqueResult()).intValue() > 0;
		}
	}

}
