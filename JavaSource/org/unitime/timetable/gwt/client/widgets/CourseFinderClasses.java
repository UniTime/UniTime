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
package org.unitime.timetable.gwt.client.widgets;

import java.util.Collection;

import org.unitime.timetable.gwt.resources.GwtAriaMessages;
import org.unitime.timetable.gwt.resources.StudentSectioningConstants;
import org.unitime.timetable.gwt.resources.StudentSectioningMessages;
import org.unitime.timetable.gwt.shared.ClassAssignmentInterface;
import org.unitime.timetable.gwt.shared.ClassAssignmentInterface.ClassAssignment;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;

/**
 * @author Tomas Muller
 */
public class CourseFinderClasses extends WebTable implements CourseFinder.CourseFinderCourseDetails<Collection<ClassAssignmentInterface.ClassAssignment>> {
	protected static final StudentSectioningMessages MESSAGES = GWT.create(StudentSectioningMessages.class);
	protected static final StudentSectioningConstants CONSTANTS = GWT.create(StudentSectioningConstants.class);
	protected static final GwtAriaMessages ARIA = GWT.create(GwtAriaMessages.class);
	
	private String iValue = null;
	private DataProvider<String, Collection<ClassAssignment>> iDataProvider = null;
	
	public CourseFinderClasses() {
		super();
		setHeader(new WebTable.Row(
				new WebTable.Cell(MESSAGES.colSubpart(), 1, "50px"),
				new WebTable.Cell(MESSAGES.colClass(), 1, "90px"),
				new WebTable.Cell(MESSAGES.colLimit(), 1, "60px"),
				new WebTable.Cell(MESSAGES.colDays(), 1, "60px"),
				new WebTable.Cell(MESSAGES.colStart(), 1, "60px"),
				new WebTable.Cell(MESSAGES.colEnd(), 1, "60px"),
				new WebTable.Cell(MESSAGES.colDate(), 1, "100px"),
				new WebTable.Cell(MESSAGES.colRoom(), 1, "100px"),
				new WebTable.Cell(MESSAGES.colInstructor(), 1, "120px"),
				new WebTable.Cell(MESSAGES.colParent(), 1, "90px"),
				new WebTable.Cell(MESSAGES.colHighDemand(), 1, "10px"),
				new WebTable.Cell(MESSAGES.colNoteIcon(), 1, "10px")
			));
		setEmptyMessage(MESSAGES.courseSelectionNoCourseSelected());
	}

	@Override
	public Widget asWidget() {
		return this;
	}

	@Override
	public void setValue(String value) {
		if (value == null || value.isEmpty()) {
			iValue = value;
			setEmptyMessage(MESSAGES.courseSelectionNoCourseSelected());
			clearData(true);
		} else if (!value.equals(iValue)) {
			iValue = value;
			setEmptyMessage(MESSAGES.courseSelectionLoadingClasses());
			clearData(true);
			iDataProvider.getData(value, new AsyncCallback<Collection<ClassAssignmentInterface.ClassAssignment>>() {
				public void onFailure(Throwable caught) {
					setEmptyMessage(caught.getMessage());
				}
				public void onSuccess(Collection<ClassAssignmentInterface.ClassAssignment> result) {
					if (!result.isEmpty()) {
						WebTable.Row[] rows = new WebTable.Row[result.size()];
						int idx = 0;
						Long lastSubpartId = null;
						for (ClassAssignmentInterface.ClassAssignment clazz: result) {
							WebTable.Row row = null;
							if (clazz.isAssigned()) {
								row = new WebTable.Row(
										new WebTable.Cell(clazz.getSubpart()),
										new WebTable.Cell(clazz.getSection()),
										new WebTable.Cell(clazz.getLimitString()),
										new WebTable.Cell(clazz.getDaysString(CONSTANTS.shortDays())),
										new WebTable.Cell(clazz.getStartString(CONSTANTS.useAmPm())),
										new WebTable.Cell(clazz.getEndString(CONSTANTS.useAmPm())),
										new WebTable.Cell(clazz.getDatePattern()),
										new WebTable.Cell(clazz.getRooms(", ")),
										new WebTable.Cell(clazz.getInstructors(", ")),
										new WebTable.Cell(clazz.getParentSection()),
										(clazz.isSaved() ? new WebTable.IconCell(RESOURCES.saved(), null, null) : clazz.isOfHighDemand() ? new WebTable.IconCell(RESOURCES.highDemand(), MESSAGES.highDemand(clazz.getExpected(), clazz.getAvailableLimit()), null) : new WebTable.Cell("")),
										clazz.hasNote() ? new WebTable.IconCell(RESOURCES.note(), clazz.getNote(), "") : new WebTable.Cell(""));
							} else {
								row = new WebTable.Row(
										new WebTable.Cell(clazz.getSubpart()),
										new WebTable.Cell(clazz.getSection()),
										new WebTable.Cell(clazz.getLimitString()),
										new WebTable.Cell(MESSAGES.arrangeHours(), 4, null),
										new WebTable.Cell(clazz.getRooms(", ")),
										new WebTable.Cell(clazz.getInstructors(", ")),
										new WebTable.Cell(clazz.getParentSection()),
										(clazz.isSaved() ? new WebTable.IconCell(RESOURCES.saved(), null, null) : clazz.isOfHighDemand() ? new WebTable.IconCell(RESOURCES.highDemand(), MESSAGES.highDemand(clazz.getExpected(), clazz.getAvailableLimit()), null) : new WebTable.Cell("")),
										clazz.hasNote() ? new WebTable.IconCell(RESOURCES.note(), clazz.getNote(), "") : new WebTable.Cell(""));

							}
							row.setId(clazz.getClassId().toString());
							String styleName = "";
							if (lastSubpartId != null && !clazz.getSubpartId().equals(lastSubpartId))
								styleName += "top-border-dashed";
							if (!clazz.isSaved() && !clazz.isAvailable())
								styleName += " text-gray";
							for (WebTable.Cell cell: row.getCells())
								cell.setStyleName(styleName.trim());
							rows[idx++] = row;
							lastSubpartId = clazz.getSubpartId();
							if (!clazz.isSaved() && !clazz.isAvailable())
								row.setAriaLabel(ARIA.courseFinderClassNotAvailable(
										MESSAGES.clazz(clazz.getSubject(), clazz.getCourseNbr(), clazz.getSubpart(), clazz.getSection()),
										clazz.isAssigned() ? clazz.getTimeStringAria(CONSTANTS.longDays(), CONSTANTS.useAmPm(), ARIA.arrangeHours()) + " " + clazz.getRooms(",") : ARIA.arrangeHours()));
							else
								row.setAriaLabel(ARIA.courseFinderClassAvailable(
										MESSAGES.clazz(clazz.getSubject(), clazz.getCourseNbr(), clazz.getSubpart(), clazz.getSection()),
										clazz.isAssigned() ? clazz.getTimeStringAria(CONSTANTS.longDays(), CONSTANTS.useAmPm(), ARIA.arrangeHours()) + " " + clazz.getRooms(",") : ARIA.arrangeHours(),
										clazz.getLimitString()));
						}
						setData(rows);
					} else {
						setEmptyMessage(MESSAGES.courseSelectionNoClasses(iValue));
					}
				}
			});
		}
	}

	@Override
	public String getValue() {
		return iValue;
	}

	@Override
	public void setDataProvider(DataProvider<String, Collection<ClassAssignment>> provider) {
		iDataProvider = provider;
	}

	@Override
	public String getName() {
		return MESSAGES.courseSelectionClasses();
	}
}
