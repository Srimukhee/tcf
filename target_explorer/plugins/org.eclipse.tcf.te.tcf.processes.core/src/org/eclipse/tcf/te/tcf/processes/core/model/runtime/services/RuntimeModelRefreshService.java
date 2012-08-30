/*******************************************************************************
 * Copyright (c) 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.processes.core.model.runtime.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.protocol.IToken;
import org.eclipse.tcf.protocol.Protocol;
import org.eclipse.tcf.services.IProcesses;
import org.eclipse.tcf.services.ISysMonitor;
import org.eclipse.tcf.services.ISysMonitor.SysMonitorContext;
import org.eclipse.tcf.te.core.async.AsyncCallbackCollector;
import org.eclipse.tcf.te.runtime.callback.Callback;
import org.eclipse.tcf.te.runtime.interfaces.callback.ICallback;
import org.eclipse.tcf.te.runtime.model.interfaces.IModelNode;
import org.eclipse.tcf.te.tcf.core.async.CallbackInvocationDelegate;
import org.eclipse.tcf.te.tcf.core.model.interfaces.IModel;
import org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelChannelService;
import org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelLookupService;
import org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelRefreshService;
import org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelUpdateService;
import org.eclipse.tcf.te.tcf.core.model.services.AbstractModelService;
import org.eclipse.tcf.te.tcf.processes.core.activator.CoreBundleActivator;
import org.eclipse.tcf.te.tcf.processes.core.model.interfaces.IProcessContextNode;
import org.eclipse.tcf.te.tcf.processes.core.model.interfaces.IProcessContextNode.TYPE;
import org.eclipse.tcf.te.tcf.processes.core.model.interfaces.IProcessContextNodeProperties;
import org.eclipse.tcf.te.tcf.processes.core.model.interfaces.runtime.IRuntimeModel;

/**
 * Runtime model refresh service implementation.
 */
public class RuntimeModelRefreshService extends AbstractModelService<IRuntimeModel> implements IModelRefreshService {

	/**
	 * Constructor.
	 *
	 * @param model The parent model. Must not be <code>null</code>.
	 */
	public RuntimeModelRefreshService(IRuntimeModel model) {
	    super(model);
    }

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelRefreshService#refresh(org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void refresh(ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		refresh(NONE, callback);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelRefreshService#refresh(int, org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void refresh(final int flags, final ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the parent model
		final IRuntimeModel model = getModel();

		// If the parent model is already disposed, the service will drop out immediately
		if (model.isDisposed()) {
			if (callback != null) callback.done(this, Status.OK_STATUS);
			return;
		}

		// Get the list of old children (update node instances where possible)
		final List<IProcessContextNode> oldChildren = model.getChildren(IProcessContextNode.class);

		// Refresh the process contexts from the agent
		refreshContextChildren(oldChildren, model, null, new Callback() {
			@Override
			protected void internalDone(Object caller, IStatus status) {
				final AtomicBoolean isDisposed = new AtomicBoolean();
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						isDisposed.set(model.isDisposed());
					}
				};
				if (Protocol.isDispatchThread()) runnable.run();
				else Protocol.invokeAndWait(runnable);

				if (!isDisposed.get()) {
					// If there are remaining old children, remove them from the model (non-recursive)
					for (IProcessContextNode oldChild : oldChildren) model.getService(IModelUpdateService.class).remove(oldChild);
				}

				// Invoke the callback
				if (callback != null) callback.done(this, Status.OK_STATUS);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelRefreshService#refresh(org.eclipse.tcf.te.runtime.model.interfaces.IModelNode, org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void refresh(IModelNode node, ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		refresh(node, NONE, callback);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.tcf.core.model.interfaces.services.IModelRefreshService#refresh(org.eclipse.tcf.te.runtime.model.interfaces.IModelNode, int, org.eclipse.tcf.te.runtime.interfaces.callback.ICallback)
	 */
	@Override
	public void refresh(IModelNode node, int flags, ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$

		// Get the parent model
		final IRuntimeModel model = getModel();

		// If the parent model is already disposed, the service will drop out immediately
		if (model.isDisposed() || !(node instanceof IProcessContextNode)) {
			if (callback != null) callback.done(this, Status.OK_STATUS);
			return;
		}

		// Perform the refresh of the node
		doRefresh(model, node, flags, callback);
	}

	/**
	 * Performs the refresh of the given model node.
	 *
	 * @param model The runtime model. Must not be <code>null</code>.
	 * @param node  The node. Must not be <code>null</code>.
	 * @param flags The flags. See the defined constants for details.
	 * @param callback The callback to invoke once the refresh operation finished, or <code>null</code>.
	 */
	protected void doRefresh(final IRuntimeModel model, final IModelNode node, final int flags, final ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(model);
		Assert.isNotNull(node);

		// Refresh the process context from the agent
		refreshContext(model, node, new Callback() {
			@Override
			protected void internalDone(Object caller, IStatus status) {
				if (status.getSeverity() == IStatus.ERROR) {
					if (callback != null) callback.done(caller, status);
					return;
				}

				// Get the list of old children (update node instances where possible)
				final List<IProcessContextNode> oldChildren = ((IProcessContextNode)node).getChildren(IProcessContextNode.class);

				// Refresh the children of the process context node from the agent
				refreshContextChildren(oldChildren, model, (IProcessContextNode)node, new Callback() {
					@Override
					protected void internalDone(Object caller, IStatus status) {
						final AtomicBoolean isDisposed = new AtomicBoolean();
						Runnable runnable = new Runnable() {
							@Override
							public void run() {
								isDisposed.set(model.isDisposed());
							}
						};
						if (Protocol.isDispatchThread()) runnable.run();
						else Protocol.invokeAndWait(runnable);

						if (!isDisposed.get()) {
							// If there are remaining old children, remove them from the parent node (recursive)
							for (IProcessContextNode oldChild : oldChildren) ((IProcessContextNode)node).remove(oldChild, true);
						}

						// Invoke the callback
						if (callback != null) callback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
					}
				});
			}
		});
	}

	/**
	 * Process the given map of process contexts and update the given model.
	 *
	 * @param contexts The map of contexts to process. Must not be <code>null</code>.
	 * @param oldChildren The list of old children. Must not be <code>null</code>.
	 * @param model The model. Must not be <code>null</code>.
	 * @param parent The parent context node or <code>null</code>.
	 */
	protected void processContexts(Map<UUID, IProcessContextNode> contexts, List<IProcessContextNode> oldChildren, IModel model, IProcessContextNode parent) {
		Assert.isNotNull(contexts);
		Assert.isNotNull(oldChildren);
		Assert.isNotNull(model);

		for (Entry<UUID, IProcessContextNode> entry : contexts.entrySet()) {
			// Get the context instance for the current id
			IProcessContextNode candidate = entry.getValue();
			// Try to find an existing context node first
			IModelNode[] nodes = model.getService(IModelLookupService.class).lkupModelNodesById(candidate.getStringProperty(IModelNode.PROPERTY_ID));
			// If found, update the context node properties from the new one
			if (nodes.length > 0) {
				for (IModelNode node : nodes) {
					model.getService(IModelUpdateService.class).update(node, candidate);
					oldChildren.remove(node);
				}
			} else {
				if (parent == null) {
					model.getService(IModelUpdateService.class).add(candidate);
				} else {
					parent.add(candidate);
				}
			}
		}
	}

	/**
	 * Refresh the given process context node.
	 *
	 * @param model The runtime model. Must not be <code>null</code>.
	 * @param node  The node. Must not be <code>null</code>.
	 * @param callback The callback to invoke once the refresh operation finished, or <code>null</code>.
	 */
	protected void refreshContext(final IRuntimeModel model, final IModelNode node, final ICallback callback) {
		Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
		Assert.isNotNull(model);
		Assert.isNotNull(node);

		// Get an open channel
		IModelChannelService channelService = getModel().getService(IModelChannelService.class);
		channelService.openChannel(new IModelChannelService.DoneOpenChannel() {
			@Override
			public void doneOpenChannel(Throwable error, final IChannel channel) {
				if (error == null) {
					final IProcesses service = channel.getRemoteService(IProcesses.class);
					Assert.isNotNull(service);
					final ISysMonitor sysMonService = channel.getRemoteService(ISysMonitor.class);
					Assert.isNotNull(sysMonService);
					final String contextId = ((IProcessContextNode)node).getStringProperty(IModelNode.PROPERTY_ID);
					sysMonService.getContext(contextId, new ISysMonitor.DoneGetContext() {
						@Override
						public void doneGetContext(IToken token, Exception error, SysMonitorContext context) {
							((IProcessContextNode)node).setSysMonitorContext(context);

							// Get the process context
							service.getContext(contextId, new IProcesses.DoneGetContext() {
								@Override
								public void doneGetContext(IToken token, Exception error, IProcesses.ProcessContext context) {
									((IProcessContextNode)node).setProcessContext(context);
									callback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
								}
							});
						}
					});
				} else {
					callback.done(RuntimeModelRefreshService.this, new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), error.getLocalizedMessage(), error));
				}
			}
		});
	}

	/**
	 * Refresh the children of the given process context node.
	 *
	 * @param oldChildren The list of old children. Must not be <code>null</code>.
	 * @param model The model. Must not be <code>null</code>.
	 * @param parent The parent context node or <code>null</code>.
	 * @param callback The callback to invoke at the end of the operation. Must not be <code>null</code>.
	 */
	protected void refreshContextChildren(final List<IProcessContextNode> oldChildren, final IModel model, final IProcessContextNode parent, final ICallback callback) {
		Assert.isNotNull(oldChildren);
		Assert.isNotNull(model);
		Assert.isNotNull(callback);

		// Make sure that the callback is invoked even for unexpected cases
		try {
			// The map of contexts created from the agents response
			final Map<UUID, IProcessContextNode> contexts = new HashMap<UUID, IProcessContextNode>();

			// Get an open channel
			IModelChannelService channelService = getModel().getService(IModelChannelService.class);
			channelService.openChannel(new IModelChannelService.DoneOpenChannel() {
				@Override
				public void doneOpenChannel(Throwable error, final IChannel channel) {
					if (error == null) {
						// Determine the parent context id
						String parentContextId = null;
						if (parent != null && parent.getProcessContext() != null) parentContextId = parent.getStringProperty(IModelNode.PROPERTY_ID);

						// Get the Systems service and query the configuration id's
						final IProcesses service = channel.getRemoteService(IProcesses.class);
						Assert.isNotNull(service);
						final ISysMonitor sysMonService = channel.getRemoteService(ISysMonitor.class);
						Assert.isNotNull(sysMonService);
						sysMonService.getChildren(parentContextId, new ISysMonitor.DoneGetChildren() {
							@Override
							public void doneGetChildren(IToken token, Exception error, String[] context_ids) {
								if (error == null) {
									if (context_ids != null && context_ids.length > 0) {
										final AsyncCallbackCollector collector = new AsyncCallbackCollector(new Callback() {
											@Override
											protected void internalDone(Object caller, IStatus status) {
												Assert.isTrue(Protocol.isDispatchThread(), "Illegal Thread Access"); //$NON-NLS-1$
												if (status.getSeverity() == IStatus.OK) {
													// Process the read process contexts
													if (!contexts.isEmpty()) processContexts(contexts, oldChildren, model, parent);
													callback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
												} else {
													callback.done(RuntimeModelRefreshService.this, new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), status.getMessage(), status.getException()));
												}
											}
										}, new CallbackInvocationDelegate());

										// Loop the returned context id's and query the context data
										for (String id : context_ids) {
											final String contextId = id;
											final ICallback innerCallback = new AsyncCallbackCollector.SimpleCollectorCallback(collector);
											sysMonService.getContext(contextId, new ISysMonitor.DoneGetContext() {
												@Override
												public void doneGetContext(IToken token, Exception error, SysMonitorContext context) {
													// Ignore errors. Some of the context might be OS context we do not have
													// permissions to read the properties from.
													if (context != null) {
														final IProcessContextNode node = createContextNodeFrom(context);
														Assert.isNotNull(node);
														node.setType(parent == null ? TYPE.Process : TYPE.Thread);
														contexts.put(node.getUUID(), node);

														// Query the corresponding process context
														service.getContext(contextId, new IProcesses.DoneGetContext() {
															@Override
															public void doneGetContext(IToken token, Exception error, IProcesses.ProcessContext context) {
																// Errors are ignored
																node.setProcessContext(context);
																if (context != null) node.setProperty(IProcessContextNodeProperties.PROPERTY_NAME, context.getName());

																// Refresh the children of the node
//																List<IProcessContextNode> oldChildren = node.getChildren(IProcessContextNode.class);
//																refreshContextChildren(oldChildren, model, node, innerCallback);
																innerCallback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
															}
														});
													} else {
														innerCallback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
													}
												}
											});
										}

										collector.initDone();
									} else {
										callback.done(RuntimeModelRefreshService.this, Status.OK_STATUS);
									}
								} else {
									callback.done(RuntimeModelRefreshService.this, new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), error.getLocalizedMessage(), error));
								}
							}
						});
					} else {
						callback.done(RuntimeModelRefreshService.this, new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), error.getLocalizedMessage(), error));
					}

				}
			});
		} catch (Throwable e) {
			callback.done(RuntimeModelRefreshService.this, new Status(IStatus.ERROR, CoreBundleActivator.getUniqueIdentifier(), e.getLocalizedMessage(), e));
		}
	}

	/**
	 * Create a context node instance from the given process context.
	 *
	 * @param context The system monitor context. Must not be <code>null</code>.
	 * @return The context node instance.
	 */
	public IProcessContextNode createContextNodeFrom(SysMonitorContext context) {
		Assert.isNotNull(context);

		// Create a context node and associate the given context
		IProcessContextNode node = getModel().getFactory().newInstance(IProcessContextNode.class);
		node.setSysMonitorContext(context);

		// Re-create the context properties from the context
		node.setProperty(IProcessContextNodeProperties.PROPERTY_ID, context.getID());

		return node;
	}
}
