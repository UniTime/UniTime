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

import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.studentsct.model.Request;
import net.sf.cpsolver.studentsct.model.Section;

/**
 * @author Tomas Muller
 */
public class PercentageOverExpected implements OverExpectedCriterion {
	private double iPercentage = 1.05; 
	
	public PercentageOverExpected(DataProperties config) {
		iPercentage = config.getPropertyDouble("OverExpected.Percentage", iPercentage);
	}
	
	public PercentageOverExpected(double percentage) {
		iPercentage = percentage;
	}

	@Override
	public boolean isOverExpected(Section section, Request request) {
		return section.getLimit() <= 0 ? false : Math.round(section.getEnrollmentWeight(request) + iPercentage * section.getSpaceExpected() + request.getWeight()) > section.getLimit();
	}

}
