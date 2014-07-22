/*******************************************************************************
 * Copyright (c) 2014 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.tcf.internal.debug.ui.commands;

import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.commands.IDebugCommandRequest;
import org.eclipse.debug.core.commands.IDropToFrameHandler;
import org.eclipse.debug.core.commands.IEnabledStateRequest;
import org.eclipse.tcf.internal.debug.actions.TCFActionStepOut;
import org.eclipse.tcf.internal.debug.model.TCFContextState;
import org.eclipse.tcf.internal.debug.ui.Activator;
import org.eclipse.tcf.internal.debug.ui.model.TCFModel;
import org.eclipse.tcf.internal.debug.ui.model.TCFNode;
import org.eclipse.tcf.internal.debug.ui.model.TCFNodeExecContext;
import org.eclipse.tcf.internal.debug.ui.model.TCFNodeStackFrame;
import org.eclipse.tcf.internal.debug.ui.model.TCFRunnable;
import org.eclipse.tcf.protocol.IChannel;
import org.eclipse.tcf.services.IBreakpoints;
import org.eclipse.tcf.services.IRunControl;
import org.eclipse.tcf.services.IRunControl.RunControlContext;
import org.eclipse.tcf.services.IStackTrace.StackTraceContext;
import org.eclipse.tcf.util.TCFDataCache;

/**
 * Drop-to-frame command handler for TCF.
 */
public class DropToFrameCommand implements IDropToFrameHandler {

    private static class StepStateMachine extends TCFActionStepOut {

        private final IDebugCommandRequest monitor;
        private final Runnable done;
        private final TCFNodeExecContext node;
        private final TCFNodeStackFrame frame;

        StepStateMachine(TCFModel model, IDebugCommandRequest monitor,
                IRunControl.RunControlContext ctx, TCFNodeStackFrame frame, Runnable done) {
            super(model.getLaunch(), ctx, false);
            this.monitor = monitor;
            this.done = done;
            this.frame = frame;
            this.node = (TCFNodeExecContext)frame.getParent();
        }

        @Override
        protected TCFDataCache<TCFContextState> getContextState() {
            return node.getState();
        }

        @Override
        protected TCFDataCache<StackTraceContext> getStackFrame() {
            return frame.getStackTraceContext();
        }

        @Override
        protected int getStackFrameIndex() {
            return frame.getFrameNo();
        }

        @Override
        protected TCFDataCache<?> getStackTrace() {
            return node.getStackTrace();
        }

        protected void exit(Throwable error) {
            exit(error, "Drop To Frame");
        }

        @Override
        protected void exit(Throwable error, String reason) {
            if (exited) return;
            super.exit(error, reason);
            if (error != null && node.getChannel().getState() == IChannel.STATE_OPEN) {
                monitor.setStatus(new Status(IStatus.ERROR,
                        Activator.PLUGIN_ID, 0, "Cannot drop to frame: " + error.getLocalizedMessage(), error));
            }
            if (aborted) {
                monitor.setStatus(Status.CANCEL_STATUS);
            }
            done.run();
        }
    }

    private final TCFModel model;

    public DropToFrameCommand(TCFModel tcfModel) {
        model = tcfModel;
    }

    public void canExecute(final IEnabledStateRequest request) {
        new TCFRunnable(model, request) {
            public void run() {
                Object[] elements = request.getElements();
                if (elements.length != 1 || !(elements[0] instanceof TCFNodeStackFrame)) {
                    request.setEnabled(false);
                    done();
                    return;
                }
                TCFNodeStackFrame frameNode = (TCFNodeStackFrame) elements[0];
                TCFNodeExecContext exeNode = (TCFNodeExecContext) frameNode.getParent();
                if (!exeNode.getStackTrace().validate(this)) return;
                if (frameNode.getFrameNo() < 1) {
                    request.setEnabled(false);
                    done();
                    return;
                }
                TCFDataCache<IRunControl.RunControlContext> ctx_cache = exeNode.getRunContext();
                if (!ctx_cache.validate(this)) {
                    return;
                }
                IRunControl.RunControlContext ctx = ctx_cache.getData();
                if (!canStepOut(ctx)) {
                    request.setEnabled(false);
                    done();
                    return;
                }
                int action_cnt = model.getLaunch().getContextActionsCount(ctx.getID());
                if (action_cnt > 0 || !canStepOut(ctx)) {
                    request.setEnabled(false);
                    done();
                    return;
                }
                TCFDataCache<TCFContextState> state_cache = exeNode.getState();
                if (!state_cache.validate(this)) {
                    return;
                }
                TCFContextState state_data = state_cache.getData();
                request.setEnabled(state_data != null && state_data.is_suspended);
                done();
            }

            private boolean canStepOut(RunControlContext ctx) {
                if (ctx == null) return false;
                if (ctx.canResume(IRunControl.RM_STEP_OUT)) return true;
                if (!ctx.hasState()) return false;
                if (ctx.canResume(IRunControl.RM_RESUME) && model.getLaunch().getService(IBreakpoints.class) != null) return true;
                return false;
            }
        };
    }

    public boolean execute(final IDebugCommandRequest request) {
        new TCFRunnable(model, request) {
            public void run() {
                Object[] elements = request.getElements();
                if (elements.length != 1 || !(elements[0] instanceof TCFNodeStackFrame)) {
                    request.setStatus(Status.CANCEL_STATUS);
                    done();
                    return;
                }
                final TCFNodeStackFrame frameNode = (TCFNodeStackFrame) elements[0];
                TCFNodeExecContext exeNode = (TCFNodeExecContext) frameNode.getParent();
                if (!exeNode.getStackTrace().validate(this)) return;
                int frameNo = frameNode.getFrameNo();
                if (frameNo < 1) {
                    request.setStatus(Status.CANCEL_STATUS);
                    done();
                    return;
                }
                TCFDataCache<IRunControl.RunControlContext> ctx_cache = exeNode.getRunContext();
                if (!ctx_cache.validate(this)) return;
                TCFDataCache<TCFContextState> state_cache = exeNode.getState();
                if (!state_cache.validate(this)) return;
                TCFContextState state_data = state_cache.getData();
                if (state_data == null || !state_data.is_suspended) {
                    request.setStatus(Status.CANCEL_STATUS);
                    done();
                    return;
                }
                Map<String, TCFNode> stack = exeNode.getStackTrace().getData();
                for (TCFNode node : stack.values()) {
                    TCFNodeStackFrame frame_to_step_out = (TCFNodeStackFrame) node;
                    if (frame_to_step_out.getFrameNo() == frameNo - 1) {
                        new StepStateMachine(model, request, ctx_cache.getData(), frame_to_step_out, new Runnable() {
                            public void run() {
                                request.done();
                            }
                        });
                        return;
                    }
                }
                request.setStatus(Status.CANCEL_STATUS);
                done();
            }
        };
        return false;
    }
}
