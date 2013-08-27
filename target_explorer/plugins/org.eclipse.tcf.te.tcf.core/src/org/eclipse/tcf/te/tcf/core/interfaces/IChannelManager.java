/*******************************************************************************
 * Copyright (c) 2011, 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.core.interfaces;

import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IPeer;

/**
 * TCF channel manager public API declaration.
 */
public interface IChannelManager extends IAdaptable {

	/**
	 * If set to <code>true</code>, a new and not reference counted channel is opened.
	 * The returned channel must be closed by the caller himself. The channel manager
	 * is not keeping track of non reference counted channels.
	 * <p>
	 * If not present in the flags map passed in to open channel, the default value is
	 * <code>false</code>.
	 */
	public static final String FLAG_FORCE_NEW = "forceNew"; //$NON-NLS-1$

	/**
	 * If set to <code>true</code>, a new and not reference counted channel is opened,
	 * and no value add is launched and associated with the channel. This option should
	 * be used with extreme caution.
	 * <p>
	 * The returned channel must be closed by the caller himself. The channel manager
	 * is not keeping track of non reference counted channels.
	 * <p>
	 * If not present in the flags map passed in to open channel, the default value is
	 * <code>false</code>.
	 */
	public static final String FLAG_NO_VALUE_ADD = "noValueAdd"; //$NON-NLS-1$

	/**
	 * If set to <code>true</code>, a new and not reference counted channel is opened,
	 * and the configured path map is not auto applied to the opened channel.
	 * <p>
	 * The returned channel must be closed by the caller himself. The channel manager
	 * is not keeping track of non reference counted channels.
	 * <p>
	 * If not present in the flags map passed in to open channel, the default value is
	 * <code>false</code>.
	 */
	public static final String FLAG_NO_PATH_MAP = "noPathMap"; //$NON-NLS-1$

	/**
	 * Client call back interface for openChannel(...).
	 */
	interface DoneOpenChannel {
		/**
		 * Called when the channel fully opened or failed to open.
		 * <p>
		 * <b>Note:</b> If error is of type {@link OperationCanceledException}, than it signals that
		 * the channel got closed normally while still in state {@link IChannel#STATE_OPENING}. Clients
		 * should handle the case explicitly if necessary.
		 *
		 * @param error The error description if operation failed, <code>null</code> if succeeded.
		 * @param channel The channel object or <code>null</code>.
		 */
		void doneOpenChannel(Throwable error, IChannel channel);
	}

	/**
	 * Opens a new channel to communicate with the given peer.
	 * <p>
	 * Reference counted channels are cached by the channel manager and must be closed via {@link #closeChannel(IChannel)}.
	 * <p>
	 * The method can be called from any thread context.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	public void openChannel(IPeer peer, Map<String, Boolean> flags, DoneOpenChannel done);

	/**
	 * Opens a new channel to communicate with the peer described by the given peer attributes.
	 * <p>
	 * Reference counted channels are cached by the channel manager and must be closed via {@link #closeChannel(IChannel)}.
	 * <p>
	 * The method can be called from any thread context.
	 *
	 * @param peerAttributes The peer attributes. Must not be <code>null</code>.
	 * @param flags Map containing the flags to parameterize the channel opening, or <code>null</code>.
	 * @param done The client callback. Must not be <code>null</code>.
	 */
	public void openChannel(Map<String, String> peerAttributes, Map<String, Boolean> flags, DoneOpenChannel done);

	/**
	 * Returns the shared channel instance for the given peer. Channels retrieved using this
	 * method cannot be closed by the caller.
	 * <p>
	 * Callers of this method are expected to test for the current channel state themselves.
	 * <p>
	 * The method can be called from any thread context.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 * @return The channel instance or <code>null</code>.
	 */
	public IChannel getChannel(IPeer peer);

	/**
	 * Closes the given channel.
	 * <p>
	 * If the given channel is a reference counted channel, the channel will be closed if the reference counter
	 * reaches 0. For non reference counted channels, the channel is closed immediately.
	 * <p>
	 * The method can be called from any thread context.
	 *
	 * @param channel The channel. Must not be <code>null</code>.
	 */
	public void closeChannel(IChannel channel);

	/**
	 * Shutdown the communication to the given peer, no matter of the current
	 * reference count. A possible associated value-add is shutdown as well.
	 *
	 * @param peer The peer. Must not be <code>null</code>.
	 */
	public void shutdown(IPeer peer);

	/**
	 * Close all open channel, no matter of the current reference count.
	 * <p>
	 * If <code>wait</code> equals <code>false</code>, the method can be called
	 * from any thread context. Otherwise it must be called from outside the
	 * TCF event dispatch thread.
	 *
	 * @param wait If <code>true</code> the method will wait until all channels or closed. If <code>false</code>,
	 *             the method will return immediately.
	 */
	public void closeAll(boolean wait);
}
