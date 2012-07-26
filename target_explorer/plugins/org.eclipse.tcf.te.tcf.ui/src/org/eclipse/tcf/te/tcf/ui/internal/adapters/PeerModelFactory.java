/*******************************************************************************
 * Copyright (c) 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.ui.internal.adapters;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelRefreshService;
import org.eclipse.tcf.te.tcf.locator.model.Model;
import org.eclipse.tcf.te.ui.views.editor.EditorInput;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;

/**
 * The element factory to create an peer model editor input from a memento which is read
 * from an external persistent storage and holds a peer id.
 */
public class PeerModelFactory implements IElementFactory {

	protected boolean isModelRefreshed = false;

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.IElementFactory#createElement(org.eclipse.ui.IMemento)
	 */
	@Override
	public IAdaptable createElement(IMemento memento) {
		String peerId = memento.getString("peerId"); //$NON-NLS-1$
		// refresh the model
		if (!isModelRefreshed) {
			Protocol.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					ILocatorModelRefreshService service = Model.getModel().getService(ILocatorModelRefreshService.class);
					if (service != null) {
						service.refresh();
						service.refreshStaticPeers();
					}
					isModelRefreshed = true;
				}
			});
		}
		// search the peerId in the models peers
		IPeerModel[] peerModels = Model.getModel().getPeers();
		IPeerModel node = null;
		for (IPeerModel peerModel : peerModels) {
			if (peerModel.getPeer().getID().equals(peerId)) {
				node = peerModel;
				break;
			}
		}

		if (node != null) {
			ILocatorModel model = node.getModel();
			ILocatorModelPeerNodeQueryService queryService = model.getService(ILocatorModelPeerNodeQueryService.class);
			queryService.queryRemoteServices(node);
		}
		return node != null ? new EditorInput(node) : null;
	}
}
