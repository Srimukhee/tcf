/*******************************************************************************
 * Copyright (c) 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.ui.wizards;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.IService;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepContext;
import org.eclipse.tcf.te.runtime.stepper.interfaces.IStepperService;
import org.eclipse.tcf.te.runtime.stepper.job.StepperJob;
import org.eclipse.tcf.te.runtime.utils.StatusHelper;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModelProperties;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.IStepperServiceOperations;
import org.eclipse.tcf.te.tcf.ui.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.ui.wizards.pages.AbstractConfigWizardPage;
import org.eclipse.ui.IWorkbench;

/**
 * Abstract new configuration wizard implementation.
 */
public abstract class AbstractConfigWizard extends NewTargetWizard {

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchWizard#init(org.eclipse.ui.IWorkbench, org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		super.init(workbench, selection);
		// Set the window title
		setWindowTitle(getWizardTitle());
		// Signal the need for a progress monitor
		setNeedsProgressMonitor(true);
	}

	/**
	 * Returns the new configuration wizard title.
	 *
	 * @return The wizard title. Never <code>null</code>.
	 */
	protected abstract String getWizardTitle();

	/**
	 * Returns the new configuration wizard page.
	 *
	 * @return The new configuration wizard page or <code>null</code>:
	 */
	protected abstract AbstractConfigWizardPage getConfigWizardPage();

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.ui.wizards.NewTargetWizard#postPerformFinish(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel)
	 */
	@Override
	protected void postPerformFinish(final IPeerModel peerModel) {
		Assert.isNotNull(peerModel);

		// Determine if or if not to auto-connect the created connection.
		boolean autoConnect = true;
		// If set as system property, take the system property into account first
		if (System.getProperty("NoWizardAutoConnect") != null) { //$NON-NLS-1$
			autoConnect &= !Boolean.getBoolean("NoWizardAutoConnect"); //$NON-NLS-1$
		}
		// Apply the preference setting
		autoConnect &= !UIPlugin.getDefault().getPreferenceStore().getBoolean("NoWizardAutoConnect"); //$NON-NLS-1$

		// If auto-connect is switched off, we are done here.
		if (!autoConnect) return;

		// Attach the debugger
		final AtomicBoolean attachDebugger = new AtomicBoolean();
		Protocol.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				attachDebugger.set(Boolean.parseBoolean(peerModel.getPeer().getAttributes().get(IPeerModelProperties.PROP_AUTO_START_DEBUGGER)));
			}
		});

		if (attachDebugger.get()) {
			IService[] services = ServiceManager.getInstance().getServices(peerModel, IStepperService.class, false);
			IStepperService stepperService = null;
			for (IService service : services) {
				if (service instanceof IStepperService && ((IStepperService)service).isHandledOperation(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER)) {
					stepperService = (IStepperService)service;
					break;
				}
	        }
			if (stepperService != null) {
				String stepGroupId = stepperService.getStepGroupId(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER);
				IStepContext stepContext = stepperService.getStepContext(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER);
				String name = stepperService.getStepGroupName(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER);
				IPropertiesContainer data = stepperService.getStepData(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER);
				boolean enabled = stepperService.isEnabled(peerModel, IStepperServiceOperations.ATTACH_DEBUGGER);

				if (enabled && stepGroupId != null && stepContext != null) {
					try {
						StepperJob job = new StepperJob(name != null ? name : "", //$NON-NLS-1$
														stepContext,
														data,
														stepGroupId,
														IStepperServiceOperations.ATTACH_DEBUGGER,
														true);

						job.schedule();
					} catch (IllegalStateException e) {
						if (Platform.inDebugMode()) {
							UIPlugin.getDefault().getLog().log(StatusHelper.getStatus(e));
						}
					}
				}
			}
		}
	}
}
