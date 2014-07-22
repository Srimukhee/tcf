/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.te.ui.terminals.local.launcher;

import org.eclipse.core.runtime.Assert;
import org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer;
import org.eclipse.tcf.te.ui.terminals.interfaces.IMementoHandler;
import org.eclipse.ui.IMemento;

/**
 * SSH terminal connection memento handler implementation.
 */
public class LocalMementoHandler implements IMementoHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.IMementoHandler#saveState(org.eclipse.ui.IMemento, org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer)
	 */
	@Override
	public void saveState(IMemento memento, IPropertiesContainer properties) {
		Assert.isNotNull(memento);
		Assert.isNotNull(properties);

	}

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.ui.terminals.interfaces.IMementoHandler#restoreState(org.eclipse.ui.IMemento, org.eclipse.tcf.te.runtime.interfaces.properties.IPropertiesContainer)
	 */
	@Override
	public void restoreState(IMemento memento, IPropertiesContainer properties) {
		Assert.isNotNull(memento);
		Assert.isNotNull(properties);
	}
}
