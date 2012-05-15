/*******************************************************************************
 * Copyright (c) 2011, 2012 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.actions;

import org.eclipse.jface.action.IAction;
import org.eclipse.tcf.te.ui.terminals.activator.UIPlugin;
import org.eclipse.tcf.te.ui.terminals.interfaces.ImageConsts;
import org.eclipse.tcf.te.ui.terminals.nls.Messages;
import org.eclipse.tm.internal.terminal.control.ITerminalViewControl;
import org.eclipse.tm.internal.terminal.control.actions.AbstractTerminalAction;

/**
 * Terminal console tab scroll lock action.
 */
@SuppressWarnings("restriction")
public class TabScrollLockAction extends AbstractTerminalAction {

	/**
	 * Constructor.
	 */
	public TabScrollLockAction() {
		super(null, TabScrollLockAction.class.getName(), IAction.AS_RADIO_BUTTON);

        setupAction(Messages.TabScrollLockAction_text,
                    Messages.TabScrollLockAction_tooltip,
                    UIPlugin.getImageDescriptor(ImageConsts.ACTION_ScrollLock_Hover),
                    UIPlugin.getImageDescriptor(ImageConsts.ACTION_ScrollLock_Enabled),
                    UIPlugin.getImageDescriptor(ImageConsts.ACTION_ScrollLock_Disabled),
                    true);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.internal.terminal.control.actions.AbstractTerminalAction#run()
	 */
	@Override
	public void run() {
		ITerminalViewControl target = getTarget();
		if (target != null) {
			target.setScrollLock(!target.isScrollLock());
			setChecked(target.isScrollLock());
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.internal.terminal.control.actions.AbstractTerminalAction#updateAction(boolean)
	 */
	@Override
	public void updateAction(boolean aboutToShow) {
		setEnabled(getTarget() != null && aboutToShow);
	}
}
