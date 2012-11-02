/*******************************************************************************
 * Copyright (c) 2011, 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.ui.navigator;

import org.eclipse.core.runtime.Assert;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.IAdapterService;
import org.eclipse.tcf.te.tcf.locator.interfaces.IModelListener;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.locator.listener.ModelAdapter;
import org.eclipse.tcf.te.ui.views.editor.EditorInput;
import org.eclipse.tcf.te.ui.views.interfaces.IUIConstants;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.CommonViewer;


/**
 * TCF locator model listener implementation.
 */
public class ModelListener extends ModelAdapter {
	private final ILocatorModel parentModel;
	/* default */ final CommonViewer viewer;

	/**
	 * Constructor.
	 *
	 * @param parent The parent locator model. Must not be <code>null</code>.
	 * @param viewer The common viewer instance. Must not be <code>null</code>.
	 */
	public ModelListener(ILocatorModel parent, CommonViewer viewer) {
		Assert.isNotNull(parent);
		Assert.isNotNull(viewer);

		this.parentModel = parent;
		this.viewer = viewer;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.locator.listener.ModelAdapter#locatorModelChanged(org.eclipse.tcf.te.tcf.locator.interfaces.nodes.ILocatorModel, org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel, boolean)
	 */
	@Override
	public void locatorModelChanged(final ILocatorModel model, final IPeerModel peerModel, final boolean added) {
		if (parentModel.equals(model)) {
			// Locator model changed -> refresh the tree
			Tree tree = viewer.getTree();
			if (tree != null && !tree.isDisposed()) {
				Display display = tree.getDisplay();
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						if (viewer.getTree() != null && !viewer.getTree().isDisposed()) {
							viewer.refresh();
						}
					}
				});
			}

			if (peerModel != null) {
				// Check if the peer model node can be adapted to IModelListener.
				IAdapterService service = ServiceManager.getInstance().getService(peerModel, IAdapterService.class);
				IModelListener listener = service != null ? service.getAdapter(peerModel, IModelListener.class) : null;
				// If yes -> Invoke the adapted model listener instance
				if (listener != null) {
					listener.locatorModelChanged(model, peerModel, added);
				}
				// If no -> Default behavior is to close the editor (if any)
				else if (!added) {
					Display display = PlatformUI.getWorkbench().getDisplay();
					if (display != null) {
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								// Get the currently active workbench window
								IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
								if (window != null) {
									// Get the active page
									IWorkbenchPage page = window.getActivePage();
									// Create the editor input object
									IEditorInput input = new EditorInput(peerModel);
									// Lookup the editors matching the editor input
									IEditorReference[] editors = page.findEditors(input, IUIConstants.ID_EDITOR, IWorkbenchPage.MATCH_INPUT);
									if (editors != null && editors.length > 0) {
										// Close the editors
										page.closeEditors(editors, true);
									}
								}
							}
						});
					}
				}
			}
		}
	}
}
