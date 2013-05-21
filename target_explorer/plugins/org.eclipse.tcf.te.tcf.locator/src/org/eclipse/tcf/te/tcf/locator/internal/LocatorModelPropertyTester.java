/*******************************************************************************
 * Copyright (c) 2011, 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.locator.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.tcf.locator.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.locator.interfaces.ITracing;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModelProperties;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService;
import org.eclipse.tcf.te.tcf.locator.nodes.PeerRedirector;

/**
 * Locator model property tester.
 */
public class LocatorModelPropertyTester extends PropertyTester {

	/* (non-Javadoc)
	 * @see org.eclipse.core.expressions.IPropertyTester#test(java.lang.Object, java.lang.String, java.lang.Object[], java.lang.Object)
	 */
	@Override
	public boolean test(final Object receiver, final String property, final Object[] args, final Object expectedValue) {
		// The receiver is expected to be a peer model node or a peer
		if (receiver instanceof IPeerModel || receiver instanceof IPeer) {
			final AtomicBoolean result = new AtomicBoolean();

			// If we have to test for local or remote services, we have to handle it special
			if ("hasLocalService".equals(property) || "hasRemoteService".equals(property)) { //$NON-NLS-1$ //$NON-NLS-2$
				// This tests must happen outside the TCF dispatch thread's
				if (!Protocol.isDispatchThread()) {
					result.set(testServices((IPeerModel) receiver, property, args, expectedValue));
				}
			}
			else {
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						if (receiver instanceof IPeerModel) {
							result.set(testPeerModel((IPeerModel) receiver, property, args, expectedValue));
						} else {
							result.set(testPeer((IPeer) receiver, property, args, expectedValue));
						}
					}
				};

				if (Protocol.isDispatchThread()) {
					runnable.run();
				}
				else {
					Protocol.invokeAndWait(runnable);
				}
			}

			return result.get();
		}
		return false;
	}

	/**
	 * Test the specific peer model node properties.
	 *
	 * @param node The model node. Must not be <code>null</code>.
	 * @param property The property to test.
	 * @param args The property arguments.
	 * @param expectedValue The expected value.
	 *
	 * @return <code>True</code> if the property to test has the expected value, <code>false</code>
	 *         otherwise.
	 */
	protected boolean testPeerModel(IPeerModel node, String property, Object[] args, Object expectedValue) {
		Assert.isNotNull(node);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		return testPeer(node.getPeer(), property, args, expectedValue);
	}

	/**
	 * Test the specific peer properties.
	 *
	 * @param node The peer. Must not be <code>null</code>.
	 * @param property The property to test.
	 * @param args The property arguments.
	 * @param expectedValue The expected value.
	 *
	 * @return <code>True</code> if the property to test has the expected value, <code>false</code>
	 *         otherwise.
	 */
	protected boolean testPeer(IPeer node, String property, Object[] args, Object expectedValue) {
		Assert.isNotNull(node);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		if ("name".equals(property)) { //$NON-NLS-1$
			if (node.getName() != null && node.getName().equals(expectedValue)) {
				return true;
			}
		}

		if ("nameRegex".equals(property) && expectedValue instanceof String) { //$NON-NLS-1$
			if (node.getName() != null && node.getName().matches((String)expectedValue)) {
				return true;
			}
		}

		if ("osName".equals(property)) { //$NON-NLS-1$
			if (node.getOSName() != null && node.getOSName().equals(expectedValue)) {
				return true;
			}
		}

		if ("osNameRegex".equals(property) && expectedValue instanceof String) { //$NON-NLS-1$
			if (node.getOSName() != null && node.getOSName().matches((String)expectedValue)) {
				return true;
			}
		}

		if ("isStaticPeer".equals(property)) { //$NON-NLS-1$
			String value = node.getAttributes().get("static.transient"); //$NON-NLS-1$
			boolean isStaticPeer = value != null && Boolean.parseBoolean(value.trim());
			if (expectedValue instanceof Boolean) {
				return ((Boolean) expectedValue).booleanValue() == isStaticPeer;
			}
		}

		if ("isRedirected".equals(property)) { //$NON-NLS-1$
			boolean isRedirected = node instanceof PeerRedirector;
			if (expectedValue instanceof Boolean) {
				return ((Boolean) expectedValue).booleanValue() == isRedirected;
			}
		}

		if ("isProxy".equals(property)) { //$NON-NLS-1$
			boolean isProxy = node.getAttributes().containsKey("Proxy"); //$NON-NLS-1$
			if (expectedValue instanceof Boolean) {
				return ((Boolean) expectedValue).booleanValue() == isProxy;
			}
		}

		if ("isValueAdd".equals(property)) { //$NON-NLS-1$
			String value = node.getAttributes().get("ValueAdd"); //$NON-NLS-1$
			boolean isValueAdd = value != null && ("1".equals(value.trim()) || Boolean.parseBoolean(value.trim())); //$NON-NLS-1$
			if (expectedValue instanceof Boolean) {
				return ((Boolean) expectedValue).booleanValue() == isValueAdd;
			}
		}

		if ("isOfType".equals(property)) { //$NON-NLS-1$
			String value = node.getAttributes().get(IPeerModelProperties.PROP_TYPE);
			if (expectedValue instanceof String) {
				return value != null ? ((String)expectedValue).equals(value) : ((String)expectedValue).equalsIgnoreCase("null"); //$NON-NLS-1$
			}
		}

		if ("hasAttribute".equals(property)) { //$NON-NLS-1$
			String name = args != null && args.length > 0 ? (String)args[0] : null;
			boolean hasAttribute = name != null && !"".equals(name) ? node.getAttributes().containsKey(name) : false; //$NON-NLS-1$
			if (expectedValue instanceof Boolean) {
				return ((Boolean) expectedValue).booleanValue() == hasAttribute;
			}
		}

		if ("isAttribute".equals(property)) { //$NON-NLS-1$
			String name = args != null && args.length > 0 ? (String)args[0] : null;
			String value = name != null && !"".equals(name) ? node.getAttributes().get(name) : null; //$NON-NLS-1$
			if (expectedValue != null) {
				return expectedValue.toString().equals(value);
			}
		}

		if ("hasOfflineService".equals(property)) { //$NON-NLS-1$
			String services = node.getAttributes().get(IPeerModelProperties.PROP_OFFLINE_SERVICES);
			List<String> list = services != null ? Arrays.asList(services.split(",\\s*")) : Collections.EMPTY_LIST; //$NON-NLS-1$
			return list.contains(expectedValue);
		}

		return false;
	}

	/**
	 * Test for the peer model node local or remote services.
	 * <p>
	 * <b>Node:</b> This method cannot be called from within the TCF Dispatch Thread.
	 *
	 * @param node The model node. Must not be <code>null</code>.
	 * @param property The property to test.
	 * @param args The property arguments.
	 * @param expectedValue The expected value.
	 *
	 * @return <code>True</code> if the property to test has the expected value, <code>false</code>
	 *         otherwise.
	 */
	protected boolean testServices(final IPeerModel node, final String property, final Object[] args, final Object expectedValue) {
		Assert.isNotNull(node);
		Assert.isTrue(!Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		String services = null;

		ILocatorModel model = node.getModel();
		ILocatorModelPeerNodeQueryService queryService = model.getService(ILocatorModelPeerNodeQueryService.class);
		if ("hasLocalService".equals(property)) { //$NON-NLS-1$
			services = queryService.queryLocalServices(node);
		} else {
			services = queryService.queryRemoteServices(node);
		}

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(ITracing.ID_TRACE_PROPERTY_TESTER)) {
			CoreBundleActivator.getTraceHandler().trace("testServices: property = " + property + ", expectedValue = " + expectedValue + ", services = " + services, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
														ITracing.ID_TRACE_PROPERTY_TESTER, LocatorModelPropertyTester.this);
		}

		if (services != null) {
			// Lookup each service individually to avoid "accidental" matching
			for (String service : services.split(",")) { //$NON-NLS-1$
				if (service != null && service.trim().equals(expectedValue)) {
					return true;
				}
			}
		}

		return false;
	}
}
