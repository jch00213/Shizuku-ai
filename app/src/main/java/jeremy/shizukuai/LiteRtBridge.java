package com.jeremy.shizukuai;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;

public class LiteRtBridge {
    public static Engine createEngine(String modelPath) {
        EngineConfig config = new EngineConfig(
                modelPath,
                Backend.CPU, // Primary backend
                null,        // Secondary backend
                null,        // Ternary backend
                null,        // Max tokens (null uses default)
                null,        // TopK (null uses default)
                null         // Cache dir
        );
        return new Engine(config);
    }
}
