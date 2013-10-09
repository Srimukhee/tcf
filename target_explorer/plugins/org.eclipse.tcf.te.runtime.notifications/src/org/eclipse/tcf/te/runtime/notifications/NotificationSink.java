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

package org.eclipse.tcf.te.runtime.notifications;

/**
 * @author Steffen Pingel
 */
public abstract class NotificationSink {

	public abstract void notify(NotificationSinkEvent event);

}
