/*******************************************************************************
 * Copyright (c) 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/

package org.eclipse.tcf.te.ui.controls.validator;

import org.eclipse.tcf.te.ui.controls.nls.Messages;

/**
 * TargetPathValidator
 */
public class TargetPathValidator extends RegexValidator {

	public static final int ATTR_MULTIPLE = 4;

	protected static final String TARGET_PATH_SEGMENT_REGEX = "[^;?* /\\\\]+"; //$NON-NLS-1$
	protected static final String TARGET_PATH_REGEX = "(/(" + TARGET_PATH_SEGMENT_REGEX + "))+"; //$NON-NLS-1$ //$NON-NLS-2$

	protected static final String TARGET_PATH_MULTIPLE_REGEX = TARGET_PATH_REGEX + "(;" + TARGET_PATH_REGEX + ")*"; //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * Constructor.
	 * @param attributes
	 * @param regex
	 */
	public TargetPathValidator(int attributes) {
		super(attributes, isAttribute(ATTR_MULTIPLE, attributes) ? TARGET_PATH_MULTIPLE_REGEX : TARGET_PATH_REGEX);
		setMessageText(INFO_MISSING_VALUE, Messages.TargetPathValidator_Information_MissingTargetPath);
		setMessageText(ERROR_INVALID_VALUE, Messages.TargetPathValidator_Error_InvalidTargetPath);
	}
}
