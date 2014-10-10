/*******************************************************************************
 * Copyright (c) 2011, 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.ui.wizards;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.runtime.persistence.interfaces.IURIPersistenceService;
import org.eclipse.tcf.te.runtime.properties.PropertiesContainer;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.tcf.core.interfaces.IPeerProperties;
import org.eclipse.tcf.te.tcf.core.peers.Peer;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorNode;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerNode;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.IPeerModelLookupService;
import org.eclipse.tcf.te.tcf.locator.interfaces.services.IPeerModelRefreshService;
import org.eclipse.tcf.te.tcf.locator.model.ModelManager;
import org.eclipse.tcf.te.tcf.ui.nls.Messages;
import org.eclipse.tcf.te.tcf.ui.wizards.pages.NewTargetWizardPage;
import org.eclipse.tcf.te.ui.interfaces.data.IDataExchangeNode;
import org.eclipse.tcf.te.ui.swt.DisplayUtil;
import org.eclipse.tcf.te.ui.views.ViewsUtil;
import org.eclipse.tcf.te.ui.views.interfaces.IUIConstants;
import org.eclipse.tcf.te.ui.wizards.pages.AbstractWizardPage;
import org.eclipse.ui.IWorkbench;

/**
 * New peer wizard implementation.
 */
public class NewWizard extends AbstractNewConfigWizard {
	// Session wide new peer counter
	private final static AtomicInteger counter = new AtomicInteger();

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IWorkbenchWizard#init(org.eclipse.ui.IWorkbench, org.eclipse.jface.viewers.IStructuredSelection)
	 */
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		super.init(workbench, selection);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	@Override
	public void addPages() {
		addPage(new NewTargetWizardPage());
	}

	protected String getPeerType() {
		return null;
	}

	protected boolean isAllowedForeignAttribute(String key) {
		return key.equals(IPeer.ATTR_NAME) ||
						key.equals(IPeer.ATTR_TRANSPORT_NAME) ||
						key.equals(IPeer.ATTR_IP_HOST) ||
						key.equals(IPeer.ATTR_IP_PORT) ||
						key.equals(IPeerProperties.PROP_PROXIES) ||
						key.equals(IPeerProperties.PROP_AUTO_CONNECT);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.wizards.AbstractWizard#getInitialData()
	 */
	@Override
	protected IPropertiesContainer getInitialData() {
		IStructuredSelection selection = getSelection();
		if (selection != null) {
			final IPeer peer;
			boolean isPeerNode = false;
			if (selection.getFirstElement() instanceof IPeer) {
				peer = (IPeer)selection.getFirstElement();
			}
			else if (selection.getFirstElement() instanceof ILocatorNode) {
				peer = ((ILocatorNode)selection.getFirstElement()).getPeer();
			}
			else if (selection.getFirstElement() instanceof IPeerNode) {
				isPeerNode = true;
				peer = ((IPeerNode)selection.getFirstElement()).getPeer();
			}
			else {
				peer = null;
			}

			if (peer != null) {
				String selPeerType = peer.getAttributes().get(IPeerProperties.PROP_TYPE);
				final boolean sameType = getPeerType() == null ? selPeerType == null : getPeerType().equals(selPeerType);
				final boolean finIsPeerNode = isPeerNode;
				final IPropertiesContainer data = new PropertiesContainer();
				Protocol.invokeAndWait(new Runnable() {
					@Override
					public void run() {
						for (Entry<String, String> attribute : peer.getAttributes().entrySet()) {
							if (sameType || (!finIsPeerNode && isAllowedForeignAttribute(attribute.getKey()))) {
								data.setProperty(attribute.getKey(), attribute.getValue());
							}
                        }
					}
				});

				return data;
			}
		}
		return super.getInitialData();
	}

	/**
	 * Extract the peer attributes from the wizard pages.
	 *
	 * @param peerAttributes The peer attributes. Must not be <code>null</code>.
	 */
	protected void extractData(IPropertiesContainer peerAttributes) {
		Assert.isNotNull(peerAttributes);

		// Walk through the page list and extract the attributes from it
		for (IWizardPage page : getPages()) {
			if (page instanceof AbstractWizardPage) ((AbstractWizardPage)page).saveWidgetValues();
			if (page instanceof IDataExchangeNode) {
				((IDataExchangeNode)page).extractData(peerAttributes);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#performFinish()
	 */
	@Override
	public boolean performFinish() {
		// Create the peer attributes
		final IPropertiesContainer peerAttributes = new PropertiesContainer();

		// Extract the data from the wizard pages
		extractData(peerAttributes);

		// Fill in the minimum set of peer attributes to create a new peer
		if (!peerAttributes.containsKey(IPeer.ATTR_ID)) {
			peerAttributes.setProperty(IPeer.ATTR_ID, UUID.randomUUID().toString());
		}
		if (!peerAttributes.containsKey(IPeer.ATTR_NAME)) {
			peerAttributes.setProperty(IPeer.ATTR_NAME, NLS.bind(Messages.NewTargetWizard_newPeer_name, Integer.valueOf(counter.incrementAndGet())));
		}

		// Convert the properties container into a Map<String, String>
		final Map<String, String> attrs = new HashMap<String, String>();
		for (Entry<String, Object> entry : peerAttributes.getProperties().entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			attrs.put(entry.getKey(), entry.getValue() instanceof String ? (String)entry.getValue() : entry.getValue().toString());
		}

		try {
			// Save the new peer
			IURIPersistenceService persistenceService = ServiceManager.getInstance().getService(IURIPersistenceService.class);
			if (persistenceService == null) {
				throw new IOException("Persistence service instance unavailable."); //$NON-NLS-1$
			}
			persistenceService.write(new Peer(attrs), null);

			// Trigger a refresh of the model to read in the newly created static peer
			Protocol.invokeLater(new Runnable() {
				@Override
				public void run() {
					IPeerModelRefreshService service = ModelManager.getPeerModel().getService(IPeerModelRefreshService.class);
					// Refresh the model now (must be executed within the TCF dispatch thread)
					if (service != null) service.refresh(new Callback() {
						@Override
						protected void internalDone(Object caller, IStatus status) {
							// Get the peer model node from the model and select it in the tree
							final IPeerNode peerNode = ModelManager.getPeerModel().getService(IPeerModelLookupService.class).lkupPeerModelById(attrs.get(IPeer.ATTR_ID));
							if (peerNode != null) {
								// Refresh the viewer
								ViewsUtil.refresh(IUIConstants.ID_EXPLORER);
								// Create the selection
								ISelection selection = new StructuredSelection(peerNode);
								// Set the selection
								ViewsUtil.setSelection(IUIConstants.ID_EXPLORER, selection);
								// And open the properties on the selection
								if (isOpenEditorOnPerformFinish()) ViewsUtil.openEditor(selection);
								// Allow subclasses to add logic to the performFinish().
								DisplayUtil.safeAsyncExec(new Runnable() {
									@Override
									public void run() {
										postPerformFinish(peerNode);
									}
								});
							}
						}
					});
				}
			});
		} catch (IOException e) {
			if (getContainer().getCurrentPage() instanceof WizardPage) {
				String message = NLS.bind(Messages.NewTargetWizard_error_savePeer, e.getLocalizedMessage());
				((WizardPage)getContainer().getCurrentPage()).setMessage(message, IMessageProvider.ERROR);
				getContainer().updateMessage();
			}
			return false;
		}

		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.ui.wizards.AbstractNewConfigWizard#getWizardTitle()
	 */
    @Override
    protected String getWizardTitle() {
	    return Messages.NewTargetWizard_windowTitle;
    }
}
