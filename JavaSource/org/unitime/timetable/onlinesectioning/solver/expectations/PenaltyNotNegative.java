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
package org.unitime.timetable.onlinesectioning.solver.expectations;

import org.cpsolver.ifs.assignment.Assignment;
import org.cpsolver.ifs.util.DataProperties;
import org.cpsolver.studentsct.model.Enrollment;
import org.cpsolver.studentsct.model.Request;
import org.cpsolver.studentsct.model.Section;


/**
 * @author Tomas Muller
 */
public class PenaltyNotNegative implements OverExpectedCriterion {
	
	public PenaltyNotNegative(DataProperties config) {}

	@Override
	public double getOverExpected(Assignment<Request, Enrollment> assignment, Section section, Request request) {
		if (section.getPenalty() < 0) return 0.0;
		int subparts = section.getSubpart().getConfig().getSubparts().size();
		return 1.0 / subparts;
	}

	
	@Override
	public String toString() {
		return "not-negative";
	}

}
