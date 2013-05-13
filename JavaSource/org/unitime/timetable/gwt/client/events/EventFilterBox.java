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
package org.unitime.timetable.gwt.client.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.unitime.timetable.gwt.client.widgets.FilterBox;
import org.unitime.timetable.gwt.client.widgets.TimeSelector;
import org.unitime.timetable.gwt.client.widgets.FilterBox.Chip;
import org.unitime.timetable.gwt.client.widgets.FilterBox.Suggestion;
import org.unitime.timetable.gwt.client.widgets.TimeSelector.TimeUtils;
import org.unitime.timetable.gwt.command.client.GwtRpcResponseList;
import org.unitime.timetable.gwt.command.client.GwtRpcService;
import org.unitime.timetable.gwt.command.client.GwtRpcServiceAsync;
import org.unitime.timetable.gwt.resources.GwtAriaMessages;
import org.unitime.timetable.gwt.resources.GwtConstants;
import org.unitime.timetable.gwt.resources.GwtMessages;
import org.unitime.timetable.gwt.shared.AcademicSessionProvider;
import org.unitime.timetable.gwt.shared.AcademicSessionProvider.AcademicSessionChangeEvent;
import org.unitime.timetable.gwt.shared.AcademicSessionProvider.AcademicSessionChangeHandler;
import org.unitime.timetable.gwt.shared.EventInterface.EventFilterRpcRequest;
import org.unitime.timetable.gwt.shared.EventInterface.FilterRpcResponse;
import org.unitime.timetable.gwt.shared.EventInterface.RequestSessionDetails;
import org.unitime.timetable.gwt.shared.EventInterface.SessionMonth;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.BlurHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.FocusHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;

public class EventFilterBox extends UniTimeFilterBox<EventFilterRpcRequest> {
	private ListBox iSponsors;
	private static final GwtConstants CONSTANTS = GWT.create(GwtConstants.class);
	private static final GwtMessages MESSAGES = GWT.create(GwtMessages.class);
	private static GwtAriaMessages ARIA = GWT.create(GwtAriaMessages.class);
	private static final GwtRpcServiceAsync RPC = GWT.create(GwtRpcService.class);
	private static DateTimeFormat sDateFormat = DateTimeFormat.getFormat(CONSTANTS.eventDateFormat());
	private FilterBox.CustomFilter iOther = null;
	private TextBox iRequested;
	private Chip iLastRequested = null;
	
	public EventFilterBox(AcademicSessionProvider session) {
		super(session);
		
		addFilter(new FilterBox.StaticSimpleFilter("type"));
		
		iSponsors = new ListBox(true);
		iSponsors.setWidth("100%"); iSponsors.setVisibleItemCount(3);
		
		addFilter(new FilterBox.CustomFilter("sponsor", iSponsors) {
			@Override
			public void getSuggestions(List<Chip> chips, String text, AsyncCallback<Collection<Suggestion>> callback) {
				if (text.isEmpty()) {
					callback.onSuccess(null);
				} else {
					List<Suggestion> suggestions = new ArrayList<Suggestion>();
					for (int i = 0; i < iSponsors.getItemCount(); i++) {
						Chip chip = new Chip("sponsor", iSponsors.getValue(i));
						String name = iSponsors.getItemText(i);
						if (iSponsors.getValue(i).toLowerCase().startsWith(text.toLowerCase())) {
							suggestions.add(new Suggestion(name, chip));
						} else if (text.length() > 2 && name.toLowerCase().contains(" " + text.toLowerCase())) {
							suggestions.add(new Suggestion(name, chip));
						}
					}
					callback.onSuccess(suggestions);
				}
			}
			@Override
			public boolean isVisible() {
				return iSponsors.getItemCount() > 0;
			}
		});
		iSponsors.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				boolean changed = false;
				for (int i = 0; i < iSponsors.getItemCount(); i++) {
					Chip chip = new Chip("sponsor", iSponsors.getValue(i));
					if (iSponsors.isItemSelected(i)) {
						if (!hasChip(chip)) {
							addChip(chip, false); changed = true;
						}
					} else {
						if (hasChip(chip)) {
							removeChip(chip, false); changed = true;
						}
					}
				}
				if (changed)
					fireValueChangeEvent();
			}
		});
		
		FilterBox.StaticSimpleFilter mode = new FilterBox.StaticSimpleFilter("mode");
		mode.setMultipleSelection(false);
		addFilter(mode);
		
		Label reqLab = new Label(MESSAGES.propRequestedBy());

		iRequested = new TextBox();
		iRequested.setStyleName("unitime-TextArea");
		iRequested.setMaxLength(100); iRequested.setWidth("200px");
		
		final CheckBox conflicts = new CheckBox(MESSAGES.checkDisplayConflicts());
		conflicts.getElement().getStyle().setMarginLeft(10, Unit.PX);
		
		final CheckBox sessions = new CheckBox(MESSAGES.checkSpanMultipleSessions());
		sessions.getElement().getStyle().setMarginLeft(10, Unit.PX);
		
		iOther = new FilterBox.CustomFilter("other", reqLab, iRequested, conflicts, sessions) {
			@Override
			public void getSuggestions(final List<Chip> chips, final String text, AsyncCallback<Collection<FilterBox.Suggestion>> callback) {
				if (text.isEmpty()) {
					callback.onSuccess(null);
				} else {
					List<FilterBox.Suggestion> suggestions = new ArrayList<FilterBox.Suggestion>();
					if ("conflicts".startsWith(text.toLowerCase()) || MESSAGES.checkDisplayConflicts().toLowerCase().startsWith(text.toLowerCase())) {
						suggestions.add(new Suggestion(MESSAGES.checkDisplayConflicts(), new Chip("flag", "Conflicts")));
					}
					if ("sessinons".startsWith(text.toLowerCase()) || MESSAGES.checkSpanMultipleSessions().toLowerCase().startsWith(text.toLowerCase())) {
						suggestions.add(new Suggestion(MESSAGES.checkSpanMultipleSessions(), new Chip("flag", "All Sessions")));
					}
					callback.onSuccess(suggestions);
				}
			}
		};
		addFilter(iOther);
		
		addFilter(new FilterBox.StaticSimpleFilter("requested"));
		addFilter(new FilterBox.StaticSimpleFilter("flag"));

		iRequested.addChangeHandler(new ChangeHandler() {
			@Override
			public void onChange(ChangeEvent event) {
				requestedChanged(true);
			}
		});
		iRequested.addKeyPressHandler(new KeyPressHandler() {
			@Override
			public void onKeyPress(KeyPressEvent event) {
				Scheduler.get().scheduleDeferred(new ScheduledCommand() {
					@Override
					public void execute() {
						requestedChanged(false);
					}
				});
			}
		});
		iRequested.addKeyUpHandler(new KeyUpHandler() {
			@Override
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_BACKSPACE)
					Scheduler.get().scheduleDeferred(new ScheduledCommand() {
						@Override
						public void execute() {
							requestedChanged(false);
						}
					});
			}
		});
		iRequested.addBlurHandler(new BlurHandler() {
			@Override
			public void onBlur(BlurEvent event) {
				requestedChanged(true);
			}
		});
		
		conflicts.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				Chip chip = new Chip("flag", "Conflicts");
				if (event.getValue()) {
					if (!hasChip(chip)) addChip(chip, true);
				} else {
					if (hasChip(chip)) removeChip(chip, true);
				}
			}
		});
		conflicts.addMouseDownHandler(new MouseDownHandler() {
			@Override
			public void onMouseDown(MouseDownEvent event) {
				event.getNativeEvent().stopPropagation();
				event.getNativeEvent().preventDefault();
			}
		});
		
		sessions.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
			@Override
			public void onValueChange(ValueChangeEvent<Boolean> event) {
				Chip chip = new Chip("flag", "All Sessions");
				if (event.getValue()) {
					if (!hasChip(chip)) addChip(chip, true);
				} else {
					if (hasChip(chip)) removeChip(chip, true);
				}
			}
		});
		sessions.addMouseDownHandler(new MouseDownHandler() {
			@Override
			public void onMouseDown(MouseDownEvent event) {
				event.getNativeEvent().stopPropagation();
				event.getNativeEvent().preventDefault();
			}
		});
		
		AbsolutePanel m = new AbsolutePanel();
		m.setStyleName("unitime-DateSelector");
		final SingleDateSelector.SingleMonth m1 = new SingleDateSelector.SingleMonth("From");
		m1.setAllowDeselect(true);
		m.add(m1);
		final SingleDateSelector.SingleMonth m2 = new SingleDateSelector.SingleMonth("To");
		m2.setAllowDeselect(true);
		m.add(m2);
		addFilter(new FilterBox.CustomFilter("date", m) {
			@Override
			public void getSuggestions(List<Chip> chips, String text, AsyncCallback<Collection<Suggestion>> callback) {
				List<FilterBox.Suggestion> suggestions = new ArrayList<FilterBox.Suggestion>();
				Chip chFrom = null, chTo = null;
				for (Chip c: chips) {
					if (c.getCommand().equals("from")) chFrom = c;
					if (c.getCommand().equals("to")) chTo = c;
				}
				try {
					Date date = DateTimeFormat.getFormat("MM/dd").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				try {
					Date date = DateTimeFormat.getFormat("dd.MM").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				try {
					Date date = DateTimeFormat.getFormat("MM/dd/yy").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				try {
					Date date = DateTimeFormat.getFormat("dd.MM.yy").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				try {
					Date date = DateTimeFormat.getFormat("MMM dd").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				try {
					Date date = DateTimeFormat.getFormat("MMM dd yy").parse(text);
					suggestions.add(new FilterBox.Suggestion(new Chip("from", sDateFormat.format(date)), chFrom));
					suggestions.add(new FilterBox.Suggestion(new Chip("to", sDateFormat.format(date)), chTo));
				} catch (Exception e) {
				}
				callback.onSuccess(suggestions);
			}			
		});
		addFilter(new FilterBox.StaticSimpleFilter("from"));
		addFilter(new FilterBox.StaticSimpleFilter("to"));

		session.addAcademicSessionChangeHandler(new AcademicSessionChangeHandler() {
			@Override
			public void onAcademicSessionChange(AcademicSessionChangeEvent event) {
				if (event.isChanged() && event.getNewAcademicSessionId() != null) {
					RPC.execute(new RequestSessionDetails(event.getNewAcademicSessionId()), new AsyncCallback<GwtRpcResponseList<SessionMonth>>() {

						@Override
						public void onFailure(Throwable caught) {
						}

						@Override
						public void onSuccess(GwtRpcResponseList<SessionMonth> result) {
							m1.setMonths(result);
							m2.setMonths(result);
						}
					});
				}
			}
		});
		
		m1.addValueChangeHandler(new ValueChangeHandler<Date>() {
			@Override
			public void onValueChange(ValueChangeEvent<Date> event) {
				Chip ch = getChip("from");
				if (event.getValue() == null) {
					if (ch != null) removeChip(ch, true);	
				} else {
					String text = m1.toString();
					if (ch != null) {
						if (ch.getCommand().equals(text)) return;
						removeChip(ch, false);
					}
					addChip(new Chip("from", text), true);
				}
			}
		});
		m2.addValueChangeHandler(new ValueChangeHandler<Date>() {
			@Override
			public void onValueChange(ValueChangeEvent<Date> event) {
				Chip ch = getChip("to");
				if (event.getValue() == null) {
					if (ch != null) removeChip(ch, true);	
				} else {
					String text = m2.toString();
					if (ch != null) {
						if (ch.getCommand().equals(text)) return;
						removeChip(ch, false);
					}
					addChip(new Chip("to", text), true);
				}
			}
		});
		
		addFilter(new FilterBox.StaticSimpleFilter("day", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"));
		
		final TimeSelector st = new TimeSelector(null);
		final TimeSelector et = new TimeSelector(st);
		st.setStyleName("unitime-TextArea"); st.addStyleName("unitime-TimeSelector");
		et.setStyleName("unitime-TextArea"); et.addStyleName("unitime-TimeSelector");
		addFilter(new FilterBox.CustomFilter("time", new Label(MESSAGES.propAfter()), st, new Label(" " + MESSAGES.propBefore()), et) {
			@Override
			public void getSuggestions(List<Chip> chips, String text, AsyncCallback<Collection<Suggestion>> callback) {
				List<FilterBox.Suggestion> suggestions = new ArrayList<FilterBox.Suggestion>();
				Chip chStart = null, chStop = null;
				for (Chip c: chips) {
					if (c.getCommand().equals("after")) chStart = c;
					if (c.getCommand().equals("before")) chStop = c;
				}
				Integer start = TimeSelector.TimeUtils.parseTime(CONSTANTS, text, null);
				Integer stop = TimeSelector.TimeUtils.parseTime(CONSTANTS, text, chStart == null ? null : TimeSelector.TimeUtils.parseTime(CONSTANTS, chStart.getValue(), null));
				if (chStart == null) {
					if (start != null) {
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+3)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+6)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+9)), chStart));
					}
					if (stop != null) {
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+3)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+6)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+9)), chStop));
					}					
				} else {
					if (stop != null) {
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+3)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+6)), chStop));
						suggestions.add(new FilterBox.Suggestion(new Chip("before", TimeUtils.slot2time(stop+9)), chStop));
					}					
					if (start != null) {
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+3)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+6)), chStart));
						suggestions.add(new FilterBox.Suggestion(new Chip("after", TimeUtils.slot2time(start+9)), chStart));
					}
				}
				callback.onSuccess(suggestions);
			}
		});
		st.addValueChangeHandler(new ValueChangeHandler<Integer>() {
			@Override
			public void onValueChange(ValueChangeEvent<Integer> event) {
				Chip ch = getChip("after");
				if (event.getValue() == null) {
					if (ch != null) removeChip(ch, true);
				} else {
					String text = TimeUtils.slot2time(event.getValue());
					if (ch != null) {
						if (ch.getCommand().equals(text)) return;
						removeChip(ch, false);
					}
					addChip(new Chip("after", text), true);
				}
			}
		});
		et.addValueChangeHandler(new ValueChangeHandler<Integer>() {
			@Override
			public void onValueChange(ValueChangeEvent<Integer> event) {
				Chip ch = getChip("before");
				if (event.getValue() == null) {
					if (ch != null) removeChip(ch, true);
				} else {
					String text = TimeUtils.slot2time(event.getValue());
					if (ch != null) {
						if (ch.getCommand().equals(text)) return;
						removeChip(ch, false);
					}
					addChip(new Chip("before", text), true);
				}
			}
		});
		
		addFilter(new FilterBox.StaticSimpleFilter("after"));
		addFilter(new FilterBox.StaticSimpleFilter("before"));
		
		addValueChangeHandler(new ValueChangeHandler<String>() {
			@Override
			public void onValueChange(ValueChangeEvent<String> event) {
				iLastRequested = getChip("requested");
				if (!isFilterPopupShowing()) {
					conflicts.setValue(hasChip(new Chip("flag", "Conflicts")));
					sessions.setValue(hasChip(new Chip("flag", "All Sessions")));
					Chip req = getChip("requested");
					if (req == null)
						iRequested.setText("");
					else
						iRequested.setText(req.getValue());
					for (int i = 0; i < iSponsors.getItemCount(); i++) {
						String value = iSponsors.getValue(i);
						iSponsors.setItemSelected(i, hasChip(new Chip("sponsor", value)));
					}
					Chip chFrom = getChip("from");
					if (chFrom != null)
						m1.setDate(sDateFormat.parse(chFrom.getValue()));
					else
						m1.clearSelection();
					Chip chTo = getChip("to");
					if (chTo != null)
						m2.setDate(sDateFormat.parse(chTo.getValue()));
					else
						m2.clearSelection();
					Chip chStart = getChip("after");
					if (chStart != null)
						st.setValue(TimeSelector.TimeUtils.parseTime(CONSTANTS, chStart.getValue(), null));
					else
						st.setValue(null);
					Chip chStop = getChip("before");
					if (chStop != null)
						et.setValue(TimeSelector.TimeUtils.parseTime(CONSTANTS, chStop.getValue(), st.getValue()));
					else
						et.setValue(null);
				}
				if (getAcademicSessionId() != null)
					init(false, getAcademicSessionId(), new Command() {
						@Override
						public void execute() {
							if (isFilterPopupShowing())
								showFilterPopup();
						}
					});
				setAriaLabel(ARIA.eventFilter(toAriaString()));
			}
		});
		
		addFocusHandler(new FocusHandler() {
			@Override
			public void onFocus(FocusEvent event) {
				setAriaLabel(ARIA.eventFilter(toAriaString()));
			}
		});
	}
	
	public void setOtherVisible(boolean visible) { iOther.setVisible(visible); }
	
	@Override
	protected boolean populateFilter(FilterBox.Filter filter, List<FilterRpcResponse.Entity> entities) {
		if ("sponsor".equals(filter.getCommand())) {
			iSponsors.clear();
			if (entities != null)
				for (FilterRpcResponse.Entity entity: entities)
					iSponsors.addItem(entity.getName() + (entity.getCount() <= 0 ? "" : " (" + entity.getCount() + ")"), entity.getAbbreviation());
			for (int i = 0; i < iSponsors.getItemCount(); i++) {
				String value = iSponsors.getValue(i);
				iSponsors.setItemSelected(i, hasChip(new Chip("sponsor", value)));
			}
			return true;
		} else return super.populateFilter(filter, entities);
	}
	
	@Override
	protected void addSuggestion(List<FilterBox.Suggestion> suggestions, FilterRpcResponse.Entity entity) {
		if ("Requested By".equals(entity.getProperty("hint", null))) {
			suggestions.add(new FilterBox.Suggestion(entity.getName(), new FilterBox.Chip("requested", entity.getAbbreviation()), getChip("requested")));
		} else {
			super.addSuggestion(suggestions, entity);
		}
	}
	
	private void requestedChanged(boolean fireChange) {
		Chip oldChip = getChip("requested");
		if (iRequested.getText().isEmpty()) {
			if (oldChip != null)
				removeChip(oldChip, fireChange);
		} else {
			Chip newChip = new Chip("requested", iRequested.getText());
			if (oldChip != null) {
				if (newChip.equals(oldChip)) {
					if (fireChange && !newChip.equals(iLastRequested)) fireValueChangeEvent();
					return;
				}
				removeChip(oldChip, false);
			}
			addChip(newChip, fireChange);
		}
	}

	@Override
	public EventFilterRpcRequest createRpcRequest() {
		return new EventFilterRpcRequest();
	}

}
