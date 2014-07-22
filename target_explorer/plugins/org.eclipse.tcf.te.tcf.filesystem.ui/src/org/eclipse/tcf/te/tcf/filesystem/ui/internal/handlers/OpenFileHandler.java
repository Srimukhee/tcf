/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.ui.internal.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.operations.IOpExecutor;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.operations.OpCacheUpdate;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.utils.CacheManager;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.utils.ContentTypeHelper;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.utils.PersistenceManager;
import org.eclipse.tcf.te.tcf.filesystem.core.model.CacheState;
import org.eclipse.tcf.te.tcf.filesystem.core.model.FSTreeNode;
import org.eclipse.tcf.te.tcf.filesystem.ui.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.filesystem.ui.internal.operations.UiExecutor;
import org.eclipse.tcf.te.tcf.filesystem.ui.nls.Messages;
import org.eclipse.tcf.te.ui.swt.DisplayUtil;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

/**
 * The action handler to open a file on the remote file system.
 */
public class OpenFileHandler extends AbstractHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.AbstractHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selection = (IStructuredSelection) HandlerUtil.getCurrentSelectionChecked(event);
		final FSTreeNode node = (FSTreeNode) selection.getFirstElement();
		final IWorkbenchPage page = HandlerUtil.getActiveSite(event).getPage();
		if (ContentTypeHelper.isBinaryFile(node)) {
			// If the file is a binary file.
			Shell parent = HandlerUtil.getActiveShell(event);
			MessageDialog.openWarning(parent, Messages.OpenFileHandler_Warning,
					Messages.OpenFileHandler_OpeningBinaryNotSupported);
		} else {
			if (UIPlugin.isAutoSaving()) {
				// Refresh the node to determine the cache state correctly
				node.refresh(new Callback() {
					@Override
					protected void internalDone(Object caller, IStatus status) {
						File file = CacheManager.getCacheFile(node);
						if (node.getCacheState() == CacheState.outdated) {
							file.delete();
						}

						DisplayUtil.safeAsyncExec(new Runnable() {
							@Override
							public void run() {
								// Open the file node.
								openFile(node, page);
							}
						});
					}
				});
			} else {
				// Open the file node.
				openFile(node, page);
			}

		}
		return null;
	}

	/**
	 * Open the file node in an editor of the specified workbench page. If the
	 * local cache file of the node is stale, then download it. Then open its
	 * local cache file.
	 *
	 * @param node
	 *            The file node to be opened.
	 * @param page
	 *            The workbench page in which the editor is opened.
	 */
	/* default */ void openFile(FSTreeNode node, IWorkbenchPage page) {
		File file = CacheManager.getCacheFile(node);
		if (!file.exists()) {
			// If the file node's local cache does not exist yet, download it.
			IOpExecutor executor = new UiExecutor();
			IStatus status = executor.execute(new OpCacheUpdate(node));
			if (!status.isOK()) {
				return;
			}
		}
		openEditor(page, node);
	}

	/**
	 * Open the editor to display the file node in the UI thread.
	 *
	 * @param page
	 *            The workbench page in which the editor is opened.
	 * @param node
	 *            The file node whose local cache file is opened.
	 */
	private void openEditor(final IWorkbenchPage page, final FSTreeNode node) {
		Display display = page.getWorkbenchWindow().getWorkbench().getDisplay();
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				IPath path = CacheManager.getCachePath(node);
				IFileStore fileStore = EFS.getLocalFileSystem().getStore(path);
				String editorID = PersistenceManager.getInstance().getPersistentProperties(node).get(IDE.EDITOR_KEY);
				try {
					if(editorID!=null){
						FileStoreEditorInput input = new FileStoreEditorInput(fileStore);
						page.openEditor(input, editorID, true, IWorkbenchPage.MATCH_INPUT|IWorkbenchPage.MATCH_ID);
					}else{
						IDE.openEditorOnFileStore(page, fileStore);
					}
				} catch (PartInitException e) {
				}
			}
		});
	}
}
