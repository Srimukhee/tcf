/*******************************************************************************
 * Copyright (c) 2011, 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.views.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.IUIService;
import org.eclipse.tcf.te.ui.interfaces.handler.IPropertiesHandlerDelegate;
import org.eclipse.tcf.te.ui.views.activator.UIPlugin;
import org.eclipse.tcf.te.ui.views.editor.EditorInput;
import org.eclipse.tcf.te.ui.views.interfaces.IUIConstants;
import org.eclipse.tcf.te.ui.views.nls.Messages;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;


/**
 * Properties command handler implementation.
 */
public class PropertiesCommandHandler extends AbstractHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// Get the active selection
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		// Get the currently active workbench window
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		if (window == null) window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		if (selection != null && window != null) openEditorOnSelection(window, selection);

		return null;
	}

	/**
	 * Opens the properties editor in the given workbench window on the given selection.
	 *
	 * @param window The workbench window. Must not be <code>null</code>.
	 * @param selection The selection. Must not be <code>null</code>.
	 */
	public static void openEditorOnSelection(IWorkbenchWindow window, ISelection selection) {
		Assert.isNotNull(window);
		Assert.isNotNull(selection);

		if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
			Object element = ((IStructuredSelection)selection).getFirstElement();
			if (element != null) {
				// Get the active page
				IWorkbenchPage page = window.getActivePage();
				// Create the editor input object
				IUIService service = ServiceManager.getInstance().getService(element, IUIService.class);
				IPropertiesHandlerDelegate delegate = service != null ? service.getDelegate(element, IPropertiesHandlerDelegate.class) : null;
				IEditorInput input = delegate != null ? delegate.getEditorInput(element) : new EditorInput(element);
				try {
					// Opens the Target Explorer properties editor
					IEditorPart editor = page.openEditor(input, IUIConstants.ID_EDITOR);
					// Lookup the ui service for post action
					if (delegate != null) {
						delegate.postOpenProperties(editor, element);
					}
				} catch (PartInitException e) {
					IStatus status = new Status(IStatus.ERROR, UIPlugin.getUniqueIdentifier(),
												Messages.PropertiesCommandHandler_error_initPartFailed, e);
					UIPlugin.getDefault().getLog().log(status);
				}
			}
		}
	}

}
