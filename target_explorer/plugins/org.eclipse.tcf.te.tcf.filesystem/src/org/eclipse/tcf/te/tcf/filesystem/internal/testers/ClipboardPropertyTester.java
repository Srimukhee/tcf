/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.filesystem.internal.testers;

import java.util.List;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.tcf.te.tcf.filesystem.activator.UIPlugin;
import org.eclipse.tcf.te.tcf.filesystem.internal.operations.FSClipboard;
import org.eclipse.tcf.te.tcf.filesystem.model.FSTreeNode;
/**
 * Provide a tester to test if the paste operation is enabled.
 */
public class ClipboardPropertyTester extends PropertyTester {

	/**
	 * Create an instance.
	 */
	public ClipboardPropertyTester() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.core.expressions.IPropertyTester#test(java.lang.Object, java.lang.String, java.lang.Object[], java.lang.Object)
	 */
	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		Assert.isTrue(receiver instanceof IStructuredSelection);
		if (property.equals("canPaste")) { //$NON-NLS-1$
			FSClipboard cb = UIPlugin.getDefault().getClipboard();
			Clipboard clipboard = cb.getSystemClipboard();
			Object contents = clipboard.getContents(FileTransfer.getInstance());
			if(contents != null) {
				List<FSTreeNode> selection = ((IStructuredSelection) receiver).toList();
				FSTreeNode hovered = null;
				Assert.isTrue(!selection.isEmpty());
				if (selection.size() == 1) {
					FSTreeNode node = selection.get(0);
					if (node.isFile()) {
						hovered = node.parent;
					}
					else {
						hovered = node;
					}
				}
				else {
					for (FSTreeNode node : selection) {
						if (hovered == null) hovered = node.parent;
						else if (hovered != node.parent) return false;
					}
				}
				if (hovered.isDirectory() && hovered.isWritable()) {
					return true;
				}
				return false;
			} 
			else if (!cb.isEmpty()) {
				List<FSTreeNode> nodes = cb.getFiles();
				int operation = cb.getOperation();
				boolean moving = operation == FSClipboard.CUT;
				boolean copying = operation == FSClipboard.COPY;
				List<FSTreeNode> selection = ((IStructuredSelection) receiver).toList();
				FSTreeNode hovered = null;
				Assert.isTrue(!selection.isEmpty());
				if (selection.size() == 1) {
					FSTreeNode node = selection.get(0);
					if (node.isDirectory() && moving) {
						hovered = node;
					}
					else if (node.isRoot()) {
						hovered = node;
					}
					else {
						hovered = node.parent;
					}
				}
				else {
					for (FSTreeNode node : selection) {
						if (hovered == null) hovered = node.parent;
						else if (hovered != node.parent) return false;
					}
				}
				if (hovered.isDirectory() && hovered.isWritable() && (moving || copying)) {
					FSTreeNode head = nodes.get(0);
					String hid = head.peerNode.getPeerId();
					String tid = hovered.peerNode.getPeerId();
					if (hid.equals(tid)) {
						for (FSTreeNode node : nodes) {
							if (moving && node.parent == hovered || node.isAncestorOf(hovered)) {
								return false;
							}
						}
						return true;
					}
				}
			}
		}
		return false;
	}
}
