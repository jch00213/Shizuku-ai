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
                new Backend.CPU(),
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

    public String generateCommand(Context context, String prompt) {
        ConversationConfig conversationConfig = new ConversationConfig();

        try (Conversation conversation = engine.createConversation(conversationConfig)) {
            String fullPrompt = "You are an Android execution agent with system tools.\n" +
                    "1. For shell execution, return ONLY the raw executable command.\n" +
                    "2. To send a text message, reply strictly with: send_sms:<phone_number>|<message>\n" +
                    "3. To read recent incoming text messages, reply strictly with: read_sms\n" +
                    "Do NOT use markdown code blocks, explanations, or leading quotes.\n" +
                    "User Request: " + prompt;

            Message responseMessage = conversation.sendMessage(fullPrompt);
            String responseText = extractMessageText(responseMessage);
            String cleaned = cleanOutput(responseText);

            // Action Router: Execute native SMS tools or return raw shell command
            return handleAgentOutput(context, cleaned);

        } catch (Exception e) {
            return "echo Error: " + e.getLocalizedMessage();
        }
    }

    private String handleAgentOutput(Context context, String actionText) {
        if (actionText == null || actionText.isEmpty()) {
            return "echo Output was empty.";
        }

        // 1. Intercept Native Send SMS
        if (actionText.toLowerCase().startsWith("send_sms:")) {
            String payload = actionText.substring("send_sms:".length()).trim();
            String[] parts = payload.split("\\|", 2);
            if (parts.length == 2) {
                String recipient = parts[0].trim();
                String message = parts[1].trim();
                String status = SmsTool.INSTANCE.sendSms(context, recipient, message);
                return "echo \"" + status + "\"";
            } else {
                return "echo \"Error: Invalid send_sms syntax emitted by agent.\"";
            }
        }

        // 2. Intercept Native Read SMS
        if (actionText.equalsIgnoreCase("read_sms")) {
            String inboxResult = SmsTool.INSTANCE.readRecentSms(context, 5);
            return "echo \"" + inboxResult.replace("\"", "\\\"") + "\"";
        }

        // 3. Fallback: Standard raw shell command
        return actionText;
    }

    private String extractMessageText(Message message) {
        if (message == null) return "";
        try {
            return message.toString();
        } catch (Exception e) {
            return "";
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
