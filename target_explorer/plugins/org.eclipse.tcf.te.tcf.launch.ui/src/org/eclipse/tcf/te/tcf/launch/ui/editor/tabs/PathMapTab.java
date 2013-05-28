/*******************************************************************************
 * Copyright (c) 2012, 2013 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.tcf.launch.ui.editor.tabs;

import org.eclipse.tcf.internal.debug.ui.launch.TCFPathMapTab;
import org.eclipse.tcf.te.tcf.launch.ui.editor.AbstractTcfLaunchTabContainerEditorPage;
import org.eclipse.tcf.te.tcf.launch.ui.nls.Messages;

/**
 * Customized TCF path map launch configuration tab implementation to work better
 * inside an configuration editor tab.
 */
public class PathMapTab extends TCFPathMapTab {
	// Reference to the parent editor page
	private final AbstractTcfLaunchTabContainerEditorPage parentEditorPage;

	/**
     * Constructor
     *
     * @param parentEditorPage The parent editor page. Must not be <code>null</code>.
     */
    public PathMapTab(AbstractTcfLaunchTabContainerEditorPage parentEditorPage) {
    	super();
    	this.parentEditorPage = parentEditorPage;
    }

    /**
     * Returns the parent editor page.
     *
     * @return The parent editor page.
     */
    public final AbstractTcfLaunchTabContainerEditorPage getParentEditorPage() {
    	return parentEditorPage;
    }

    /* (non-Javadoc)
     * @see org.eclipse.tcf.internal.debug.ui.launch.TCFPathMapTab#updateLaunchConfigurationDialog()
     */
	@Override
	protected void updateLaunchConfigurationDialog() {
		super.updateLaunchConfigurationDialog();
		if (parentEditorPage != null) {
			performApply(AbstractTcfLaunchTabContainerEditorPage.getLaunchConfig(parentEditorPage.getPeerModel(parentEditorPage.getEditorInput())));
			parentEditorPage.checkLaunchConfigDirty();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.internal.debug.ui.launch.TCFPathMapTab#getName()
	 */
	@Override
	public String getName() {
	    return Messages.PathMapEditorPage_name;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.internal.debug.ui.launch.TCFPathMapTab#getColumnText(int)
	 */
	@Override
	protected String getColumnText(int column) {
		String text = super.getColumnText(column);
		if (text != null && text.trim().length() > 0) {
			String key = "PathMapEditorPage_column_" + text; //$NON-NLS-1$
			if (Messages.hasString(key))
				text = Messages.getString(key);
			else {
    			key = "PathMapEditorPage_column_" + column; //$NON-NLS-1$
    			if (Messages.hasString(key))
    				text = Messages.getString(key);
			}
		}
	    return text != null ? text : ""; //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.internal.debug.ui.launch.TCFPathMapTab#showContextQuery()
	 */
	@Override
	protected boolean showContextQuery() {
	    return false;
	}
}
