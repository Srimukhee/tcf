/*******************************************************************************
 * Copyright (c) 2012, 2015 Mentor Graphics Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Anna Dushistova (Mentor Graphics) - initial API and implementation
 * Anna Dushistova (Mentor Graphics) - moved to org.eclipse.cdt.launch.remote.tabs
 * Anna Dushistova (MontaVista)      - adapted from TEDSFDebuggerTab
 *******************************************************************************/

package org.eclipse.tcf.te.tcf.launch.cdt.tabs;

import org.eclipse.cdt.dsf.gdb.service.SessionType;

public class TEApplicationDebuggerTab extends TEAbstractDebuggerTab {

	public TEApplicationDebuggerTab() {
		super(SessionType.REMOTE, false);
	}

	@Override
	public String getId() {
		return "org.eclipse.tcf.te.remotecdt.debug.debuggerTab"; //$NON-NLS-1$
	}

}
