/*******************************************************************************
 * Copyright (c) 2012, 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.locator.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.runtime.interfaces.IConditionTester;
import org.eclipse.tcf.te.tcf.core.Tcf;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModelProperties;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelUpdateService;

/**
 * Default locator model peer node query service implementation.
 */
public class LocatorModelPeerNodeQueryService extends AbstractLocatorModelService implements ILocatorModelPeerNodeQueryService {

	/**
	 * Constructor.
	 *
	 * @param parentModel The parent locator model instance. Must not be <code>null</code>.
	 */
	public LocatorModelPeerNodeQueryService(ILocatorModel parentModel) {
		super(parentModel);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService#queryLocalServices(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel)
	 */
	@Override
	public String queryLocalServices(final IPeerModel node) {
		Assert.isTrue(!Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(node);

		// Check if the services has been cached before
		final AtomicReference<String> services = new AtomicReference<String>();
		Protocol.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				services.set(node.getStringProperty(IPeerModelProperties.PROP_LOCAL_SERVICES));
			}
		});

		if (services.get() != null && !"".equals(services.get())) { //$NON-NLS-1$
			// Services are cached -> return immediately
			return services.get();
		}

		// Get the service asynchronously and block the caller thread until
		// the callback returned
		final AtomicBoolean completed = new AtomicBoolean(false);
		Protocol.invokeLater(new Runnable() {
			@Override
			public void run() {
				queryServicesAsync(node, new DoneQueryServices() {
					@Override
					public void doneQueryServices(Throwable error) {
						if (error == null) {
							services.set(node.getStringProperty(IPeerModelProperties.PROP_LOCAL_SERVICES));
						}
						completed.set(true);
					}
				});
			}
		});

		final long startTime = System.currentTimeMillis();
		final IConditionTester tester = new IConditionTester() {
			@Override
			public boolean isConditionFulfilled() {
				return completed.get();
			}

			@Override
			public void cleanup() {}
		};

		while ((startTime + 1000) < System.currentTimeMillis() && !tester.isConditionFulfilled()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) { /* ignored on purpose */ }
		}

		return services.get();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService#queryRemoteServices(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel)
	 */
	@Override
	public String queryRemoteServices(final IPeerModel node) {
		Assert.isTrue(!Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(node);

		// Check if the services has been cached before
		final AtomicReference<String> services = new AtomicReference<String>();
		Protocol.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				services.set(node.getStringProperty(IPeerModelProperties.PROP_REMOTE_SERVICES));
			}
		});

		if (services.get() != null && !"".equals(services.get())) { //$NON-NLS-1$
			// Services are cached -> return immediately
			return services.get();
		}

		// Get the service asynchronously and block the caller thread until
		// the callback returned
		final AtomicBoolean completed = new AtomicBoolean(false);
		Protocol.invokeLater(new Runnable() {
			@Override
			public void run() {
				queryServicesAsync(node, new DoneQueryServices() {
					@Override
					public void doneQueryServices(Throwable error) {
						if (error == null) {
							services.set(node.getStringProperty(IPeerModelProperties.PROP_REMOTE_SERVICES));
						}
						completed.set(true);
					}
				});
			}
		});

		final long startTime = System.currentTimeMillis();
		final IConditionTester tester = new IConditionTester() {
			@Override
			public boolean isConditionFulfilled() {
				return completed.get();
			}

			@Override
			public void cleanup() {}
		};

		while ((startTime + 1000) < System.currentTimeMillis() && !tester.isConditionFulfilled()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) { /* ignored on purpose */ }
		}

		return services.get();
	}

	/* default */ final Map<IPeerModel, List<DoneQueryServices>> serviceQueriesInProgress = new HashMap<IPeerModel, List<DoneQueryServices>>();

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService#queryServicesAsync(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel, org.eclipse.tcf.te.tcf.locator.interfaces.services.ILocatorModelPeerNodeQueryService.DoneQueryServices)
	 */
	@Override
	public void queryServicesAsync(final IPeerModel node, final DoneQueryServices done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(node);
		Assert.isNotNull(done);

		// If a service query for the same peer is already in progress,
		// attach the new done to the list to call and drop out
		if (serviceQueriesInProgress.containsKey(node)) {
			List<DoneQueryServices> dones = serviceQueriesInProgress.get(node);
			Assert.isNotNull(dones);
			dones.add(done);
			return;
		}

		// Add the done callback to a list of waiting callbacks per peer node
		List<DoneQueryServices> dones = new ArrayList<DoneQueryServices>();
		dones.add(done);
		serviceQueriesInProgress.put(node, dones);

		// Create the inner callback that invokes the queued outer callbacks
		final DoneQueryServices innerDone = new DoneQueryServices() {

			@Override
			public void doneQueryServices(Throwable error) {
				// Get the list of the original done callbacks
				List<DoneQueryServices> dones = serviceQueriesInProgress.remove(node);
				if (dones != null) {
					for (DoneQueryServices done : dones) {
						done.doneQueryServices(error);
					}
				}
			}
		};

		// Do not try to open a channel to peers known to be unreachable
		int state = node.getIntProperty(IPeerModelProperties.PROP_STATE);
		if (state == IPeerModelProperties.STATE_ERROR || state == IPeerModelProperties.STATE_NOT_REACHABLE || !node.isComplete()) {
			innerDone.doneQueryServices(null);
			return;
		}

		// Opens a channel with the full value-add chain
		Tcf.getChannelManager().openChannel(node.getPeer(), null, new IChannelManager.DoneOpenChannel() {

			@Override
			public void doneOpenChannel(Throwable error, IChannel channel) {
				// If the channel opening failed -> return immediately
				if (error != null) {
					innerDone.doneQueryServices(error);
				} else {
					// Get the local service
					List<String> localServices = new ArrayList<String>(channel.getLocalServices());
					// Get the remote services
					List<String> remoteServices = new ArrayList<String>(channel.getRemoteServices());

					// Close the channel
					Tcf.getChannelManager().closeChannel(channel);

					// Sort the service lists
					Collections.sort(localServices);
					Collections.sort(remoteServices);

					// Update the services
					ILocatorModelUpdateService updateService = node.getModel().getService(ILocatorModelUpdateService.class);
					updateService.updatePeerServices(node, localServices, remoteServices);

					// Invoke the callback
					innerDone.doneQueryServices(null);
				}
			}
		});
	}
}
