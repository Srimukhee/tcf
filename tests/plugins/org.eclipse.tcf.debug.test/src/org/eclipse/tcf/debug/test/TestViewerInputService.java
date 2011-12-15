/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Wind River - Pawel Piech - NPE when closing the Variables view (Bug 213719)
 *******************************************************************************/
package org.eclipse.tcf.debug.test;

import org.eclipse.debug.internal.ui.viewers.model.provisional.ITreeModelViewer;
import org.eclipse.debug.internal.ui.viewers.model.ViewerAdapterService;
import org.eclipse.debug.internal.ui.viewers.model.ViewerInputUpdate;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerInputProvider;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerInputRequestor;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IViewerInputUpdate;

/**
 * Copied from org.eclipse.debug.internal.ui.viewers.model.provisional 
 * for customization.
 * 
 * Service to compute a viewer input from a source object
 * for a given presentation context.
 */
@SuppressWarnings("restriction")
public class TestViewerInputService {
	
    /**
     * An input object which will yield a null input element. 
     * 
     * @since 3.6
     */
    public final static Object NULL_INPUT = new IViewerInputProvider() {
        public void update(IViewerInputUpdate update) {
            update.setInputElement(null);
            update.done();
        }
    };
    
	// previous update request, cancelled when a new request comes in
	private IViewerInputUpdate fPendingUpdate = null;
	
	private IViewerInputRequestor fRequestor = null;
	
	private ITreeModelViewer fViewer;
	
	private IViewerInputRequestor fProxyRequest = new IViewerInputRequestor() {
		public void viewerInputComplete(final IViewerInputUpdate update) {
			synchronized (TestViewerInputService.this) {
				fPendingUpdate = null;
			}
			fRequestor.viewerInputComplete(update);
		}
	};
	
	/**
	 * Constructs a viewer input service for the given requester and presentation context.
	 * 
	 * @param requestor client requesting viewer inputs 
	 * @param context context for which inputs are required
	 */
	public TestViewerInputService(ITreeModelViewer viewer, IViewerInputRequestor requestor) {
		fRequestor = requestor;
		fViewer = viewer;
	}
	
	/**
	 * Resolves a viewer input derived from the given source object.
	 * Reports the result to the given this service's requester. A requester may be called back
	 * in the same or thread, or asynchronously in a different thread. Cancels any previous
	 * incomplete request from this service's requester.
	 * 
	 * @param source source from which to derive a viewer input
	 */
	public void resolveViewerInput(Object source) {
		IViewerInputProvider provdier = ViewerAdapterService.getInputProvider(source);
		synchronized (this) {
			// cancel any pending update
			if (fPendingUpdate != null) {
				fPendingUpdate.cancel();
			}
			fPendingUpdate = new ViewerInputUpdate(fViewer.getPresentationContext(), fViewer.getInput(), fProxyRequest, source);
		}
		if (provdier == null) {
			fPendingUpdate.setInputElement(source);
            fPendingUpdate.done();
		} else {
			provdier.update(fPendingUpdate);
		}
	}

	/**
	 * Disposes this viewer input service, canceling any pending jobs.
	 */
	public synchronized void dispose() {
        if (fPendingUpdate != null) {
            fPendingUpdate.cancel();
            fPendingUpdate = null;
        }
	}
}
