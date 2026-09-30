package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;

    private LiteRtEngine(Engine engine) {
        this.engine = engine;
    }

    public static LiteRtEngine create(Context context, String modelPath) {
        EngineConfig config = new EngineConfig(
                modelPath,
                Backend.CPU,
                null,
                null,
                null,
                null,
                null
        );
        Engine engine = new Engine(config);
        engine.initialize();
        return new LiteRtEngine(engine);
    }

    public String generateCommand(String prompt) {
        // Construct basic conversation config
        ConversationConfig conversationConfig = new ConversationConfig();

        try (Conversation conversation = engine.createConversation(conversationConfig)) {
            String fullPrompt = "You are an Android shell command agent. " +
                    "Convert the user intent into a single raw shell command. " +
                    "Return ONLY the executable command, no markdown, no explanation.\n" +
                    "User Request: " + prompt;

            Message responseMessage = conversation.sendMessage(fullPrompt);
            String responseText = (responseMessage != null) ? responseMessage.getText() : "";

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
