/*******************************************************************************
 * Copyright (c) 2011, 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 * Max Weninger (Wind River) - [361352] [TERMINALS][SSH] Add SSH terminal support
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.ssh.controls;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.IPropertiesAccessService;
import org.eclipse.tcf.te.runtime.services.interfaces.constants.IPropertiesAccessServiceConstants;
import org.eclipse.tcf.te.runtime.services.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tcf.te.ui.controls.BaseDialogPageControl;
import org.eclipse.tcf.te.ui.interfaces.data.IDataExchangeNode;
import org.eclipse.tcf.te.ui.jface.interfaces.IValidatingContainer;
import org.eclipse.tcf.te.ui.terminals.panels.AbstractConfigurationPanel;
import org.eclipse.tcf.te.ui.terminals.ssh.nls.Messages;
import org.eclipse.tm.internal.terminal.provisional.api.AbstractSettingsPage;
import org.eclipse.tm.internal.terminal.provisional.api.ISettingsPage;
import org.eclipse.tm.internal.terminal.ssh.SshConnector;
import org.eclipse.tm.internal.terminal.ssh.SshSettings;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * SSH wizard configuration panel implementation.
 */
@SuppressWarnings("restriction")
public class SshWizardConfigurationPanel extends AbstractConfigurationPanel implements IDataExchangeNode {

	private static final String SAVE_PASSWORD = "savePassword"; //$NON-NLS-1$

    private SshSettings sshSettings;
	private ISettingsPage sshSettingsPage;
	private Button passwordButton;

	/**
	 * Constructor.
	 *
	 * @param parentControl The parent control. Must not be <code>null</code>!
	 */
	public SshWizardConfigurationPanel(BaseDialogPageControl parentControl) {
	    super(parentControl);
    }

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.controls.interfaces.IWizardConfigurationPanel#setupPanel(org.eclipse.swt.widgets.Composite, org.eclipse.ui.forms.widgets.FormToolkit)
	 */
	@Override
	public void setupPanel(Composite parent, FormToolkit toolkit) {
		Composite panel = new Composite(parent, SWT.NONE);
		panel.setLayout(new GridLayout());
		GridData data = new GridData(SWT.FILL, SWT.FILL, true, true);
		panel.setLayoutData(data);

		// Create the host selection combo
		if (isWithoutSelection()) createHostsUI(panel, true);

		SshConnector conn = new SshConnector();
		sshSettings = (SshSettings) conn.getSshSettings();
		sshSettings.setHost(getSelectionHost());
		sshSettings.setUser(getDefaultUser());

		sshSettingsPage = conn.makeSettingsPage();
		if (sshSettingsPage instanceof AbstractSettingsPage) {
			((AbstractSettingsPage)sshSettingsPage).setHasControlDecoration(true);
		}
		sshSettingsPage.createControl(panel);

		// Add the listener to the settings page
		if (getParentControl() instanceof IValidatingContainer) {
			sshSettingsPage.addListener(new ISettingsPage.Listener() {

				@Override
				public void onSettingsPageChanged(Control control) {
					((IValidatingContainer)getParentControl()).validate();
				}
			});
		}

		// Create the encoding selection combo
		createEncodingUI(panel, true);

		// if password for host should be saved or no
		createPasswordUI(panel, true);

		setControl(panel);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.controls.interfaces.IWizardConfigurationPanel#dataChanged(org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer, org.eclipse.swt.events.TypedEvent)
	 */
	@Override
	public boolean dataChanged(IPropertiesContainer data, TypedEvent e) {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.wizards.interfaces.ISharedDataExchangeNode#setupData(org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer)
	 */
	@Override
    public void setupData(IPropertiesContainer data) {
		if (data == null || sshSettings == null || sshSettingsPage == null) return;

		String value = data.getStringProperty(ITerminalsConnectorConstants.PROP_IP_HOST);
		if (value != null) sshSettings.setHost(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_IP_PORT);
		if (value != null) sshSettings.setPort(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_TIMEOUT);
		if (value != null) sshSettings.setTimeout(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE);
		if (value != null) sshSettings.setKeepalive(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_SSH_PASSWORD);
		if (value != null) sshSettings.setPassword(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_SSH_USER);
		if (value != null) sshSettings.setUser(value);

		value = data.getStringProperty(ITerminalsConnectorConstants.PROP_ENCODING);
		if (value != null) setEncoding(value);

		sshSettingsPage.loadSettings();
    }

	/**
	 * Returns the default user name.
	 *
	 * @return The default user name.
	 */
	private String getDefaultUser() {
		ISelection selection = getSelection();
		if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
			Object element = ((IStructuredSelection) selection).getFirstElement();
			IPropertiesAccessService service = ServiceManager.getInstance().getService(element, IPropertiesAccessService.class);
			if (service != null) {
				Object user = service.getProperty(element, IPropertiesAccessServiceConstants.PROP_USER);
				if (user instanceof String) return (String) user;
			}
		}

		return System.getProperty("user.name"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.wizards.interfaces.ISharedDataExchangeNode#extractData(org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer)
	 */
	@Override
    public void extractData(IPropertiesContainer data) {
    	// set the terminal connector id for ssh
    	data.setProperty(ITerminalsConnectorConstants.PROP_TERMINAL_CONNECTOR_ID, "org.eclipse.tm.internal.terminal.ssh.SshConnector"); //$NON-NLS-1$

    	// set the connector type for ssh
    	data.setProperty(ITerminalsConnectorConstants.PROP_CONNECTOR_TYPE_ID, "org.eclipse.tcf.te.ui.terminals.type.ssh"); //$NON-NLS-1$

    	sshSettingsPage.saveSettings();
		data.setProperty(ITerminalsConnectorConstants.PROP_IP_HOST,sshSettings.getHost());
		data.setProperty(ITerminalsConnectorConstants.PROP_IP_PORT, sshSettings.getPort());
		data.setProperty(ITerminalsConnectorConstants.PROP_TIMEOUT, sshSettings.getTimeout());
		data.setProperty(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE, sshSettings.getKeepalive());
		data.setProperty(ITerminalsConnectorConstants.PROP_SSH_PASSWORD, sshSettings.getPassword());
		data.setProperty(ITerminalsConnectorConstants.PROP_SSH_USER, sshSettings.getUser());
		data.setProperty(ITerminalsConnectorConstants.PROP_ENCODING, getEncoding());
    }

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.panels.AbstractConfigurationPanel#fillSettingsForHost(java.lang.String)
	 */
	@Override
	protected void fillSettingsForHost(String host){
		boolean savePassword = false;
		if (host != null && host.length() != 0){
			if (hostSettingsMap.containsKey(host)){
				Map<String, String> hostSettings = hostSettingsMap.get(host);
				if (hostSettings.get(ITerminalsConnectorConstants.PROP_IP_HOST) != null) {
					sshSettings.setHost(hostSettings.get(ITerminalsConnectorConstants.PROP_IP_HOST));
				}
				if (hostSettings.get(ITerminalsConnectorConstants.PROP_IP_PORT) != null) {
					sshSettings.setPort(hostSettings.get(ITerminalsConnectorConstants.PROP_IP_PORT));
				}
				if (hostSettings.get(ITerminalsConnectorConstants.PROP_TIMEOUT) != null) {
					sshSettings.setTimeout(hostSettings.get(ITerminalsConnectorConstants.PROP_TIMEOUT));
				}
				if (hostSettings.get(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE) != null) {
					sshSettings.setKeepalive(hostSettings.get(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE));
				}
				if (hostSettings.get(ITerminalsConnectorConstants.PROP_SSH_USER) != null) {
					sshSettings.setUser(hostSettings.get(ITerminalsConnectorConstants.PROP_SSH_USER));
				}
				if (hostSettings.get(SAVE_PASSWORD) != null) {
					savePassword = new Boolean(hostSettings.get(SAVE_PASSWORD)).booleanValue();
				}
				if (!savePassword){
					sshSettings.setPassword(""); //$NON-NLS-1$
				} else {
					String password = accessSecurePassword(sshSettings.getHost());
					if (password != null) {
						sshSettings.setPassword(password);
					}
				}

				String encoding = hostSettings.get(ITerminalsConnectorConstants.PROP_ENCODING);
				if (encoding == null || "null".equals(encoding)) encoding = "ISO-8859-1"; //$NON-NLS-1$ //$NON-NLS-2$
				setEncoding(encoding);
			} else {
				sshSettings.setHost(getSelectionHost());
				sshSettings.setUser(getDefaultUser());
				savePassword = false;
			}
			// set settings in page
			sshSettingsPage.loadSettings();
			passwordButton.setSelection(savePassword);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.controls.interfaces.IWizardConfigurationPanel#doSaveWidgetValues(org.eclipse.jface.dialogs.IDialogSettings, java.lang.String)
	 */
	@Override
    public void doSaveWidgetValues(IDialogSettings settings, String idPrefix) {
    	saveSettingsForHost(true);
    	super.doSaveWidgetValues(settings, idPrefix);
    }

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.panels.AbstractConfigurationPanel#saveSettingsForHost(boolean)
	 */
	@Override
	protected void saveSettingsForHost(boolean add){
		boolean savePassword = passwordButton.getSelection();
		String host = getHostFromSettings();
		if (host != null && host.length() != 0) {
			if (hostSettingsMap.containsKey(host)){
				Map<String, String> hostSettings = hostSettingsMap.get(host);
				hostSettings.put(ITerminalsConnectorConstants.PROP_IP_HOST, sshSettings.getHost());
				hostSettings.put(ITerminalsConnectorConstants.PROP_IP_PORT, Integer.toString(sshSettings.getPort()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_TIMEOUT, Integer.toString(sshSettings.getTimeout()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE, Integer.toString(sshSettings.getKeepalive()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_SSH_USER, sshSettings.getUser());
				hostSettings.put(ITerminalsConnectorConstants.PROP_ENCODING, getEncoding());
				hostSettings.put(SAVE_PASSWORD, Boolean.toString(savePassword));

				if (savePassword && sshSettings.getPassword() != null && sshSettings.getPassword().length() != 0){
					saveSecurePassword(host, sshSettings.getPassword());
				}

				// maybe unchecked the password button - so try to remove a saved password - if any
				if (!savePassword){
					removeSecurePassword(host);
				}
			} else if (add) {
				Map<String, String> hostSettings = new HashMap<String, String>();
				hostSettings.put(ITerminalsConnectorConstants.PROP_IP_HOST, sshSettings.getHost());
				hostSettings.put(ITerminalsConnectorConstants.PROP_IP_PORT, Integer.toString(sshSettings.getPort()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_TIMEOUT, Integer.toString(sshSettings.getTimeout()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_SSH_KEEP_ALIVE, Integer.toString(sshSettings.getKeepalive()));
				hostSettings.put(ITerminalsConnectorConstants.PROP_SSH_USER, sshSettings.getUser());
				hostSettings.put(ITerminalsConnectorConstants.PROP_ENCODING, getEncoding());
				hostSettings.put(SAVE_PASSWORD, Boolean.toString(savePassword));
				hostSettingsMap.put(host, hostSettings);

				if (savePassword && sshSettings.getPassword() != null && sshSettings.getPassword().length() != 0){
					saveSecurePassword(host, sshSettings.getPassword());
				}
			}
		}
	}

	/**
	 * Save the password to the secure storage.
	 *
	 * @param host The host. Must not be <code>null</code>.
	 * @param password The password. Must not be <code>null</code>.
	 */
	private void saveSecurePassword(String host, String password) {
		Assert.isNotNull(host);
		Assert.isNotNull(password);

		// To access the secure storage, we need the preference instance
		ISecurePreferences preferences = SecurePreferencesFactory.getDefault();
		if (preferences != null) {
			// Construct the secure preferences node key
			String nodeKey = "/Target Explorer SSH Password/" + host; //$NON-NLS-1$
			ISecurePreferences node = preferences.node(nodeKey);
			if (node != null) {
				try {
					node.put("password", password, true); //$NON-NLS-1$
				}
				catch (StorageException ex) { /* ignored on purpose */ }
			}
		}
	}

	/**
	 * Reads the password from the secure storage.
	 *
	 * @param host The host. Must not be <code>null</code>.
	 * @return The password or <code>null</code>.
	 */
	private String accessSecurePassword(String host) {
		Assert.isNotNull(host);

		// To access the secure storage, we need the preference instance
		ISecurePreferences preferences = SecurePreferencesFactory.getDefault();
		if (preferences != null) {
			// Construct the secure preferences node key
			String nodeKey = "/Target Explorer SSH Password/" + host; //$NON-NLS-1$
			ISecurePreferences node = preferences.node(nodeKey);
			if (node != null) {
				String password = null;
				try {
					password = node.get("password", null); //$NON-NLS-1$
				}
				catch (StorageException ex) { /* ignored on purpose */ }

				return password;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.panels.AbstractConfigurationPanel#removeSecurePassword(java.lang.String)
	 */
	@Override
	protected void removeSecurePassword(String host) {
		Assert.isNotNull(host);

		// To access the secure storage, we need the preference instance
		ISecurePreferences preferences = SecurePreferencesFactory.getDefault();
		if (preferences != null) {
			// Construct the secure preferences node key
			String nodeKey = "/Target Explorer SSH Password/" + host; //$NON-NLS-1$
			ISecurePreferences node = preferences.node(nodeKey);
			if (node != null) {
				node.remove("password"); //$NON-NLS-1$
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.controls.panels.AbstractWizardConfigurationPanel#isValid()
	 */
	@Override
    public boolean isValid(){
		return isEncodingValid() && sshSettingsPage.validateSettings();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.panels.AbstractConfigurationPanel#getHostFromSettings()
	 */
	@Override
    protected String getHostFromSettings() {
		sshSettingsPage.saveSettings();
	    return sshSettings.getHost();
    }

	private void createPasswordUI(final Composite parent, boolean separator) {
		Assert.isNotNull(parent);

		if (separator) {
			Label sep = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);
			sep.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		}

		Composite panel = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		panel.setLayout(layout);
		panel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		passwordButton = new Button(panel, SWT.CHECK);
		passwordButton.setLayoutData(new GridData(SWT.FILL, SWT.RIGHT, true, false));
		passwordButton.setText(Messages.SshWizardConfigurationPanel_savePasword);
	}
}
