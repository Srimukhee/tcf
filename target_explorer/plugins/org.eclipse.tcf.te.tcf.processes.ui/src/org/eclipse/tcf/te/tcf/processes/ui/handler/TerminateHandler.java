/*******************************************************************************
 * Copyright (c) 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.processes.ui.handler;

import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.services.ServiceManager;
import org.eclipse.tcf.te.runtime.services.interfaces.IUIService;
import org.eclipse.tcf.te.runtime.statushandler.StatusHandlerUtil;
import org.eclipse.tcf.te.tcf.core.model.interfaces.IModel;
import org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelUpdateService;
import org.eclipse.tcf.te.tcf.locator.interfaces.nodes.IPeerModel;
import org.eclipse.tcf.te.tcf.processes.core.model.interfaces.IProcessContextNode;
import org.eclipse.tcf.te.tcf.processes.core.model.steps.TerminateStep;
import org.eclipse.tcf.te.tcf.processes.ui.help.IContextHelpIds;
import org.eclipse.tcf.te.tcf.processes.ui.interfaces.IProcessMonitorMessageProviderDelegate;
import org.eclipse.tcf.te.tcf.processes.ui.nls.Messages;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

/**
 * The handler to terminate the selected process.
 */
public class TerminateHandler extends AbstractHandler implements IElementUpdater {

	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
			Iterator<?> iterator = ((IStructuredSelection)selection).iterator();
			while (iterator.hasNext()) {
				Object candidate = iterator.next();
				if (candidate instanceof IProcessContextNode) {
					final IProcessContextNode process = (IProcessContextNode)candidate;
					Runnable runnable = new Runnable() {
						@Override
						public void run() {
							TerminateStep step = new TerminateStep();
							step.executeTerminate(process, new Callback() {
								@Override
								protected void internalDone(Object caller, final IStatus status) {
									if (status.getSeverity() != IStatus.ERROR) {
										IModel model = process.getParent(IModel.class);
										Assert.isNotNull(model);
										model.getService(IModelUpdateService.class).remove(process);
									} else {
										// Build up the message template
										String template = NLS.bind(Messages.TerminateHandler_terminateFailed, process.getName(), Messages.PossibleCause);
										// Handle the status
										StatusHandlerUtil.handleStatus(status, process, template, null, IContextHelpIds.MESSAGE_TERMINATE_FAILED, TerminateHandler.this, null);
									}
								}
							});
						}
					};
					Protocol.invokeLater(runnable);
				}
			}
		}

		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.commands.IElementUpdater#updateElement(org.eclipse.ui.menus.UIElement, java.util.Map)
	 */
	@Override
	public void updateElement(UIElement element, Map parameters) {
		IWorkbenchPartSite site = (IWorkbenchPartSite)parameters.get("org.eclipse.ui.part.IWorkbenchPartSite"); //$NON-NLS-1$
		if (site != null) {
			IWorkbenchPart part = site.getPart();
			if (part instanceof IEditorPart) {
				IEditorInput editorInput = ((IEditorPart)part).getEditorInput();
				IPeerModel node = editorInput != null ? (IPeerModel) editorInput.getAdapter(IPeerModel.class) : null;

				IUIService service = ServiceManager.getInstance().getService(node, IUIService.class);
				IProcessMonitorMessageProviderDelegate delegate = service != null ? service.getDelegate(node, IProcessMonitorMessageProviderDelegate.class) : null;

				if (delegate != null) {
					String text = delegate.getMessage("TerminateHandler_updateElement_text"); //$NON-NLS-1$
					if (text != null) element.setText(text);

					String tooltip = delegate.getMessage("TerminateHandler_updateElement_tooltip"); //$NON-NLS-1$
					if (tooltip != null) element.setTooltip(tooltip);
				}
			}
		}
	}
}
