/*******************************************************************************
 * Copyright (c) 2011 - 2015 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.local.nls;

import org.eclipse.osgi.util.NLS;

/**
 * Process terminal connector plug-in externalized strings management.
 */
public class Messages extends NLS {

	// The plug-in resource bundle name
	private static final String BUNDLE_NAME = "org.eclipse.tcf.te.ui.terminals.local.nls.Messages"; //$NON-NLS-1$

	/**
	 * Static constructor.
	 */
	static {
		// Load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	// **** Declare externalized string id's down here *****

	public static String ProcessConnector_error_creatingProcess;

	public static String PreferencePage_label;
	public static String PreferencePage_executables_label;
	public static String PreferencePage_executables_column_name_label;
	public static String PreferencePage_executables_column_path_label;
	public static String PreferencePage_executables_button_add_label;
	public static String PreferencePage_executables_button_edit_label;
	public static String PreferencePage_executables_button_remove_label;
	public static String PreferencePage_workingDir_label;
	public static String PreferencePage_workingDir_userhome_label;
	public static String PreferencePage_workingDir_eclipsehome_label;
	public static String PreferencePage_workingDir_eclipsews_label;
	public static String PreferencePage_workingDir_button_browse;
	public static String PreferencePage_workingDir_note_label;
	public static String PreferencePage_workingDir_note_text;

	public static String ExternalExecutablesDialog_title_add;
	public static String ExternalExecutablesDialog_title_edit;
	public static String ExternalExecutablesDialog_button_add;
	public static String ExternalExecutablesDialog_field_path;
	public static String ExternalExecutablesDialog_name_info_missingValue;
	public static String ExternalExecutablesDialog_path_info_missingFilename;
	public static String ExternalExecutablesDialog_path_error_mustExist;
	public static String ExternalExecutablesDialog_path_error_invalidFilename;
	public static String ExternalExecutablesDialog_path_error_noAccess;
	public static String ExternalExecutablesDialog_path_error_isRelativ;
	public static String ExternalExecutablesDialog_field_args;
	public static String ExternalExecutablesDialog_field_icon;
	public static String ExternalExecutablesDialog_field_translate;
	public static String ExternalExecutablesDialog_icon_error_mustExist;
	public static String ExternalExecutablesDialog_icon_error_invalidFilename;
	public static String ExternalExecutablesDialog_icon_error_noAccess;
	public static String ExternalExecutablesDialog_icon_error_isRelativ;
}
