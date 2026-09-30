package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;

    private LiteRtEngine(Engine engine) {
        this.engine = engine;
    }

    public static LiteRtEngine create(Context context, String modelPath) {
        EngineConfig config = new EngineConfig(modelPath);
        Engine engine = new Engine(config);
        engine.initialize();
        return new LiteRtEngine(engine);
    }

    public String generateCommand(String prompt) {
        try (Conversation conversation = engine.createConversation()) {
            String fullPrompt = "You are an Android shell command agent. " +
                    "Convert the user intent into a single raw shell command. " +
                    "Return ONLY the executable command, no markdown, no explanation.\n" +
                    "User Request: " + prompt;

            String responseText = conversation.sendMessage(fullPrompt);
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
