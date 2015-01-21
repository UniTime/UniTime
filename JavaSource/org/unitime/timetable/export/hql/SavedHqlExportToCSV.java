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
package org.unitime.timetable.export.hql;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.hibernate.MappingException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.type.CollectionType;
import org.hibernate.type.Type;
import org.springframework.stereotype.Service;
import org.unitime.localization.impl.Localization;
import org.unitime.timetable.export.BufferedPrinter;
import org.unitime.timetable.export.CSVPrinter;
import org.unitime.timetable.export.ExportHelper;
import org.unitime.timetable.export.Exporter;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.PageAccessException;
import org.unitime.timetable.gwt.shared.SavedHQLException;
import org.unitime.timetable.gwt.shared.SavedHQLInterface;
import org.unitime.timetable.model.SavedHQL;
import org.unitime.timetable.model.dao.SavedHQLDAO;
import org.unitime.timetable.model.dao._RootDAO;
import org.unitime.timetable.security.UserContext;

/**
 * @author Tomas Muller
 */
@Service("org.unitime.timetable.export.Exporter:hql-report.csv")
public class SavedHqlExportToCSV implements Exporter {
	protected static GwtMessages MESSAGES = Localization.create(GwtMessages.class);
	private static Logger sLog = Logger.getLogger(SavedHqlExportToCSV.class);
	
	@Override
	public String reference() {
		return "hql-report.csv";
	}

	@Override
	public void export(ExportHelper helper) throws IOException {
		// Check rights
		// FIXME: helper.getSessionContext().checkPermission(Right.???);
		
		// Retrive report
		String report = helper.getParameter("report");
		if (report == null) throw new IllegalArgumentException("No report provided, please set the report parameter.");
		SavedHQL hql = SavedHQLDAO.getInstance().get(Long.valueOf(report));
		if (hql == null) throw new IllegalArgumentException("Report " + report + " does not exist.");
		
		SavedHQLInterface.Query q = new SavedHQLInterface.Query();
		q.setName(hql.getName());
		q.setId(hql.getUniqueId());
		q.setQuery(hql.getQuery());
		q.setFlags(hql.getType());
		q.setDescription(hql.getDescription());
		
		List<SavedHQLInterface.IdValue> params = new ArrayList<SavedHQLInterface.IdValue>();
		if (helper.getParameter("params") != null) {
			String[] p = helper.getParameter("params").split(":");
			int i = 0;
			for (SavedHQL.Option o: SavedHQL.Option.values()) {
				if (!o.allowSingleSelection() && !o.allowMultiSelection()) continue;
				if (q.getQuery().contains("%" + o.name() + "%")) {
					SavedHQLInterface.IdValue v = new SavedHQLInterface.IdValue();
					v.setValue(o.name());
					v.setText(i < p.length ? p[i] : "");
					params.add(v);
					i++;
				}
			}
		}
		
		BufferedPrinter out = new BufferedPrinter(new CSVPrinter(helper.getWriter(), false));
		helper.setup(out.getContentType(), q.getName().replace('/', '-').replace('\\', '-').replace(':', '-') + ".csv", false);
		
		execute(helper.getSessionContext().getUser(), out, q, params, 0, -1);
		
		String sort = helper.getParameter("sort");
		if (sort != null && !"0".equals(sort)) {
			final boolean asc = Integer.parseInt(sort) > 0;
			final int col = Math.abs(Integer.parseInt(sort)) - 1;
			Collections.sort(out.getBuffer(), new Comparator<String[]>() {
				int compare(String[] a, String[] b, int col) {
					for (int i = 0; i < a.length; i++) {
						int c = (col + i) % a.length;
						try {
							int cmp = Double.valueOf(a[c] == null ? "0" : a[c]).compareTo(Double.valueOf(b[c] == null ? "0" : b[c]));
							if (cmp != 0) return cmp;
						} catch (NumberFormatException e) {
							int cmp = (a[c] == null ? "" : a[c]).compareTo(b[c] == null ? "" : b[c]);
							if (cmp != 0) return cmp;
						}
					}
					return 0;
				}	
				@Override
				public int compare(String[] a, String[] b) {
					return asc ? compare(a, b, col) : compare(b, a, col);
				}
			});
		}
		
		out.close();
	}
	
	public static void execute(UserContext user, Printer out, SavedHQLInterface.Query query, List<SavedHQLInterface.IdValue> options, int fromRow, int maxRows) throws SavedHQLException, PageAccessException {
		try {
			String hql = query.getQuery();
			for (SavedHQL.Option o: SavedHQL.Option.values()) {
				if (hql.indexOf("%" + o.name() + "%") >= 0) {
					String value = null;
					for (SavedHQLInterface.IdValue v: options)
						if (o.name().equals(v.getValue())) { value = v.getText(); break; }
					if (value == null || value.isEmpty()) {
						Map<Long, String> vals = o.values(user);
						if (vals == null || vals.isEmpty())
							throw new SavedHQLException(MESSAGES.errorUnableToSetParameterNoValues(o.name()));
						value = "";
						for (Long id: vals.keySet()) {
							if (!value.isEmpty()) value += ",";
							value += id.toString();
						}
					}
					hql = hql.replace("%" + o.name() + "%", "(" + value + ")");
				}
			}
			org.hibernate.Session hibSession = SavedHQLDAO.getInstance().getSession();
			org.hibernate.Query q = hibSession.createQuery(hql);
			if (maxRows > 0)
				q.setMaxResults(maxRows);
			if (fromRow > 0)
				q.setFirstResult(fromRow);
			q.setCacheable(true);
			int len = -1;
			for (Object o: q.list()) {
				if (len < 0) {
					len = length(o);
					String[] line = new String[len];
					header(line, o, q.getReturnAliases());
					if (line.length > 0 && line[0].startsWith("__")) out.hideColumn(0);
					out.printHeader(line);
				}
				String[] line = new String[len];
				line(line, o, (SessionImplementor)hibSession);
				out.printLine(line);
				out.flush();
			}
		} catch (PageAccessException e) {
			throw e;
		} catch (SavedHQLException e) {
			throw e;
		} catch (Exception e) {
			sLog.error(e.getMessage(), e);
			throw new SavedHQLException(MESSAGES.failedExecution(e.getMessage() + (e.getCause() == null ? "" : " (" + e.getCause().getMessage() + ")")));
		}
	}
	
	private static boolean skip(Type t, boolean lazy) {
        try {
            if (t.isCollectionType()) {
                if (!lazy) return true;
                SessionFactory hibSessionFactory = new _RootDAO().getSession().getSessionFactory();
                Type w = ((CollectionType)t).getElementType((SessionFactoryImplementor)hibSessionFactory);
                Class ts = w.getReturnedClass().getMethod("toString", new Class[]{}).getDeclaringClass();
                return (ts.equals(Object.class) || ts.getName().startsWith("org.unitime.timetable.model.base.Base"));
            }
        } catch (MappingException e) {
            return true;
        } catch (NoSuchMethodException e) {
            return true;
        }
        try {
            Class ts = t.getReturnedClass().getMethod("toString", new Class[]{}).getDeclaringClass();
            return (ts.equals(Object.class) || ts.getName().startsWith("org.unitime.timetable.model.base.Base"));
        } catch (NoSuchMethodException e) {
            return true;
        }
    }
	
    private static int length(Object o) {
    	if (o == null) return 1;
    	int len = 0;
    	if (o instanceof Object[]) {
    		for (Object x: (Object[])o) {
    			if (x == null) {
    				len++;
    			} else {
            		ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(x.getClass());
            		if (meta == null) {
            			len++;
            		} else {
            			if (meta.getIdentifierPropertyName() != null) len++;
            			for (int i=0;i<meta.getPropertyNames().length;i++) {
                            if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
                            	len++;
            			}
            		}
    			}
    		}
    	} else {
    		ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(o.getClass());
    		if (meta == null) {
    			len++;
    		} else {
    			if (meta.getIdentifierPropertyName() != null) len++;
    			for (int i=0;i<meta.getPropertyNames().length;i++) {
                    if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
                    	len++;
    			}
    		}
    	}
    	return len;
    }
    
    private static String format(String column) {
    	if (column == null || column.isEmpty()) return "?";
    	return column.substring(0, 1).toUpperCase() + column.substring(1);
    }
	
	private static void header(String[] ret, Object o, String[] alias) {
		if (o == null) {
			if (alias != null && alias.length >= 1 && alias[0] != null && !alias[0].isEmpty())
				ret[0] = alias[0];
			else
				ret[0] = "Result";
		} else if (o instanceof Object[]) {
			int a = 0, idx = 0;
			for (Object x: (Object[])o) {
				if (x == null) {
					if (alias != null && alias.length > a && alias[a] != null && !alias[a].isEmpty())
						ret[idx++] = alias[a];
					else
						ret[idx++] = "Column" + (a + 1);
				} else {
					ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(x.getClass());
					if (meta == null) {
						if (alias != null && alias.length > a && alias[a] != null && !alias[a].isEmpty())
							ret[idx++] = alias[a];
						else
							ret[idx++] = "Column" + (a + 1);
					} else {
						if (meta.getIdentifierPropertyName() != null)
							ret[idx++] = meta.getIdentifierPropertyName();
		                for (int i = 0; i < meta.getPropertyNames().length; i ++) {
		                    if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
		                    	ret[idx++] = format(meta.getPropertyNames()[i]);
		                }
					}
				}
				a++;					
			}
		} else {
			ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(o.getClass());
			if (meta == null) {
				if (alias != null && alias.length >= 1 && alias[0] != null && !alias[0].isEmpty())
					ret[0] = alias[0];
				else
					ret[0] = "Result";
			} else {
				int idx = 0;
				if (meta.getIdentifierPropertyName() != null)
					ret[idx++] = meta.getIdentifierPropertyName();
                for (int i = 0; i < meta.getPropertyNames().length; i ++) {
                    if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
                    	ret[idx++] = format(meta.getPropertyNames()[i]);
                }
			}
		}
	}
	
	private static String toString(Object o) {
		if (o != null && o instanceof Document) return ((Document)o).asXML();
		return (o == null ? "" : o.toString());
	}
	
	private static void line(String[] ret, Object o, SessionImplementor session) {
		if (o == null) {
			ret[0] = "";
		} else if (o instanceof Object[]) {
			int idx = 0;
			for (Object x: (Object[])o) {
				if (x == null) {
					ret[idx++] = "";
				} else {
					ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(x.getClass());
					if (meta == null) {
						ret[idx++] = toString(x);
					} else {
						if (meta.getIdentifierPropertyName() != null)
							ret[idx++] = toString(meta.getIdentifier(x, session));
		                for (int i = 0; i < meta.getPropertyNames().length; i ++) {
		                    if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
		                    	ret[idx++] = toString(meta.getPropertyValue(x, meta.getPropertyNames()[i]));
		                }
					}
				}
			}
		} else {
			ClassMetadata meta = SavedHQLDAO.getInstance().getSession().getSessionFactory().getClassMetadata(o.getClass());
			if (meta == null) {
				ret[0] = toString(o);
			} else {
				int idx = 0;
				if (meta.getIdentifierPropertyName() != null)
					ret[idx++] = toString(meta.getIdentifier(o, session));
                for (int i = 0; i < meta.getPropertyNames().length; i ++) {
                    if (!skip(meta.getPropertyTypes()[i], meta.getPropertyLaziness()[i]))
                    	ret[idx++] = toString(meta.getPropertyValue(o, meta.getPropertyNames()[i]));
                }
			}
		}
	}

}
