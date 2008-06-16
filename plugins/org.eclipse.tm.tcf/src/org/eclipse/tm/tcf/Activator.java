/*******************************************************************************
 * Copyright (c) 2007, 2008 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html 
 *  
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tm.tcf;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.tm.tcf.protocol.Protocol;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;


/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends Plugin {

    public static final String PLUGIN_ID = "org.eclipse.tm.tcf";

    private static Activator plugin;
    private static final EventQueue queue = new EventQueue();
    private static final BundleListener bundle_listener = new BundleListener() {
        private boolean started = false;
        public void bundleChanged(BundleEvent event) {
            if (plugin != null && !started && event.getBundle() == plugin.getBundle() &&
                    plugin.getBundle().getState() == Bundle.ACTIVE) {
                queue.start();
                started = true;
            }
        }
    };

    public Activator() {
        plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        Protocol.setEventQueue(queue);
        Protocol.invokeLater(new Runnable() {
            public void run() {
                runTCFStartup();
            }
        });
        context.addBundleListener(bundle_listener);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        context.removeBundleListener(bundle_listener);
        queue.shutdown();
        plugin = null;
        super.stop(context);
    }

    @SuppressWarnings("unchecked")
    private void runTCFStartup() {
        try {
            IExtensionPoint point = Platform.getExtensionRegistry().getExtensionPoint(PLUGIN_ID, "startup");
            IExtension[] extensions = point.getExtensions();
            for (int i = 0; i < extensions.length; i++) {
                try {
                    Bundle bundle = Platform.getBundle(extensions[i].getNamespaceIdentifier());
                    bundle.start();
                    IConfigurationElement[] e = extensions[i].getConfigurationElements();
                    for (int j = 0; j < e.length; j++) {
                        String nm = e[j].getName();
                        if (nm.equals("class")) { //$NON-NLS-1$
                            Class c = bundle.loadClass(e[j].getAttribute("name")); //$NON-NLS-1$
                            Class.forName(c.getName(), true, c.getClassLoader());
                        }
                    }
                }
                catch (Throwable x) {
                    log("TCF startup error", x);
                }
            }
        }
        catch (Exception x) {
            log("TCF startup error", x);
        }
    }

    /**
     * Returns the shared instance
     * 
     * @return the shared instance
     */
    public static Activator getDefault() {
        return plugin;
    }

    /**
     * Send error message into Eclipse log.
     * @param msg - error message test
     * @param err - exception
     */
    public static void log(String msg, Throwable err) {
        if (plugin == null || plugin.getLog() == null) {
            err.printStackTrace();
        }
        else {
            plugin.getLog().log(new Status(IStatus.ERROR,
                    plugin.getBundle().getSymbolicName(), IStatus.OK, msg, err));
        }
    }  
}
