/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 * Max Weninger (Wind River) - [361352] [TERMINALS][SSH] Add SSH terminal support
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.ssh.launcher;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.te.core.terminals.TerminalServiceFactory;
import org.eclipse.tcf.te.core.terminals.interfaces.ITerminalService;
import org.eclipse.tcf.te.core.terminals.interfaces.ITerminalService.Done;
import org.eclipse.tcf.te.core.terminals.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tcf.te.ui.terminals.interfaces.IConfigurationPanel;
import org.eclipse.tcf.te.ui.terminals.interfaces.IConfigurationPanelContainer;
import org.eclipse.tcf.te.ui.terminals.interfaces.IMementoHandler;
import org.eclipse.tcf.te.ui.terminals.internal.SettingsStore;
import org.eclipse.tcf.te.ui.terminals.launcher.AbstractLauncherDelegate;
import org.eclipse.tcf.te.ui.terminals.ssh.controls.SshWizardConfigurationPanel;
import org.eclipse.tcf.te.ui.terminals.ssh.nls.Messages;
import org.eclipse.tm.internal.terminal.provisional.api.ISettingsStore;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.internal.terminal.provisional.api.TerminalConnectorExtension;
import org.eclipse.tm.internal.terminal.ssh.SshSettings;

/**
 * SSH launcher delegate implementation.
 */
@SuppressWarnings("restriction")
public class SshLauncherDelegate extends AbstractLauncherDelegate {
	// The SSH terminal connection memento handler
	private final IMementoHandler mementoHandler = new SshMementoHandler();

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.ILauncherDelegate#needsUserConfiguration()
	 */
	@Override
	public boolean needsUserConfiguration() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.ILauncherDelegate#getPanel(org.eclipse.tcf.te.ui.terminals.interfaces.IConfigurationPanelContainer)
	 */
	@Override
	public IConfigurationPanel getPanel(IConfigurationPanelContainer container) {
		return new SshWizardConfigurationPanel(container);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.ILauncherDelegate#execute(java.util.Map, org.eclipse.tcf.te.core.terminals.interfaces.ITerminalService.Done)
	 */
	@Override
	public void execute(Map<String, Object> properties, Done done) {
		Assert.isNotNull(properties);

		// Set the terminal tab title
		String terminalTitle = getTerminalTitle(properties);
		if (terminalTitle != null) {
			properties.put(ITerminalsConnectorConstants.PROP_TITLE, terminalTitle);
		}

		// For SSH terminals, force a new terminal tab each time it is launched,
		// if not set otherwise from outside
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_FORCE_NEW)) {
			properties.put(ITerminalsConnectorConstants.PROP_FORCE_NEW, Boolean.TRUE);
		}

		// SSH terminals do have a disconnect button
		if (!properties.containsKey(ITerminalsConnectorConstants.PROP_HAS_DISCONNECT_BUTTON)) {
			properties.put(ITerminalsConnectorConstants.PROP_HAS_DISCONNECT_BUTTON, Boolean.TRUE);
		}

		// Get the terminal service
		ITerminalService terminal = TerminalServiceFactory.getService();
		// If not available, we cannot fulfill this request
		if (terminal != null) {
			terminal.openConsole(properties, done);
		}
	}

	/**
	 * Returns the terminal title string.
	 * <p>
	 * The default implementation constructs a title like &quot;SSH @ host (Start time) &quot;.
	 *
	 * @return The terminal title string or <code>null</code>.
	 */
	private String getTerminalTitle(Map<String, Object> properties) {
		String host = (String)properties.get(ITerminalsConnectorConstants.PROP_IP_HOST);
		String user = (String)properties.get(ITerminalsConnectorConstants.PROP_SSH_USER);

		if (host != null && user!= null) {
			DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
			String date = format.format(new Date(System.currentTimeMillis()));
			return NLS.bind(Messages.SshLauncherDelegate_terminalTitle, new String[]{user, host, date});
		}
		return Messages.SshLauncherDelegate_terminalTitle_default;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class adapter) {
		if (IMementoHandler.class.equals(adapter)) {
			return mementoHandler;
		}
	    return super.getAdapter(adapter);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.ILauncherDelegate#createTerminalConnector(java.util.Map)
	 */
    @Override
	public ITerminalConnector createTerminalConnector(Map<String, Object> properties) {
    	Assert.isNotNull(properties);

    	// Check for the terminal connector id
    	String connectorId = (String)properties.get(ITerminalsConnectorConstants.PROP_TERMINAL_CONNECTOR_ID);
		if (connectorId == null) connectorId = "org.eclipse.tm.internal.terminal.ssh.SshConnector"; //$NON-NLS-1$

		// Extract the ssh properties
		String host = (String)properties.get(ITerminalsConnectorConstants.PROP_IP_HOST);
		Object value = properties.get(ITerminalsConnectorConstants.PROP_IP_PORT);
		String port = value != null ? value.toString() : null;
		value = properties.get(ITerminalsConnectorConstants.PROP_TIMEOUT);
		String timeout = value != null ? value.toString() : null;
		value = properties.get(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE);
		String keepAlive = value != null ? value.toString() : null;
		String password = (String)properties.get(ITerminalsConnectorConstants.PROP_SSH_PASSWORD);
		String user = (String)properties.get(ITerminalsConnectorConstants.PROP_SSH_USER);

		int portOffset = 0;
		if (properties.get(ITerminalsConnectorConstants.PROP_IP_PORT_OFFSET) instanceof Integer) {
			portOffset = ((Integer)properties.get(ITerminalsConnectorConstants.PROP_IP_PORT_OFFSET)).intValue();
			if (portOffset < 0) portOffset = 0;
		}

		// The real port to connect to is port + portOffset
		port = Integer.toString(Integer.decode(port).intValue() + portOffset);

		// Construct the ssh settings store
		ISettingsStore store = new SettingsStore();

		// Construct the telnet settings
		SshSettings sshSettings = new SshSettings();
		sshSettings.setHost(host);
		sshSettings.setPort(port);
		sshSettings.setTimeout(timeout);
		sshSettings.setKeepalive(keepAlive);
		sshSettings.setPassword(password);
		sshSettings.setUser(user);

		// And save the settings to the store
		sshSettings.save(store);

		// MWE TODO make sure this is NOT passed outside as this is plain text
		store.put("Password", password); //$NON-NLS-1$

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
