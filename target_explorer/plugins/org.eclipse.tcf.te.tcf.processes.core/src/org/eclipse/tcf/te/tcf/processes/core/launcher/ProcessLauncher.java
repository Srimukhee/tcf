/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.processes.core.launcher;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IChannel.IChannelListener;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.IToken;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.services.IProcesses;
import org.eclipse.tcf.services.IProcesses.ProcessContext;
import org.eclipse.tcf.services.IProcessesV1;
import org.eclipse.tcf.services.IStreams;
import org.eclipse.tcf.te.runtime.callback.AsyncCallbackCollector;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.events.DisposedEvent;
import org.eclipse.tcf.te.runtime.events.EventManager;
import org.eclipse.tcf.te.runtime.interfaces.callback.ICallback;
import org.eclipse.tcf.te.runtime.interfaces.events.IEventListener;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.properties.PropertiesContainer;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.ITerminalService;
import org.eclipse.tcf.te.runtime.services.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tcf.te.runtime.utils.StatusHelper;
import org.eclipse.tcf.te.tcf.core.Tcf;
import org.eclipse.tcf.te.tcf.core.async.CallbackInvocationDelegate;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.core.streams.StreamsDataProvider;
import org.eclipse.tcf.te.tcf.core.streams.StreamsDataReceiver;
import org.eclipse.tcf.te.tcf.processes.core.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessContextAwareListener;
import org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessLauncher;
import org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessStreamsProxy;
import org.eclipse.tcf.te.tcf.processes.core.interfaces.tracing.ITraceIds;
import org.eclipse.tcf.te.tcf.processes.core.nls.Messages;

/**
 * Remote process launcher.
 * <p>
 * The process launcher is implemented fully asynchronous.
 */
public class ProcessLauncher extends PlatformObject implements IProcessLauncher {
	// The channel instance
	/* default */ IChannel channel = null;
	// Flag to signal if the channel needs to be closed on disposed
	/* default */ boolean closeChannelOnDispose = false;
	// Flag to signal if the channel is a private or shared channel
	/* default */ boolean sharedChannel = false;
	// The process properties instance
	/* default */ IPropertiesContainer properties;

	// The processes service instance
	/* default */ IProcesses svcProcesses;
	// The streams service instance
	/* default */ IStreams svcStreams;
	// The remote process context
	/* default */ IProcesses.ProcessContext processContext;

	// The callback instance
	private ICallback callback;

	// The streams listener instance
	private IChannelManager.IStreamsListener streamsListener = null;
	// The process listener instance
	private IProcesses.ProcessesListener processesListener = null;
	// The event listener instance
	private IEventListener eventListener = null;

	// The streams proxy instance
	private IProcessStreamsProxy streamsProxy = null;

	// The active token.
	IToken activeToken = null;

	/**
	 * Message ID for error message in case the process launch failed.
	 */
	public static final String PROCESS_LAUNCH_FAILED_MESSAGE = "processLaunchFailedMessage"; //$NON-NLS-1$

	/**
	 * Constructor.
	 */
	public ProcessLauncher() {
		super();
	}

	/**
	 * Constructor.
	 */
	public ProcessLauncher(IProcessStreamsProxy streamsProxy) {
		super();
		this.streamsProxy = streamsProxy;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessLauncher#dispose()
	 */
	@Override
	public void dispose() {
		// Unlink the process context
		processContext = null;

		// Store a final reference to the channel instance
		final IChannel finChannel = channel;

		// Remove the notification listener
		if (eventListener != null) {
			EventManager.getInstance().removeEventListener(eventListener);
			eventListener = null;
		}

		// Create the callback collector
		final AsyncCallbackCollector collector = new AsyncCallbackCollector(new Callback() {
			@Override
			protected void internalDone(Object caller, IStatus status) {
				Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
				// Close the channel as all disposal is done
				if (finChannel != null && closeChannelOnDispose) {
					Tcf.getChannelManager().closeChannel(finChannel);
				}
			}
		}, new CallbackInvocationDelegate());

		if (streamsListener != null) {
			// Dispose the streams listener
			if (streamsListener instanceof ProcessStreamsListener) {
				((ProcessStreamsListener)streamsListener).dispose(new AsyncCallbackCollector.SimpleCollectorCallback(collector));
			}
			streamsListener = null;
		}

		// Dispose the processes listener if created
		if (processesListener != null) {
			// Remove the processes listener from the processes service
			getSvcProcesses().removeListener(processesListener);

			// Dispose the processes listener
			if (processesListener instanceof ProcessProcessesListener) {
				((ProcessProcessesListener)processesListener).dispose(new AsyncCallbackCollector.SimpleCollectorCallback(collector));
			}

			processesListener = null;
		}

		// Dispose the streams proxy if created
		if (streamsProxy != null) {
			streamsProxy.dispose(new AsyncCallbackCollector.SimpleCollectorCallback(collector));
			streamsProxy = null;
		}
		// Mark the collector initialization as done
		collector.initDone();

		// Dissociate the channel
		channel = null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessLauncher#terminate()
	 */
	@Override
	public void terminate() {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				if (channel != null && channel.getState() == IChannel.STATE_OPEN) {
					if (processContext != null && processContext.canTerminate()) {
						// Try to terminate the process the usual way first (sending SIGTERM)
						processContext.terminate(new IProcesses.DoneCommand() {
							@Override
							public void doneCommand(IToken token, Exception error) {
								onTerminateDone(processContext, error);
							}
						});
					}
				}
			}
		};

		Protocol.invokeLater(runnable);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessLauncher#cancel()
	 */
	@Override
	public void cancel() {
		if (activeToken != null && (callback == null || !callback.isDone())) {
			final IToken token = activeToken;
			activeToken = null;
	    	Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
			    	token.cancel();
				}
			});
		}
	}

	/**
	 * Check if the process context really died after sending SIGTERM.
	 * <p>
	 * Called from {@link #terminate()}.
	 *
	 * @param context The process context. Must not be <code>null</code>.
	 * @param error The exception in case {@link #terminate()} returned with an error or <code>null</code>.
	 */
	protected void onTerminateDone(IProcesses.ProcessContext context, Exception error) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(context);

		// If the terminate of the remote process context failed, give a warning to the user
		if (error != null) {
			String message = NLS.bind(Messages.ProcessLauncher_error_processTerminateFailed, context.getName());
			message += NLS.bind(Messages.ProcessLauncher_error_possibleCause, StatusHelper.unwrapErrorReport(error.getLocalizedMessage()));

			IStatus status = new Status(IStatus.WARNING, CoreBundleActivator.getUniqueIdentifier(), message, error);
			Platform.getLog(CoreBundleActivator.getContext().getBundle()).log(status);

			// Dispose the launcher directly
			dispose();
		}
		// No error from terminate, this does not mean that the process went down
		// really -> SIGTERM might have been ignored from the process!
		else {
			final IProcesses.ProcessContext finContext = context;
			// Let's see if we can still get information about the context
			getSvcProcesses().getContext(context.getID(), new IProcesses.DoneGetContext() {
				@Override
				public void doneGetContext(IToken token, Exception error, ProcessContext context) {
					// In case there is no error and we do get back an process context,
					// the process must be still running, having ignored the SIGTERM.
					if (error == null && context != null && context.getID().equals(finContext.getID())) {
						// Let's send a SIGHUP next.
						getSvcProcesses().signal(context.getID(), 15, new IProcesses.DoneCommand() {
							@Override
							public void doneCommand(IToken token, Exception error) {
								onSignalSIGHUPDone(finContext, error);
							}
						});
					}
				}
			});
		}
	}

	/**
	 * Check if the process context died after sending SIGHUP.
	 * <p>
	 * Called from {@link #onTerminateDone(IProcesses.ProcessContext, Exception)}.
	 *
	 * @param context The process context. Must not be <code>null</code>.
	 * @param error The exception in case sending the signal returned with an error or <code>null</code>.
	 */
	protected void onSignalSIGHUPDone(IProcesses.ProcessContext context, Exception error) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(context);

		// If the terminate of the remote process context failed, give a warning to the user
		if (error != null) {
			String message = NLS.bind(Messages.ProcessLauncher_error_processSendSignalFailed, "SIGHUP(15)", context.getName()); //$NON-NLS-1$
			message += NLS.bind(Messages.ProcessLauncher_error_possibleCause, StatusHelper.unwrapErrorReport(error.getLocalizedMessage()));

			IStatus status = new Status(IStatus.WARNING, CoreBundleActivator.getUniqueIdentifier(), message, error);
			Platform.getLog(CoreBundleActivator.getContext().getBundle()).log(status);

			// Dispose the launcher directly
			dispose();
		}
		// No error from terminate, this does not mean that the process went down
		// really -> SIGTERM might have been ignored from the process!
		else {
			final IProcesses.ProcessContext finContext = context;
			// Let's see if we can still get information about the context
			getSvcProcesses().getContext(context.getID(), new IProcesses.DoneGetContext() {
				@Override
				public void doneGetContext(IToken token, Exception error, ProcessContext context) {
					// In case there is no error and we do get back an process context,
					// the process must be still running, having ignored the SIGHUP.
					if (error == null && context != null && context.getID().equals(finContext.getID())) {
						// Finally send a SIGKILL.
						getSvcProcesses().signal(context.getID(), 9, new IProcesses.DoneCommand() {
							@Override
							public void doneCommand(IToken token, Exception error) {
								if (error != null) {
									String message = NLS.bind(Messages.ProcessLauncher_error_processSendSignalFailed, "SIGKILL(15)", finContext.getName()); //$NON-NLS-1$
									message += NLS.bind(Messages.ProcessLauncher_error_possibleCause, StatusHelper.unwrapErrorReport(error.getLocalizedMessage()));

									IStatus status = new Status(IStatus.WARNING, CoreBundleActivator.getUniqueIdentifier(), message, error);
									Platform.getLog(CoreBundleActivator.getContext().getBundle()).log(status);

									// Dispose the launcher
									dispose();
								}
							}
						});
					}
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.processes.core.interfaces.launcher.IProcessLauncher#launch(org.eclipse.tcf.protocol.IPeer, org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer, org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void launch(final IPeer peer, final IPropertiesContainer properties, final ICallback callback) {
		Assert.isNotNull(peer);
		Assert.isNotNull(properties);

		// Normalize the callback
		if (callback == null) {
			this.callback = new Callback() {
				/* (non-Javadoc)
				 * @see org.eclipse.tcf.te.runtime.callback.Callback#internalDone(java.lang.Object, org.eclipse.core.runtime.IStatus)
				 */
				@Override
				public void internalDone(Object caller, IStatus status) {
				}
			};
		}
		else {
			this.callback = callback;
		}

		// Remember the process properties
		this.properties = properties;

		// Check if we get the channel to use passed in by the launch properties
		if (properties.containsKey(IProcessLauncher.PROP_CHANNEL)) {
			IChannel c = (IChannel) properties.getProperty(IProcessLauncher.PROP_CHANNEL);
			if (c != null && c.getState() == IChannel.STATE_OPEN) {
				channel = c;
				closeChannelOnDispose = false;
				sharedChannel = true;
			}
		}

		// Open a dedicated channel to the given peer
		final Map<String, Boolean> flags = new HashMap<String, Boolean>();
		flags.put(IChannelManager.FLAG_FORCE_NEW, properties.containsKey(IChannelManager.FLAG_FORCE_NEW) ? Boolean.valueOf(properties.getBooleanProperty(IChannelManager.FLAG_FORCE_NEW)) : Boolean.TRUE);
		if (channel != null && channel.getState() == IChannel.STATE_OPEN) {
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					onChannelOpenDone(peer);
				}
			});
		}
		else {
			Tcf.getChannelManager().openChannel(peer, flags, new IChannelManager.DoneOpenChannel() {
				/* (non-Javadoc)
				 * @see org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel#doneOpenChannel(java.lang.Throwable, org.eclipse.tcf.protocol.IChannel)
				 */
				@Override
				public void doneOpenChannel(Throwable error, IChannel channel) {
					if (error == null) {
						ProcessLauncher.this.channel = channel;
						ProcessLauncher.this.closeChannelOnDispose = true;
						ProcessLauncher.this.sharedChannel = !flags.get(IChannelManager.FLAG_FORCE_NEW).booleanValue();

						onChannelOpenDone(peer);
					} else {
						IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
													NLS.bind(Messages.ProcessLauncher_error_channelConnectFailed, peer.getID(), error.getLocalizedMessage()),
													error);
						invokeCallback(status, null);
					}
				}
			});
		}
	}

	protected void onChannelOpenDone(final IPeer peer) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Attach a channel listener so we can dispose ourself if the channel
		// is closed from the remote side.
		channel.addChannelListener(new IChannelListener() {
			/* (non-Javadoc)
			 * @see org.eclipse.tcf.protocol.IChannel.IChannelListener#onChannelOpened()
			 */
			@Override
			public void onChannelOpened() {
			}
			/* (non-Javadoc)
			 * @see org.eclipse.tcf.protocol.IChannel.IChannelListener#onChannelClosed(java.lang.Throwable)
			 */
			@Override
			public void onChannelClosed(Throwable error) {
				if (error != null) {
					IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
									NLS.bind(Messages.ProcessLauncher_error_channelConnectFailed, peer.getID(), error.getLocalizedMessage()),
									error);
					invokeCallback(status, null);
				}
			}
			/* (non-Javadoc)
			 * @see org.eclipse.tcf.protocol.IChannel.IChannelListener#congestionLevel(int)
			 */
			@Override
			public void congestionLevel(int level) {
			}
		});

		// Check if the channel is in connected state
		if (channel.getState() != IChannel.STATE_OPEN) {
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							Messages.ProcessLauncher_error_channelNotConnected,
							new IllegalStateException());
			invokeCallback(status, null);
			return;
		}

		// Do some very basic sanity checking on the process properties
		if (properties.getStringProperty(PROP_PROCESS_PATH) == null) {
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							Messages.ProcessLauncher_error_missingProcessPath,
							new IllegalArgumentException(PROP_PROCESS_PATH));
			invokeCallback(status, null);
			return;
		}

		// Check if the user wants to force the use of the IProcesses service
		String property = System.getProperty("processLauncher.force.IProcesses"); //$NON-NLS-1$
		boolean forceIProcesses = property != null ? Boolean.parseBoolean(property) : false;

		// Get the process and streams services. Try the V1 processes service first
		// before falling back to the standard processes service.
		if (!forceIProcesses) svcProcesses = channel.getRemoteService(IProcessesV1.class);
		if (svcProcesses == null) svcProcesses = channel.getRemoteService(IProcesses.class);
		if (svcProcesses == null) {
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							NLS.bind(Messages.ProcessLauncher_error_missingRequiredService, IProcesses.class.getName()),
							null);

			invokeCallback(status, null);
			return;
		}

		svcStreams = channel.getRemoteService(IStreams.class);
		if (svcStreams == null) {
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							NLS.bind(Messages.ProcessLauncher_error_missingRequiredService, IStreams.class.getName()),
							null);
			invokeCallback(status, null);
			return;
		}

		// Execute the launch
		executeLaunch();
	}

	/**
	 * Executes the launch of the remote process.
	 */
	protected void executeLaunch() {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the process properties container
		final IPropertiesContainer properties = getProperties();
		if (properties == null) {
			// This is an illegal argument. Properties must be set
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							NLS.bind(Messages.ProcessLauncher_error_illegalNullArgument, "properties"), //$NON-NLS-1$
							new IllegalArgumentException());
			invokeCallback(status, null);
			return;
		}

		// If a console should be associated, a streams listener needs to be created
		if (properties.getBooleanProperty(IProcessLauncher.PROP_PROCESS_ASSOCIATE_CONSOLE)
						|| properties.getStringProperty(IProcessLauncher.PROP_PROCESS_OUTPUT_REDIRECT_TO_FILE) != null) {
			// Create the streams listener
			streamsListener = createStreamsListener();
			// If available, we need to subscribe to the streams.
			if (streamsListener != null) {
				// Subscribe the streams service
				Tcf.getChannelManager().subscribeStream(channel, getSvcProcesses() instanceof IProcessesV1 ? IProcessesV1.NAME : IProcesses.NAME, streamsListener, new IChannelManager.DoneSubscribeStream() {

					@Override
					public void doneSubscribeStream(Throwable error) {
						// In case the subscribe to the stream fails, we pass on
						// the error to the user and stop the launch
						if (error != null) {
							// Construct the error message to show to the user
							String message = NLS.bind(getProcessLaunchFailedMessageTemplate(),
											properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH),
											makeString((String[])properties.getProperty(IProcessLauncher.PROP_PROCESS_ARGS)));
							message += NLS.bind(Messages.ProcessLauncher_error_possibleCause, Messages.ProcessLauncher_cause_subscribeFailed);

							// Construct the status object
							IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), message, error);
							invokeCallback(status, null);
						} else {
							// Initialize the console or output file
							onSubscribeStreamsDone();
						}
					}
				});
			} else {
				// No streams to attach to -> go directly to the process launch
				onAttachStreamsDone();
			}
		} else {
			// No streams to attach to -> go directly to the process launch
			onAttachStreamsDone();
		}
	}

	/**
	 * Initialize and attach the output console and/or the output file.
	 * <p>
	 * Called from {@link IChannelManager#subscribeStream(IChannel, String, org.eclipse.tcf.services.IStreams.StreamsListener, org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneSubscribeStream)}
	 */
	protected void onSubscribeStreamsDone() {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the process properties container
		IPropertiesContainer properties = getProperties();
		if (properties == null) {
			// This is an illegal argument. Properties must be set
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							NLS.bind(Messages.ProcessLauncher_error_illegalNullArgument, "properties"), //$NON-NLS-1$
							new IllegalArgumentException());
			invokeCallback(status, null);
			return;
		}

		// The callback collector assure that all necessary work, like opening the terminal
		// window and terminal tab, is done before the process get launched finally.
		AsyncCallbackCollector collector = new AsyncCallbackCollector(new Callback() {
			@Override
			protected void internalDone(Object caller, IStatus status) {
				if (status.getSeverity() == IStatus.ERROR) {
					invokeCallback(status, null);
					return;
				}

				// Launch the process
				onAttachStreamsDone();
			}
		}, new CallbackInvocationDelegate());

		// The streams got subscribed, check if we shall attach the console
		if (properties.getBooleanProperty(IProcessLauncher.PROP_PROCESS_ASSOCIATE_CONSOLE)) {
			// If no specific streams proxy is set, the output redirection will default
			// to the standard terminals console view
			if (streamsProxy == null) {
				// Register the notification listener to listen to the console disposal
				eventListener = new ProcessLauncherEventListener(this);
				EventManager.getInstance().addEventListener(eventListener, DisposedEvent.class);

				// Get the terminal service
				ITerminalService terminal = ServiceManager.getInstance().getService(ITerminalService.class);
				// If not available, we cannot fulfill this request
				if (terminal != null) {
					// Create the terminal streams settings
					PropertiesContainer props = new PropertiesContainer();
					props.setProperty(ITerminalsConnectorConstants.PROP_CONNECTOR_TYPE_ID, "org.eclipse.tcf.te.ui.terminals.type.streams"); //$NON-NLS-1$
					props.setProperty(ITerminalsConnectorConstants.PROP_ID, "org.eclipse.tcf.te.ui.terminals.TerminalsView"); //$NON-NLS-1$
					// Set the terminal tab title
					String terminalTitle = getTerminalTitle();
					if (terminalTitle != null) {
						props.setProperty(ITerminalsConnectorConstants.PROP_TITLE, terminalTitle);
					}

					// Get the process output listener list from the properties
					Object value = properties.getProperty(PROP_PROCESS_OUTPUT_LISTENER);
					StreamsDataReceiver.Listener[] listeners = value instanceof StreamsDataReceiver.Listener[] ? (StreamsDataReceiver.Listener[]) value : null;

					// Create and store the streams which will be connected to the terminals stdin
					props.setProperty(ITerminalsConnectorConstants.PROP_STREAMS_STDIN, connectRemoteOutputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDIN_ID }));
					// Create and store the streams the terminal will see as stdout
					props.setProperty(ITerminalsConnectorConstants.PROP_STREAMS_STDOUT, connectRemoteInputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDOUT_ID }, listeners));
					// Create and store the streams the terminal will see as stderr
					props.setProperty(ITerminalsConnectorConstants.PROP_STREAMS_STDERR, connectRemoteInputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDERR_ID }, null));

					// Copy the terminal properties
					props.setProperty(ITerminalsConnectorConstants.PROP_LOCAL_ECHO, properties.getBooleanProperty(ITerminalsConnectorConstants.PROP_LOCAL_ECHO));
					props.setProperty(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR, properties.getStringProperty(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR));
					props.setProperty(ITerminalsConnectorConstants.PROP_FORCE_NEW, properties.getBooleanProperty(ITerminalsConnectorConstants.PROP_FORCE_NEW));

					// The custom data object is the process launcher itself
					props.setProperty(ITerminalsConnectorConstants.PROP_DATA, this);

					// Initialize the process specific terminal state text representations
					props.setProperty("TabFolderManager_state_connected", Messages.ProcessLauncher_state_connected); //$NON-NLS-1$
					props.setProperty("TabFolderManager_state_connecting", Messages.ProcessLauncher_state_connecting); //$NON-NLS-1$
					props.setProperty("TabFolderManager_state_closed", Messages.ProcessLauncher_state_closed); //$NON-NLS-1$

					// Open the console
					terminal.openConsole(props, new AsyncCallbackCollector.SimpleCollectorCallback(collector));
				}
			} else {
				// Create and connect the streams which will be connected to the terminals stdin
				streamsProxy.connectInputStreamMonitor(connectRemoteOutputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDIN_ID }));
				// Create and store the streams the terminal will see as stdout
				streamsProxy.connectOutputStreamMonitor(connectRemoteInputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDOUT_ID }, null));
				// Create and store the streams the terminal will see as stderr
				streamsProxy.connectErrorStreamMonitor(connectRemoteInputStream(getStreamsListener(), new String[] { IProcesses.PROP_STDERR_ID }, null));
			}
		}

		// The streams got subscribed, check if we shall configure the output redirection to a file
		if (properties.getStringProperty(IProcessLauncher.PROP_PROCESS_OUTPUT_REDIRECT_TO_FILE) != null) {
			// Get the file name where to redirect the process output to
			String filename = properties.getStringProperty(IProcessLauncher.PROP_PROCESS_OUTPUT_REDIRECT_TO_FILE);
			try {
				// Create the receiver instance. If the file already exist, we
				// overwrite the file content.
				StreamsDataReceiver receiver = new StreamsDataReceiver(new BufferedWriter(new FileWriter(filename)),
								new String[] { IProcesses.PROP_STDOUT_ID, IProcesses.PROP_STDERR_ID });
				// Register the receiver to the streams listener
				if (getStreamsListener() instanceof ProcessStreamsListener) {
					((ProcessStreamsListener)getStreamsListener()).registerDataReceiver(receiver);
				}
			} catch (IOException e) {
				// Construct the error message to show to the user
				String message = NLS.bind(getProcessLaunchFailedMessageTemplate(),
								properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH),
								makeString((String[])properties.getProperty(IProcessLauncher.PROP_PROCESS_ARGS)));
				message += NLS.bind(Messages.ProcessLauncher_error_possibleCause,
								e.getLocalizedMessage() != null ? StatusHelper.unwrapErrorReport(e.getLocalizedMessage()) : Messages.ProcessLauncher_cause_ioexception);

				// Construct the status object
				IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), message, e);
				invokeCallback(status, null);
				return;
			}
		}

		// Mark the collector initialization as done
		collector.initDone();
	}

	/**
	 * Returns the message template for the process launch failed error message.
	 *
	 * @return The message template.
	 */
	protected String getProcessLaunchFailedMessageTemplate() {
		if (properties != null && properties.containsKey(PROCESS_LAUNCH_FAILED_MESSAGE)) {
			return properties.getStringProperty(PROCESS_LAUNCH_FAILED_MESSAGE);
		}
		return Messages.ProcessLauncher_error_processLaunchFailed;
	}

	/**
	 * Returns the terminal title string.
	 * <p>
	 * The default implementation constructs a title like &quot;<process> (Start time) [channel name]&quot;.
	 *
	 * @return The terminal title string or <code>null</code>.
	 */
	protected String getTerminalTitle() {
		if (properties == null) {
			return null;
		}

		StringBuilder title = new StringBuilder();

		IPath processPath = new Path(properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH));
		IPath monitoredProcessPath = null;
		if (properties.getStringProperty(IProcessLauncher.PROP_PROCESS_MONITORED_PATH) != null) {
			monitoredProcessPath = new Path(properties.getStringProperty(IProcessLauncher.PROP_PROCESS_MONITORED_PATH));
		}

		// In case, we do have a monitored process path here, we construct the title
		// as <monitor_app_basename>: <monitored_app>"
		if (monitoredProcessPath != null) {
			title.append(processPath.lastSegment());
			title.append(": "); //$NON-NLS-1$
			processPath = monitoredProcessPath;
		}

		// Avoid very long terminal title's by shortening the path if it has more than 3 segments
		if (processPath.segmentCount() > 3) {
			title.append(".../"); //$NON-NLS-1$
			title.append(processPath.lastSegment());
		} else {
			title.append(processPath.toString());
		}

		// Get the peer name
		final AtomicReference<String> peerName = new AtomicReference<String>(getProperties().getStringProperty(IProcessLauncher.PROP_CONNECTION_NAME));
		if (peerName.get() == null) {
			// Query the peer from the open channel
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					if (channel != null) {
						peerName.set(channel.getRemotePeer().getName());
					}
				}
			};

			if (Protocol.isDispatchThread()) runnable.run();
			else Protocol.invokeAndWait(runnable);
		}

		if (peerName.get() != null) {
			title.append(" [").append(peerName.get()).append("]"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
		String date = format.format(new Date(System.currentTimeMillis()));
		title.append(" (").append(date).append(")"); //$NON-NLS-1$ //$NON-NLS-2$

		return title.toString();
	}

	/**
	 * Connects the given stream id's to a local {@link InputStream} instance.
	 *
	 * @param streamsListener The streams listener. Must not be <code>null</code>.
	 * @param streamIds The stream id's. Must not be <code>null</code>.
	 * @param listeners A set of listeners to register to the streams data receiver or <code>null</code>.
	 *
	 * @return The local input stream instance or <code>null</code>.
	 */
	protected InputStream connectRemoteInputStream(IStreams.StreamsListener streamsListener, String[] streamIds, StreamsDataReceiver.Listener[] listeners) {
		Assert.isNotNull(streamsListener);
		Assert.isNotNull(streamIds);

		InputStream stream = null;

		// Create the output stream receiving the data from remote
		PipedOutputStream remoteStreamDataReceiverStream = new PipedOutputStream();
		// Create the piped input stream instance
		try { stream = new PipedInputStream(remoteStreamDataReceiverStream); } catch (IOException e) { /* ignored on purpose */ }

		// If the input stream creation succeeded, connect the data receiver
		if (stream != null) {
			StreamsDataReceiver receiver = new StreamsDataReceiver(new OutputStreamWriter(remoteStreamDataReceiverStream), streamIds);

			// Register the listeners if given
			if (listeners != null && listeners.length > 0) {
				for (StreamsDataReceiver.Listener listener : listeners) {
					receiver.addListener(listener);
				}
			}

			// Register the data receiver to the streams listener
			if (getStreamsListener() instanceof ProcessStreamsListener) {
				((ProcessStreamsListener)getStreamsListener()).registerDataReceiver(receiver);
			}
		}

		return stream;
	}

	/**
	 * Connects the given stream id's to a local {@link OutputStream} instance.
	 *
	 * @param streamsListener The streams listener. Must not be <code>null</code>.
	 * @param streamIds The stream id's. Must not be <code>null</code>.
	 *
	 * @return The local output stream instance or <code>null</code>.
	 */
	protected OutputStream connectRemoteOutputStream(IStreams.StreamsListener streamsListener, String[] streamIds) {
		Assert.isNotNull(streamsListener);
		Assert.isNotNull(streamIds);

		PipedInputStream inStream = null;

		// Create the output stream receiving the data from local
		PipedOutputStream stream = new PipedOutputStream();
		// Create the piped input stream instance
		try { inStream = new PipedInputStream(stream); } catch (IOException e) { stream = null; }

		// If the stream creation succeeded, connect the data provider
		if (stream != null && inStream != null) {
			StreamsDataProvider provider = new StreamsDataProvider(new InputStreamReader(inStream), streamIds);
			// Register the data provider to the streams listener
			if (getStreamsListener() instanceof ProcessStreamsListener) {
				((ProcessStreamsListener)getStreamsListener()).setDataProvider(provider);
			}
		}

		return stream;
	}

	/**
	 * Queries the initial new process environment from remote.
	 */
	protected void onAttachStreamsDone() {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Query the default environment for a new process
		getSvcProcesses().getEnvironment(new IProcesses.DoneGetEnvironment() {
			@Override
			public void doneGetEnvironment(IToken token, Exception error, Map<String, String> environment) {
				if (error != null) {
					// Construct the error message to show to the user
					String message = Messages.ProcessLauncher_error_getEnvironmentFailed;
					message += NLS.bind(Messages.ProcessLauncher_error_possibleCause,
									error.getLocalizedMessage() != null ? StatusHelper.unwrapErrorReport(error.getLocalizedMessage()) : Messages.ProcessLauncher_cause_startFailed);

					// Construct the status object
					IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), message, error);
					invokeCallback(status, null);
				} else {
					// Initiate the process launch
					onGetEnvironmentDone(environment);
				}
			}
		});
	}

	/**
	 * Initiate the process launch.
	 * <p>
	 * Called from {@link #executeLaunch()} or {@link #onAttachStreamsDone()}.
	 */
	protected void onGetEnvironmentDone(final Map<String, String> environment) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the process properties container
		final IPropertiesContainer properties = getProperties();
		if (properties == null) {
			// This is an illegal argument. Properties must be set
			IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(),
							NLS.bind(Messages.ProcessLauncher_error_illegalNullArgument, "properties"), //$NON-NLS-1$
							new IllegalArgumentException());
			invokeCallback(status, null);
			return;
		}

		// Create the process listener
		processesListener = createProcessesListener();
		if (processesListener != null) {
			getSvcProcesses().addListener(processesListener);
		}

		// Get the process attributes
		String processPath = properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH);

		Boolean processArgsAsIs = (Boolean)properties.getProperty(IProcessLauncher.PROP_USE_PROCESS_ARGS_AS_IS);
		String[] processArgs = (String[])properties.getProperty(IProcessLauncher.PROP_PROCESS_ARGS);
		// Assure that the first argument is the process path itself
		if (!(processArgs != null && processArgs.length > 0 && processPath.equals(processArgs[0]))) {
			// Prepend the process path to the list of arguments
			List<String> args = processArgs != null ? new ArrayList<String>(Arrays.asList(processArgs)) : new ArrayList<String>();
			if (processArgsAsIs == null || !processArgsAsIs.booleanValue()) {
				args.add(0, processPath);
			}
			processArgs = args.toArray(new String[args.size()]);
		}

		Boolean processCWDAsIs = (Boolean)properties.getProperty(IProcessLauncher.PROP_USE_PROCESS_CWD_AS_IS);
		String processCWD = properties.getStringProperty(IProcessLauncher.PROP_PROCESS_CWD);
		// If the process working directory is not explicitly set, default to the process path directory
		if (processCWD == null || "".equals(processCWD.trim())) { //$NON-NLS-1$
			processCWD = (processCWDAsIs == null || !processCWDAsIs.booleanValue()) ? new Path(processPath).removeLastSegments(1).toString() : ""; //$NON-NLS-1$
		}

		// Merge the initial process environment and the desired process environment
		Map<String, String> processEnv = new HashMap<String, String>(environment);
		@SuppressWarnings("unchecked")
        Map<String, String> processEnvDiff = (Map<String, String>)properties.getProperty(IProcessLauncher.PROP_PROCESS_ENV);
		mergeEnvironment(processEnv, processEnvDiff);

		boolean processConsole = properties.getBooleanProperty(IProcessLauncher.PROP_PROCESS_ASSOCIATE_CONSOLE);

		if (processConsole) {
			// Assure that the TERM variable is set to "ansi"
			processEnv.put("TERM", "ansi"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		boolean attach = properties.getBooleanProperty(IProcessLauncher.PROP_PROCESS_ATTACH);

		// Launch the remote process
		if (getSvcProcesses() instanceof IProcessesV1) {
			// Fill in the process launch parameter
            Map<String, Object> params = new HashMap<String, Object>();

            // Explicitly set the assumed defaults to achieve predictable behavior
            // independent of the actual agent side implementation.
            params.put(IProcessesV1.START_ATTACH, Boolean.FALSE);
            params.put(IProcessesV1.START_ATTACH_CHILDREN, Boolean.FALSE);
            params.put(IProcessesV1.START_STOP_AT_ENTRY, Boolean.FALSE);
            params.put(IProcessesV1.START_STOP_AT_MAIN, Boolean.FALSE);
            params.put(IProcessesV1.START_USE_TERMINAL, Boolean.FALSE);

            if (properties.getProperty(IProcessLauncher.PROP_PROCESSESV1_PARAMS) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> addParams = (Map<String, Object>)properties.getProperty(IProcessLauncher.PROP_PROCESSESV1_PARAMS);
                for (Entry<String,Object> entry : addParams.entrySet()) {
	                params.put(entry.getKey(), entry.getValue());
                }
            }
            else {
                params.put(IProcessesV1.START_ATTACH, Boolean.valueOf(attach));
                params.put(IProcessesV1.START_ATTACH_CHILDREN, Boolean.valueOf(properties.getBooleanProperty(IProcessesV1.START_ATTACH_CHILDREN)));
                params.put(IProcessesV1.START_STOP_AT_ENTRY, Boolean.valueOf(properties.getBooleanProperty(IProcessesV1.START_STOP_AT_ENTRY)));
                params.put(IProcessesV1.START_STOP_AT_MAIN, Boolean.valueOf(properties.getBooleanProperty(IProcessesV1.START_STOP_AT_MAIN)));
                params.put(IProcessesV1.START_USE_TERMINAL, Boolean.valueOf(processConsole));
            }

            activeToken = ((IProcessesV1)getSvcProcesses()).start(processCWD, processPath, processArgs, processEnv, params, new IProcesses.DoneStart() {
				@Override
				public void doneStart(IToken token, Exception error, ProcessContext process) {
					activeToken = null;
					if (error != null) {
						// Construct the error message to show to the user
						String message = NLS.bind(getProcessLaunchFailedMessageTemplate(),
												  properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH),
												  makeString((String[])properties.getProperty(IProcessLauncher.PROP_PROCESS_ARGS)));
						message += NLS.bind(Messages.ProcessLauncher_error_possibleCause,
										error.getLocalizedMessage() != null ? StatusHelper.unwrapErrorReport(error.getLocalizedMessage()) : Messages.ProcessLauncher_cause_startFailed);

						// Construct the status object
						IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), message, error);
						invokeCallback(status, null);
					} else {
						// Register the process context to the listener
						onProcessLaunchDone(process);
					}
				}
			});
		} else {
			activeToken = getSvcProcesses().start(processCWD, processPath, processArgs, processEnv, attach, new IProcesses.DoneStart() {
				@Override
				public void doneStart(IToken token, Exception error, ProcessContext process) {
					activeToken = null;
					if (error != null) {
						// Construct the error message to show to the user
						String message = NLS.bind(getProcessLaunchFailedMessageTemplate(),
												  properties.getStringProperty(IProcessLauncher.PROP_PROCESS_PATH),
												  makeString((String[])properties.getProperty(IProcessLauncher.PROP_PROCESS_ARGS)));
						message += NLS.bind(Messages.ProcessLauncher_error_possibleCause,
										error.getLocalizedMessage() != null ? StatusHelper.unwrapErrorReport(error.getLocalizedMessage()) : Messages.ProcessLauncher_cause_startFailed);

						// Construct the status object
						IStatus status = new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), message, error);
						invokeCallback(status, null);
					} else {
						// Register the process context to the listener
						onProcessLaunchDone(process);
					}
				}
			});
		}
	}

	/**
	 * Merge original channel environment and process environment.
	 * @param processEnv
	 * @param processEnvDiff
	 */
	protected void mergeEnvironment(Map<String,String> processEnv, Map<String,String> processEnvDiff) {
		if (processEnv != null && processEnvDiff != null && !processEnvDiff.isEmpty()) {
			processEnv.putAll(processEnvDiff);
		}
	}

	/**
	 * Register the process context to the listeners.
	 *
	 * @param process The process context or <code>null</code>.
	 */
	protected void onProcessLaunchDone(ProcessContext process) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Register the process context with the listeners
		if (process != null) {
			if (CoreBundleActivator.getTraceHandler().isSlotEnabled(0, ITraceIds.TRACE_PROCESS_LAUNCHER)) {
				CoreBundleActivator.getTraceHandler().trace("Process context created: id='" + process.getID() + "', name='" + process.getName() + "'", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								0, ITraceIds.TRACE_PROCESS_LAUNCHER, IStatus.INFO, getClass());
			}

			// Remember the process context
			processContext = process;

			// Push the process context to the listeners
			if (getStreamsListener() instanceof IProcessContextAwareListener) {
				((IProcessContextAwareListener)getStreamsListener()).setProcessContext(process);
			}
			if (getProcessesListener() instanceof IProcessContextAwareListener) {
				((IProcessContextAwareListener)getProcessesListener()).setProcessContext(process);
			}

			// Send a notification
			ProcessStateChangeEvent event = createRemoteProcessStateChangeEvent(process);
			if (event != null) {
				EventManager.getInstance().fireEvent(event);
			}
		}

		// Invoke the callback to signal that we are done
		invokeCallback(Status.OK_STATUS, process);
	}

	/**
	 * Creates a new remote process state change event instance.
	 *
	 * @param context The process context. Must not be <code>null</code>.
	 * @return The event instance or <code>null</code>.
	 */
	protected ProcessStateChangeEvent createRemoteProcessStateChangeEvent(IProcesses.ProcessContext context) {
		Assert.isNotNull(context);
		return new ProcessStateChangeEvent(context, ProcessStateChangeEvent.EVENT_PROCESS_CREATED, Boolean.FALSE, Boolean.TRUE, -1);
	}

	/**
	 * Invoke the callback with the given parameters. If the given status severity
	 * is {@link IStatus#ERROR}, the process launcher object is disposed automatically.
	 *
	 * @param status The status. Must not be <code>null</code>.
	 * @param result The result object or <code>null</code>.
	 */
	protected void invokeCallback(IStatus status, Object result) {
		// Dispose the process launcher if we report an error
		if (status.getSeverity() == IStatus.ERROR) {
			dispose();
		}

		// Invoke the callback
		ICallback callback = getCallback();
		if (callback != null) {
			callback.setResult(result);
			callback.done(this, status);
		}
	}

	/**
	 * Returns the channel instance.
	 *
	 * @return The channel instance or <code>null</code> if none.
	 */
	public final IChannel getChannel() {
		return channel;
	}

	/**
	 * Returns if the channel is a private or shared channel.
	 *
	 * @return <code>True</code> if the channel a shared channel, <code>false</code> otherwise.
	 */
	public final boolean isSharedChannel() {
		return sharedChannel;
	}

	/**
	 * Returns the process properties container.
	 *
	 * @return The process properties container or <code>null</code> if none.
	 */
	public final IPropertiesContainer getProperties() {
		return properties;
	}

	/**
	 * Returns the processes service instance.
	 *
	 * @return The processes service instance or <code>null</code> if none.
	 */
	public final IProcesses getSvcProcesses() {
		return svcProcesses;
	}

	/**
	 * Returns the streams service instance.
	 *
	 * @return The streams service instance or <code>null</code> if none.
	 */
	public final IStreams getSvcStreams() {
		return svcStreams;
	}

	/**
	 * Returns the callback instance.
	 *
	 * @return The callback instance or <code>null</code> if none.
	 */
	protected final ICallback getCallback() {
		return callback;
	}

	/**
	 * Create the streams listener instance.
	 *
	 * @return The streams listener instance or <code>null</code> if none.
	 */
	protected IChannelManager.IStreamsListener createStreamsListener() {
		return new ProcessStreamsListener(this);
	}

	/**
	 * Returns the streams listener instance.
	 *
	 * @return The streams listener instance or <code>null</code>.
	 */
	protected final IStreams.StreamsListener getStreamsListener() {
		return streamsListener;
	}

	/**
	 * Create the processes listener instance.
	 *
	 * @return The processes listener instance or <code>null</code> if none.
	 */
	protected IProcesses.ProcessesListener createProcessesListener() {
		return new ProcessProcessesListener(this);
	}

	/**
	 * Returns the processes listener instance.
	 *
	 * @return The processes listener instance or <code>null</code> if none.
	 */
	protected final IProcesses.ProcessesListener getProcessesListener() {
		return processesListener;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class adapter) {
		if (adapter.isAssignableFrom(IProcesses.ProcessesListener.class)) {
			return processesListener;
		}
		else if (adapter.isAssignableFrom(IStreams.StreamsListener.class)) {
			return streamsListener;
		}
		else if (adapter.isAssignableFrom(IStreams.class)) {
			return svcStreams;
		}
		else if (adapter.isAssignableFrom(IProcesses.class)) {
			return svcProcesses;
		}
		else if (adapter.isAssignableFrom(IChannel.class)) {
			return channel;
		}
		else if (adapter.isAssignableFrom(IPropertiesContainer.class)) {
			return properties;
		}
		else if (adapter.isAssignableFrom(IProcesses.ProcessContext.class)) {
			return processContext;
		}
		else if (adapter.isAssignableFrom(this.getClass())) {
			return this;
		}


		return super.getAdapter(adapter);
	}

	/**
	 * Makes a space separated string from the given array.
	 *
	 * @param array The string array or <code>null</code>.
	 * @return The string.
	 */
	String makeString(String[] array) {
		if (array == null) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder result = new StringBuilder();
		for (String element : array) {
			if (result.length() > 0) {
				result.append(' ');
			}
			result.append(element);
		}
		return result.toString();
	}
}
