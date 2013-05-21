/*******************************************************************************
 * Copyright (c) 2012, 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/

package org.eclipse.tcf.te.tcf.launch.core.steps.iterators;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.tcf.te.launch.core.steps.iterators.AbstractLaunchStepGroupIterator;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IFullQualifiedId;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;

/**
 * Abstract TCF launch step group iterator.
 */
public abstract class AbstractTcfLaunchStepGroupIterator extends AbstractLaunchStepGroupIterator {

	/**
	 * Returns the active peer model context that is currently used.
	 *
	 * @param context The step context. Must not be <code>null</code>.
	 * @param data The data giving object. Must not be <code>null</code>.
	 * @param fullQualifiedId The full qualfied id for this step. Must not be <code>null</code>.
	 * @return The active peer model context.
	 */
	protected IPeerModel getActivePeerModelContext(IStepContext context, IPropertiesContainer data, IFullQualifiedId fullQualifiedId) {
		Object activeContext = getActiveContext(context, data, fullQualifiedId);
		IPeerModel peerModel = null;
		if (activeContext instanceof IPeerModel)
			return (IPeerModel)activeContext;
		if (activeContext instanceof IAdaptable)
			peerModel = (IPeerModel)((IAdaptable)activeContext).getAdapter(IPeerModel.class);
		if (peerModel == null)
			peerModel = (IPeerModel)Platform.getAdapterManager().getAdapter(activeContext, IPeerModel.class);

		return peerModel;
	}
}
