/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.ui.internal.compare;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tcf.te.tcf.filesystem.core.interfaces.IOperation;
import org.eclipse.tcf.te.tcf.filesystem.core.internal.operations.OpOutStreamOp;
import org.eclipse.tcf.te.tcf.filesystem.core.model.FSTreeNode;
import org.eclipse.tcf.te.tcf.filesystem.ui.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.filesystem.ui.nls.Messages;

/**
 * A <code>RemoteTypedElement</code> wraps an <code>FSTreeNode</code> so that it
 * can be used as input for the differencing engine (<code>ITypedElement</code>)
 * as the right element of the comparison editor.
 *
 * @since 3.7
 */
public class RemoteTypedElement extends MergeTypedElement {
	/**
	 * Creates a <code>RemoteTypedElement</code> for the given node.
	 *
	 * @param node
	 *            the tree node.
	 */
	public RemoteTypedElement(FSTreeNode node) {
		super(node);
	}

	/**
	 * Return an input stream that opens that remote file to provide the stream
	 * content.
	 *
	 * @return a buffered input stream containing the contents of this file
	 * @exception CoreException
	 *                if the contents of this storage could not be accessed
	 */
	@Override
	protected InputStream createStream() throws CoreException {
		try {
			return node.getLocationURL().openStream();
		} catch (IOException e) {
			Status error = new Status(IStatus.ERROR,
					UIPlugin.getUniqueIdentifier(), e.getMessage(), e);
			throw new CoreException(error);
		}
	}

	/**
	 * Download the remote file and save the content so that it is cached for
	 * getContents call.
	 *
	 * @param monitor
	 *            The monitor used to display downloading progress.
	 * @throws InvocationTargetException
	 *             throws when an exception occurs during downloading.
	 *          InterruptedException
	 *          	throws when the loading process is canceled.
	 */
	public void cacheContents(IProgressMonitor monitor)
			throws InvocationTargetException, InterruptedException {
		monitor.beginTask(NLS.bind(Messages.RemoteTypedElement_DowloadingFile, node.name), 100);
		OutputStream output = null;
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		output = new BufferedOutputStream(baos);
		monitor.beginTask(Messages.RemoteTypedElement_GettingRemoteContent + node.name, 100);
		IOperation operation = new OpOutStreamOp(node, output);
		operation.run(monitor);
		if (!monitor.isCanceled()) {
			setContent(baos.toByteArray());
		}
	}

	/**
	 * Return the external form of the URL to the remote file of this node. It
	 * is used to compute its hash code and as the title of the comparison
	 * editor.
	 */
	@Override
	public String toString() {
		return node.getLocationURI().toString();
	}
}
