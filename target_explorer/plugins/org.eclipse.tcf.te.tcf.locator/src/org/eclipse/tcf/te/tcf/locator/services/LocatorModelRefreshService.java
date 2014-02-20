/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.locator.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Status;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.runtime.interfaces.callback.ICallback;
import org.eclipse.tcf.te.tcf.core.Tcf;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelLookupService;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelRefreshService;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService;


/**
 * Default locator model refresh service implementation.
 */
public class LocatorModelRefreshService extends AbstractLocatorModelService implements ILocatorModelRefreshService {

	/**
	 * Constructor.
	 *
	 * @param parentModel The parent locator model instance. Must not be <code>null</code>.
	 */
	public LocatorModelRefreshService(ILocatorModel parentModel) {
		super(parentModel);
	}

	/**
	 * Asynchronously invoke the callback within the TCF dispatch thread.
	 *
	 * @param callback The callback to invoke or <code>null</code>.
	 */
	protected final void invokeCallback(final ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		if (callback != null) {
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					callback.done(LocatorModelRefreshService.this, Status.OK_STATUS);
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.IPeerModelRefreshService#refresh(org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void refresh(final ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the parent peer model
		ILocatorModel model = getLocatorModel();

		// If the parent model is already disposed, the service will drop out immediately
		if (model.isDisposed()) {
			invokeCallback(callback);
			return;
		}

		// If the TCF framework isn't initialized yet, the service will drop out immediately
		if (!Tcf.isRunning()) {
			invokeCallback(callback);
			return;
		}

		// Get the list of old children (update node instances where possible)
		final List<IPeer> oldChildren = new ArrayList<IPeer>(Arrays.asList(model.getPeers()));

		// Refresh the static peer definitions
		processPeers(Protocol.getLocator().getPeers(), oldChildren, model);

		// If there are remaining old children, remove them from the model (non-recursive)
		for (IPeer oldChild : oldChildren) {
			model.getService(ILocatorModelUpdateService.class).remove(oldChild);
		}

		// Invoke the callback
		invokeCallback(callback);
	}

	/**
	 * Process the given map of peers and update the given locator model.
	 *
	 * @param peers The map of peers to process. Must not be <code>null</code>.
	 * @param oldChildren The list of old children. Must not be <code>null</code>.
	 * @param model The locator model. Must not be <code>null</code>.
	 */
	protected void processPeers(Map<String, IPeer> peers, List<IPeer> oldChildren, ILocatorModel model) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(peers);
		Assert.isNotNull(oldChildren);
		Assert.isNotNull(model);

		for (Entry<String, IPeer> entry : peers.entrySet()) {
			// Get the peer instance for the current peer id
			IPeer peer = entry.getValue();
			// Check if the peer is filtered
			if (isFiltered(peer)) continue;
			// Try to find an existing peer node first
			IPeer lkupPeer = model.getService(ILocatorModelLookupService.class).lkupPeerById(entry.getKey());
			// And create a new one if we cannot find it
			if (lkupPeer == null) {
				// Validate peer before adding
				lkupPeer = model.validatePeer(peer);
				if (lkupPeer != null) model.getService(ILocatorModelUpdateService.class).add(lkupPeer);
			}
			else {
				oldChildren.remove(peer);
			}
		}
	}

	/**
	 * Returns if or if not the given peer is filtered.
	 *
	 * @param peer The peer or <code>null</code>.
	 * @return <code>True</code> if the given peer is filtered, <code>false</code> otherwise.
	 */
	private boolean isFiltered(IPeer peer) {
		boolean filtered = peer == null;

		if (!filtered) {
			String value = peer.getAttributes().get("ValueAdd"); //$NON-NLS-1$
			boolean isValueAdd = value != null && ("1".equals(value.trim()) || Boolean.parseBoolean(value.trim())); //$NON-NLS-1$

			filtered |= isValueAdd;

			filtered |= peer.getName() != null
							&& (peer.getName().endsWith("Command Server") || peer.getName().endsWith("CLI Server")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		return filtered;
	}
}
