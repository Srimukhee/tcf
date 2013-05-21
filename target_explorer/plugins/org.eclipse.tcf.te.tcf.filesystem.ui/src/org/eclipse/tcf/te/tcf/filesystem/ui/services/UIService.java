/**
 * UIService.java
 * Created on Nov 15, 2012
 *
 * Copyright (c) 2012, 2013 Wind River Systems, Inc.
 *
 * The right to copy, distribute, modify, or otherwise make use
 * of this software may be licensed only pursuant to the terms
 * of an applicable Wind River license agreement.
 */
package org.eclipse.tcf.te.tcf.filesystem.ui.services;

import org.eclipse.tcf.te.runtime.services.AbstractService;
import org.eclipse.tcf.te.runtime.services.interfaces.IUIService;
import org.eclipse.tcf.te.tcf.filesystem.ui.internal.handlers.EditorHandlerDelegate;
import org.eclipse.tcf.te.ui.interfaces.handler.IEditorHandlerDelegate;

/**
 * UI service implementation.
 */
public class UIService extends AbstractService implements IUIService {
	private final IEditorHandlerDelegate editorHandlerDelegate = new EditorHandlerDelegate();

	/* (non-Javadoc)
	 * @see org.eclipse.tcf.te.runtime.services.interfaces.IUIService#getDelegate(java.lang.Object, java.lang.Class)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <V> V getDelegate(Object context, Class<? extends V> clazz) {

		if (IEditorHandlerDelegate.class.isAssignableFrom(clazz)) {
			return (V) editorHandlerDelegate;
		}

		return null;
	}

}
