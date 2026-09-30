package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;
    private final Conversation conversation;

    private LiteRtEngine(Engine engine, Conversation conversation) {
        this.engine = engine;
        this.conversation = conversation;
    }

    public static LiteRtEngine create(Context context, String modelPath) {
        // 1. Create EngineConfig for LiteRT-LM 0.17.1
        EngineConfig config = new EngineConfig(
                modelPath,
                new Backend.CPU(),               // Primary backend (CPU / GPU)
                context.getCacheDir().getPath()  // Cache directory for accelerated reloading
        );

        // 2. Instantiate and initialize Engine
        Engine engine = new Engine(config);
        engine.initialize();

        // 3. Configure conversation sampling parameters
        SamplerConfig samplerConfig = new SamplerConfig(
                /* topK */ 40,
                /* topP */ 0.95f,
                /* temperature */ 0.2f
        );

        ConversationConfig conversationConfig = new ConversationConfig(
                /* systemInstruction */ null,
                /* initialMessages */ null,
                samplerConfig
        );

        Conversation conversation = engine.createConversation(conversationConfig);
        return new LiteRtEngine(engine, conversation);
    }

    public String generateCommand(String prompt) {
        try {
            String fullPrompt = "You are an Android shell command agent. " +
                    "Convert the user intent into a single raw shell command. " +
                    "Return ONLY the executable command, no markdown, no explanation.\n" +
                    "User Request: " + prompt;

            // In 0.17.1, sendMessage returns a Message object
            Message responseMessage = conversation.sendMessage(fullPrompt);
            String responseText = responseMessage != null ? responseMessage.toString() : "";
            
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
        if (conversation != null) {
            try {
                conversation.close();
            } catch (Exception ignored) {}
        }
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception ignored) {}
        }
    }
}
