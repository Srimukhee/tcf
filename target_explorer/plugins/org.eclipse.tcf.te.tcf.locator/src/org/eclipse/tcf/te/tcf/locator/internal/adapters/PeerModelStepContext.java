/*******************************************************************************
 * Copyright (c) 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.locator.internal.adapters;

import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.te.runtime.stepper.context.AbstractStepContext;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;

/**
 * Peer model step context implementation.
 */
public class PeerModelStepContext extends AbstractStepContext {

	/**
     * Constructor
     */
    public PeerModelStepContext(IPeerModel peerModel) {
    	super(peerModel);
    }

	/**
	 * Returns the peer model.
	 * @return The peer model.
	 */
	public IPeerModel getPeerModel() {
		return (IPeerModel)getContextObject();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext#getId()
	 */
	@Override
	public String getId() {
		return getPeerModel().getPeerId();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext#getName()
	 */
	@Override
	public String getName() {
		return getPeerModel().getName();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(final Class adapter) {
		if (IPeerModel.class.equals(adapter)) {
			return getPeerModel();
		}

		if (IPeer.class.equals(adapter)) {
			return getPeerModel().getPeer();
		}

		return super.getAdapter(adapter);
	}
}
