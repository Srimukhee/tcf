/*******************************************************************************
 * Copyright (c) 2010, 2013 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Wind River Systems - Extracted from o.e.mylyn.commons and adapted for Target Explorer
 *******************************************************************************/

package org.eclipse.tcf.te.ui.notifications.internal;

import java.util.List;

/**
 * Manages actions that are triggered when a {@link NotificationEvent} occurs.
 *
 * @author Steffen Pingel
 */
public class NotificationHandler {

	private final List<NotificationAction> actions;

	private final NotificationEvent event;

	public NotificationHandler(NotificationEvent event, List<NotificationAction> actions) {
		this.event = event;
		this.actions = actions;
	}

	public List<NotificationAction> getActions() {
		return actions;
	}

	public NotificationEvent getEvent() {
		return event;
	}

}
