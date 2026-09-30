package com.jeremy.shizukuai;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;

public class LiteRtBridge {
    public static Engine createEngine(String modelPath) {
        EngineConfig config = new EngineConfig(
                modelPath,
                new Backend.CPU(), // Instantiated backend class
                null,              // Secondary backend
                null,              // Ternary backend
                null,              // Max tokens
                null,              // TopK
                null               // Cache dir
        );
        return new Engine(config);
    }
}
