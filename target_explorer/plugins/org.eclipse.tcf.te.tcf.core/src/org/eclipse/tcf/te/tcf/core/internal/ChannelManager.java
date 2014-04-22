/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.core.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.core.AbstractPeer;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.IToken;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.services.IPathMap;
import org.eclipse.tcf.services.IPathMap.PathMapRule;
import org.eclipse.tcf.services.IStreams;
import org.eclipse.tcf.services.IStreams.StreamsListener;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.interfaces.IDisposable;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.tcf.core.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.core.interfaces.IPathMapService;
import org.eclipse.tcf.te.tcf.core.interfaces.tracing.ITraceIds;
import org.eclipse.tcf.te.tcf.core.nls.Messages;
import org.eclipse.tcf.te.tcf.core.peers.Peer;
import org.eclipse.tcf.te.tcf.core.va.ValueAddManager;
import org.eclipse.tcf.te.tcf.core.va.interfaces.IValueAdd;


/**
 * TCF channel manager implementation.
 */
public final class ChannelManager extends PlatformObject implements IChannelManager {
	// The map of reference counters per peer id
	/* default */ final Map<String, AtomicInteger> refCounters = new HashMap<String, AtomicInteger>();
	// The map of channels per peer id
	/* default */ final Map<String, IChannel> channels = new HashMap<String, IChannel>();
	// The map of channels opened via "forceNew" flag (needed to handle the close channel correctly)
	/* default */ final List<IChannel> forcedChannels = new ArrayList<IChannel>();
	// The map of stream listener proxies per channel
	/* default */ final Map<IChannel, List<StreamListenerProxy>> streamProxies = new HashMap<IChannel, List<StreamListenerProxy>>();

	/**
	 * Constructor.
	 */
	public ChannelManager() {
		super();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#openChannel(org.eclipse.tcf.protocol.IPeer, java.util.Map, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel)
	 */
	@Override
	public void openChannel(final IPeer peer, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			try {
				throw new Throwable();
			} catch (Throwable e) {
				CoreBundleActivator.getTraceHandler().trace("ChannelManager#openChannel called from:", //$NON-NLS-1$
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
				e.printStackTrace();
			}
		}

		DoneOpenChannel innerDone = null;
		boolean noPathMap = flags != null && flags.containsKey(IChannelManager.FLAG_NO_PATH_MAP) ? flags.get(IChannelManager.FLAG_NO_PATH_MAP).booleanValue() : false;
		if (noPathMap) {
			innerDone = done;
		} else {
			innerDone = new DoneOpenChannel() {
				@Override
				public void doneOpenChannel(final Throwable error, final IChannel channel) {
					// If open channel failed, pass on to the original done
					if (error != null || channel == null || channel.getState() != IChannel.STATE_OPEN) {
						done.doneOpenChannel(error, channel);
					} else {
						// Take care of the path map
						final IPathMapService service = ServiceManager.getInstance().getService(peer, IPathMapService.class);
						final IPathMap svc = channel.getRemoteService(IPathMap.class);
						if (service != null && svc != null) {
							// Get the configured path maps
							final AtomicReference<PathMapRule[]> configuredMap = new AtomicReference<PathMapRule[]>();
							// The runnable to execute within the TCF event dispatch thread once
							// the configured path map has been retrieved from the launch configuration.
							final Runnable runnable = new Runnable() {
								@Override
								public void run() {
									if (configuredMap.get() != null && configuredMap.get().length > 0) {
										// Get the old path maps first. Keep path map rules not coming from us
										svc.get(new IPathMap.DoneGet() {
		                                    @Override
											public void doneGet(IToken token, Exception e, PathMapRule[] map) {
												// Merge the maps to a new list
												List<PathMapRule> rules = new ArrayList<PathMapRule>();

												if (map != null && map.length > 0) {
													for (PathMapRule rule : map) {
														if (rule.getID() == null || !rule.getID().startsWith(service.getClientID())) {
															rules.add(rule);
														}
													}
												}

												for (PathMapRule rule : configuredMap.get()) {
													if (IPathMapService.PATHMAP_PROTOCOL_HOST_TO_TARGET.equals(rule.getProtocol())) continue;
													rules.add(rule);
												}

												if (!rules.isEmpty()) {
													svc.set(rules.toArray(new PathMapRule[rules.size()]), new IPathMap.DoneSet() {
														@Override
														public void doneSet(IToken token, Exception e) {
															done.doneOpenChannel(error, channel);
														}
													});
												} else {
													done.doneOpenChannel(error, channel);
												}
											}
										});
									} else {
										done.doneOpenChannel(error, channel);
									}
								}
							};

							// Getting the path map from the launch configuration must happen
							// outside the TCF dispatch thread as it may trigger the launch
							// configuration change listeners.
							Thread thread = new Thread(new Runnable() {
								@Override
								public void run() {
									configuredMap.set(service.getPathMap(peer));
									Protocol.invokeLater(runnable);
								}
							});
							thread.start();
						} else {
							done.doneOpenChannel(null, channel);
						}
					}
				}
			};
		}
		final DoneOpenChannel finInnerDone = innerDone;

		Runnable runnable = new Runnable() {
			@Override
            public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

				// Check on the value-add's first
				internalHandleValueAdds(peer, flags, new DoneHandleValueAdds() {
					@Override
					public void doneHandleValueAdds(final Throwable error, final IValueAdd[] valueAdds) {
						// If the error is null, continue and open the channel
						if (error == null) {
							// Do we have any value add in the chain?
							if (valueAdds != null && valueAdds.length > 0) {
								// There are value-add's -> chain them now
								internalChainValueAdds(valueAdds, peer, flags, finInnerDone);
							} else {
								// No value-add's -> open a channel to the target peer directly
								internalOpenChannel(peer, flags, finInnerDone);
							}
						} else {
							// Shutdown the value-add's launched
							internalShutdownValueAdds(peer, valueAdds);
							// Fail the channel opening
							finInnerDone.doneOpenChannel(error, null);
						}
					}
				});
			}
		};
		if (Protocol.isDispatchThread()) runnable.run();
		else Protocol.invokeLater(runnable);
	}

	/**
	 * Internal implementation of {@link #openChannel(IPeer, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel)}.
	 * <p>
	 * Reference counted channels are cached by the channel manager and must be closed via {@link #closeChannel(IChannel)} .
	 * <p>
	 * Method must be called within the TCF dispatch thread.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void internalOpenChannel(final IPeer peer, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		Assert.isNotNull(peer);
		Assert.isNotNull(done);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// The channel instance to return
		IChannel channel = null;

		// Get the peer id
		final String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_message, id, flags),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
		}

		// Extract the flags of interest form the given flags map
		boolean forceNew = flags != null && flags.containsKey(IChannelManager.FLAG_FORCE_NEW) ? flags.get(IChannelManager.FLAG_FORCE_NEW).booleanValue() : false;
		boolean noValueAdd = flags != null && flags.containsKey(IChannelManager.FLAG_NO_VALUE_ADD) ? flags.get(IChannelManager.FLAG_NO_VALUE_ADD).booleanValue() : false;
		// If noValueAdd == true -> forceNew has to be true as well
		if (noValueAdd) forceNew = true;

		final boolean finForceNew = forceNew;

		// Check if there is already a channel opened to this peer
		channel = !forceNew ? channels.get(id) : null;
		if (channel != null && (channel.getState() == IChannel.STATE_OPEN || channel.getState() == IChannel.STATE_OPENING)) {
			// Increase the reference count
			AtomicInteger counter = refCounters.get(id);
			if (counter == null) {
				counter = new AtomicInteger(0);
				refCounters.put(id, counter);
			}
			counter.incrementAndGet();

			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_reuse_message, id, counter.toString()),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}
		} else if (channel != null) {
			// Channel is not in open state -> drop the instance
			channel = null;
			channels.remove(id);
			refCounters.remove(id);
		}

		// Opens a new channel if necessary
		if (channel == null) {
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_new_message, id),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}

			try {
				channel = peer.openChannel();

				if (channel != null) {
					if (!forceNew) channels.put(id, channel);
					if (!forceNew) refCounters.put(id, new AtomicInteger(1));
					if (forceNew) forcedChannels.add(channel);

					// Register the channel listener
					final IChannel finChannel = channel;
					channel.addChannelListener(new IChannel.IChannelListener() {

						@Override
						public void onChannelOpened() {
							// Remove ourself as listener from the channel
							finChannel.removeChannelListener(this);

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_success_message, id),
																			0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager.this);
							}

							// Channel opening succeeded
							done.doneOpenChannel(null, finChannel);
						}

						@Override
						public void onChannelClosed(Throwable error) {
							// Remove ourself as listener from the channel
							finChannel.removeChannelListener(this);
							// Clean the reference counter and the channel map
							if (!finForceNew) channels.remove(id);
							if (!finForceNew) refCounters.remove(id);
							if (finForceNew) forcedChannels.remove(finChannel);

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_failed_message, id, error),
																			0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
							}

							// Channel opening failed
							done.doneOpenChannel(error != null ? error : new OperationCanceledException(), finChannel);
						}

						@Override
						public void congestionLevel(int level) {
							// ignored
						}
					});
				} else {
					// Channel is null? Something went terrible wrong.
					done.doneOpenChannel(new Exception("Unexpected null return value from IPeer#openChannel()!"), null); //$NON-NLS-1$
				}
			} catch (Throwable e) {
				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_failed_message, id, e),
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
				}

				// Channel opening failed
				done.doneOpenChannel(e, channel);
			}
		} else {
			// Wait for the channel to be fully opened if still in "OPENING" state
			if (channel.getState() == IChannel.STATE_OPENING) {
				final IChannel finChannel = channel;
				channel.addChannelListener(new IChannel.IChannelListener() {

					@Override
					public void onChannelOpened() {
						done.doneOpenChannel(null, finChannel);
					}

					@Override
					public void onChannelClosed(Throwable error) {
						done.doneOpenChannel(error != null ? error : new OperationCanceledException(), finChannel);
					}

					@Override
					public void congestionLevel(int level) {
					}
				});
			}
			else {
				done.doneOpenChannel(null, channel);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#openChannel(java.util.Map, java.util.Map, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel)
	 */
	@Override
	public void openChannel(final Map<String, String> peerAttributes, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		Runnable runnable = new Runnable() {
			@Override
            public void run() {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				internalOpenChannel(peerAttributes, flags, done);
			}
		};
		if (Protocol.isDispatchThread()) runnable.run();
		else Protocol.invokeLater(runnable);
	}

	/**
	 * Internal implementation of {@link #openChannel(Map, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel)}.
	 * <p>
	 * Method must be called within the TCF dispatch thread.
	 *
	 * @param peerAttributes The peer attributes. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void internalOpenChannel(final Map<String, String> peerAttributes, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		Assert.isNotNull(peerAttributes);
		Assert.isNotNull(done);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		// Call openChannel(IPeer, ...) instead of calling internalOpenChannel(IPeer, ...) directly
		// to include the value-add handling.
		openChannel(getOrCreatePeerInstance(peerAttributes), flags, done);
	}

	/**
	 * Tries to find an existing peer instance or create an new {@link IPeer}
	 * instance if not found.
	 * <p>
	 * <b>Note:</b> This method must be invoked at the TCF dispatch thread.
	 *
	 * @param peerAttributes The peer attributes. Must not be <code>null</code>.
	 * @return The peer instance.
	 */
	private IPeer getOrCreatePeerInstance(final Map<String, String> peerAttributes) {
		Assert.isNotNull(peerAttributes);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the peer id from the properties
		String peerId = peerAttributes.get(IPeer.ATTR_ID);
		Assert.isNotNull(peerId);

		// Check if we shall open the peer transient
		boolean isTransient = peerAttributes.containsKey("transient") ? Boolean.parseBoolean(peerAttributes.remove("transient")) : false; //$NON-NLS-1$ //$NON-NLS-2$

		// Look the peer via the Locator Service.
		IPeer peer = Protocol.getLocator().getPeers().get(peerId);
		// If not peer could be found, create a new one
		if (peer == null) {
			peer = isTransient ? new Peer(peerAttributes) : new AbstractPeer(peerAttributes);

			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_createPeer_new_message, peerId, Boolean.valueOf(isTransient)),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}
		}

		// Return the peer instance
		return peer;
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
			refCounters.remove(id);
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
	/* default */ void internalCloseChannel(IChannel channel) {
		Assert.isNotNull(channel);
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the id of the remote peer
		IPeer peer = channel.getRemotePeer();
		String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_message, id),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
		}

		// Determine if the given channel is a reference counted channel
		final boolean isRefCounted = !forcedChannels.contains(channel);

		// Get the reference counter (if the channel is a reference counted channel)
		AtomicInteger counter = isRefCounted ? refCounters.get(id) : null;

		// If the counter is null or get 0 after the decrement, close the channel
		if (counter == null || counter.decrementAndGet() == 0) {
			channel.close();

			// Get the value-add's for the peer to shutdown (if the reference counter is 0)
			if (counter != null && counter.get() == 0) {
				IValueAdd[] valueAdds = ValueAddManager.getInstance().getValueAdd(peer);
				if (valueAdds != null && valueAdds.length > 0) {
					internalShutdownValueAdds(peer, valueAdds);
				}
			}

			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_closed_message, id),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}

			// Clean the reference counter and the channel map
			if (isRefCounted) refCounters.remove(id);
			if (isRefCounted) channels.remove(id);
			if (!isRefCounted) forcedChannels.remove(channel);
		} else {
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_closeChannel_inuse_message, id, counter.toString()),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
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
			refCounters.remove(id);

			// Close the channel
			internalCloseChannel(channel);
		}

		// Make sure to shutdown all value-add's for the peer
		IValueAdd[] valueAdds = ValueAddManager.getInstance().getValueAdd(peer);
		if (valueAdds != null && valueAdds.length > 0) {
			internalShutdownValueAdds(peer, valueAdds);
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

		internalShutdownAllValueAdds();
	}

	/**
	 * Shutdown the given value-adds for the given peer.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @param valueAdds The list of value-adds. Must not be <code>null</code>.
	 */
	/* default */ void internalShutdownValueAdds(final IPeer peer, final IValueAdd[] valueAdds) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(peer);
		Assert.isNotNull(valueAdds);

		// Get the peer id
		final String id = peer.getID();

		if (valueAdds.length > 0) {
			doShutdownValueAdds(id, valueAdds);
		}
	}

	/**
	 * Shutdown the given value-adds for the given peer id.
	 *
	 * @param id The peer id. Must not be <code>null</code>.
	 * @param valueAdds The list of value-add's. Must not be <code>null</code>.
	 */
	/* default */ void doShutdownValueAdds(final String id, final IValueAdd[] valueAdds) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(id);
		Assert.isNotNull(valueAdds);

		for (IValueAdd valueAdd : valueAdds) {
			valueAdd.shutdown(id, new Callback() {
				@Override
				protected void internalDone(Object caller, IStatus status) {
				}
			});
		}
	}

	/**
	 * Shutdown all value-add's running. Called from {@link #closeAll(boolean)}
	 */
	/* default */ void internalShutdownAllValueAdds() {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get all value-add's
		IValueAdd[] valueAdds = ValueAddManager.getInstance().getValueAdds(false);
		for (IValueAdd valueAdd : valueAdds) {
			valueAdd.shutdownAll(new Callback() {
				@Override
				protected void internalDone(Object caller, IStatus status) {
				}
			});
		}
	}

	/**
	 * Client call back interface for internalHandleValueAdds(...).
	 */
	interface DoneHandleValueAdds {
		/**
		 * Called when all the value-adds are launched or the launched failed.
		 *
		 * @param error The error description if operation failed, <code>null</code> if succeeded.
		 * @param valueAdds The list of value-adds or <code>null</code>.
		 */
		void doneHandleValueAdds(Throwable error, IValueAdd[] valueAdds);
	}

	/**
	 * Check on the value-adds for the given peer. Launch the value-adds
	 * if necessary.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void internalHandleValueAdds(final IPeer peer, final Map<String, Boolean> flags, final DoneHandleValueAdds done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(peer);
		Assert.isNotNull(done);

		// Get the peer id
		final String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_check, id),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
		}

		// Extract the flags of interest form the given flags map
		boolean forceNew = flags != null && flags.containsKey(IChannelManager.FLAG_FORCE_NEW) ? flags.get(IChannelManager.FLAG_FORCE_NEW).booleanValue() : false;
		boolean noValueAdd = flags != null && flags.containsKey(IChannelManager.FLAG_NO_VALUE_ADD) ? flags.get(IChannelManager.FLAG_NO_VALUE_ADD).booleanValue() : false;
		// If noValueAdd == true -> forceNew has to be true as well
		if (noValueAdd) forceNew = true;

		// Check if there is already a channel opened to this peer
		IChannel channel = !forceNew ? channels.get(id) : null;
		if (noValueAdd || channel != null && (channel.getState() == IChannel.STATE_OPEN || channel.getState() == IChannel.STATE_OPENING)) {
			// Got an existing channel or a channel without value-add decoration
			// got requested -> drop out immediately
			done.doneHandleValueAdds(null, null);
			return;
		}

		internalHandleValueAdds(peer, done);
	}

	/* default */ final Map<String, List<DoneHandleValueAdds>> inProgress = new HashMap<String, List<DoneHandleValueAdds>>();

	/**
	 * Check on the value-adds for the given peer. Launch the value-adds
	 * if necessary.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void internalHandleValueAdds(final IPeer peer, final DoneHandleValueAdds done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(peer);
		Assert.isNotNull(done);

		// Get the peer id
		final String id = peer.getID();

		// If a launch for the same value add is in progress already, attach the new done to
		// the list to call and drop out
		if (inProgress.containsKey(id)) {
			List<DoneHandleValueAdds> dones = inProgress.get(id);
			Assert.isNotNull(dones);
			dones.add(done);
			return;
		}

		// Add the done callback to a list of waiting callbacks per peer
		List<DoneHandleValueAdds> dones = new ArrayList<DoneHandleValueAdds>();
		dones.add(done);
		inProgress.put(id, dones);

		// The "myDone" callback is invoking the callbacks from the list
		// of waiting callbacks.
		final DoneHandleValueAdds myDone = new DoneHandleValueAdds() {

			@Override
			public void doneHandleValueAdds(Throwable error, IValueAdd[] valueAdds) {
				// Get the list of the original done callbacks
				List<DoneHandleValueAdds> dones = inProgress.remove(id);
				for (DoneHandleValueAdds done : dones) {
					done.doneHandleValueAdds(error, valueAdds);
				}
			}
		};

		// Do we have applicable value-add contributions
		final IValueAdd[] valueAdds = ValueAddManager.getInstance().getValueAdd(peer);
		if (valueAdds.length == 0) {
			// There are no applicable value-add's -> drop out immediately
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_noneApplicable, id),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}
			myDone.doneHandleValueAdds(null, valueAdds);
			return;
		}

		// There are at least applicable value-add contributions
		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_numApplicable, Integer.valueOf(valueAdds.length), id),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
		}

		final List<IValueAdd> available = new ArrayList<IValueAdd>();

		final DoneLaunchValueAdd innerDone = new DoneLaunchValueAdd() {
			@Override
			public void doneLaunchValueAdd(Throwable error, List<IValueAdd> available) {
				myDone.doneHandleValueAdds(error, available.toArray(new IValueAdd[available.size()]));
			}
		};

		doLaunchValueAdd(id, valueAdds, 0, available, innerDone);
	}

	/**
	 * Client call back interface for doLaunchValueAdd(...).
	 */
	interface DoneLaunchValueAdd {
		/**
		 * Called when a value-add has been chained.
		 *
		 * @param error The error description if operation failed, <code>null</code> if succeeded.
		 * @param available The list of available value-adds.
		 */
		void doneLaunchValueAdd(Throwable error, List<IValueAdd> available);
	}

	/**
	 * Test the value-add at the given index to be alive. Launch the value-add if necessary.
	 *
	 * @param id The peer id. Must not be <code>null</code>.
	 * @param valueAdds The list of value-add's to check. Must not be <code>null</code>.
	 * @param i The index.
	 * @param available The list of available value-adds. Must not be <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void doLaunchValueAdd(final String id, final IValueAdd[] valueAdds, final int i, final List<IValueAdd> available, final DoneLaunchValueAdd done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(id);
		Assert.isNotNull(valueAdds);
		Assert.isTrue(valueAdds.length > 0);
		Assert.isNotNull(available);
		Assert.isNotNull(done);

		// Get the value-add to launch
		final IValueAdd valueAdd = valueAdds[i];

		// Check if the value-add to launch is alive
		valueAdd.isAlive(id, new Callback() {
			@Override
			protected void internalDone(Object caller, IStatus status) {
				boolean alive = ((Boolean)getResult()).booleanValue();

				if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
					CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_isAlive, new Object[] { Integer.valueOf(i), valueAdd.getLabel(), Boolean.valueOf(alive), id }),
																0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager.this);
				}

				if (!alive) {
					// Launch the value-add
					valueAdd.launch(id, new Callback() {
						@Override
						protected void internalDone(Object caller, IStatus status) {
							Throwable error = status.getException();

							if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
								CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_launch, new Object[] { Integer.valueOf(i), valueAdd.getLabel(),
												(error == null ? "success" : "failed"), //$NON-NLS-1$ //$NON-NLS-2$
												(error != null ? error.getLocalizedMessage() : null),
												id }),
												0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager.this);
							}

							// If we got an error and the value-add is optional,
							// ignore the error and drop the value-add from the chain.
							if (error != null && valueAdd.isOptional()) {
								error = null;
							} else if (error == null) {
								available.add(valueAdd);
							}

							// If the value-add failed to launch, no other value-add's are launched
							if (error != null) {
								done.doneLaunchValueAdd(error, available);
							} else {
								// Launch the next one, if there is any
								if (i + 1 < valueAdds.length) {
									DoneLaunchValueAdd innerDone = new DoneLaunchValueAdd() {
										@Override
										public void doneLaunchValueAdd(Throwable error, List<IValueAdd> available) {
											done.doneLaunchValueAdd(error, available);
										}
									};
									doLaunchValueAdd(id, valueAdds, i + 1, available, innerDone);
								} else {
									// Last value-add in chain launched -> call parent callback
									done.doneLaunchValueAdd(null, available);
								}
							}
						}
					});
				} else {
					// Already alive -> add it to the list of available value-add's
					available.add(valueAdd);
					// Launch the next one, if there is any
					if (i + 1 < valueAdds.length) {
						DoneLaunchValueAdd innerDone = new DoneLaunchValueAdd() {
							@Override
							public void doneLaunchValueAdd(Throwable error, List<IValueAdd> available) {
								done.doneLaunchValueAdd(error, available);
							}
						};
						doLaunchValueAdd(id, valueAdds, i + 1, available, innerDone);
					} else {
						// Last value-add in chain launched -> call parent callback
						done.doneLaunchValueAdd(null, available);
					}
				}
			}
		});
	}

	/**
	 * Client call back interface for doChainValueAdd(...).
	 */
	interface DoneChainValueAdd {
		/**
		 * Called when a value-add has been chained.
		 *
		 * @param error The error description if operation failed, <code>null</code> if succeeded.
		 * @param channel The channel object or <code>null</code>.
		 */
		void doneChainValueAdd(Throwable error, IChannel channel);
	}

	/**
	 * Chain the value-adds until the original target peer is reached.
	 *
	 * @param valueAdds The list of value-add's to chain. Must not be <code>null</code>.
	 * @param peer The original target peer. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	/* default */ void internalChainValueAdds(final IValueAdd[] valueAdds, final IPeer peer, final Map<String, Boolean> flags, final DoneOpenChannel done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(valueAdds);
		Assert.isNotNull(peer);
		Assert.isNotNull(done);

		// Get the peer id
		final String id = peer.getID();

		if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
			CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_startChaining, id),
														0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
		}

		// Extract the flags of interest form the given flags map
		boolean forceNew = flags != null && flags.containsKey(IChannelManager.FLAG_FORCE_NEW) ? flags.get(IChannelManager.FLAG_FORCE_NEW).booleanValue() : false;
		boolean noValueAdd = flags != null && flags.containsKey(IChannelManager.FLAG_NO_VALUE_ADD) ? flags.get(IChannelManager.FLAG_NO_VALUE_ADD).booleanValue() : false;
		boolean noPathMap = flags != null && flags.containsKey(IChannelManager.FLAG_NO_PATH_MAP) ? flags.get(IChannelManager.FLAG_NO_PATH_MAP).booleanValue() : false;
		// If noValueAdd == true or noPathMap == true -> forceNew has to be true as well
		if (noValueAdd || noPathMap) forceNew = true;

		// Check if there is already a channel opened to this peer
		IChannel channel = !forceNew ? channels.get(id) : null;
		if (channel != null && (channel.getState() == IChannel.STATE_OPEN || channel.getState() == IChannel.STATE_OPENING)) {
			// Increase the reference count
			AtomicInteger counter = refCounters.get(id);
			if (counter == null) {
				counter = new AtomicInteger(0);
				refCounters.put(id, counter);
			}
			counter.incrementAndGet();

			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_reuse_message, id, counter.toString()),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}
			// Got an existing channel -> drop out immediately if the channel is
			// already fully opened. Otherwise wait for the channel to be fully open.
			if (channel.getState() == IChannel.STATE_OPENING) {
				final IChannel finChannel = channel;
				channel.addChannelListener(new IChannel.IChannelListener() {
					@Override
					public void onChannelOpened() {
						done.doneOpenChannel(null, finChannel);
					}
					@Override
					public void onChannelClosed(Throwable error) {
						done.doneOpenChannel(error != null ? error : new OperationCanceledException(), finChannel);
					}
					@Override
					public void congestionLevel(int level) {
					}
				});
			}
			else {
				done.doneOpenChannel(null, channel);
			}
			return;
		} else if (channel != null) {
			// Channel is not in open state -> drop the instance
			channels.remove(id);
			refCounters.remove(id);
		}

		// No existing channel -> open a new one
		final DoneChainValueAdd innerDone = new DoneChainValueAdd() {
			@Override
			public void doneChainValueAdd(Throwable error, IChannel channel) {
				done.doneOpenChannel(error, channel);
			}
		};

		doChainValueAdd(id, peer.getAttributes(), forceNew, valueAdds, innerDone);
	}

	/* default */ void doChainValueAdd(final String id, final Map<String, String> attrs, final boolean forceNew, final IValueAdd[] valueAdds, final DoneChainValueAdd done) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(id);
		Assert.isNotNull(attrs);
		Assert.isNotNull(valueAdds);
		Assert.isNotNull(done);

		// The index of the currently processed value-add
		final AtomicInteger index = new AtomicInteger(0);

		// Get the value-add to chain
		final AtomicReference<IValueAdd> valueAdd = new AtomicReference<IValueAdd>();
		valueAdd.set(valueAdds[index.get()]);
		Assert.isNotNull(valueAdd.get());
		// Get the next value-add in chain
		final AtomicReference<IValueAdd> nextValueAdd = new AtomicReference<IValueAdd>();
		nextValueAdd.set(index.get() + 1 < valueAdds.length ? valueAdds[index.get() + 1] : null);

		// Get the peer for the value-add to chain
		final AtomicReference<IPeer> valueAddPeer = new AtomicReference<IPeer>();
		valueAddPeer.set(valueAdd.get().getPeer(id));
		if (valueAddPeer.get() == null) {
			done.doneChainValueAdd(new IllegalStateException("Invalid value-add peer."), null); //$NON-NLS-1$
			return;
		}

		// Get the peer for the next value-add in chain
		final AtomicReference<IPeer> nextValueAddPeer = new AtomicReference<IPeer>();
		nextValueAddPeer.set(nextValueAdd.get() != null ? nextValueAdd.get().getPeer(id) : null);
		if (nextValueAdd.get() != null && nextValueAddPeer.get() == null) {
			done.doneChainValueAdd(new IllegalStateException("Invalid value-add peer."), null); //$NON-NLS-1$
			return;
		}

		IChannel channel = null;
		try {
			// Open a channel to the value-add
			channel = valueAddPeer.get().openChannel();
			if (channel != null) {
				if (!forceNew) channels.put(id, channel);
				if (!forceNew) refCounters.put(id, new AtomicInteger(1));
				if (forceNew) forcedChannels.add(channel);

				// Attach the channel listener to catch open/closed events
				final IChannel finChannel = channel;
				channel.addChannelListener(new IChannel.IChannelListener() {
					@Override
					public void onChannelOpened() {
						if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
							CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_redirect_succeeded,
																				 new Object[] { valueAddPeer.get().getID(), finChannel.getRemotePeer().getID(), Integer.valueOf(index.get()) }),
																		0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager.this);
						}

						// Channel opened. Check if we are done.
						if (nextValueAdd.get() == null) {
							// Remove ourself as channel listener
							finChannel.removeChannelListener(this);

							// No other value-add in the chain -> all done
							done.doneChainValueAdd(null, finChannel);
						} else {
							// Process the next value-add in chain
							index.incrementAndGet();

							// Update the value-add references
							valueAdd.set(nextValueAdd.get());
							valueAddPeer.set(nextValueAddPeer.get());

							if (valueAddPeer.get() == null) {
								// Remove ourself as channel listener
								finChannel.removeChannelListener(this);
								// Close the channel
								finChannel.close();
								// Invoke the callback
								done.doneChainValueAdd(new IllegalStateException("Invalid value-add peer."), null); //$NON-NLS-1$
								return;
							}

							nextValueAdd.set(index.get() + 1 < valueAdds.length ? valueAdds[index.get() + 1] : null);
							nextValueAddPeer.set(nextValueAdd.get() != null ? nextValueAdd.get().getPeer(id) : null);
							if (nextValueAdd.get() != null && nextValueAddPeer.get() == null) {
								// Remove ourself as channel listener
								finChannel.removeChannelListener(this);
								// Close the channel
								finChannel.close();
								// Invoke the callback
								done.doneChainValueAdd(new IllegalStateException("Invalid value-add peer."), null); //$NON-NLS-1$
								return;
							}

							// Redirect the channel to the next value-add in chain
							finChannel.redirect(nextValueAddPeer.get() != null ? nextValueAddPeer.get().getAttributes() : attrs);
						}
					}

					@Override
					public void onChannelClosed(Throwable error) {
						// Remove ourself as channel listener
						finChannel.removeChannelListener(this);

						if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
							CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_valueAdd_redirect_failed, valueAddPeer.get().getID(),
																				 nextValueAddPeer.get() != null ? nextValueAddPeer.get().getID() : id),
																		0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, ChannelManager.this);
						}

						// Clean the reference counter and the channel map
						if (!forceNew) channels.remove(id);
						if (!forceNew) refCounters.remove(id);
						if (forceNew) forcedChannels.remove(finChannel);

						// Channel opening failed -> This will break everything
						done.doneChainValueAdd(error, finChannel);
					}

					@Override
					public void congestionLevel(int level) {
					}
				});

				// Redirect the channel to the next value-add in chain
				// Note: If the redirect succeeds, channel.getRemotePeer().getID() will be identical to id.
				channel.redirect(nextValueAddPeer.get() != null ? nextValueAddPeer.get().getAttributes() : attrs);
			} else {
				// Channel is null? Something went terrible wrong.
				done.doneChainValueAdd(new Exception("Unexpected null return value from IPeer#openChannel()!"), null); //$NON-NLS-1$

			}
		} catch (Throwable e) {
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_CHANNEL_MANAGER)) {
				CoreBundleActivator.getTraceHandler().trace(NLS.bind(Messages.ChannelManager_openChannel_failed_message, id, e),
															0, ITraceIds.TRACE_CHANNEL_MANAGER, IStatus.INFO, this);
			}

			// Channel opening failed
			done.doneChainValueAdd(e, channel);
		}
	}

	/**
	 * Private stream listener proxy implementation.
	 */
	private final class StreamListenerProxy implements IStreams.StreamsListener {
		// The stream type the proxy is registered for
		private final String streamType;
		// The list of proxied stream listeners
		/* default */ ListenerList listeners = new ListenerList();

		/**
         * Constructor
         *
         * @param The channel. Must not be <code>null</code>.
         */
        public StreamListenerProxy(final IChannel channel, final String streamType) {
        	Assert.isNotNull(channel);
        	Assert.isNotNull(streamType);

        	channel.addChannelListener(new IChannel.IChannelListener() {
				@Override
				public void onChannelOpened() {}

				@Override
				public void onChannelClosed(Throwable error) {
					// Channel is closed, remove ourself
					channel.removeChannelListener(this);
					// Dispose all registered streams listener
					Object[] candidates = listeners.getListeners();
					listeners.clear();
					for (Object listener : candidates) {
						if (listener instanceof IDisposable) {
							((IDisposable)listener).dispose();
						}
					}
				}

				@Override
				public void congestionLevel(int level) {
				}
			});

        	// Remember the stream type
        	this.streamType = streamType;
        }

        /**
         * Returns the stream type the proxy is registered for.
         *
         * @return The stream type.
         */
        public String getStreamType() {
        	return streamType;
        }

        /**
         * Adds the given streams listener to the list of proxied listeners.
         *
         * @param listener The streams listener. Must not be <code>null</code>.
         */
        public void addListener(IStreams.StreamsListener listener) {
        	Assert.isNotNull(listener);
        	listeners.add(listener);
        }

        /**
         * Removes the given streams listener from the list of proxied listeners.
         *
         * @param listener The streams listener. Must not be <code>null</code>.
         */
        public void removeListener(IStreams.StreamsListener listener) {
        	Assert.isNotNull(listener);
        	listeners.remove(listener);
        }

        /**
         * Returns if the proxied listeners list is empty or not.
         *
         * @return <code>True</code> if the list is empty, <code>false</code> otherwise.
         */
        public boolean isEmpty() {
        	return listeners.isEmpty();
        }

		/* (non-Javadoc)
		 * @see org.eclipse.tcf.services.IStreams.StreamsListener#created(java.lang.String, java.lang.String, java.lang.String)
		 */
        @Override
        public void created(String stream_type, String stream_id, String context_id) {
        	for (Object l : listeners.getListeners()) {
        		((IStreams.StreamsListener)l).created(stream_type, stream_id, context_id);
        	}
        }

		/* (non-Javadoc)
		 * @see org.eclipse.tcf.services.IStreams.StreamsListener#disposed(java.lang.String, java.lang.String)
		 */
        @Override
        public void disposed(String stream_type, String stream_id) {
        	for (Object l : listeners.getListeners()) {
        		((IStreams.StreamsListener)l).disposed(stream_type, stream_id);
        	}
        }
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#subscribeStream(org.eclipse.tcf.protocol.IChannel, java.lang.String, org.eclipse.tcf.services.IStreams.StreamsListener, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneSubscribeStream)
	 */
    @Override
    public void subscribeStream(final IChannel channel, final String streamType, final StreamsListener listener, final DoneSubscribeStream done) {
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
     * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager#unsubscribeStream(org.eclipse.tcf.protocol.IChannel, java.lang.String, org.eclipse.tcf.services.IStreams.StreamsListener, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneUnsubscribeStream)
     */
    @Override
    public void unsubscribeStream(final IChannel channel, final String streamType, final StreamsListener listener, final DoneUnsubscribeStream done) {
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
