/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.internal.operations;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IPeer;
import org.eclipse.tcf.protocol.IToken;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.services.IFileSystem;
import org.eclipse.tcf.services.IFileSystem.DirEntry;
import org.eclipse.tcf.services.IFileSystem.DoneMkDir;
import org.eclipse.tcf.services.IFileSystem.DoneOpen;
import org.eclipse.tcf.services.IFileSystem.DoneReadDir;
import org.eclipse.tcf.services.IFileSystem.DoneRemove;
import org.eclipse.tcf.services.IFileSystem.FileSystemException;
import org.eclipse.tcf.services.IFileSystem.IFileHandle;
import org.eclipse.tcf.te.tcf.core.Tcf;
import org.eclipse.tcf.te.tcf.core.concurrent.BlockingCallProxy;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager;
import org.eclipse.tcf.te.tcf.core.interfaces.IChannelManager.DoneOpenChannel;
import org.eclipse.tcf.te.tcf.filesystem.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.filesystem.internal.ImageConsts;
import org.eclipse.tcf.te.tcf.filesystem.internal.exceptions.TCFChannelException;
import org.eclipse.tcf.te.tcf.filesystem.internal.exceptions.TCFException;
import org.eclipse.tcf.te.tcf.filesystem.internal.exceptions.TCFFileSystemException;
import org.eclipse.tcf.te.tcf.filesystem.internal.utils.CacheManager;
import org.eclipse.tcf.te.tcf.filesystem.internal.utils.PersistenceManager;
import org.eclipse.tcf.te.tcf.filesystem.model.FSTreeNode;
import org.eclipse.tcf.te.tcf.filesystem.nls.Messages;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * FSOperation is the base class of file system operation classes including FSCopy, FSDelete, FSMove
 * and FSRename.
 */
public class FSOperation implements IRunnableWithProgress{
	// The flag indicating if the following action should be executed without asking.
	protected boolean yes2All;

	/**
	 * Create an instance.
	 */
	public FSOperation() {
		this.yes2All = false;
	}

	/**
	 * Get the top most nodes of the specified node list, removing those nodes whose ancestors are
	 * one of the other nodes in the list. This method is used to remove those children or grand
	 * children of the nodes that are cut, copied, moved or deleted.
	 *
	 * @param nodes The original node list.
	 * @return The top most nodes.
	 */
	protected List<FSTreeNode> getTopNodes(List<FSTreeNode> nodes) {
		List<FSTreeNode> result = new ArrayList<FSTreeNode>();
		for (FSTreeNode node : nodes) {
			if (!hasAncestor(node, nodes)) {
				result.add(node);
			}
		}
		return result;
	}

	/**
	 * If the target node has ancestor in the specified node list.
	 *
	 * @param target The node to be tested.
	 * @param nodes The node list to search in.
	 * @return true if the target node has an ancestor in the node list.
	 */
	private boolean hasAncestor(FSTreeNode target, List<FSTreeNode> nodes) {
		for (FSTreeNode node : nodes) {
			if (node.isAncestorOf(target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Clean up the folder node after moving, deleting or copying.
	 *
	 * @param node the folder node that is to be cleaned.
	 */
	protected void cleanUpFolder(FSTreeNode node) {
		File file = CacheManager.getInstance().getCacheFile(node);
		if (file.exists()) {
			file.delete();
		}
		FSTreeNode parent = node.parent;
		if (parent != null) {
			parent.removeChild(node);
		}
	}

	/**
	 * Close the editor that opens the specified file.
	 * <p>
	 * <b>Note:</b> The method must be called within the UI thread.
	 *
	 * @param file The file that is opened.
	 */
	protected void closeEditor(final File file) {
		Assert.isNotNull(Display.getCurrent());
		final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IEditorReference[] refs = page.getEditorReferences();
		for (IEditorReference ref : refs) {
			final IEditorReference editorRef = ref;
			SafeRunner.run(new SafeRunnable() {
				@Override
                public void handleException(Throwable e) {
					// Ignore exception
                }
				@Override
				public void run() throws Exception {
					IEditorInput input = editorRef.getEditorInput();
					if (input instanceof IURIEditorInput) {
						IURIEditorInput editorInput = (IURIEditorInput) input;
						URI uri = editorInput.getURI();
						if (file.toURI().equals(uri)) {
							IEditorPart editor = editorRef.getEditor(true);
							page.closeEditor(editor, false);
						}
					}
				}
			});
		}
	}

	/**
	 * Clean up the file node after moving, deleting or copying.
	 *
	 * @param node the file node that is to be cleaned.
	 */
	protected void cleanUpFile(FSTreeNode node) {
		final File file = CacheManager.getInstance().getCacheFile(node);
		if (file.exists()) {
			Display display = PlatformUI.getWorkbench().getDisplay();
			display.asyncExec(new Runnable(){
				@Override
	            public void run() {
					closeEditor(file);
				}
			});
			file.delete();
		}
		PersistenceManager.getInstance().removeBaseTimestamp(node.getLocationURI());
		FSTreeNode parent = node.parent;
		if (parent != null) {
			parent.removeChild(node);
		}
	}

	/**
	 * Open a channel connected to the target represented by the peer.
	 *
	 * @return The channel or null if the operation fails.
	 */
	public static IChannel openChannel(final IPeer peer) throws TCFChannelException {
		IChannelManager channelManager = Tcf.getChannelManager();
		channelManager = BlockingCallProxy.newInstance(IChannelManager.class, channelManager);
		final TCFChannelException[] errors = new TCFChannelException[1];
		final IChannel[] channels = new IChannel[1];
		channelManager.openChannel(peer, false, new DoneOpenChannel() {
			@Override
			public void doneOpenChannel(Throwable error, IChannel channel) {
				if (error != null) {
					if (error instanceof ConnectException) {
						String message = NLS.bind(Messages.FSOperation_NotResponding, peer.getID());
						errors[0] = new TCFChannelException(message);
					}
					else {
						String message = NLS.bind(Messages.OpeningChannelFailureMessage, peer.getID(), error.getLocalizedMessage());
						errors[0] = new TCFChannelException(message, error);
					}
				}
				else {
					channels[0] = channel;
				}
			}
		});
		if (errors[0] != null) {
			throw errors[0];
		}
		return channels[0];
	}

	/**
	 * Get a blocking file system service from the channel. The 
	 * returned file system service is a service that delegates the 
	 * method call to the file system proxy. If the method returns
	 * asynchronously with a callback, it will block the call until
	 * the callback returns.
	 * <p>
	 * <em>Note: All the method of the returned file system 
	 * service must be called outside of the dispatching thread.</em>
	 * 
	 * @param channel The channel to get the file system service.
	 * @return The blocking file system service.
	 */
	public static IFileSystem getBlockingFileSystem(final IChannel channel) {
		if(Protocol.isDispatchThread()) {
			IFileSystem service = channel.getRemoteService(IFileSystem.class);
			return BlockingCallProxy.newInstance(IFileSystem.class, service);
		}
		final IFileSystem[] service = new IFileSystem[1];
		Protocol.invokeAndWait(new Runnable(){
			@Override
            public void run() {
				service[0] = getBlockingFileSystem(channel);
            }});
		return service[0];
	}

	/**
	 * Count the total nodes in the node list including their children and grand children
	 * recursively.
	 *
	 * @param service The file system service used to open those folders that are not expanded yet.
	 * @param nodes The node list to be counted.
	 * @return The count of the total nodes.
	 * @throws TCFFileSystemException Thrown when expanding the unexpanded folders.
	 */
	protected int count(IFileSystem service, List<FSTreeNode> nodes) throws TCFFileSystemException {
		int count = 0;
		for (FSTreeNode node : nodes) {
			if (node.isFile()) {
				count++;
			}
			else if (node.isDirectory()) {
				List<FSTreeNode> children = getChildren(node, service);
				count += count(service, children) + 1;
			}
		}
		return count;
	}

	/**
	 * Get the children of the specified folder node. If the folder node is not expanded, then
	 * expanded using the specified file system service.
	 *
	 * @param node The folder node.
	 * @param service The file system service.
	 * @return The children of the folder node.
	 * @throws TCFFileSystemException Thrown during querying the children nodes.
	 */
	protected List<FSTreeNode> getChildren(final FSTreeNode node, final IFileSystem service) throws TCFFileSystemException {
		if (node.childrenQueried) {
			return node.unsafeGetChildren();
		}
		loadChildren(node, service);
		return getChildren(node, service);
	}

	/**
	 * Get the children the specified folder node. If the folder has not yet been loaded, then load it.
	 * 
	 * @param node The folder node.
	 * @return The children of the folder node.
	 * @throws TCFException Thrown during querying the children nodes.
	 */
	public List<FSTreeNode> getChildren(final FSTreeNode node) throws TCFException {
		if(node.childrenQueried) {
			return node.unsafeGetChildren();
		}
		IChannel channel = null;
		try {
			channel = openChannel(node.peerNode.getPeer());
			IFileSystem service = getBlockingFileSystem(channel);
			if (service != null) {
				return getChildren(node, service);
			}
			String message = NLS.bind(Messages.FSOperation_NoFileSystemError, node.peerNode.getPeerId());
			throw new TCFFileSystemException(message);
		}
		finally {
			if (channel != null) Tcf.getChannelManager().closeChannel(channel);
		}
	}

	/**
	 * Load the children of the specified folder node using the file system service.
	 *
	 * @param node The folder node.
	 * @param service The file system service.
	 * @throws TCFFileSystemException Thrown during querying the children nodes.
	 */
	protected void loadChildren(final FSTreeNode node, final IFileSystem service) throws TCFFileSystemException {
		List<FSTreeNode> children = queryChildren(node, service);
		node.addChidren(children);
		node.childrenQueried = true;
	}

	/**
	 * Query the children of the specified node using the file system service.
	 *
	 * @param node The folder node.
	 * @param service The file system service.
	 * @return The children of the folder node.
	 * @throws TCFFileSystemException Thrown during querying the children nodes.
	 */
	protected List<FSTreeNode> queryChildren(final FSTreeNode node, final IFileSystem service) throws TCFFileSystemException {
		final TCFFileSystemException[] errors = new TCFFileSystemException[1];
		final IFileHandle[] handles = new IFileHandle[1];
		try {
			String dir = node.getLocation();
			service.opendir(dir, new DoneOpen() {
				@Override
				public void doneOpen(IToken token, FileSystemException error, IFileHandle handle) {
					if (error != null) {
						String message = NLS.bind(Messages.FSOperation_CannotOpenDir, node.name, error);
						errors[0] = new TCFFileSystemException(message, error);
					}
					else {
						handles[0] = handle;
					}
				}
			});
			if (errors[0] != null) {
				throw errors[0];
			}
			errors[0] = null;
			final List<FSTreeNode> children = new ArrayList<FSTreeNode>();
			final boolean[] eofs = new boolean[1];
			while (!eofs[0]) {
				service.readdir(handles[0], new DoneReadDir() {
					@Override
					public void doneReadDir(IToken token, FileSystemException error, DirEntry[] entries, boolean eof) {
						if (eof) {
							eofs[0] = true;
						}
						if (error == null) {
							if (entries != null && entries.length > 0) {
								for (DirEntry entry : entries) {
									FSTreeNode childNode = new FSTreeNode(node, entry, false);
									children.add(childNode);
								}
							}
						}
						else {
							String message = NLS.bind(Messages.FSOperation_CannotReadDir, node.name, error);
							errors[0] = new TCFFileSystemException(message, error);
						}
					}
				});
				if (errors[0] != null) throw errors[0];
			}
			return children;
		}
		finally {
			if (handles[0] != null) {
				service.close(handles[0], new IFileSystem.DoneClose() {
					@Override
					public void doneClose(IToken token, FileSystemException error) {
					}
				});
			}
		}
	}

	/**
	 * Remove the child from the children list of the specified folder. If the folder has not yet
	 * expanded, then expand it.
	 *
	 * @param service The file system service.
	 * @param folder The folder node from which the node is going to be removed.
	 * @param child The child node to be removed.
	 * @throws TCFFileSystemException Thrown during children querying.
	 */
	protected void removeChild(final IFileSystem service, final FSTreeNode folder, final FSTreeNode child) throws TCFFileSystemException {
		if (Protocol.isDispatchThread()) {
			if (!folder.childrenQueried) {
				loadChildren(folder, service);
			}
			folder.removeChild(child);
			child.parent = null;
		}
		else {
			final TCFFileSystemException[] errors = new TCFFileSystemException[1];
			Protocol.invokeAndWait(new Runnable() {

				@Override
				public void run() {
					try {
						removeChild(service, folder, child);
					}
					catch (TCFFileSystemException e) {
						errors[0] = e;
					}
				}
			});
			if (errors[0] != null) throw errors[0];
		}
	}

	/**
	 * Find the node with the name from the children list of the folder.
	 *
	 * @param service The file system service.
	 * @param folder The folder node.
	 * @param name The target node's name.
	 * @return The node with the specified name or null if no such node is found.
	 * @throws TCFFileSystemException Thrown when querying the children.
	 */
	protected FSTreeNode findChild(IFileSystem service, FSTreeNode folder, String name) throws TCFFileSystemException {
		List<FSTreeNode> children = getChildren(folder, service);
		for (FSTreeNode child : children) {
			if (child.name.equals(name)) return child;
		}
		return null;
	}

	/**
	 * Create the name for the target file that is copied. If there exists a file with the same
	 * name, then "Copy of xxxx" and "Copy (n) of xxxx" will be used as the target file name.
	 *
	 * @param service File system service used to query the children nodes of the folder.
	 * @param node The node whose target file is to be created.
	 * @param dest The destination folder.
	 * @return The new target node with the new name following the rule.
	 * @throws TCFFileSystemException Thrown during children querying.
	 */
	protected FSTreeNode createCopyDestination(IFileSystem service, FSTreeNode node, FSTreeNode dest) throws TCFFileSystemException {
		FSTreeNode copy = (FSTreeNode) node.clone();
		String name = node.name;
		FSTreeNode possibleChild = findChild(service, dest, name);
		for (int n = 0; possibleChild != null; n++) {
			if (n > 0) {
				name = NLS.bind(Messages.FSOperation_CopyNOfFile, Integer.valueOf(n), node.name);
			}
			else {
				name = NLS.bind(Messages.FSOperation_CopyOfFile, node.name);
			}
			possibleChild = findChild(service, dest, name);
		}
		copy.name = name;
		addChild(service, dest, copy);
		return copy;
	}

	/**
	 * Make a new directory with for the new node.
	 *
	 * @param service The file system service.
	 * @param node The directory node to be made.
	 * @throws TCFFileSystemException Thrown during children querying.
	 */
	protected void mkdir(IFileSystem service, final FSTreeNode node) throws TCFFileSystemException {
		final TCFFileSystemException[] errors = new TCFFileSystemException[1];
		String path = node.getLocation(true);
		service.mkdir(path, node.attr, new DoneMkDir() {
			@Override
			public void doneMkDir(IToken token, FileSystemException error) {
				if (error != null) {
					String message = NLS
					                .bind(Messages.FSOperation_CannotCreateDirectory, new Object[] { node.name, error });
					errors[0] = new TCFFileSystemException(message, error);
				}
			}
		});
		if (errors[0] != null) {
			throw errors[0];
		}
	}

	/**
	 * Confirm if the file/folder represented by the specified should be replaced.
	 *
	 * @param node The file/folder node.
	 * @return The confirming result. true yes, false no.
	 * @throws InterruptedException Thrown when canceled.
	 */
	protected boolean confirmReplace(final FSTreeNode node) throws InterruptedException {
		if (yes2All) return true;
		final int[] results = new int[1];
		Display display = PlatformUI.getWorkbench().getDisplay();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				Shell parent = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
				String title = node.isDirectory() ? Messages.FSOperation_ConfirmFolderReplaceTitle : Messages.FSOperation_ConfirmFileReplace;
				String message = NLS.bind(node.isDirectory() ? Messages.FSOperation_ConfirmFolderReplaceMessage : Messages.FSOperation_ConfirmFileReplaceMessage, node.name);
				final Image titleImage = UIPlugin.getImage(ImageConsts.REPLACE_FOLDER_CONFIRM);
				MessageDialog qDialog = new MessageDialog(parent, title, null, message, MessageDialog.QUESTION, new String[] { Messages.FSOperation_ConfirmDialogYes, Messages.FSOperation_ConfirmDialogYesToAll, Messages.FSOperation_ConfirmDialogNo, Messages.FSOperation_ConfirmDialogCancel }, 0) {
					@Override
					public Image getQuestionImage() {
						return titleImage;
					}
				};
				results[0] = qDialog.open();
			}
		});
		switch (results[0]) {
		case 0:
			return true;
		case 1:
			yes2All = true;
			return true;
		case 2:
			return false;
		}
		throw new InterruptedException();
	}

	/**
	 * Add the specified child to the folder node's children list.
	 *
	 * @param service The file system service.
	 * @param folder The folder node.
	 * @param child The child node to be added.
	 * @throws TCFFileSystemException Thrown during children querying.
	 */
	protected void addChild(final IFileSystem service, final FSTreeNode folder, final FSTreeNode child) throws TCFFileSystemException {
		if (Protocol.isDispatchThread()) {
			if (!folder.childrenQueried) {
				loadChildren(folder, service);
			}
			child.parent = folder;
			folder.addChild(child);
		}
		else {
			final TCFFileSystemException[] errors = new TCFFileSystemException[1];
			Protocol.invokeAndWait(new Runnable() {

				@Override
				public void run() {
					try {
						addChild(service, folder, child);
					}
					catch (TCFFileSystemException e) {
						errors[0] = e;
					}
				}
			});
			if (errors[0] != null) throw errors[0];
		}
	}

	/**
	 * Remove the file.
	 *
	 * @param node
	 * @param service
	 * @throws TCFFileSystemException
	 */
	protected void removeFile(final FSTreeNode node, IFileSystem service) throws TCFFileSystemException {
		// Do the actual deleting.
		String path = node.getLocation(true);
		final TCFFileSystemException[] errors = new TCFFileSystemException[1];
		service.remove(path, new DoneRemove() {
			@Override
			public void doneRemove(IToken token, FileSystemException error) {
				if (error == null) {
					cleanUpFile(node);
				}
				else {
					String message = NLS.bind(Messages.FSDelete_CannotRemoveFile, node.name, error);
					errors[0] = new TCFFileSystemException(message, error);
				}
			}
		});
		if (errors[0] != null) {
			throw errors[0];
		}
	}

	/**
	 * Remove the folder.
	 *
	 * @param node
	 * @param service
	 * @throws TCFFileSystemException
	 */
	protected void removeFolder(final FSTreeNode node, IFileSystem service) throws TCFFileSystemException {
		// Do the actual deleting.
		String path = node.getLocation(true);
		final TCFFileSystemException[] errors = new TCFFileSystemException[1];
		service.rmdir(path, new DoneRemove() {
			@Override
			public void doneRemove(IToken token, FileSystemException error) {
				if (error == null) {
					cleanUpFolder(node);
				}
				else {
					String message = NLS.bind(Messages.FSDelete_CannotRemoveFile, node.name, error);
					errors[0] = new TCFFileSystemException(message, error);
				}
			}
		});
		if (errors[0] != null) {
			throw errors[0];
		}
	}

	/**
	 * Do the actual operation.
	 *
	 * @return Status.OK_STATUS if it is successful.
	 */
	public IStatus doit(){
		return Status.OK_STATUS;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.operation.IRunnableWithProgress#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	@Override
    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    }
}
