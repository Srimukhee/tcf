/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.types;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.te.core.terminals.interfaces.ITerminalServiceOutputStreamMonitorListener;
import org.eclipse.tcf.te.core.terminals.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tcf.te.ui.terminals.internal.SettingsStore;
import org.eclipse.tcf.te.ui.terminals.streams.StreamsSettings;
import org.eclipse.tm.internal.terminal.provisional.api.ISettingsStore;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.internal.terminal.provisional.api.TerminalConnectorExtension;

/**
 * Streams terminal connector type implementation.
 */
@SuppressWarnings("restriction")
public class StreamsConnectorType extends AbstractConnectorType {

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.IConnectorType#createTerminalConnector(java.util.Map)
	 */
	@Override
	public ITerminalConnector createTerminalConnector(Map<String, Object> properties) {
		Assert.isNotNull(properties);

    	// Check for the terminal connector id
    	String connectorId = (String)properties.get(ITerminalsConnectorConstants.PROP_TERMINAL_CONNECTOR_ID);
		if (connectorId == null) connectorId = "org.eclipse.tcf.te.ui.terminals.StreamsConnector"; //$NON-NLS-1$

		// Extract the streams properties
		OutputStream stdin = (OutputStream)properties.get(ITerminalsConnectorConstants.PROP_STREAMS_STDIN);
		InputStream stdout = (InputStream)properties.get(ITerminalsConnectorConstants.PROP_STREAMS_STDOUT);
		InputStream stderr = (InputStream)properties.get(ITerminalsConnectorConstants.PROP_STREAMS_STDERR);
		Object value = properties.get(ITerminalsConnectorConstants.PROP_LOCAL_ECHO);
		boolean localEcho =  value instanceof Boolean ? ((Boolean)value).booleanValue() : false;
		String lineSeparator = (String)properties.get(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR);
		ITerminalServiceOutputStreamMonitorListener[] stdoutListeners = (ITerminalServiceOutputStreamMonitorListener[])properties.get(ITerminalsConnectorConstants.PROP_STDOUT_LISTENERS);
		ITerminalServiceOutputStreamMonitorListener[] stderrListeners = (ITerminalServiceOutputStreamMonitorListener[])properties.get(ITerminalsConnectorConstants.PROP_STDERR_LISTENERS);

		// Construct the terminal settings store
		ISettingsStore store = new SettingsStore();

		// Construct the streams settings
		StreamsSettings streamsSettings = new StreamsSettings();
		streamsSettings.setStdinStream(stdin);
		streamsSettings.setStdoutStream(stdout);
		streamsSettings.setStderrStream(stderr);
		streamsSettings.setLocalEcho(localEcho);
		streamsSettings.setLineSeparator(lineSeparator);
		streamsSettings.setStdOutListeners(stdoutListeners);
		streamsSettings.setStdErrListeners(stderrListeners);
		// And save the settings to the store
		streamsSettings.save(store);

		// Construct the terminal connector instance
		ITerminalConnector connector = TerminalConnectorExtension.makeTerminalConnector(connectorId);
		if (connector != null) {
			// Apply default settings
			connector.makeSettingsPage();
			// And load the real settings
			connector.load(store);
		}

		return connector;
	}
}
