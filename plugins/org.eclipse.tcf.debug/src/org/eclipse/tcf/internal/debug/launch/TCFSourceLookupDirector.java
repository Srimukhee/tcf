/*******************************************************************************
 * Copyright (c) 2007, 2011 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.internal.debug.launch;

import java.io.File;
import java.net.URI;
import java.util.Map;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.tcf.internal.debug.model.TCFLaunch;
import org.eclipse.tcf.services.ILineNumbers;
import org.eclipse.tcf.services.IPathMap;
import org.eclipse.tcf.util.TCFDataCache;
import org.eclipse.tcf.util.TCFTask;

/**
 * TCF source lookup director.
 * For TCF source lookup there is one source lookup participant.
 */
public class TCFSourceLookupDirector extends AbstractSourceLookupDirector {

    public static Object lookup(final TCFLaunch launch, Object element) {
        if (element instanceof ILineNumbers.CodeArea) {
            element = TCFSourceLookupParticipant.toFileName((ILineNumbers.CodeArea)element);
        }
        Object source_element = null;
        ISourceLocator locator = launch.getSourceLocator();
        if (locator instanceof ISourceLookupDirector) {
            source_element = ((ISourceLookupDirector)locator).getSourceElement(element);
        }
        else if (element instanceof IStackFrame) {
            source_element = locator.getSourceElement((IStackFrame)element);
        }
        if (source_element == null && element instanceof String) {
            /* Try to lookup the element using target side path mapping rules */
            final String str = (String)element;
            Map<String,IStorage> map = launch.getTargetPathMappingCache();
            synchronized (map) {
                if (map.containsKey(str)) return map.get(str);
            }
            IStorage storage = new TCFTask<IStorage>(launch.getChannel()) {
                public void run() {
                    TCFDataCache<IPathMap.PathMapRule[]> cache = launch.getTargetPathMap();
                    if (cache != null) {
                        if (!cache.validate(this)) return;
                        IPathMap.PathMapRule[] data = cache.getData();
                        if (data != null) {
                            for (IPathMap.PathMapRule r : data) {
                                String fnm = TCFSourceLookupParticipant.toFileName(r, str);
                                if (fnm == null) continue;
                                File file = new File(fnm);
                                if (file.isAbsolute() && file.exists() && file.isFile()) {
                                    done(new LocalFileStorage(file));
                                    return;
                                }
                            }
                        }
                    }
                    done(null);
                }
            }.getE();
            if (storage != null) {
                /* Map to workspace resource */
                IPath path = storage.getFullPath();
                if (path != null) {
                    URI uri = URIUtil.toURI(path);
                    IFile[] arr = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(uri);
                    if (arr != null && arr.length > 0) {
                        for (IFile resource : arr) {
                            if (resource.isAccessible()) {
                                storage = resource;
                                break;
                            }
                        }
                    }
                }
            }
            synchronized (map) {
                map.put(str, storage);
            }
            source_element = storage;
        }
        return source_element;
    }

    public void initializeParticipants() {
        addParticipants(new ISourceLookupParticipant[] { new TCFSourceLookupParticipant() });
    }
}
