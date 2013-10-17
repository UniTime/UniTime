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
package org.unitime.timetable.onlinesectioning.model;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;

import net.sf.cpsolver.studentsct.model.Request;

import org.unitime.timetable.model.CourseDemand;

/**
 * @author Tomas Muller
 */
public abstract class XRequest implements Serializable, Comparable<XRequest>, Externalizable {
	private static final long serialVersionUID = 1L;
	protected Long iRequestId = null;
	protected int iPriority = 0;
	protected boolean iAlternative = false;
	protected Long iStudentId;
    
    public XRequest() {}
    
    public XRequest(CourseDemand demand) {
    	iRequestId = demand.getUniqueId();
    	iPriority = demand.getPriority();
    	iAlternative = demand.isAlternative();
    	iStudentId = demand.getStudent().getUniqueId();
    }
    
    public XRequest(Request request) {
    	iRequestId = request.getId();
    	iPriority = request.getPriority();
    	iAlternative = request.isAlternative();
    	iStudentId = request.getStudent().getId();
    }

    /** Request id */
    public Long getRequestId() { return iRequestId; }

    /**
     * Request priority -- if there is a choice, request with lower priority is
     * more preferred to be assigned
     */
    public int getPriority() { return iPriority; }

    /**
     * True, if the request is alternative (alternative request can be assigned
     * instead of a non-alternative course requests, if it is left unassigned)
     */
    public boolean isAlternative() { return iAlternative; }

    /** Student to which this request belongs */
    public Long getStudentId() { return iStudentId; }
    
    @Override
    public int hashCode() {
        return (int)(getRequestId() ^ (getRequestId() >>> 32));
    }
    
    @Override
    public int compareTo(XRequest request) {
    	return (isAlternative() != request.isAlternative() ? isAlternative() ? 1 : -1 : getPriority() < request.getPriority() ? -1 : 1);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof XRequest)) return false;
        return getRequestId().equals(((XRequest)o).getRequestId()) && getStudentId().equals(((XRequest)o).getStudentId());
    }
    
    @Override
    public String toString() {
    	return (isAlternative() ? "A" : "") + getPriority() + ".";
    }
    
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		iRequestId = in.readLong();
		iPriority = in.readInt();
		iAlternative = in.readBoolean();
		iStudentId = in.readLong();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(iRequestId);
		out.writeInt(iPriority);
		out.writeBoolean(iAlternative);
		out.writeLong(iStudentId);
	}
}