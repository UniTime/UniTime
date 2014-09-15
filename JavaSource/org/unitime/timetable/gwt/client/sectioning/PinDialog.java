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
package org.unitime.timetable.gwt.client.sectioning;

import org.unitime.timetable.gwt.client.aria.AriaButton;
import org.unitime.timetable.gwt.client.aria.AriaDialogBox;
import org.unitime.timetable.gwt.client.aria.AriaTextBox;
import org.unitime.timetable.gwt.client.widgets.LoadingWidget;
import org.unitime.timetable.gwt.resources.GwtAriaMessages;
import org.unitime.timetable.gwt.resources.StudentSectioningMessages;
import org.unitime.timetable.gwt.services.SectioningService;
import org.unitime.timetable.gwt.services.SectioningServiceAsync;
import org.unitime.timetable.gwt.shared.OnlineSectioningInterface;
import org.unitime.timetable.gwt.shared.OnlineSectioningInterface.EligibilityCheck;
import org.unitime.timetable.gwt.shared.SectioningException;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;

public class PinDialog extends AriaDialogBox {
	public static final StudentSectioningMessages MESSAGES = GWT.create(StudentSectioningMessages.class);
	public static final GwtAriaMessages ARIA = GWT.create(GwtAriaMessages.class);
	
	private static final SectioningServiceAsync sSectioningService = GWT.create(SectioningService.class);
	
	private AriaTextBox iPin = null;
	private AriaButton iButton = null;
	private PinCallback iCallback = null;
	
	private boolean iOnline;
	private Long iSessionId, iStudentId;

	public PinDialog() {
		super();
		setText(MESSAGES.dialogPin());
		setAnimationEnabled(true);
		setAutoHideEnabled(false);
		setGlassEnabled(true);
		setModal(true);
		addCloseHandler(new CloseHandler<PopupPanel>() {
			@Override
			public void onClose(CloseEvent<PopupPanel> event) {
				iPin.setText("");
			}
		});
		
		HorizontalPanel panel = new HorizontalPanel();
		panel.setSpacing(5);

		panel.add(new Label(MESSAGES.pin()));
		iPin = new AriaTextBox();
		iPin.setStyleName("gwt-SuggestBox");
		iPin.setAriaLabel(ARIA.propPinNumber());
		panel.add(iPin);
		
		
		iButton = new AriaButton(MESSAGES.buttonSetPin());
		panel.add(iButton);
		
		setWidget(panel);
		
		iButton.addClickHandler(new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				sendPin();
			}
		});
		
		iPin.addKeyUpHandler(new KeyUpHandler() {
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					sendPin();
				} else if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
					hide();
					iCallback.onFailure(new SectioningException(MESSAGES.exceptionAuthenticationPinNotProvided()));
				}
			}
		});
	}
	
	protected void sendPin() {
		final String pin = iPin.getText();
		hide();
		LoadingWidget.getInstance().show(MESSAGES.waitEligibilityCheck());
		sSectioningService.checkEligibility(iOnline, iSessionId, iStudentId, pin, new AsyncCallback<EligibilityCheck>() {
			
			@Override
			public void onSuccess(EligibilityCheck result) {
				LoadingWidget.getInstance().hide();
				iCallback.onMessage(result);
				if (result.hasFlag(OnlineSectioningInterface.EligibilityCheck.EligibilityFlag.PIN_REQUIRED)) {
					center();
					Scheduler.get().scheduleDeferred(new ScheduledCommand() {
						@Override
						public void execute() {
							iPin.selectAll();
							iPin.setFocus(true);
						}
					});
				} else {
					iCallback.onSuccess(result);
				}
			}
			
			@Override
			public void onFailure(Throwable caught) {
				LoadingWidget.getInstance().hide();
				iCallback.onFailure(caught);
			}
		});
	}
	
	public void checkEligibility(boolean online, Long sessionId, Long studentId, PinCallback callback) {
		iOnline = online;
		iSessionId = sessionId;
		iStudentId = studentId;
		iCallback = callback;
		iPin.setText("");
		center();
		Scheduler.get().scheduleDeferred(new ScheduledCommand() {
			@Override
			public void execute() {
				iPin.setFocus(true);
			}
		});
	}
	
	public static interface PinCallback extends AsyncCallback<EligibilityCheck> {
		public void onMessage(EligibilityCheck result);
	}

}
