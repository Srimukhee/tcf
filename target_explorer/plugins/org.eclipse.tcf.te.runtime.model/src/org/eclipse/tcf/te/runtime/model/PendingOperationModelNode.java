/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.runtime.model;

import org.eclipse.tcf.te.runtime.model.nls.Messages;


/**
 * An immutable model node to visualize a pending operation.
 */
public class PendingOperationModelNode extends MessageModelNode {

	/**
	 * Constructor.
	 */
	public PendingOperationModelNode() {
		super(Messages.PendingOperationModelNode_label, PENDING, true);
	}
}
