/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * William Chen (Wind River)- [345387]Open the remote files with a proper editor
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.internal.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tcf.te.tcf.filesystem.internal.utils.CacheManager;
import org.eclipse.tcf.te.tcf.filesystem.internal.utils.ContentTypeHelper;
import org.eclipse.tcf.te.tcf.filesystem.internal.utils.PersistenceManager;
import org.eclipse.tcf.te.tcf.filesystem.model.FSTreeNode;
import org.eclipse.tcf.te.tcf.filesystem.nls.Messages;
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
		IWorkbenchPage page = HandlerUtil.getActiveSite(event).getPage();
		if (ContentTypeHelper.getInstance().isBinaryFile(node)) {
			// If the file is a binary file.
			Shell parent = HandlerUtil.getActiveShell(event);
			MessageDialog.openWarning(parent, Messages.OpenFileHandler_Warning,
					Messages.OpenFileHandler_OpeningBinaryNotSupported);
		} else {
			// Open the file node.
			openFile(node, page);
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
	private void openFile(FSTreeNode node, IWorkbenchPage page) {
		File file = CacheManager.getInstance().getCacheFile(node);
		if (!file.exists()) {
			// If the file node's local cache does not exist yet, download it.
			boolean successful = CacheManager.getInstance().download(node);
			if (!successful) {
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
				IPath path = CacheManager.getInstance().getCachePath(node);
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
