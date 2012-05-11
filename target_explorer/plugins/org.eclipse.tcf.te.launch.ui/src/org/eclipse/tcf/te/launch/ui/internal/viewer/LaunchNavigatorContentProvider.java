/*******************************************************************************
 * Copyright (c) 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.launch.ui.internal.viewer;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jface.viewers.ITreePathContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.tcf.te.launch.core.lm.interfaces.ICommonLaunchAttributes;
import org.eclipse.tcf.te.launch.core.persistence.DefaultPersistenceDelegate;
import org.eclipse.tcf.te.launch.ui.model.LaunchModel;
import org.eclipse.tcf.te.launch.ui.model.LaunchNode;
import org.eclipse.tcf.te.runtime.concurrent.util.ExecutorsUtil;
import org.eclipse.tcf.te.runtime.events.ChangeEvent;
import org.eclipse.tcf.te.runtime.events.EventManager;
import org.eclipse.tcf.te.runtime.interfaces.events.IEventListener;
import org.eclipse.tcf.te.runtime.model.interfaces.IContainerModelNode;
import org.eclipse.tcf.te.runtime.model.interfaces.IModelNode;
import org.eclipse.tcf.te.ui.trees.TreeContentProvider;
import org.eclipse.tcf.te.ui.views.Managers;
import org.eclipse.tcf.te.ui.views.extensions.CategoriesExtensionPointManager;
import org.eclipse.tcf.te.ui.views.interfaces.ICategory;
import org.eclipse.tcf.te.ui.views.interfaces.IUIConstants;
import org.eclipse.ui.PlatformUI;

/**
 * Launches content provider for the common navigator of Target Explorer.
 */
public class LaunchNavigatorContentProvider extends TreeContentProvider implements ITreePathContentProvider, IEventListener {

	/**
	 * Constructor.
	 */
	public LaunchNavigatorContentProvider() {
		super();
		EventManager.getInstance().addEventListener(this, ChangeEvent.class);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
	 */
	@Override
	public Object getParent(Object element) {
		if (element instanceof LaunchNode) {
			LaunchNode node = (LaunchNode)element;
			if (node.getParent() == null ||
							LaunchNode.TYPE_ROOT.equals(node.getType()) ||
							(!isTypeNodeVisible() && LaunchNode.TYPE_LAUNCH_CONFIG.equals(node.getType())) ||
							(!isRootNodeVisible() && LaunchNode.TYPE_LAUNCH_CONFIG_TYPE.equals(node.getType()))) {
				return node.getModel().getModelRoot();
			}

			return node.getParent();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.trees.TreeContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	@Override
	public void inputChanged(final Viewer viewer, Object oldInput, Object newInput) {
		super.inputChanged(viewer, oldInput, newInput);

		if (newInput != null && !newInput.equals(oldInput)) {
			LaunchModel model = LaunchModel.getLaunchModel(newInput);
			if (model != null) {
				LaunchNode lastLaunchedNode = null;
				long nodeValue = -1;
				for (IModelNode typeNode : model.getRootNode().getChildren()) {
					for (IModelNode launchNode : ((IContainerModelNode)typeNode).getChildren()) {
						ILaunchConfiguration config = ((LaunchNode)launchNode).getLaunchConfiguration();
						String lastLaunched = DefaultPersistenceDelegate.getAttribute(config, ICommonLaunchAttributes.ATTR_LAST_LAUNCHED, (String)null);
						if (lastLaunched != null) {
							long last = Long.parseLong(lastLaunched);
							if (last > nodeValue) {
								nodeValue = last;
								lastLaunchedNode = (LaunchNode)launchNode;
							}
						}
					}
				}
				if (lastLaunchedNode != null) {
					final LaunchNode node = lastLaunchedNode;
					ExecutorsUtil.executeInUI(new Runnable() {
						@Override
						public void run() {
							viewer.setSelection(new StructuredSelection(node));
						}
					});
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.trees.TreeContentProvider#dispose()
	 */
	@Override
	public void dispose() {
		super.dispose();
		EventManager.getInstance().removeEventListener(this);
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
	 */
	@Override
	public Object[] getChildren(Object element) {
		super.getChildren(element);

		if (element instanceof LaunchNode) {
			LaunchNode node = (LaunchNode)element;
			List<IModelNode> children = new ArrayList<IModelNode>();
			if (LaunchNode.TYPE_ROOT.equals(node.getType())) {
				if (isTypeNodeVisible()) {
					// return all type nodes of the model
					if (isEmptyTypeNodeVisible()) {
						return node.getChildren();
					}
					// return only _not_ empty type nodes of the model
					for (IModelNode typeNode : node.getChildren()) {
						if (((IContainerModelNode)typeNode).hasChildren()) {
							children.add(typeNode);
						}
					}
					return children.toArray();
				}
				// return all config nodes of all type nodes of the model
				for (IModelNode typeNode : node.getChildren()) {
					for (IModelNode configNode : ((IContainerModelNode)typeNode).getChildren()) {
						children.add(configNode);
					}
				}
				return children.toArray();
			}
			return node.getChildren();
		}

		LaunchModel model = LaunchModel.getLaunchModel(element);
		if (model != null) {
			if (isRootNodeVisible() && model.getRootNode().hasChildren()) {
				return new Object[]{model.getRootNode()};
			}
			return getChildren(model.getRootNode());
		}
		return NO_ELEMENTS;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
	 */
	@Override
	public boolean hasChildren(Object element) {
		if (element instanceof LaunchNode) {
			LaunchNode node = (LaunchNode)element;
			List<IModelNode> children = new ArrayList<IModelNode>();
			if (LaunchNode.TYPE_ROOT.equals(node.getType())) {
				if (isTypeNodeVisible()) {
					// return all type nodes of the model
					if (isEmptyTypeNodeVisible()) {
						return ((LaunchNode)element).hasChildren();
					}
					// return only _not_ empty type nodes of the model
					for (IModelNode typeNode : node.getChildren()) {
						if (((IContainerModelNode)typeNode).hasChildren()) {
							children.add(typeNode);
						}
					}
					return !children.isEmpty();
				}
				// return all config nodes of all type nodes of the model
				for (IModelNode typeNode : node.getChildren()) {
					for (IModelNode configNode : ((IContainerModelNode)typeNode).getChildren()) {
						children.add(configNode);
					}
				}
				return !children.isEmpty();
			}
			return ((LaunchNode)element).hasChildren();
		}

		LaunchModel model = LaunchModel.getLaunchModel(element);
		if (model != null) {
			if (isRootNodeVisible()) {
				return true;
			}
			return hasChildren(model.getRootNode());
		}
		return false;
	}

	/**
	 * If the root node of the tree is visible.
	 * 
	 * @return true if it is visible.
	 */
	protected boolean isRootNodeVisible() {
		return true;
	}

	/**
	 * If the launch config type node in the tree is visible.
	 * 
	 * @return true if it is visible.
	 */
	protected boolean isTypeNodeVisible() {
		return true;
	}

	/**
	 * If an empty launch config type node in the tree is visible.
	 * 
	 * @return true if it is visible.
	 */
	protected boolean isEmptyTypeNodeVisible() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.interfaces.events.IEventListener#eventFired(java.util.EventObject)
	 */
	@Override
	public void eventFired(EventObject event) {
		final TreeViewer viewer = this.viewer;
		if (event.getSource() instanceof LaunchModel) {
			final LaunchModel model = (LaunchModel)event.getSource();
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					viewer.refresh((isRootNodeVisible() ? model.getRootNode() : model.getModelRoot()), true);
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreePathContentProvider#getChildren(org.eclipse.jface.viewers.TreePath)
	 */
	@Override
	public Object[] getChildren(TreePath parentPath) {
		return parentPath != null ? getChildren(parentPath.getLastSegment()) : NO_ELEMENTS;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreePathContentProvider#hasChildren(org.eclipse.jface.viewers.TreePath)
	 */
	@Override
	public boolean hasChildren(TreePath path) {
		return path != null ? hasChildren(path.getLastSegment()) : false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ITreePathContentProvider#getParents(java.lang.Object)
	 */
	@Override
	public TreePath[] getParents(Object element) {
		// Not sure if we ever have to calculate the _full_ tree path. The parent NavigatorContentServiceContentProvider
		// is consuming only the last segment.
		List<TreePath> pathes = new ArrayList<TreePath>();

		Object parent = getParent(element);
		TreePath parentPath = new TreePath(new Object[]{parent});
		if (!(parent instanceof ICategory) && element instanceof LaunchNode && ((LaunchNode)element).getLaunchConfiguration() != null) {
			if (Managers.getCategoryManager().belongsTo(IUIConstants.ID_CAT_FAVORITES, LaunchModel.getCategoryId(((LaunchNode)element).getLaunchConfiguration()))) {
				// Get the "Favorites" category
				ICategory favCategory = CategoriesExtensionPointManager.getInstance().getCategory(IUIConstants.ID_CAT_FAVORITES, false);
				if (favCategory != null) {
					pathes.add(new TreePath(new Object[]{favCategory}));
				}
			}
		}
		if (!pathes.contains(parentPath)) {
			pathes.add(parentPath);
		}

		return pathes.toArray(new TreePath[pathes.size()]);
	}
}
