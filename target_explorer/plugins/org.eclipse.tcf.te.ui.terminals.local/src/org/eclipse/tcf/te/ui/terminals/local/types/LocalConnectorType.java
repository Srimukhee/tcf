/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.local.types;

import java.io.File;

import org.eclipse.cdt.utils.pty.PTY;
import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.services.interfaces.ITerminalServiceOutputStreamMonitorListener;
import org.eclipse.tcf.te.runtime.services.interfaces.constants.ILineSeparatorConstants;
import org.eclipse.tcf.te.runtime.services.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tcf.te.runtime.utils.Host;
import org.eclipse.tcf.te.ui.terminals.internal.SettingsStore;
import org.eclipse.tcf.te.ui.terminals.process.ProcessSettings;
import org.eclipse.tcf.te.ui.terminals.types.AbstractConnectorType;
import org.eclipse.tm.internal.terminal.provisional.api.ISettingsStore;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.internal.terminal.provisional.api.TerminalConnectorExtension;

/**
 * Streams terminal connector type implementation.
 */
@SuppressWarnings("restriction")
public class LocalConnectorType extends AbstractConnectorType {

	/**
	 * Returns the default shell to launch. Looks at the environment
	 * variable "SHELL" first before assuming some default default values.
	 *
	 * @return The default shell to launch.
	 */
	private final File defaultShell() {
		String shell = null;
		if (Host.isWindowsHost()) {
			if (System.getenv("ComSpec") != null && !"".equals(System.getenv("ComSpec").trim())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				shell = System.getenv("ComSpec").trim(); //$NON-NLS-1$
			} else {
				shell = "cmd.exe"; //$NON-NLS-1$
			}
		}
		if (shell == null) {
			if (System.getenv("SHELL") != null && !"".equals(System.getenv("SHELL").trim())) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				shell = System.getenv("SHELL").trim(); //$NON-NLS-1$
			} else {
				shell = "/bin/sh"; //$NON-NLS-1$
			}
		}

		return new File(shell);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.IConnectorType#createTerminalConnector(org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer)
	 */
	@Override
	public ITerminalConnector createTerminalConnector(IPropertiesContainer properties) {
		Assert.isNotNull(properties);

		// Check for the terminal connector id
		String connectorId = properties.getStringProperty(ITerminalsConnectorConstants.PROP_TERMINAL_CONNECTOR_ID);
		if (connectorId == null) connectorId = "org.eclipse.tcf.te.ui.terminals.local.LocalConnector"; //$NON-NLS-1$

		// Extract the process properties using defaults
		String image;
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_PROCESS_PATH)
				|| properties.getStringProperty(ITerminalsConnectorConstants.PROP_PROCESS_PATH) == null) {
			File defaultShell = defaultShell();
			image = defaultShell.isAbsolute() ? defaultShell.getAbsolutePath() : defaultShell.getPath();
		} else {
			image = properties.getStringProperty(ITerminalsConnectorConstants.PROP_PROCESS_PATH);
		}

		// Determine if a PTY will be used
		boolean isUsingPTY = (properties.getProperty(ITerminalsConnectorConstants.PROP_PROCESS_OBJ) == null && PTY.isSupported(PTY.Mode.TERMINAL))
								|| properties.getProperty(ITerminalsConnectorConstants.PROP_PTY_OBJ) instanceof PTY;

		boolean localEcho = false;
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_LOCAL_ECHO)
				|| properties.getStringProperty(ITerminalsConnectorConstants.PROP_LOCAL_ECHO) == null) {
			// On Windows, turn on local echo by default if no PTY is used (bug 433645)
			if (Host.isWindowsHost()) {
				localEcho = !isUsingPTY;
			}
		} else {
			localEcho = properties.getBooleanProperty(ITerminalsConnectorConstants.PROP_LOCAL_ECHO);
		}

		String lineSeparator = null;
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR)
				|| properties.getStringProperty(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR) == null) {
			// No line separator will be set if a PTY is used
			if (!isUsingPTY) {
				lineSeparator = Host.isWindowsHost() ? ILineSeparatorConstants.LINE_SEPARATOR_CRLF : ILineSeparatorConstants.LINE_SEPARATOR_LF;
			}
		} else {
			lineSeparator = properties.getStringProperty(ITerminalsConnectorConstants.PROP_LINE_SEPARATOR);
		}

		String arguments = properties.getStringProperty(ITerminalsConnectorConstants.PROP_PROCESS_ARGS);
		Process process = (Process)properties.getProperty(ITerminalsConnectorConstants.PROP_PROCESS_OBJ);
		PTY pty = (PTY)properties.getProperty(ITerminalsConnectorConstants.PROP_PTY_OBJ);
		ITerminalServiceOutputStreamMonitorListener[] stdoutListeners = (ITerminalServiceOutputStreamMonitorListener[])properties.getProperty(ITerminalsConnectorConstants.PROP_STDOUT_LISTENERS);
		ITerminalServiceOutputStreamMonitorListener[] stderrListeners = (ITerminalServiceOutputStreamMonitorListener[])properties.getProperty(ITerminalsConnectorConstants.PROP_STDERR_LISTENERS);
		String workingDir = properties.getStringProperty(ITerminalsConnectorConstants.PROP_PROCESS_WORKING_DIR);

		String[] envp = null;
		if (properties.containsKey(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT) &&
						properties.getProperty(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT) != null &&
						properties.getProperty(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT) instanceof String[]){
			envp = (String[])properties.getProperty(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT);
		}

		Assert.isTrue(image != null || process != null);

		// Construct the terminal settings store
		ISettingsStore store = new SettingsStore();

		// Construct the process settings
		ProcessSettings processSettings = new ProcessSettings();
		processSettings.setImage(image);
		processSettings.setArguments(arguments);
		processSettings.setProcess(process);
		processSettings.setPTY(pty);
		processSettings.setLocalEcho(localEcho);
		processSettings.setLineSeparator(lineSeparator);
		processSettings.setStdOutListeners(stdoutListeners);
		processSettings.setStdErrListeners(stderrListeners);
		processSettings.setWorkingDir(workingDir);
		processSettings.setEnvironment(envp);

		if (properties.containsKey(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT)) {
			processSettings.setMergeWithNativeEnvironment(properties.getBooleanProperty(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT));
		}

		// And save the settings to the store
		processSettings.save(store);

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
