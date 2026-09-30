package com.jeremy.shizukuai;

import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;

public class LiteRtBridge {
    public static Engine createEngine(String modelPath) {
        EngineConfig config = new EngineConfig(modelPath);
        return new Engine(config);
    }
}
