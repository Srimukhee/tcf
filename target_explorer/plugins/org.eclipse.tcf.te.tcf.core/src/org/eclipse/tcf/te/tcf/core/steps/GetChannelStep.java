/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.core.steps;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.te.runtime.interfaces.callback.ICallback;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.stepper.StepperAttributeUtil;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IFullQualifiedId;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext;
import org.eclipse.tcf.te.tcf.core.Tcf;
import org.eclipse.tcf.te.tcf.core.interfaces.steps.ITcfStepAttributes;

/**
 * Get channel step implementation.
 */
public class GetChannelStep extends AbstractPeerStep {

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.stepper.interfaces.IExtendedStep#validateExecute(org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext, org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer, org.eclipse.tcf.te.runtime.stepper.interfaces.IFullQualifiedId, org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
	public void validateExecute(IStepContext context, IPropertiesContainer data, IFullQualifiedId fullQualifiedId, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(context);
		Assert.isNotNull(data);
		Assert.isNotNull(fullQualifiedId);
		Assert.isNotNull(monitor);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.stepper.interfaces.IStep#execute(org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext, org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer, org.eclipse.tcf.te.runtime.stepper.interfaces.IFullQualifiedId, org.eclipse.core.runtime.IProgressMonitor, org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void execute(final IStepContext context, final IPropertiesContainer data, final IFullQualifiedId fullQualifiedId, IProgressMonitor monitor, ICallback callback) {
		Assert.isNotNull(context);
		Assert.isNotNull(data);
		Assert.isNotNull(fullQualifiedId);
		Assert.isNotNull(monitor);
		Assert.isNotNull(callback);

		IChannel channel = Tcf.getChannelManager().getChannel(getActivePeerContext(context, data, fullQualifiedId));
		if (channel != null) {
			StepperAttributeUtil.setProperty(ITcfStepAttributes.ATTR_CHANNEL, fullQualifiedId, data, channel, true);
		}
		callback(data, fullQualifiedId, callback, Status.OK_STATUS, null);
	}
}
