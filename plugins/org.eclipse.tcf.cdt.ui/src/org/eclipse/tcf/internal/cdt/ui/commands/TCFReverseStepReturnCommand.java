/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.internal.cdt.ui.commands;

import org.eclipse.cdt.debug.core.model.IUncallHandler;
import org.eclipse.tcf.internal.debug.ui.commands.BackReturnCommand;
import org.eclipse.tcf.internal.debug.ui.model.TCFModel;

/**
 * Debug command handler for reverse step return.
 */
public class TCFReverseStepReturnCommand extends BackReturnCommand
        implements IUncallHandler {

    public TCFReverseStepReturnCommand(TCFModel model) {
        super(model);
    }
}
