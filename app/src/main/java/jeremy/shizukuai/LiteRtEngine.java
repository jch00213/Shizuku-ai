package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Engine;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;

    private LiteRtEngine(Engine engine) {
        this.engine = engine;
    }

    public static LiteRtEngine create(Context context, String modelPath) {
        // In 0.17.1, Engine handles initialization via builder/model path directly
        Engine engine = Engine.builder(context)
                .setModelPath(modelPath)
                .build();

        engine.initialize();
        return new LiteRtEngine(engine);
    }

    public String generateCommand(String prompt) {
        try {
            String fullPrompt = "You are an Android shell command agent. " +
                    "Convert the user intent into a single raw shell command. " +
                    "Return ONLY the executable command, no markdown, no explanation.\n" +
                    "User Request: " + prompt;

            // In 0.17.1, generate / sendMessage is called directly on Engine or its Session
            String responseText = engine.generate(fullPrompt);

            return cleanOutput(responseText);
        } catch (Exception e) {
            return "echo Error: " + e.getLocalizedMessage();
        }
    }

    private String cleanOutput(String rawResponse) {
        if (rawResponse == null) return "";
        return rawResponse.trim()
                .replaceAll("^```[a-zA-Z]*", "")
                .replaceAll("```$", "")
                .trim();
    }

    @Override
    public void close() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception ignored) {}
        }
    }
}
