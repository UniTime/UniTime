/*
 * UniTime 3.4 (University Timetabling Application)
 * Copyright (C) 2012, UniTime LLC, and individual contributors
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
package org.unitime.timetable.server.admin;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.TreeSet;

import net.sf.cpsolver.ifs.util.ToolBox;

import org.hibernate.Session;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.SimpleEditInterface;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Field;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.FieldType;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Flag;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.PageName;
import org.unitime.timetable.gwt.shared.SimpleEditInterface.Record;
import org.unitime.timetable.model.ChangeLog;
import org.unitime.timetable.model.DepartmentalInstructor;
import org.unitime.timetable.model.Student;
import org.unitime.timetable.model.StudentGroup;
import org.unitime.timetable.model.ChangeLog.Operation;
import org.unitime.timetable.model.ChangeLog.Source;
import org.unitime.timetable.model.dao.SessionDAO;
import org.unitime.timetable.model.dao.StudentGroupDAO;
import org.unitime.timetable.security.SessionContext;
import org.unitime.timetable.security.rights.Right;

@Service("gwtAdminTable[type=group]")
public class StudentGroups implements AdminTable {
	protected static final GwtMessages MESSAGES = Localization.create(GwtMessages.class);
	
	@Override
	public PageName name() {
		return new PageName(MESSAGES.pageStudentGroup(), MESSAGES.pageStudentGroups());
	}

	@Override
	@PreAuthorize("checkPermission('StudentGroups')")
	public SimpleEditInterface load(SessionContext context, Session hibSession) {
		SimpleEditInterface data = new SimpleEditInterface(
				new Field(MESSAGES.fieldExternalId(), FieldType.text, 120, 40, Flag.READ_ONLY),
				new Field(MESSAGES.fieldCode(), FieldType.text, 80, 10, Flag.UNIQUE),
				new Field(MESSAGES.fieldName(), FieldType.text, 300, 50, Flag.UNIQUE),
				new Field(MESSAGES.fieldStudents(), FieldType.students, 200));
		data.setSortBy(1,2);
		for (StudentGroup group: StudentGroupDAO.getInstance().findBySession(hibSession, context.getUser().getCurrentAcademicSessionId())) {
			Record r = data.addRecord(group.getUniqueId());
			r.setField(0, group.getExternalUniqueId());
			r.setField(1, group.getGroupAbbreviation());
			r.setField(2, group.getGroupName());
			String students = "";
			for (Student student: new TreeSet<Student>(group.getStudents())) {
				if (!students.isEmpty()) students += "\n";
				students += student.getExternalUniqueId() + " " + student.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle);
			}
			r.setField(3, students, group.getExternalUniqueId() == null);
			r.setDeletable(group.getExternalUniqueId() == null);
		}
		data.setEditable(context.hasPermission(Right.StudentGroupEdit));
		return data;
	}

	@Override
	@PreAuthorize("checkPermission('StudentGroupEdit')")
	public void save(SimpleEditInterface data, SessionContext context, Session hibSession) {
		for (StudentGroup group: StudentGroupDAO.getInstance().findBySession(hibSession, context.getUser().getCurrentAcademicSessionId())) {
			Record r = data.getRecord(group.getUniqueId());
			if (r == null) {
				ChangeLog.addChange(hibSession,
						context,
						group,
						group.getGroupAbbreviation() + " " + group.getGroupName(),
						Source.SIMPLE_EDIT, 
						Operation.DELETE,
						null,
						null);
				hibSession.delete(group);
			} else 
				update(group, r, context, hibSession);
		}
		for (Record r: data.getNewRecords())
			save(r, context, hibSession);
	}

	@Override
	@PreAuthorize("checkPermission('StudentGroupEdit')")
	public void save(Record record, SessionContext context, Session hibSession) {
		StudentGroup group = new StudentGroup();
		group.setExternalUniqueId(record.getField(0));
		group.setGroupAbbreviation(record.getField(1));
		group.setGroupName(record.getField(2));
		group.setSession(SessionDAO.getInstance().get(context.getUser().getCurrentAcademicSessionId(), hibSession));
		group.setStudents(new HashSet<Student>());
		if (record.getField(3) != null) {
			String students = "";
			for (String s: record.getField(3).split("\\n")) {
				if (s.indexOf(' ') >= 0) s = s.substring(0, s.indexOf(' '));
				if (s.trim().isEmpty()) continue;
				Student student = Student.findByExternalId(context.getUser().getCurrentAcademicSessionId(), s.trim());
				if (student != null) {
					group.getStudents().add(student);
					if (!students.isEmpty()) students += "\n";
					students += student.getExternalUniqueId() + " " + student.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle);
				}
			}
			record.setField(3, students, true);
		}
		record.setUniqueId((Long)hibSession.save(group));
		ChangeLog.addChange(hibSession,
				context,
				group,
				group.getGroupAbbreviation() + " " + group.getGroupName(),
				Source.SIMPLE_EDIT, 
				Operation.CREATE,
				null,
				null);
	}
	
	protected void update(StudentGroup group, Record record, SessionContext context, Session hibSession) {
		if (group == null) return;
		boolean changed = 
				!ToolBox.equals(group.getExternalUniqueId(), record.getField(0)) ||
				!ToolBox.equals(group.getGroupAbbreviation(), record.getField(1)) ||
				!ToolBox.equals(group.getGroupName(), record.getField(2));
			group.setExternalUniqueId(record.getField(0));
			group.setGroupAbbreviation(record.getField(1));
			group.setGroupName(record.getField(2));
			if (group.getExternalUniqueId() == null && record.getField(3) != null) {
				Hashtable<String, Student> students = new Hashtable<String, Student>();
				for (Student s: group.getStudents())
					students.put(s.getExternalUniqueId(), s);
				for (String line: record.getField(3).split("\\n")) {
					String extId = (line.indexOf(' ') >= 0 ? line.substring(0, line.indexOf(' ')) : line).trim();
					if (extId.isEmpty() || students.remove(extId) != null) continue;
					Student student = Student.findByExternalId(context.getUser().getCurrentAcademicSessionId(), extId);
					if (student != null) {
						group.getStudents().add(student);
						changed = true;
					}
				}
				if (!students.isEmpty()) {
					group.getStudents().removeAll(students.values());
					changed = true;
				}
				String newStudents = "";
				for (Student student: new TreeSet<Student>(group.getStudents())) {
					if (!newStudents.isEmpty()) newStudents += "\n";
					newStudents += student.getExternalUniqueId() + " " + student.getName(DepartmentalInstructor.sNameFormatLastFirstMiddle);
				}
				record.setField(3, newStudents, group.getExternalUniqueId() == null);
			}
			hibSession.saveOrUpdate(group);
			if (changed)
				ChangeLog.addChange(hibSession,
						context,
						group,
						group.getGroupAbbreviation() + " " + group.getGroupName(),
						Source.SIMPLE_EDIT, 
						Operation.UPDATE,
						null,
						null);
	}

	@Override
	@PreAuthorize("checkPermission('StudentGroupEdit')")
	public void update(Record record, SessionContext context, Session hibSession) {
		update(StudentGroupDAO.getInstance().get(record.getUniqueId()), record, context, hibSession);
	}

	protected void delete(StudentGroup group, SessionContext context, Session hibSession) {
		if (group == null) return;
		ChangeLog.addChange(hibSession,
				context,
				group,
				group.getGroupAbbreviation() + " " + group.getGroupName(),
				Source.SIMPLE_EDIT, 
				Operation.DELETE,
				null,
				null);
		hibSession.delete(group);
	}
	
	@Override
	@PreAuthorize("checkPermission('StudentGroupEdit')")
	public void delete(Record record, SessionContext context, Session hibSession) {
		delete(StudentGroupDAO.getInstance().get(record.getUniqueId()), context, hibSession);		
	}

}
