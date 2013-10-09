/*******************************************************************************
 * Copyright (c) 2011, 2013 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *     Wind River Systems - Extracted from o.e.mylyn.commons and adapted for Target Explorer
 *******************************************************************************/

package org.eclipse.tcf.te.ui.notifications.popup;

import org.eclipse.swt.graphics.Image;
import org.eclipse.tcf.te.runtime.notifications.AbstractNotification;

/**
 * A notification with UI specific extensions.
 *
 * @author Steffen Pingel
 */
public abstract class AbstractUiNotification extends AbstractNotification {

	public AbstractUiNotification(String eventId) {
		super(eventId);
	}

	public abstract Image getNotificationImage();

	public abstract Image getNotificationKindImage();

	/**
	 * Executes the default action for opening the notification.
	 */
	public abstract void open();

}
