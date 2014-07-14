/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.core.internal.channelmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.IToken;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.services.IStreams;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.interfaces.callback.ICallback;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.properties.PropertiesContainer;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepperOperationService;
import org.eclipse.tcf.te.runtime.stepper.job.StepperJob;
import org.eclipse.tcf.te.runtime.stepper.utils.StepperHelper;
import org.eclipse.tcf.te.tcf.core.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.core.interfaces.steps.ITcfStepAttributes;
import org.eclipse.tcf.te.tcf.core.interfaces.tracing.ITraceIds;
import org.eclipse.tcf.te.tcf.core.nls.Messages;

/**
 * Channel manager implementation.
 */
public class ChannelManager2 extends PlatformObject implements IChannelManager {
	// The map of reference counters per channel
	/* default */ final Map<IChannel, AtomicInteger> refCounters = new HashMap<IChannel, AtomicInteger>();
	// The map of channels per peer id
	/* default */ final Map<String, IChannel> channels = new HashMap<String, IChannel>();
	// The map of pending open channel callback's per peer id
	/* default */ final Map<String, List<DoneOpenChannel>> pendingDones = new HashMap<String, List<DoneOpenChannel>>();
	// The map of channels opened via "forceNew" flag (needed to handle the close channel correctly)
	/* default */ final List<IChannel> forcedChannels = new ArrayList<IChannel>();
	// The map of stream listener proxies per channel
	/* default */ final Map<IChannel, List<StreamListenerProxy>> streamProxies = new HashMap<IChannel, List<StreamListenerProxy>>();
	// The map of scheduled "open channel" stepper jobs per peer id
	/* default */ final Map<String, StepperJob> pendingOpenChannel = new HashMap<String, StepperJob>();
	// The map of scheduled "close channel" stepper jobs per peer id
	/* default */ final Map<String, StepperJob> pendingCloseChannel = new HashMap<String, StepperJob>();

	/**
	 * Constructor
	 */
	public ChannelManager2() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#openChannel(org.eclipse.tcf.protocol.IPeer, java.util.Map, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel)
	 */
	@Override
	public void openChannel(final IPeer peer, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		Assert.isNotNull(peer);
		Assert.isNotNull(done);

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(1, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			try {
				throw new Throwable();
			} catch (Throwable e) {
				CoreBundleActivator.getTraceHandler().trace("ChannelManager#openChannel called from:", //$NON-NLS-1$
															1, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				e.printStackTrace();
			}
		}

		// The client done callback must be called within the TCF event dispatch thread
		final DoneOpenChannel internalDone = new DoneOpenChannel() {

			@Override
			public void doneOpenChannel(final Throwable error, final IChannel channel) {
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						done.doneOpenChannel(error, channel);
					}
				};

				if (Protocol.isDispatchThread()) runnable.run();
				else Protocol.invokeLater(runnable);
			}
		};

		// The channel instance to return
		IChannel channel = null;

		// Get the peer id
		final String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_message, id, flags),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
		}

		// First thing to determine is if to open a new channel or the shared
		// channel can be used, if there is a shared channel at all.
		boolean forceNew = flags != null && flags.containsKey(IChannelManager.FLAG_FORCE_NEW) ? flags.get(IChannelManager.FLAG_FORCE_NEW).booleanValue() : false;
		boolean noValueAdd = flags != null && flags.containsKey(IChannelManager.FLAG_NO_VALUE_ADD) ? flags.get(IChannelManager.FLAG_NO_VALUE_ADD).booleanValue() : false;
		boolean noPathMap = flags != null && flags.containsKey(IChannelManager.FLAG_NO_PATH_MAP) ? flags.get(IChannelManager.FLAG_NO_PATH_MAP).booleanValue() : false;
		// If noValueAdd == true or noPathMap == true -> forceNew has to be true as well
		if (noValueAdd || noPathMap) forceNew = true;

		final boolean finForceNew = forceNew;

		// Query the shared channel if not forced to open a new channel
		if (!forceNew) channel = channels.get(id);

		// If a shared channel is available, check if the shared channel can be used
		if (channel != null) {
			// If the channel is still open, it's all done and the channel can be returned right away
			if (channel.getState() == IChannel.STATE_OPEN) {
				// Increase the reference count
				AtomicInteger counter = refCounters.get(channel);
				if (counter == null) {
					counter = new AtomicInteger(0);
					refCounters.put(channel, counter);
				}
				counter.incrementAndGet();

				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_reuse_message, id, counter.toString()),
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}

				// Invoke the channel open done callback
				internalDone.doneOpenChannel(null, channel);
			}
			// If the channel is opening, wait for the channel to become fully opened.
			// Add the done open channel callback to the list of pending callback's.
			else if (channel.getState() == IChannel.STATE_OPENING) {
				List<DoneOpenChannel> dones = pendingDones.get(id);
				if (dones == null) {
					dones = new ArrayList<DoneOpenChannel>();
					pendingDones.put(id, dones);
				}
				Assert.isNotNull(dones);
				dones.add(internalDone);

				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_pending_message, id, "0x" + Integer.toHexString(internalDone.hashCode())), //$NON-NLS-1$
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}
			}
			else {
				// Channel is not in open state -> drop the instance
				channels.remove(id);
				refCounters.remove(channel);
				channel = null;
			}
		}

		// Channel not available -> open a new one
		if (channel == null) {
			// Check if there is a pending "open channel" stepper job
			StepperJob job = pendingOpenChannel.get(id);
			if (job == null) {
				// No pending "open channel" stepper job -> schedule one and initiate opening the channel
				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_new_message, id),
									0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}

				// Create the data properties container passed to the "open channel" steps
				final IPropertiesContainer data = new PropertiesContainer();
				data.setProperty(IChannelManager.FLAG_NO_VALUE_ADD, noValueAdd);
				data.setProperty(IChannelManager.FLAG_NO_PATH_MAP, noPathMap);

				// Create the callback to be invoked once the "open channel" stepper job is completed
				final ICallback callback = new Callback() {
					@Override
					protected void internalDone(Object caller, IStatus status) {
						// Check for error
						if (status.getSeverity() == IStatus.ERROR) {
							// Extract the failure cause
							Throwable error = status.getException();

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_failed_message, id, error),
																			0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
							}

							// Job is done -> remove it from the list of pending jobs
							pendingOpenChannel.remove(id);

							// Invoke the primary "open channel" done callback
							internalDone.doneOpenChannel(error, null);

							// Invoke pending callback's
							List<DoneOpenChannel> pending = pendingDones.remove(id);
							if (pending != null && !pending.isEmpty()) {
								for (DoneOpenChannel d : pending) {
									d.doneOpenChannel(error, null);
								}
							}
						} else {
							// Get the channel
							IChannel channel = (IChannel)data.getProperty(ITcfStepAttributes.ATTR_CHANNEL);
							Assert.isNotNull(channel);
							Assert.isTrue(channel.getState() == IChannel.STATE_OPEN);

							// Store the channel
							if (!finForceNew) channels.put(id, channel);
							if (!finForceNew) refCounters.put(channel, new AtomicInteger(1));
							if (finForceNew) forcedChannels.add(channel);

							// Job is done -> remove it from the list of pending jobs
							pendingOpenChannel.remove(id);

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_success_message, id),
																			0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
							}

							// Invoke the primary "open channel" done callback
							internalDone.doneOpenChannel(null, channel);

							// Invoke pending callback's
							List<DoneOpenChannel> pending = pendingDones.remove(id);
							if (pending != null && !pending.isEmpty()) {
								for (DoneOpenChannel d : pending) {
									d.doneOpenChannel(null, channel);
								}
							}
						}
					}
				};

				// Get the stepper operation service
				IStepperOperationService stepperOperationService = StepperHelper.getService(peer, StepperOperationService.OPEN_CHANNEL);

				// Schedule the "open channel" stepper job
				IStepContext stepContext = stepperOperationService.getStepContext(peer, StepperOperationService.OPEN_CHANNEL);
				String stepGroupId = stepperOperationService.getStepGroupId(peer, StepperOperationService.OPEN_CHANNEL);

				if (stepGroupId != null && stepContext != null) {
					String name = stepperOperationService.getStepGroupName(peer, StepperOperationService.OPEN_CHANNEL);
					boolean isCancelable = stepperOperationService.isCancelable(peer, StepperOperationService.OPEN_CHANNEL);

					job = new StepperJob(name != null ? name : "", stepContext, data, stepGroupId, StepperOperationService.OPEN_CHANNEL, isCancelable, true); //$NON-NLS-1$
					job.setJobCallback(callback);
					job.markStatusHandled();
					job.schedule();
				}

				// Remember the "open channel" stepper job until finished
				if (job != null) {
					pendingOpenChannel.put(id, job);
				}
			} else {
				// There is a pending "open channel" stepper job -> add the open channel callback to the list
				List<DoneOpenChannel> dones = pendingDones.get(id);
				if (dones == null) {
					dones = new ArrayList<DoneOpenChannel>();
					pendingDones.put(id, dones);
				}
				Assert.isNotNull(dones);
				dones.add(internalDone);

				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_pending_message, id, "0x" + Integer.toHexString(internalDone.hashCode())), //$NON-NLS-1$
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#getChannel(org.eclipse.tcf.protocol.IPeer)
	 */
	@Override
	public IChannel getChannel(final IPeer peer) {
		final AtomicReference<IChannel> channel = new AtomicReference<IChannel>();
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				channel.set(internalGetChannel(peer));
			}
		};
		if (Protocol.isDispatchThread()) runnable.run();
		else Protocol.invokeAndWait(runnable);

	    return channel.get();
	}

	/**
	 * Returns the shared channel instance for the given peer.
	 * <p>
	 * <b>Note:</b> This method must be invoked at the TCF dispatch thread.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @return The channel instance or <code>null</code>.
	 */
	public IChannel internalGetChannel(IPeer peer) {
		Assert.isNotNull(peer);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the peer id
		String id = peer.getID();

		// Get the channel
		IChannel channel = channels.get(id);
		if (channel != null && !(channel.getState() == IChannel.STATE_OPEN || channel.getState() == IChannel.STATE_OPENING)) {
			// Channel is not in open state -> drop the instance
			channel = null;
			channels.remove(id);
			refCounters.remove(channel);
		}

		return channel;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#closeChannel(org.eclipse.tcf.protocol.IChannel)
	 */
	@Override
	public void closeChannel(final IChannel channel) {
		Runnable runnable = new Runnable() {
			@Override
            public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				internalCloseChannel(channel);
			}
		};
		if (Protocol.isDispatchThread()) runnable.run();
		else Protocol.invokeLater(runnable);
	}

	/**
	 * Closes the given channel.
	 * <p>
	 * If the given channel is a reference counted channel, the channel will be closed if the reference counter
	 * reaches 0. For non reference counted channels, the channel is closed immediately.
	 * <p>
	 * <b>Note:</b> This method must be invoked at the TCF dispatch thread.
	 *
	 * @param channel The channel. Must not be <code>null</code>.
	 */
	/* default */ void internalCloseChannel(final IChannel channel) {
		Assert.isNotNull(channel);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the id of the remote peer
		final IPeer peer = channel.getRemotePeer();
		final String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_message, id),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
		}

		// Determine if the given channel is a reference counted channel
		final boolean isRefCounted = !forcedChannels.contains(channel);

		// Get the reference counter (if the channel is a reference counted channel)
		AtomicInteger counter = isRefCounted ? refCounters.get(channel) : null;

		// If the counter is null or get 0 after the decrement, close the channel
		if (counter == null || counter.decrementAndGet() == 0) {
			// Check if there is a pending "close channel" stepper job
			StepperJob job = pendingCloseChannel.get(id);
			if (job == null) {
				// No pending "close channel" stepper job -> schedule one and initiate closing the channel
				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_close_message, id),
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}

				// Create the data properties container passed to the "close channel" steps
				final IPropertiesContainer data = new PropertiesContainer();

				// Create the callback to be invoked once the "close channel" stepper job is completed
				final ICallback callback = new Callback() {
					@Override
					protected void internalDone(Object caller, IStatus status) {
						// Check for error
						if (status.getSeverity() == IStatus.ERROR) {
							// Extract the failure cause
							Throwable error = status.getException();

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_failed_message, id, error),
																					 0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
							}

							// Job is done -> remove it from the list of pending jobs
							pendingCloseChannel.remove(id);
						} else {
							// Job is done -> remove it from the list of pending jobs
							pendingCloseChannel.remove(id);

							// Clean the reference counter and the channel map
							if (isRefCounted) channels.remove(id);
							if (isRefCounted) refCounters.remove(channel);
							if (!isRefCounted) forcedChannels.remove(channel);

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_closed_message, id),
																			0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
							}
						}
					}
				};

				// Get the stepper operation service
				IStepperOperationService stepperOperationService = StepperHelper.getService(peer, StepperOperationService.CLOSE_CHANNEL);

				// Schedule the "close channel" stepper job
				IStepContext stepContext = stepperOperationService.getStepContext(peer, StepperOperationService.CLOSE_CHANNEL);
				String stepGroupId = stepperOperationService.getStepGroupId(peer, StepperOperationService.CLOSE_CHANNEL);

				if (stepGroupId != null && stepContext != null) {
					String name = stepperOperationService.getStepGroupName(peer, StepperOperationService.CLOSE_CHANNEL);
					boolean isCancelable = stepperOperationService.isCancelable(peer, StepperOperationService.CLOSE_CHANNEL);

					job = new StepperJob(name != null ? name : "", stepContext, data, stepGroupId, StepperOperationService.CLOSE_CHANNEL, isCancelable, true); //$NON-NLS-1$
					job.setJobCallback(callback);
					job.markStatusHandled();
					job.schedule();
				}

				// Remember the "close channel" stepper job until finished
				if (job != null) {
					pendingCloseChannel.put(id, job);
				}
			} else {
				// There is a pending "close channel" stepper job
				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_pending_message, id),
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
				}
			}
		} else {
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_inuse_message, id, counter.toString()),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager2.this);
			}
		}

		// Clean up the list of forced channels. Remove all channels already been closed.
		ListIterator<IChannel> iter = forcedChannels.listIterator();
		while (iter.hasNext()) {
			IChannel c = iter.next();
			if (c.getState() == IChannel.STATE_CLOSED) {
				iter.remove();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#shutdown(org.eclipse.tcf.protocol.IPeer)
	 */
	@Override
	public void shutdown(final IPeer peer) {
		Runnable runnable = new Runnable() {
			@Override
            public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				internalShutdown(peer);
			}
		};
		if (Protocol.isDispatchThread()) runnable.run();
		else Protocol.invokeLater(runnable);
	}

	/**
	 * Shutdown the communication to the given peer, no matter of the current
	 * reference count. A possible associated value-add is shutdown as well.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 */
	/* default */ void internalShutdown(IPeer peer) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(peer);

		// Get the peer id
		String id = peer.getID();

		// First, close all channels that are not reference counted
		ListIterator<IChannel> iter = forcedChannels.listIterator();
		while (iter.hasNext()) {
			IChannel c = iter.next();
			if (id.equals(c.getRemotePeer().getID())) {
				c.close();
				iter.remove();
			}
		}

		// Get the channel
		IChannel channel = internalGetChannel(peer);
		if (channel != null) {
			// Reset the reference count (will force a channel close)
			refCounters.remove(channel);

			// Close the channel
			internalCloseChannel(channel);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#closeAll(boolean)
	 */
	@Override
	public void closeAll(boolean wait) {
		if (wait) Assert.isTrue(!Protocol.isDispatchThread());

		Runnable runnable = new Runnable() {
			@Override
            public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				internalCloseAll();
			}
		};

		if (Protocol.isDispatchThread()) runnable.run();
		else if (wait) Protocol.invokeAndWait(runnable);
		else Protocol.invokeLater(runnable);
	}

	/**
	 * Close all open channel, no matter of the current reference count.
	 * <p>
	 * <b>Note:</b> This method must be invoked at the TCF dispatch thread.
	 */
	/* default */ void internalCloseAll() {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		IChannel[] openChannels = channels.values().toArray(new IChannel[channels.values().size()]);

		refCounters.clear();
		channels.clear();

		for (IChannel channel : openChannels) internalCloseChannel(channel);
	}

	// ----- Streams handling -----

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#subscribeStream(org.eclipse.tcf.protocol.IChannel, java.lang.String, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.IStreamsListener, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneSubscribeStream)
	 */
    @Override
    public void subscribeStream(final IChannel channel, final String streamType, final IStreamsListener listener, final DoneSubscribeStream done) {
    	Assert.isNotNull(channel);
    	Assert.isNotNull(streamType);
    	Assert.isNotNull(listener);
    	Assert.isNotNull(done);

    	if (channel.getState() != IChannel.STATE_OPEN) {
    		done.doneSubscribeStream(new Exception(Messages.ChannelManager_stream_closed_message));
    		return;
    	}

    	StreamListenerProxy proxy = null;

    	// Get all the streams listener proxy instance for the given channel
    	List<StreamListenerProxy> proxies = streamProxies.get(channel);
    	// Loop the proxies and find the one for the given stream type
    	if (proxies != null) {
    		for (StreamListenerProxy candidate : proxies) {
    			if (streamType.equals(candidate.getStreamType())) {
    				proxy = candidate;
    				break;
    			}
    		}
    	}

    	// If the proxy already exist, add the listener to the proxy and return immediately
    	if (proxy != null) {
    		proxy.addListener(listener);
    		done.doneSubscribeStream(null);
    	} else {
    		// No proxy yet -> subscribe to the stream type for real and register the proxy
    		proxy = new StreamListenerProxy(channel, streamType);
    		if (proxies == null) {
    			proxies = new ArrayList<StreamListenerProxy>();
    			streamProxies.put(channel, proxies);
    		}
    		proxies.add(proxy);
    		proxy.addListener(listener);

    		IStreams service = channel.getRemoteService(IStreams.class);
    		if (service != null) {
    			final StreamListenerProxy finProxy = proxy;
    			final List<StreamListenerProxy> finProxies = proxies;

    			// Subscribe to the stream type
    			service.subscribe(streamType, proxy, new IStreams.DoneSubscribe() {
					@Override
					public void doneSubscribe(IToken token, Exception error) {
						if (error != null) {
							finProxy.removeListener(listener);
							if (finProxy.isEmpty()) finProxies.remove(finProxy);
			    			if (finProxies.isEmpty()) streamProxies.remove(channel);
						} else {
							finProxy.addListener(listener);
						}
						done.doneSubscribeStream(error);
					}
				});
    		} else {
    			proxy.removeListener(listener);
    			if (proxy.isEmpty()) proxies.remove(proxy);
    			if (proxies.isEmpty()) streamProxies.remove(channel);
    			done.doneSubscribeStream(new Exception(Messages.ChannelManager_stream_missing_service_message));
    		}
    	}
    }

    /* (non-Javadoc)
     * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#unsubscribeStream(org.eclipse.tcf.protocol.IChannel, java.lang.String, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.IStreamsListener, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneUnsubscribeStream)
     */
    @Override
    public void unsubscribeStream(final IChannel channel, final String streamType, final IStreamsListener listener, final DoneUnsubscribeStream done) {
    	Assert.isNotNull(channel);
    	Assert.isNotNull(streamType);
    	Assert.isNotNull(listener);
    	Assert.isNotNull(done);

    	if (channel.getState() != IChannel.STATE_OPEN) {
    		done.doneUnsubscribeStream(new Exception(Messages.ChannelManager_stream_closed_message));
    		return;
    	}

    	StreamListenerProxy proxy = null;

    	// Get all the streams listener proxy instance for the given channel
    	List<StreamListenerProxy> proxies = streamProxies.get(channel);
    	// Loop the proxies and find the one for the given stream type
    	if (proxies != null) {
    		for (StreamListenerProxy candidate : proxies) {
    			if (streamType.equals(candidate.getStreamType())) {
    				proxy = candidate;
    				break;
    			}
    		}
    	}

    	if (proxy != null) {
    		// Remove the listener from the proxy
    		proxy.removeListener(listener);
    		// Are there remaining proxied listeners for this stream type?
    		if (proxy.isEmpty()) {
    			// Unregister the stream type
        		IStreams service = channel.getRemoteService(IStreams.class);
        		if (service != null) {
        			final StreamListenerProxy finProxy = proxy;
        			final List<StreamListenerProxy> finProxies = proxies;

        			// Unsubscribe
        			service.unsubscribe(streamType, proxy, new IStreams.DoneUnsubscribe() {
						@Override
						public void doneUnsubscribe(IToken token, Exception error) {
							finProxies.remove(finProxy);
							if (finProxies.isEmpty()) streamProxies.remove(channel);
							done.doneUnsubscribeStream(error);
						}
					});
        		} else {
        			done.doneUnsubscribeStream(new Exception(Messages.ChannelManager_stream_missing_service_message));
        		}
    		}
    	}
    }

}
