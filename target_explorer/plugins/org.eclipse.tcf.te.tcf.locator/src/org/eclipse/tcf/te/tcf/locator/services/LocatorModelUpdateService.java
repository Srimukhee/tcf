/*******************************************************************************
 * Copyright (c) 2011, 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.locator.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.core.AbstractPeer;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.tcf.locator.interfaces.IModelListener;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModelProperties;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService;
import org.eclipse.tcf.te.tcf.locator.nodes.PeerRedirector;


/**
 * Default locator model update service implementation.
 */
public class LocatorModelUpdateService extends AbstractLocatorModelService implements ILocatorModelUpdateService {

	/**
	 * Constructor.
	 *
	 * @param parentModel The parent locator model instance. Must not be <code>null</code>.
	 */
	public LocatorModelUpdateService(ILocatorModel parentModel) {
		super(parentModel);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.core.interfaces.services.ILocatorModelUpdateService#add(org.eclipse.tcf.te.tcf.locator.core.interfaces.nodes.IPeerModel)
	 */
	@Override
	public void add(final IPeerModel peer) {
		Assert.isNotNull(peer);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		Map<String, IPeerModel> peers = (Map<String, IPeerModel>)getLocatorModel().getAdapter(Map.class);
		Assert.isNotNull(peers);
		peers.put(peer.getPeerId(), peer);

		final IModelListener[] listeners = getLocatorModel().getListener();
		if (listeners.length > 0) {
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					for (IModelListener listener : listeners) {
						listener.locatorModelChanged(getLocatorModel(), peer, true);
					}
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.core.interfaces.services.ILocatorModelUpdateService#remove(org.eclipse.tcf.te.tcf.locator.core.interfaces.nodes.IPeerModel)
	 */
	@Override
	public void remove(final IPeerModel peer) {
		Assert.isNotNull(peer);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		Map<String, IPeerModel> peers = (Map<String, IPeerModel>)getLocatorModel().getAdapter(Map.class);
		Assert.isNotNull(peers);
		peers.remove(peer.getPeerId());

		getLocatorModel().setChildren(peer.getPeerId(), null);

		final IModelListener[] listeners = getLocatorModel().getListener();
		if (listeners.length > 0) {
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					for (IModelListener listener : listeners) {
						listener.locatorModelChanged(getLocatorModel(), peer, false);
					}
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.core.interfaces.services.ILocatorModelUpdateService#updatePeerServices(org.eclipse.tcf.te.tcf.locator.core.interfaces.nodes.IPeerModel, java.util.Collection, java.util.Collection)
	 */
	@Override
	public void updatePeerServices(IPeerModel peerNode, Collection<String> localServices, Collection<String> remoteServices) {
		Assert.isNotNull(peerNode);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		peerNode.setProperty(IPeerModelProperties.PROP_LOCAL_SERVICES, localServices != null ? makeString(localServices) : null);
		peerNode.setProperty(IPeerModelProperties.PROP_REMOTE_SERVICES, remoteServices != null ? makeString(remoteServices) : null);
	}

	/**
	 * Transform the given collection into a plain string.
	 *
	 * @param collection The collection. Must not be <code>null</code>.
	 * @return The plain string.
	 */
	protected String makeString(Collection<String> collection) {
		Assert.isNotNull(collection);

		String buffer = collection.toString();
		buffer = buffer.replaceAll("\\[", "").replaceAll("\\]", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		return buffer.trim();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService#addChild(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel)
	 */
	@Override
	public void addChild(final IPeerModel child) {
		Assert.isNotNull(child);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Determine the parent node
		final IPeerModel parent = child.getParent(IPeerModel.class);
		if (parent == null) return;

		// Determine the peer id of the parent
		String parentPeerId = parent.getPeerId();
		Assert.isNotNull(parentPeerId);

		// Get the list of existing children
		List<IPeerModel> children = new ArrayList<IPeerModel>(getLocatorModel().getChildren(parentPeerId));
		if (!children.contains(child)) {
			children.add(child);
			getLocatorModel().setChildren(parentPeerId, children);
		}

		// Notify listeners
		parent.fireChangeEvent("changed", null, children); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService#removeChild(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel)
	 */
	@Override
	public void removeChild(final IPeerModel child) {
		Assert.isNotNull(child);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Determine the parent node
		final IPeerModel parent = child.getParent(IPeerModel.class);
		if (parent == null) return;

		// Determine the peer id of the parent
		String parentPeerId = parent.getPeerId();
		Assert.isNotNull(parentPeerId);

		// Get the list of existing children
		List<IPeerModel> children = new ArrayList<IPeerModel>(getLocatorModel().getChildren(parentPeerId));
		if (children.contains(child)) {
			children.remove(child);
			getLocatorModel().setChildren(parentPeerId, children);
		}

		// Notify listeners
		parent.fireChangeEvent("changed", null, children); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService#mergeUserDefinedAttributes(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel, org.eclipse.tcf.protocol.IPeer, boolean)
	 */
	@Override
	public void mergeUserDefinedAttributes(IPeerModel node, IPeer peer, boolean force) {
		Assert.isNotNull(node);
		Assert.isNotNull(peer);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// We can merge the peer attributes only if the destination peer is a AbstractPeer
		IPeer dst = node.getPeer();
		// If not of correct type, than we cannot update the attributes
		if (!(dst instanceof AbstractPeer) && !(dst instanceof PeerRedirector)) return;
		// If destination and source peer are the same objects(!) nothing to do here
		if (dst == peer) return;

		// If not forced, the peer id's of both attribute maps must be the same
		if (!force) Assert.isTrue(dst.getID().equals(peer.getID()));

		// Get a modifiable copy of the destination peer attributes
		Map<String, String> dstAttrs = new HashMap<String, String>(dst.getAttributes());

		// Get a modifiable copy of the source peer attributes
		Map<String, String> srcAttrs = new HashMap<String, String>(peer.getAttributes());

		// A user defined name overwrites a discovered name
		String name = srcAttrs.get(IPeer.ATTR_NAME);
		if (name != null && !"".equals(name)) { //$NON-NLS-1$
			dstAttrs.put(IPeer.ATTR_NAME, name);
		}

		// Eliminate all attributes already set in the destination attributes map
		for (String key : dstAttrs.keySet()) {
			srcAttrs.remove(key);
		}

		// Mark the peer as a remote peer, but only if there are
		// attributes to merge left
		if (!srcAttrs.isEmpty() && "RemotePeer".equals(dst.getClass().getSimpleName())) { //$NON-NLS-1$
			srcAttrs.put("remote.transient", Boolean.TRUE.toString()); //$NON-NLS-1$
		}

		// Copy all remaining attributes from source to destination
		if (!srcAttrs.isEmpty()) dstAttrs.putAll(srcAttrs);

		// And update the destination peer attributes
		if (dst instanceof AbstractPeer) {
			((AbstractPeer)dst).updateAttributes(dstAttrs);
		} else if (dst instanceof PeerRedirector) {
			((PeerRedirector)dst).updateAttributes(dstAttrs);
		}
	}
}
