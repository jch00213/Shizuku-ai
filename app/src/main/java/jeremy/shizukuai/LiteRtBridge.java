package com.jeremy.shizukuai;

import com.google.ai.edge.litertlm.Engine;

public class LiteRtBridge {
    // Wrap engine initialization or call sites here
    public static Engine createEngine(String modelPath) {
        return new Engine(modelPath);
    }
}
