package com.example.e2studio.agent.bridge.index;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IRegister;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IStep;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IMemoryBlockExtension;
import org.eclipse.debug.core.model.IMemoryBlockRetrieval;
import org.eclipse.debug.core.model.IThread;
import org.osgi.framework.Bundle;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutating debug operations: step / resume / suspend / terminate / restart,
 * memory write, register write. Always called only after DangerGate check
 * (the Router enforces this — this class itself is gate-agnostic).
 *
 * <p>Each method finds the "active" thread/target on a best-effort basis:
 * if there's exactly one suspended thread it's the target; if multiple,
 * we pick the first. For commands that require a running target (like
 * {@code suspend}) we instead pick the first non-terminated, non-suspended.
 */
public final class DebugController {

    public Map<String, Object> step(String kind) {
        Map<String, Object> out = new LinkedHashMap<>();
        IThread th = firstSuspendedThread();
        if (th == null) {
            out.put("ok", false);
            out.put("error", "no suspended thread");
            return out;
        }
        if (!(th instanceof IStep) || !((IStep) th).canStepInto()) {
            // IStep on IThread is the standard contract. If unsupported, surface clearly.
            out.put("ok", false);
            out.put("error", "thread does not support stepping");
            return out;
        }
        IStep s = (IStep) th;
        try {
            if ("into".equalsIgnoreCase(kind)) {
                if (!s.canStepInto()) { out.put("ok", false); out.put("error", "cannot stepInto"); return out; }
                s.stepInto();
            } else if ("over".equalsIgnoreCase(kind) || kind == null || kind.isEmpty()) {
                if (!s.canStepOver()) { out.put("ok", false); out.put("error", "cannot stepOver"); return out; }
                s.stepOver();
            } else if ("return".equalsIgnoreCase(kind) || "out".equalsIgnoreCase(kind)) {
                if (!s.canStepReturn()) { out.put("ok", false); out.put("error", "cannot stepReturn"); return out; }
                s.stepReturn();
            } else {
                out.put("ok", false);
                out.put("error", "unknown step kind: " + kind + " (use into|over|return)");
                return out;
            }
            out.put("ok", true);
            out.put("kind", kind == null || kind.isEmpty() ? "over" : kind);
            try { out.put("threadName", th.getName()); } catch (DebugException ignored) {}
            return out;
        } catch (DebugException e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
            return out;
        }
    }

    public Map<String, Object> resume() {
        Map<String, Object> out = new LinkedHashMap<>();
        IDebugTarget t = firstSuspendedTarget();
        if (t == null) { out.put("ok", false); out.put("error", "no suspended target"); return out; }
        try {
            if (!t.canResume()) { out.put("ok", false); out.put("error", "target cannot resume"); return out; }
            t.resume();
            out.put("ok", true);
            try { out.put("targetName", t.getName()); } catch (DebugException ignored) {}
            return out;
        } catch (DebugException e) {
            out.put("ok", false); out.put("error", e.getMessage()); return out;
        }
    }

    public Map<String, Object> suspend() {
        Map<String, Object> out = new LinkedHashMap<>();
        IDebugTarget t = firstRunningTarget();
        if (t == null) { out.put("ok", false); out.put("error", "no running target"); return out; }
        try {
            if (!t.canSuspend()) { out.put("ok", false); out.put("error", "target cannot suspend"); return out; }
            t.suspend();
            out.put("ok", true);
            try { out.put("targetName", t.getName()); } catch (DebugException ignored) {}
            return out;
        } catch (DebugException e) {
            out.put("ok", false); out.put("error", e.getMessage()); return out;
        }
    }

    public Map<String, Object> terminate() {
        Map<String, Object> out = new LinkedHashMap<>();
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        int killed = 0;
        for (ILaunch launch : mgr.getLaunches()) {
            if (launch.isTerminated()) continue;
            try {
                if (launch.canTerminate()) { launch.terminate(); killed++; }
            } catch (DebugException ignored) {}
        }
        out.put("ok", killed > 0);
        out.put("terminatedLaunches", killed);
        if (killed == 0) out.put("error", "no live launch to terminate");
        return out;
    }

    public Map<String, Object> restart() {
        Map<String, Object> out = new LinkedHashMap<>();
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunch toRestart = null;
        for (ILaunch launch : mgr.getLaunches()) {
            if (!launch.isTerminated()) { toRestart = launch; break; }
        }
        if (toRestart == null) { out.put("ok", false); out.put("error", "no live launch to restart"); return out; }
        try {
            String mode = toRestart.getLaunchMode();
            org.eclipse.debug.core.ILaunchConfiguration config = toRestart.getLaunchConfiguration();
            if (config == null) { out.put("ok", false); out.put("error", "launch has no configuration"); return out; }
            try {
                if (toRestart.canTerminate()) toRestart.terminate();
            } catch (DebugException ignored) {}
            ILaunch fresh = config.launch(mode, new org.eclipse.core.runtime.NullProgressMonitor());
            out.put("ok", true);
            out.put("configName", config.getName());
            out.put("mode", fresh.getLaunchMode());
            return out;
        } catch (Exception e) {
            out.put("ok", false); out.put("error", e.getMessage()); return out;
        }
    }

    /** Write {@code hex} bytes (no 0x prefix, even length) starting at {@code addrStr}. */
    public Map<String, Object> writeMemory(String addrStr, String hex) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (addrStr == null || hex == null) {
            out.put("ok", false); out.put("error", "addr and hex required"); return out;
        }
        BigInteger addr;
        try {
            String s = addrStr.trim();
            if (s.startsWith("0x") || s.startsWith("0X")) addr = new BigInteger(s.substring(2), 16);
            else if (s.matches("^[0-9a-fA-F]+$") && s.length() >= 3) addr = new BigInteger(s, 16);
            else addr = new BigInteger(s);
        } catch (Exception e) {
            out.put("ok", false); out.put("error", "invalid addr: " + addrStr); return out;
        }
        String h = hex.trim();
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        h = h.replaceAll("[\\s_]", "");
        if (h.length() % 2 != 0) {
            out.put("ok", false); out.put("error", "hex payload must have even length"); return out;
        }
        if (h.length() > 8192) { // 4096 bytes
            out.put("ok", false); out.put("error", "payload exceeds 4096 byte cap"); return out;
        }
        byte[] payload = new byte[h.length() / 2];
        for (int i = 0; i < payload.length; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) { out.put("ok", false); out.put("error", "invalid hex char at " + (i * 2)); return out; }
            payload[i] = (byte) ((hi << 4) | lo);
        }

        IDebugTarget t = firstSuspendedTarget();
        if (t == null) { out.put("ok", false); out.put("error", "no suspended target"); return out; }
        try {
            IMemoryBlockRetrieval ret = t.getAdapter(IMemoryBlockRetrieval.class);
            if (ret == null) { out.put("ok", false); out.put("error", "target lacks IMemoryBlockRetrieval"); return out; }
            IMemoryBlock mb;
            if (ret instanceof org.eclipse.debug.core.model.IMemoryBlockRetrievalExtension) {
                mb = ((org.eclipse.debug.core.model.IMemoryBlockRetrievalExtension) ret)
                        .getExtendedMemoryBlock("0x" + addr.toString(16), t);
            } else {
                mb = ret.getMemoryBlock(addr.longValueExact(), payload.length);
            }
            if (mb == null) { out.put("ok", false); out.put("error", "could not obtain memory block"); return out; }
            if (mb instanceof IMemoryBlockExtension) {
                ((IMemoryBlockExtension) mb).setValue(BigInteger.ZERO, payload);
            } else {
                mb.setValue(0L, payload);
            }
            out.put("ok", true);
            out.put("addr", "0x" + addr.toString(16));
            out.put("byteCount", payload.length);
            return out;
        } catch (Throwable ex) {
            out.put("ok", false);
            out.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return out;
        }
    }

    /** Set a register on the first suspended thread's top frame. */
    public Map<String, Object> writeRegister(String groupName, String regName, String value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (regName == null || regName.isEmpty() || value == null) {
            out.put("ok", false); out.put("error", "regName and value required"); return out;
        }
        IThread th = firstSuspendedThread();
        if (th == null) { out.put("ok", false); out.put("error", "no suspended thread"); return out; }
        try {
            IStackFrame[] fs = th.getStackFrames();
            if (fs.length == 0) { out.put("ok", false); out.put("error", "no stack frames"); return out; }
            IStackFrame top = fs[0];
            if (!top.hasRegisterGroups()) {
                out.put("ok", false); out.put("error", "frame has no register groups"); return out;
            }
            for (IRegisterGroup g : top.getRegisterGroups()) {
                if (groupName != null && !groupName.isEmpty()
                        && !groupName.equalsIgnoreCase(g.getName())) continue;
                for (IRegister r : g.getRegisters()) {
                    if (regName.equalsIgnoreCase(r.getName())) {
                        if (!r.supportsValueModification()) {
                            out.put("ok", false);
                            out.put("error", "register does not support modification");
                            return out;
                        }
                        if (!r.verifyValue(value)) {
                            out.put("ok", false);
                            out.put("error", "value not accepted by register: " + value);
                            return out;
                        }
                        r.setValue(value);
                        out.put("ok", true);
                        out.put("group", g.getName());
                        out.put("register", r.getName());
                        out.put("newValue", value);
                        return out;
                    }
                }
            }
            out.put("ok", false);
            out.put("error", "register not found: " + (groupName == null ? "" : groupName + "/") + regName);
            return out;
        } catch (DebugException e) {
            out.put("ok", false);
            out.put("error", e.getMessage());
            return out;
        }
    }

    /**
     * Resume execution and stop at the given source line. Implemented via
     * {@code org.eclipse.cdt.debug.core.model.IRunToLine} adapter on the top
     * stack frame. Reflection so we don't compile-time depend on CDT.
     */
    public Map<String, Object> runToLine(String fileSpec, int line, boolean skipBreakpoints) {
        return adapterCall(fileSpec, line,
                "org.eclipse.cdt.debug.core.model.IRunToLine",
                "runToLine",
                new Class<?>[]{ String.class, int.class, boolean.class },
                skipBreakpoints);
    }

    /**
     * Move execution to a given source line without running there (PC jump).
     * CDT exposes this as {@code IJumpToLine}.
     */
    public Map<String, Object> jumpToLine(String fileSpec, int line) {
        return adapterCall(fileSpec, line,
                "org.eclipse.cdt.debug.core.model.IJumpToLine",
                "jumpToLine",
                new Class<?>[]{ String.class, int.class },
                null);
    }

    private Map<String, Object> adapterCall(String fileSpec, int line, String adapterClassName,
                                            String method, Class<?>[] paramTypes, Object extra) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (fileSpec == null || fileSpec.isEmpty() || line <= 0) {
            out.put("ok", false);
            out.put("error", "file and positive line required");
            return out;
        }
        IThread th = firstSuspendedThread();
        if (th == null) {
            out.put("ok", false);
            out.put("error", "no suspended thread");
            return out;
        }
        Class<?> adapterClass;
        try {
            Bundle cdt = Platform.getBundle("org.eclipse.cdt.debug.core");
            if (cdt == null) {
                out.put("ok", false);
                out.put("error", "CDT bundle not present");
                return out;
            }
            adapterClass = cdt.loadClass(adapterClassName);
        } catch (ClassNotFoundException e) {
            out.put("ok", false);
            out.put("error", "CDT adapter class missing: " + adapterClassName);
            return out;
        }
        try {
            IStackFrame[] fs = th.getStackFrames();
            if (fs.length == 0) { out.put("ok", false); out.put("error", "no frames"); return out; }
            IStackFrame top = fs[0];
            Object adapter = top.getAdapter(adapterClass);
            if (adapter == null) {
                // Try the thread itself; some CDT models adapt at thread level.
                adapter = th.getAdapter(adapterClass);
            }
            if (adapter == null) {
                out.put("ok", false);
                out.put("error", "frame/thread does not adapt to " + adapterClass.getSimpleName());
                return out;
            }
            // Resolve a workspace IFile for the file to use as source handle.
            IResource res = resolveResource(fileSpec);
            String sourceHandle;
            if (res != null && res.getLocation() != null) sourceHandle = res.getLocation().toOSString();
            else sourceHandle = fileSpec;

            Method m = adapterClass.getMethod(method, paramTypes);
            Object[] args;
            if (paramTypes.length == 3) args = new Object[]{ sourceHandle, line, extra };
            else args = new Object[]{ sourceHandle, line };
            m.invoke(adapter, args);
            out.put("ok", true);
            out.put("sourceHandle", sourceHandle);
            out.put("line", line);
            return out;
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            out.put("ok", false);
            out.put("error", cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return out;
        }
    }

    private IResource resolveResource(String fileSpec) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        if (fileSpec.startsWith("/")) {
            IFile file = root.getFile(new Path(fileSpec));
            if (file != null && file.exists()) return file;
        }
        try {
            java.io.File fs = new java.io.File(fileSpec);
            if (fs.exists()) {
                IFile[] files = root.findFilesForLocationURI(fs.toURI());
                if (files.length > 0) return files[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ───────────────── helpers ─────────────────

    private IThread firstSuspendedThread() {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunch launch : mgr.getLaunches()) {
            if (launch.isTerminated()) continue;
            for (IDebugTarget t : launch.getDebugTargets()) {
                if (!t.isSuspended()) continue;
                try {
                    for (IThread th : t.getThreads()) {
                        if (th.isSuspended()) return th;
                    }
                } catch (DebugException ignored) {}
            }
        }
        return null;
    }

    private IDebugTarget firstSuspendedTarget() {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunch launch : mgr.getLaunches()) {
            if (launch.isTerminated()) continue;
            for (IDebugTarget t : launch.getDebugTargets()) {
                if (t.isSuspended() && !t.isTerminated()) return t;
            }
        }
        return null;
    }

    private IDebugTarget firstRunningTarget() {
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunch launch : mgr.getLaunches()) {
            if (launch.isTerminated()) continue;
            for (IDebugTarget t : launch.getDebugTargets()) {
                if (!t.isTerminated() && !t.isSuspended()) return t;
            }
        }
        return null;
    }
}
