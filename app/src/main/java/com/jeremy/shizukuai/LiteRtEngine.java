package com.jeremy.shizukuai;

import android.content.Context;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlin.coroutines.Continuation;

public class LiteRtEngine implements AutoCloseable {

    private final Engine engine;
    private final Conversation conversation;

    private LiteRtEngine(Engine engine, Conversation conversation) {
        this.engine = engine;
        this.conversation = conversation;
    }

    public static Object create(Context context, String modelPath, Continuation<? super LiteRtEngine> completion) {
        return BuildersKt.withContext(Dispatchers.getIO(), (scope, continuation) -> {
            EngineConfig config = new EngineConfig(
                modelPath,
                512,  // maxTokens
                0.2f  // temperature
            );

            Engine engine = new Engine(config);
            engine.initialize();
            Conversation conversation = engine.createConversation();

            return new LiteRtEngine(engine, conversation);
        }, completion);
    }

    public Object generateCommand(String userPrompt, Continuation<? super String> completion) {
        return BuildersKt.withContext(Dispatchers.getDefault(), (scope, continuation) -> {
            String systemContext = "You are ShizukuAI, an assistant running on Android.\n" +
                "Generate a raw bash/adb shell command to answer the request.\n" +
                "Wrap the command inside a ```bash ... ``` code block.\n" +
                "Do not prepend 'adb shell'.";

            String fullPrompt = systemContext + "\n\nUser: " + userPrompt + "\nAssistant:";

            try {
                String rawResponse = conversation.sendMessage(fullPrompt);
                String extracted = extractCommand(rawResponse);
                return extracted != null ? extracted : rawResponse;
            } catch (Exception e) {
                return "Error executing LiteRT inference: " + e.getLocalizedMessage();
            }
        }, completion);
    }

    private static String extractCommand(String text) {
        Pattern pattern = Pattern.compile("```(?:bash|sh)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
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
