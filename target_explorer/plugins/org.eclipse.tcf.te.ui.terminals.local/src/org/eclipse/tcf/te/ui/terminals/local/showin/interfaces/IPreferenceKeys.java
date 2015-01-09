/*******************************************************************************
 * Copyright (c) 2015 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.local.showin.interfaces;

/**
 * Terminals plug-in preference key definitions.
 */
public interface IPreferenceKeys {
	/**
	 * Preference keys family prefix.
	 */
	public final String PREF_TERMINAL = "terminals"; //$NON-NLS-1$

	/**
	 * Preference key: Remove terminated terminals when a new terminal is created.
	 */
	public final String PREF_LOCAL_TERMINAL_INITIAL_CWD = PREF_TERMINAL + ".localTerminalInitialCwd"; //$NON-NLS-1$

	/**
	 * Preference value: Local terminal initial working directory is "User home"
	 */
	public final String PREF_INITIAL_CWD_USER_HOME = "userhome"; //$NON-NLS-1$

	/**
	 * Preference value: Local terminal initial working directory is "Eclipse home"
	 */
	public final String PREF_INITIAL_CWD_ECLIPSE_HOME = "eclipsehome"; //$NON-NLS-1$

	/**
	 * Preference value: Local terminal initial working directory is "Eclipse workspace"
	 */
	public final String PREF_INITIAL_CWD_ECLIPSE_WS = "eclipsews"; //$NON-NLS-1$
}
