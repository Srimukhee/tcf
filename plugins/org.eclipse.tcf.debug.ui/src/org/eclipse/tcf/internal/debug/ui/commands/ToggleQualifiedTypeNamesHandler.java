/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.internal.debug.ui.commands;

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.tcf.internal.debug.ui.preferences.TCFPreferences;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

/**
 * Command handler toggling the "Show Qualified Type Names" preference.
 */
public class ToggleQualifiedTypeNamesHandler extends AbstractHandler implements IElementUpdater {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        boolean state = !HandlerUtil.toggleCommandState(event.getCommand());
        TCFPreferences.getPreferenceStore().setValue(TCFPreferences.PREF_SHOW_QUALIFIED_TYPE_NAMES, state);
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void updateElement(UIElement element, Map parameters) {
        element.setChecked(TCFPreferences.getPreferenceStore().getBoolean(TCFPreferences.PREF_SHOW_QUALIFIED_TYPE_NAMES));
    }

}
