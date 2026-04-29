package com.example.e2studio.agent.bridge.index;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs a stored launch configuration by name (or full id). Handles run, debug,
 * and Renesas Flash-Programmer launch types — they're all just launch configs
 * with different mode strings, so a single entry point covers them.
 *
 * <p>Mutating (it actually starts a process), so the Router puts this behind
 * the danger gate.
 */
public final class LaunchRunner {

    public Map<String, Object> run(String configName, String mode) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (configName == null || configName.isEmpty()) {
            out.put("ok", false); out.put("error", "configName required"); return out;
        }
        String runMode = (mode == null || mode.isEmpty()) ? ILaunchManager.RUN_MODE : mode;

        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        try {
            ILaunchConfiguration target = null;
            for (ILaunchConfiguration c : mgr.getLaunchConfigurations()) {
                if (configName.equals(c.getName())) { target = c; break; }
            }
            if (target == null) {
                out.put("ok", false);
                out.put("error", "launch configuration not found: " + configName);
                return out;
            }
            if (!target.supportsMode(runMode)) {
                out.put("ok", false);
                out.put("error", "config does not support mode: " + runMode);
                out.put("supportedModes", target.getModes());
                return out;
            }
            ILaunch launch = target.launch(runMode, new NullProgressMonitor());
            out.put("ok", true);
            out.put("configName", target.getName());
            out.put("type", target.getType().getIdentifier());
            out.put("mode", launch.getLaunchMode());
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return out;
        }
    }
}
