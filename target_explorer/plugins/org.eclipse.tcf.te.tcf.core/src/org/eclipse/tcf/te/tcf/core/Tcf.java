/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdapterManager;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.protocol.Protocol.ChannelOpenListener;
import org.eclipse.tcf.te.tcf.core.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.core.internal.Startup;
import org.eclipse.tcf.te.tcf.core.internal.channelmanager.ChannelManager;
import org.eclipse.tcf.te.tcf.core.listeners.interfaces.IChannelOpenListener;
import org.eclipse.tcf.te.tcf.core.listeners.interfaces.IChannelStateChangeListener;
import org.eclipse.tcf.te.tcf.core.listeners.interfaces.IProtocolStateChangeListener;
import org.eclipse.tcf.te.tcf.core.nls.Messages;


/**
 * The main entry point to access the TCF framework extensions.
 */
public final class Tcf {
	/* default */ IChannelManager channelManager;

	/* default */ ChannelOpenListener channelOpenListener;

	/* default */ final List<IProtocolStateChangeListener> protocolStateChangeListeners = new ArrayList<IProtocolStateChangeListener>();
	/* default */ final List<IChannelStateChangeListener> channelStateChangeListeners = new ArrayList<IChannelStateChangeListener>();


	/*
	 * Thread save singleton instance creation.
	 */
	private static class LazyInstance {
		public static Tcf instance = new Tcf();
	}

	/**
	 * Constructor.
	 */
	/* default */ Tcf() {
		super();
	}

	/**
	 * Returns the singleton instance.
	 */
	/* default */ static Tcf getInstance() {
		return LazyInstance.instance;
	}

	/**
	 * Executes the given runnable within the TCF protocol dispatch thread.
	 * <p>
	 * <b>Note:</b> Code which is executed in the TCF protocol dispatch thread
	 * cannot use any blocking API!
	 *
	 * @param runnable The runnable. Must not be <code>null</code>.
	 */
	private static final void runSafe(Runnable runnable) {
		Assert.isNotNull(runnable);

		if (Protocol.isDispatchThread()) {
			runnable.run();
		} else {
			Protocol.invokeAndWait(runnable);
		}
	}

	/**
	 * Adds a listener that will be notified once the TCF framework state changes.
	 *
	 * @param listener The listener. Must not be <code>null</code>.
	 */
	public static final void addProtocolStateChangeListener(IProtocolStateChangeListener listener) {
		Assert.isTrue(Protocol.isDispatchThread());
		Assert.isNotNull(listener);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		if (!tcf.protocolStateChangeListeners.contains(listener)) {
			tcf.protocolStateChangeListeners.add(listener);
		}
	}

	/**
	 * Removes the specified protocol state change listener.
	 *
	 * @param listener The listener. Must not be <code>null</code>.
	 */
	public static final void removeProtocolStateChangeListener(IProtocolStateChangeListener listener) {
		Assert.isTrue(Protocol.isDispatchThread());
		Assert.isNotNull(listener);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		tcf.protocolStateChangeListeners.remove(listener);
	}

	/**
	 * Adds a listener that will be notified once the TCF framework state changes.
	 *
	 * @param listener The listener. Must not be <code>null</code>.
	 */
	public static final void addChannelStateChangeListener(IChannelStateChangeListener listener) {
		Assert.isTrue(Protocol.isDispatchThread());
		Assert.isNotNull(listener);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		if (!tcf.channelStateChangeListeners.contains(listener)) {
			tcf.channelStateChangeListeners.add(listener);
		}
	}

	/**
	 * Removes the specified protocol state change listener.
	 *
	 * @param listener The listener. Must not be <code>null</code>.
	 */
	public static final void removeChannelStateChangeListener(IChannelStateChangeListener listener) {
		Assert.isTrue(Protocol.isDispatchThread());
		Assert.isNotNull(listener);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		tcf.channelStateChangeListeners.remove(listener);
	}

	/**
	 * Fires the channel state change listeners.
	 *
	 * @param channel The channel which changed state. Must not be <code>null</code>.
	 * @param state The new state.
	 */
	public static final void fireChannelStateChangeListeners(final IChannel channel, final int state) {
		Assert.isTrue(Protocol.isDispatchThread());
		Assert.isNotNull(channel);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		final IChannelStateChangeListener[] listeners = tcf.channelStateChangeListeners.toArray(new IChannelStateChangeListener[tcf.channelStateChangeListeners.size()]);
		if (listeners.length > 0) {
			for (IChannelStateChangeListener listener : listeners) {
				listener.stateChanged(channel, state);
			}
		}
	}

	/**
	 * Returns if or if not the TCF framework is up and running.
	 *
	 * @return <code>True</code> if the framework is up and running, <code>false</code> otherwise.
	 */
	public static final boolean isRunning() {
		return Startup.isStarted();
	}

	/**
	 * Startup TCF related services and listeners once the core
	 * TCF framework starts up.
	 * <p>
	 * <b>Note:</b> The method is expected to be called within the TCF protocol dispatch thread.
	 *
	 * @see Startup#setStarted(boolean)
	 */
	public static void start() {
		Assert.isTrue(Protocol.isDispatchThread());

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		// Create and register the global channel open listener
		if (tcf.channelOpenListener == null) {
			tcf.channelOpenListener = new org.eclipse.tcf.te.tcf.core.listeners.ChannelOpenListener();
			Protocol.addChannelOpenListener(tcf.channelOpenListener);
		}

		// Create and register listeners contributed via extension point
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IExtensionPoint point = registry.getExtensionPoint("org.eclipse.tcf.te.tcf.core.listeners"); //$NON-NLS-1$
        if (point != null) {
            IExtension[] extensions = point.getExtensions();
            for (IExtension extension : extensions) {
                IConfigurationElement[] elements = extension.getConfigurationElements();
                for (IConfigurationElement element : elements) {
                    if ("protocolStateChangeListener".equals(element.getName())) { //$NON-NLS-1$
                        try {
                            // Create the protocol state change listener instance
                            IProtocolStateChangeListener listener = (IProtocolStateChangeListener)element.createExecutableExtension("class"); //$NON-NLS-1$
                            if (listener != null) addProtocolStateChangeListener(listener);
                        } catch (CoreException e) {
                            IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
                                                        NLS.bind(Messages.Extension_error_invalidProtocolStateChangeListener, element.getDeclaringExtension().getUniqueIdentifier()),
                                                        e);
                            Platform.getLog(CoreBundleActivator.getContext().getBundle()).log(status);
                        }
                    }
                    else if ("channelStateChangeListener".equals(element.getName())) { //$NON-NLS-1$
                        try {
                            // Create the channel state change listener instance
                            IChannelStateChangeListener listener = (IChannelStateChangeListener)element.createExecutableExtension("class"); //$NON-NLS-1$
                            if (listener != null) addChannelStateChangeListener(listener);
                        } catch (CoreException e) {
                            IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
                                                        NLS.bind(Messages.Extension_error_invalidChannelStateChangeListener, element.getDeclaringExtension().getUniqueIdentifier()),
                                                        e);
                            Platform.getLog(CoreBundleActivator.getContext().getBundle()).log(status);
                        }
                    }
                }
            }
        }

		// Signal (asynchronously) to interested listeners that we've started up
		final IProtocolStateChangeListener[] listeners = tcf.protocolStateChangeListeners.toArray(new IProtocolStateChangeListener[tcf.protocolStateChangeListeners.size()]);
		if (listeners.length > 0) {
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					for (IProtocolStateChangeListener listener : listeners) {
						listener.stateChanged(true);
					}
				}
			});
		}
	}

	/**
	 * Shutdown TCF related services and listeners once the core
	 * TCF framework shuts down.
	 * <p>
	 * <b>Note:</b> The method is expected to be called within the TCF protocol dispatch thread.
	 *
	 * @see Startup#setStarted(boolean)
	 */
	public static void stop() {
		Assert.isTrue(Protocol.isDispatchThread());

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		// Unregister the channel open listener of created
		if (tcf.channelOpenListener != null) {
			Protocol.removeChannelOpenListener(tcf.channelOpenListener);
			tcf.channelOpenListener = null;
		}

		// Signal to interested listeners that we've just went down
		final IProtocolStateChangeListener[] listeners = tcf.protocolStateChangeListeners.toArray(new IProtocolStateChangeListener[tcf.protocolStateChangeListeners.size()]);
		if (listeners.length > 0) {
			// Catch IllegalStateException: TCF event dispatcher might have been shut down already
			try {
				Protocol.invokeLater(new Runnable() {
					@Override
					public void run() {
						for (IProtocolStateChangeListener listener : listeners) {
							listener.stateChanged(false);
						}
					}
				});
			} catch (IllegalStateException e) { /* ignored on purpose */ }
		}
	}

	/**
	 * Returns the channel manager instance.
	 * <p>
	 * <b>Note:</b> The method will create the channel manager instance on
	 * first invocation.
	 *
	 * @return The channel manager instance.
	 */
	public static IChannelManager getChannelManager() {
		final Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		runSafe(new Runnable() {
			@Override
			public void run() {
				Assert.isTrue(Protocol.isDispatchThread());

				if (tcf.channelManager == null) {
					// We have to create the channel manager
					tcf.channelManager = new ChannelManager();
				}
			}
		});

		return tcf.channelManager;
	}

	/**
	 * Returns an object which is an instance of the given class associated with the given object.
	 * Returns <code>null</code> if no such object can be found.
	 *
	 * @param adapter The type of adapter to look up
	 * @return An object castable to the given adapter type, or <code>null</code>
	 *         if the given adaptable object does not have an available adapter of the given type
	 *
	 * @see IAdapterManager#getAdapter(Object, Class)
	 */
	public static Object getAdapter(Class<?> adapter) {
		Assert.isNotNull(adapter);

		Tcf tcf = getInstance();
		Assert.isNotNull(tcf);

		if (IChannelManager.class.equals(adapter)) {
			return tcf.channelManager;
		}
		if (IChannelOpenListener.class.equals(adapter)) {
			return tcf.channelOpenListener;
		}

		return Platform.getAdapterManager().getAdapter(tcf, adapter);
	}
}
