package com.example.e2studio.agent.bridge;

import org.eclipse.ui.IStartup;

public final class AgentEarlyStartup implements IStartup {
    @Override
    public void earlyStartup() {
        BridgeServer.getInstance().startAsync();
    }
}
