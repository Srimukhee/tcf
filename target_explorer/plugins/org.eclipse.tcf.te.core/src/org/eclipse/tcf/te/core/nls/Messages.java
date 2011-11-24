/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.core.nls;

import org.eclipse.osgi.util.NLS;

/**
 * Target Explorer Core plugin externalized strings management.
 */
public class Messages extends NLS {

	// The plug-in resource bundle name
	private static final String BUNDLE_NAME = "org.eclipse.tcf.te.core.nls.Messages"; //$NON-NLS-1$

	/**
	 * Static constructor.
	 */
	static {
		// Load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	// **** Declare externalized string id's down here *****

	public static String ConnectStrategyStepExecutor_checkPoint_normalizationNeeded;
	public static String ConnectStrategyStepExecutor_info_stepFailed;
	public static String ConnectStrategyStepExecutor_warning_stepFailed;
	public static String ConnectStrategyStepExecutor_error_stepFailed;
	public static String ConnectStrategyStepExecutor_stepFailed_debugInfo;

	public static String ModelNodePersistableAdapter_export_invalidPersistable;
	public static String ModelNodePersistableAdapter_export_unknownType;
	public static String ModelNodePersistableAdapter_import_invalidReference;
	public static String ModelNodePersistableAdapter_import_cannotLoadClass;
}
