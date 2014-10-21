/*******************************************************************************
 * Copyright (c) 2006, 2012 PalmSource, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Ewa Matejska (PalmSource)
 *
 * Referenced GDBDebuggerPage code to write this.
 * Anna Dushistova (Mentor Graphics) - adapted from RemoteGDBDebuggerPage
 * Anna Dushistova (Mentor Graphics) - moved to org.eclipse.cdt.launch.remote.tabs
 * Anna Dushistova (MontaVista)      - adapted from TEDSFGDBDebuggerPage
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.launch.cdt.tabs;

import org.eclipse.cdt.dsf.gdb.internal.ui.launching.GdbDebuggerPage;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.tcf.te.tcf.launch.cdt.interfaces.IRemoteTEConfigurationConstants;
import org.eclipse.tcf.te.tcf.launch.cdt.nls.Messages;
import org.eclipse.tcf.te.ui.controls.validator.PortNumberVerifyListener;

@SuppressWarnings("restriction")
public class TEDSFGDBDebuggerPage extends GdbDebuggerPage {

	protected Text fGDBServerCommandText = null;

	protected Text fGDBServerPortNumberText = null;

	protected Text fGDBServerPortNumberMappedToText = null;

	private boolean fIsInitializing = false;

	public TEDSFGDBDebuggerPage() {
		super();
	}

	@Override
	public String getName() {
		return Messages.Remote_GDB_Debugger_Options;
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		super.setDefaults(configuration);
		configuration.setAttribute(
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_COMMAND,
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_COMMAND_DEFAULT);
		configuration.setAttribute(
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT,
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT_DEFAULT);
		configuration.setAttribute(
						IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT_MAPPED_TO,
						(String)null);
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		setInitializing(true);
		super.initializeFrom(configuration);

		String gdbserverCommand = null;
		String gdbserverPortNumber = null;
		String portNumberMappedTo = null;
		try {
			gdbserverCommand = configuration
					.getAttribute(
							IRemoteTEConfigurationConstants.ATTR_GDBSERVER_COMMAND,
							IRemoteTEConfigurationConstants.ATTR_GDBSERVER_COMMAND_DEFAULT);
		} catch (CoreException e) {
		}
		try {
			gdbserverPortNumber = configuration
					.getAttribute(
							IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT,
							IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT_DEFAULT);
		} catch (CoreException e) {
		}
		try {
			portNumberMappedTo = configuration
					.getAttribute(
							IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT_MAPPED_TO,
							(String)null);
		} catch (CoreException e) {
		}
		if (fGDBServerCommandText != null) fGDBServerCommandText.setText(gdbserverCommand);
		if (fGDBServerPortNumberText != null) fGDBServerPortNumberText.setText(gdbserverPortNumber);
		if (fGDBServerPortNumberMappedToText != null) fGDBServerPortNumberMappedToText.setText(portNumberMappedTo != null ? portNumberMappedTo : ""); //$NON-NLS-1$
		setInitializing(false);
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		super.performApply(configuration);
		String str = fGDBServerCommandText != null ? fGDBServerCommandText.getText().trim() : null;
		configuration.setAttribute(
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_COMMAND, str);
		str = fGDBServerPortNumberText != null ? fGDBServerPortNumberText.getText().trim() : null;
		configuration.setAttribute(
				IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT, str);
		str = fGDBServerPortNumberMappedToText != null ? fGDBServerPortNumberMappedToText.getText().trim() : null;
		configuration.setAttribute(
						IRemoteTEConfigurationConstants.ATTR_GDBSERVER_PORT_MAPPED_TO,
						str != null && !"".equals(str) ? str : null); //$NON-NLS-1$
	}

	protected void createGdbserverSettingsTab(TabFolder tabFolder) {
		TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
		tabItem.setText(Messages.Gdbserver_Settings_Tab_Name);

		Composite comp = new Composite(tabFolder, SWT.NULL);
		comp.setLayout(new GridLayout(1, false));
		comp.setLayoutData(new GridData(GridData.FILL_BOTH));
		comp.setFont(tabFolder.getFont());
		tabItem.setControl(comp);

		Composite subComp = new Composite(comp, SWT.NULL);
		subComp.setLayout(new GridLayout(2, false));
		subComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		subComp.setFont(tabFolder.getFont());

		Label label = new Label(subComp, SWT.LEFT);
		label.setText(Messages.Gdbserver_name_textfield_label);

		fGDBServerCommandText = new Text(subComp, SWT.SINGLE | SWT.BORDER);
		GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		fGDBServerCommandText.setLayoutData(data);
		fGDBServerCommandText.addModifyListener(new ModifyListener() {

			@SuppressWarnings("synthetic-access")
            @Override
            public void modifyText(ModifyEvent evt) {
				updateLaunchConfigurationDialog();
			}
		});

		label = new Label(subComp, SWT.LEFT);
		label.setText(Messages.Port_number_textfield_label);

		fGDBServerPortNumberText = new Text(subComp, SWT.SINGLE | SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		fGDBServerPortNumberText.setLayoutData(data);
		fGDBServerPortNumberText.addModifyListener(new ModifyListener() {

			@SuppressWarnings("synthetic-access")
            @Override
            public void modifyText(ModifyEvent evt) {
				updateLaunchConfigurationDialog();
			}
		});
		fGDBServerPortNumberText.addVerifyListener(new PortNumberVerifyListener(PortNumberVerifyListener.ATTR_DECIMAL | PortNumberVerifyListener.ATTR_HEX));

		label = new Label(subComp, SWT.LEFT);
		label.setText(Messages.Port_number_mapped_to_textfield_label);

		fGDBServerPortNumberMappedToText = new Text(subComp, SWT.SINGLE | SWT.BORDER);
		data = new GridData(SWT.FILL, SWT.CENTER, true, false);
		fGDBServerPortNumberMappedToText.setLayoutData(data);
		fGDBServerPortNumberMappedToText.addModifyListener(new ModifyListener() {

			@SuppressWarnings("synthetic-access")
            @Override
            public void modifyText(ModifyEvent evt) {
				updateLaunchConfigurationDialog();
			}
		});
		fGDBServerPortNumberMappedToText.addVerifyListener(new PortNumberVerifyListener(PortNumberVerifyListener.ATTR_DECIMAL | PortNumberVerifyListener.ATTR_HEX));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.dsf.gdb.internal.ui.launching.GdbDebuggerPage#createTabs(org.eclipse.swt.widgets.TabFolder)
	 */
	@Override
	public void createTabs(TabFolder tabFolder) {
		super.createTabs(tabFolder);
		createGdbserverSettingsTab(tabFolder);
	}

	@Override
	protected boolean isInitializing() {
		return fIsInitializing;
	}

	private void setInitializing(boolean isInitializing) {
		fIsInitializing = isInitializing;
	}

}
