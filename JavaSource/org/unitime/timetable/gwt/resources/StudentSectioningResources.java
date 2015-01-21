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
package org.unitime.timetable.gwt.resources;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

/**
 * @author Tomas Muller
 */
public interface StudentSectioningResources extends ClientBundle {
	@Source("org/unitime/timetable/gwt/resources/icons/up_Down.png")
	ImageResource up_Down();

	@Source("org/unitime/timetable/gwt/resources/icons/up.png")
	ImageResource up();

	@Source("org/unitime/timetable/gwt/resources/icons/up_Over.png")
	ImageResource up_Over();

	@Source("org/unitime/timetable/gwt/resources/icons/down_Down.png")
	ImageResource down_Down();

	@Source("org/unitime/timetable/gwt/resources/icons/down.png")
	ImageResource down();

	@Source("org/unitime/timetable/gwt/resources/icons/down_Over.png")
	ImageResource down_Over();

	@Source("org/unitime/timetable/gwt/resources/icons/search_picker_Disabled.png")
	ImageResource search_picker_Disabled();

	@Source("org/unitime/timetable/gwt/resources/icons/search_picker_Down.png")
	ImageResource search_picker_Down();

	@Source("org/unitime/timetable/gwt/resources/icons/search_picker_Normal.png")
	ImageResource search_picker_Normal();

	@Source("org/unitime/timetable/gwt/resources/icons/search_picker_Over.png")
	ImageResource search_picker_Over();

	@Source("org/unitime/timetable/gwt/resources/icons/search_picker.png")
	ImageResource search_picker();
	
	@Source("org/unitime/timetable/gwt/resources/icons/roadrunner16.png")
	ImageResource distantConflict();
	
	@Source("org/unitime/timetable/gwt/resources/icons/tick.png")
	ImageResource saved();
	
	@Source("org/unitime/timetable/gwt/resources/icons/lock.png")
	ImageResource locked();

	@Source("org/unitime/timetable/gwt/resources/icons/lock_unlock.png")
	ImageResource unlocked();
	
	@Source("org/unitime/timetable/gwt/resources/icons/warning.png")
	ImageResource courseLocked();
	
	@Source("org/unitime/timetable/gwt/resources/icons/error.png")
	ImageResource error();

	@Source("org/unitime/timetable/gwt/resources/icons/printer.png")
	ImageResource print();

	@Source("org/unitime/timetable/gwt/resources/icons/letter.png")
	ImageResource email();
	
	@Source("org/unitime/timetable/gwt/resources/icons/date.png")
	ImageResource calendar();

	@Source("org/unitime/timetable/gwt/resources/icons/comments.png")
	ImageResource comments();

	@Source("org/unitime/timetable/gwt/resources/icons/group.png")
	ImageResource highDemand();
	
	@Source("org/unitime/timetable/gwt/resources/icons/action_delete.png")
	ImageResource unassignment();

	@Source("org/unitime/timetable/gwt/resources/icons/action_add.png")
	ImageResource assignment();

	@Source("org/unitime/timetable/gwt/resources/icons/expand_node_btn.gif")
	ImageResource treeClosed();

	@Source("org/unitime/timetable/gwt/resources/icons/collapse_node_btn.gif")
	ImageResource treeOpen();
	
	@Source("org/unitime/timetable/gwt/resources/icons/loading_small.gif")
	ImageResource loading_small();

	@Source("org/unitime/timetable/gwt/resources/icons/note.png")
	ImageResource note();
	
	@Source("org/unitime/timetable/gwt/resources/icons/overlap.png")
	ImageResource overlap();
	
	@Source("org/unitime/timetable/gwt/resources/icons/delete_Down.png")
	ImageResource delete_Down();

	@Source("org/unitime/timetable/gwt/resources/icons/delete.png")
	ImageResource delete();

	@Source("org/unitime/timetable/gwt/resources/icons/delete_Over.png")
	ImageResource delete_Over();
}
