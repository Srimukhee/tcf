/*******************************************************************************
 * Copyright (c) 2004, 2013 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Wind River Systems - Extracted from o.e.mylyn.commons and adapted for Target Explorer
 *******************************************************************************/

package org.eclipse.tcf.te.ui.notifications.popup.sink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tcf.te.runtime.notifications.AbstractNotification;
import org.eclipse.tcf.te.runtime.notifications.NotificationSink;
import org.eclipse.tcf.te.runtime.notifications.NotificationSinkEvent;
import org.eclipse.tcf.te.ui.notifications.nls.Messages;
import org.eclipse.tcf.te.ui.notifications.popup.NotificationPopup;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.PlatformUI;

/**
 * @author Rob Elves
 * @author Steffen Pingel
 */
public class PopupNotificationSink extends NotificationSink {

	private static final long DELAY_OPEN = 1 * 1000;

	private static final boolean runSystem = true;

	/* default */ NotificationPopup popup;

	/* default */ final WeakHashMap<Object, Object> cancelledTokens = new WeakHashMap<Object, Object>();

	private final Set<AbstractNotification> notifications = new HashSet<AbstractNotification>();

	/* default */ final Set<AbstractNotification> currentlyNotifying = Collections.synchronizedSet(notifications);

	private final Job openJob = new Job(Messages.PopupNotificationSink_Popup_Notifier_Job_Label) {
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				if (Platform.isRunning() && PlatformUI.getWorkbench() != null
						&& PlatformUI.getWorkbench().getDisplay() != null
						&& !PlatformUI.getWorkbench().getDisplay().isDisposed()) {
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

						@Override
                        public void run() {
							collectNotifications();

							if (popup != null && popup.getReturnCode() == Window.CANCEL) {
								List<AbstractNotification> notifications = popup.getNotifications();
								for (AbstractNotification notification : notifications) {
									if (notification.getToken() != null) {
										cancelledTokens.put(notification.getToken(), null);
									}
								}
							}

							for (Iterator<AbstractNotification> it = currentlyNotifying.iterator(); it.hasNext();) {
								AbstractNotification notification = it.next();
								if (notification.getToken() != null
										&& cancelledTokens.containsKey(notification.getToken())) {
									it.remove();
								}
							}

							synchronized (PopupNotificationSink.class) {
								if (currentlyNotifying.size() > 0) {
									showPopup();
								}
							}
						}
					});
				}
			} finally {
				if (popup != null) {
					schedule(popup.getDelayClose() / 2);
				}
			}

			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			return Status.OK_STATUS;
		}

	};


	public PopupNotificationSink() {
		openJob.setSystem(runSystem);
	}

	private void cleanNotified() {
		currentlyNotifying.clear();
	}

	/** public for testing */
	public void collectNotifications() {
	}

	/**
	 * public for testing purposes
	 */
	public Set<AbstractNotification> getNotifications() {
		synchronized (PopupNotificationSink.class) {
			return currentlyNotifying;
		}
	}

	public boolean isAnimationsEnabled() {
		IPreferenceStore store = PlatformUI.getPreferenceStore();
		return store.getBoolean(IWorkbenchPreferenceConstants.ENABLE_ANIMATIONS);
	}

	@Override
	public void notify(NotificationSinkEvent event) {
		currentlyNotifying.addAll(event.getNotifications());

		if (!openJob.cancel()) {
			try {
				openJob.join();
			} catch (InterruptedException e) {
				// ignore
			}
		}
		openJob.schedule(DELAY_OPEN);
	}

	public void showPopup() {
		if (popup != null) {
			popup.close();
		}

		Shell shell = new Shell(PlatformUI.getWorkbench().getDisplay());
		popup = new NotificationPopup(shell);
		popup.setFadingEnabled(isAnimationsEnabled());
		List<AbstractNotification> toDisplay = new ArrayList<AbstractNotification>(currentlyNotifying);
		Collections.sort(toDisplay);
		popup.setContents(toDisplay);
		cleanNotified();
		popup.setBlockOnOpen(false);
		popup.open();
	}

}
