/*******************************************************************************
 * Copyright (c) 2015 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.ui.internal.handlers;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tcf.te.tcf.filesystem.core.interfaces.runtime.IFSTreeNode;
import org.eclipse.tcf.te.tcf.filesystem.core.interfaces.runtime.IRuntimeModel;
import org.eclipse.tcf.te.tcf.filesystem.ui.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.filesystem.ui.internal.operations.UiExecutor;
import org.eclipse.tcf.te.tcf.filesystem.ui.nls.Messages;
import org.eclipse.ui.handlers.HandlerUtil;
/**
 * The handler that downloads the selected files or folders to a local destination folder.
 */
public class DownloadFilesHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Shell shell = HandlerUtil.getActiveShellChecked(event);
		DirectoryDialog dlg = new DirectoryDialog(shell);
		dlg.setFilterPath(getFilterPath());
		dlg.setText(Messages.DownloadFilesHandler_folderDlg_text);
		dlg.setMessage(Messages.DownloadFilesHandler_folderDlg_message);

		String destination = dlg.open();
		if (destination == null)
			return null;

		File destinationFile = new File(destination);
		saveFilterPath(destinationFile);

		IStructuredSelection selection = (IStructuredSelection) HandlerUtil.getCurrentSelection(event);
		List<IFSTreeNode> nodes = selection.toList();
		IRuntimeModel peer = nodes.get(0).getRuntimeModel();
		UiExecutor.execute(peer.operationDownload(nodes, destinationFile, new MoveCopyCallback()));
		return null;
	}

	private String getFilterPath() {
		String lastFolder = UIPlugin.getDefault().getDialogSettings().get(getClass().getName() + ".lastFolder"); //$NON-NLS-1$
		if (lastFolder != null)
			return lastFolder;
		Location loc = Platform.getInstanceLocation();
		if (loc != null) {
			URL url = loc.getURL();
			try {
				File file = URIUtil.toFile(url.toURI());
				if (file != null)
					return file.getPath();
            } catch (URISyntaxException e) {
            }
		}
	    return null;
    }

	private void saveFilterPath(File destinationFile) {
		if (destinationFile != null) {
			UIPlugin.getDefault().getDialogSettings().put(getClass().getName() + ".lastFolder", destinationFile.getPath()); //$NON-NLS-1$
		}
    }

}
