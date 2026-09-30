package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Conversation;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;
    private final Conversation conversation;

    private LiteRtEngine(Engine engine, Conversation conversation) {
        this.engine = engine;
        this.conversation = conversation;
    }

    public static LiteRtEngine create(Context context, String modelPath) {
        EngineConfig config = EngineConfig.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.2f)
                .build();

        Engine engine = Engine.create(context, config);
        Conversation conversation = engine.createConversation();
        return new LiteRtEngine(engine, conversation);
    }

    public String generateCommand(String prompt) {
        try {
            // Send prompt to LiteRT-LM conversation
            String systemPrompt = "You are an Android shell command agent. " +
                    "Convert the user intent into a single raw shell command. " +
                    "Return ONLY the executable command, no markdown, no explanation.\n" +
                    "User Request: " + prompt;

            String response = conversation.sendMessage(systemPrompt);
            return cleanOutput(response);
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
        if (conversation != null) {
            conversation.close();
        }
        if (engine != null) {
            engine.close();
        }
    }
}
