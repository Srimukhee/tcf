/*******************************************************************************
 * Copyright (c) 2011 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.processes.ui.internal.callbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.services.ISysMonitor;
import org.eclipse.tcf.te.tcf.processes.ui.model.ProcessModel;
import org.eclipse.tcf.te.tcf.processes.ui.model.ProcessTreeNode;

/**
 * The monitor's callback invoked after all the contexts of the processes have been 
 * fetched and updated.
 */
public class RefreshDoneMonitorCallback implements Runnable {
	// The new nodes during this querying.
	List<ProcessTreeNode> newNodes;
	// The parent node whose children are refreshing.
	ProcessTreeNode parentNode;
	// The queue to cache the legitimate nodes for refreshing.
	Queue<ProcessTreeNode> queue;
	// The callback after the querying is done.
	Runnable callback;
	// The service used to fetch process context.
	ISysMonitor service;
	// The process model used to fire property change events.
	ProcessModel model;
	// The TCF channel.
	IChannel channel;

	/**
	 * Create an instance with parameters to initialize the fields.
	 */
	public RefreshDoneMonitorCallback(List<ProcessTreeNode> newNodes, ProcessTreeNode parentNode, 
					Queue<ProcessTreeNode> queue, Runnable callback, ISysMonitor service, ProcessModel model, IChannel channel) {
		this.newNodes = newNodes;
		this.parentNode = parentNode;
		this.queue = queue;
		this.callback = callback;
		this.service = service;
		this.model = model;
		this.channel = channel;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		parentNode.childrenQueryRunning = false;
		parentNode.childrenQueried = true;
		removeDead();
		for (ProcessTreeNode node : parentNode.children) {
			if (node.childrenQueried && !node.childrenQueryRunning) {
				queue.offer(node);
			}
		}
		if (queue.isEmpty()) {
			if (callback != null) {
				callback.run();
			}
		}
		else {
			ProcessTreeNode node = queue.poll();
			service.getChildren(node.id, new RefreshDoneGetChildren(model, callback, queue, channel, service, node));
		}
	}

	/**
	 * Remove the dead process nodes.
	 */
	private void removeDead() {
		List<ProcessTreeNode> dead = new ArrayList<ProcessTreeNode>();
		for (ProcessTreeNode node : parentNode.children) {
			int index = searchInList(node, newNodes);
			if (index == -1) {
				dead.add(node);
			}
		}
		for (ProcessTreeNode node : dead) {
			parentNode.children.remove(node);
		}
	}

	/**
	 * Search the specified child node in the specified list.
	 * 
	 * @param childNode The child node.
	 * @param list The process node list.
	 * @return The index of the child node or -1 if no such node.
	 */
	private int searchInList(ProcessTreeNode childNode, List<ProcessTreeNode> list) {
		synchronized (list) {
			for (int i = 0; i < list.size(); i++) {
				ProcessTreeNode node = list.get(i);
				if (childNode.id.equals(node.id)) {
					return i;
				}
			}
			return -1;
		}
	}
}
