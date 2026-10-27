package com.soulspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Aria: An empathetic, compassionate AI conversational companion on SoulSpace.
 * Powered by Google Gemini with multi-turn context, multilingual support (English, Hindi, Hinglish),
 * grounding safety guardrails, and reliable fallbacks.
 */
@Service
public class AriaService {

    private static final Logger log = LoggerFactory.getLogger(AriaService.class);

    public record ChatTurn(String role, String content) {}

    private static final String CRISIS_MSG = "I'm really glad you told me, and I'm taking it seriously. What you're carrying sounds "
            + "very heavy, and your safety matters most right now. Please reach out to someone who can be with you in this:\n\n"
            + "• Tele-MANAS: 14416 or KIRAN: 1800-599-0019 (India, 24/7, toll-free)\n"
            + "• 988 Suicide & Crisis Lifeline (US/Canada, 24/7)\n"
            + "• Crisis Text Line: Text HOME to 741741\n\n"
            + "If you might act on these thoughts soon, please call emergency services or go to the nearest emergency room. "
            + "I am here with you right now while you connect with help.";

    private static final String SYSTEM_INSTRUCTION = """
            You are Aria, a gentle, warm, and attentive conversational companion on SoulSpace, an anonymous mental wellness sanctuary.
            Your core purpose:
            1. Listen with deep empathy, calm presence, and zero judgment.
            2. Language matching: If the user talks in Hindi or Hinglish, understand them deeply and respond in gentle, comforting Hindi/Hinglish. If in English, respond in English.
            3. Validate and gently normalize the user's emotions (e.g. stress, burnout, exam anxiety, loneliness, sadness, anger).
            4. Offer one small, grounded perspective, micro-step, or breathing suggestion when fitting.
            5. Ask at most ONE gentle, open-ended question to help them reflect without feeling pressured.
            6. Keep your tone soft, unhurried, thoughtful, and human. Avoid robotic lists or corporate tone.
            7. Keep responses concise (around 60 to 120 words) so you never overwhelm the user.
            8. You are a compassionate AI companion, not a prescribing psychiatrist. Never prescribe medications or diagnose diseases.
            9. If the user expresses active intent of self-harm or suicide, prioritize their safety with tender compassion and direct them to emergency hotlines.
            """;

    private final String apiKey;
    private final String primaryModel;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public AriaService(@Value("${gemini.api.key:}") String apiKey,
                       @Value("${gemini.model:gemini-3-flash-preview}") String primaryModel,
                       ObjectMapper mapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.primaryModel = (primaryModel != null && !primaryModel.isBlank()) ? primaryModel.trim() : "gemini-3-flash-preview";
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Map<String, Object> reply(String message, int turn, List<ChatTurn> history) {
        String clean = message == null ? "" : message.trim();
        if (Safety.isCrisis(clean)) {
            return Map.of("reply", CRISIS_MSG, "crisis", true);
        }

        // Try Gemini API directly for EVERY message (no static canned shortcuts)
        if (!apiKey.isEmpty()) {
            String aiResponse = callGemini(clean, history);
            if (aiResponse != null && !aiResponse.isBlank()) {
                boolean crisisFlag = Safety.isCrisis(aiResponse);
                return Map.of("reply", aiResponse.trim(), "crisis", crisisFlag);
            }
        }

        // Emergency fallback only if Gemini is unreachable
        return ruleBasedReply(clean, turn);
    }

    public Map<String, Object> reply(String message, int turn) {
        return reply(message, turn, Collections.emptyList());
    }

    private String callGemini(String message, List<ChatTurn> history) {
        List<String> modelsToTry = List.of(primaryModel, "gemini-3.8-flash", "gemini-flash-latest");

        for (String model : modelsToTry) {
            try {
                log.info("Aria sending request to Gemini model: {}", model);
                String response = requestGemini(model, message, history);
                if (response != null && !response.isBlank()) {
                    log.info("Aria received successful response from model: {}", model);
                    return response;
                }
            } catch (Exception e) {
                log.warn("Gemini call failed for model {}: {}", model, e.getMessage());
            }
        }
        return null;
    }

    private String requestGemini(String model, String message, List<ChatTurn> history) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        ObjectNode root = mapper.createObjectNode();

        // System instruction
        ObjectNode systemInstruction = root.putObject("systemInstruction");
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", SYSTEM_INSTRUCTION);

        // Contents
        ArrayNode contents = root.putArray("contents");

        // Previous turns (limit to last 6 turns for context and speed)
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                ChatTurn t = history.get(i);
                if (t != null && t.content() != null && !t.content().isBlank()) {
                    // If the last history turn is identical to the current user message, don't duplicate it
                    if (i == history.size() - 1 && t.content().trim().equals(message) && !"model".equalsIgnoreCase(t.role()) && !"assistant".equalsIgnoreCase(t.role())) {
                        continue;
                    }
                    String role = "model".equalsIgnoreCase(t.role()) || "assistant".equalsIgnoreCase(t.role()) ? "model" : "user";
                    ObjectNode turnNode = contents.addObject();
                    turnNode.put("role", role);
                    turnNode.putArray("parts").addObject().put("text", t.content().trim());
                }
            }
        }

        // Current turn
        ObjectNode currentTurn = contents.addObject();
        currentTurn.put("role", "user");
        currentTurn.putArray("parts").addObject().put("text", message);

        // Generation config (calm, warm temperature)
        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.7);
        genConfig.put("maxOutputTokens", 400);

        String jsonPayload = mapper.writeValueAsString(root);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 200) {
            JsonNode tree = mapper.readTree(resp.body());
            JsonNode candidates = tree.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
        } else {
            log.warn("Gemini API error status {}: {}", resp.statusCode(), resp.body());
        }

        return null;
    }

    /* Fallback psychological first aid logic used only if offline/API failure */
    private record Topic(List<String> triggers, List<String> openers, List<String> tips, List<String> questions) {}

    private final List<Topic> topics = List.of(
            new Topic(List.of("anxious", "anxiety", "panic", "worried", "nervous", "overthink", "scared", "afraid"),
                    List.of("That sounds exhausting. When the mind keeps running ahead, the body often feels it too.",
                            "Anxiety can make everything feel urgent at once. You're not weak for feeling this way.",
                            "Thank you for putting that into words. Racing thoughts are hard to sit with."),
                    List.of("If you like, try one slow breath out that is longer than the breath in, three times. The breathing orb below can pace it.",
                            "Try naming five things you can see around you right now. It gently pulls attention back to the present.",
                            "Plant both feet on the floor and notice the pressure under them for a few seconds."),
                    List.of("What is the thought that keeps coming back the loudest?",
                            "When did you first notice this wave today?",
                            "What would feel even slightly lighter right now?")),
            new Topic(List.of("sleep", "insomnia", "awake", "can't sleep", "cant sleep", "tired"),
                    List.of("Being awake while everything is quiet can make feelings louder. I'm glad you're not sitting with it alone.",
                            "Poor sleep makes everything feel heavier, and that's real.",
                            "Late nights can turn small worries into big ones. That's very human."),
                    List.of("Try dimming screens and letting your shoulders drop on each exhale. The ambient sound toggle up top can soften the room.",
                            "Writing the worries down on paper can tell your mind it doesn't need to hold them till morning."),
                    List.of("Is it your thoughts that are keeping you up, or your body?",
                            "What's been on your mind most tonight?")),
            new Topic(List.of("lonely", "alone", "no friends", "isolated", "nobody", "no one"),
                    List.of("Feeling alone hurts, and it's okay to say so out loud.",
                            "Loneliness is a real ache, not a flaw in you. I'm glad you reached out here.",
                            "Thank you for trusting me with that. Wanting connection is deeply human."),
                    List.of("If you want a human voice tonight, the venting lounge can pair you with another anonymous person.",
                            "Sending one small message to someone you used to feel safe with can start to loosen the knot."),
                    List.of("When did you last feel truly connected to someone?",
                            "What would a good conversation feel like for you right now?")),
            new Topic(List.of("sad", "depress", "cry", "empty", "hopeless", "numb", "down"),
                    List.of("I'm sorry it feels this heavy. Sadness deserves room, not judgement.",
                            "That sounds really painful. You don't have to make it sound smaller for me.",
                            "Thank you for sharing something so tender. I'm listening."),
                    List.of("Be as kind to yourself as you'd be to a friend who told you this. Even a warm drink and a few minutes of quiet count.",
                            "If you can, step into daylight or fresh air for a couple of minutes. It helps a little more often than we expect."),
                    List.of("How long has it felt like this?",
                            "Is there a moment today that felt even slightly okay?")),
            new Topic(List.of("angry", "furious", "mad", "frustrated", "irritat", "rage", "hate"),
                    List.of("Anger often guards something underneath, like hurt or feeling unheard. It makes sense you're feeling it.",
                            "That sounds really frustrating. You're allowed to be upset."),
                    List.of("Your body may need to release some of that charge first: unclench your jaw and fists and breathe out slowly.",
                            "A short brisk walk, or writing everything unfiltered and then deleting it, can take the edge off."),
                    List.of("What happened that set this off?",
                            "What do you wish the other person understood?")),
            new Topic(List.of("exam", "study", "placement", "job", "work", "deadline", "pressure", "college", "career"),
                    List.of("That's a lot of pressure to carry, and it's easy to feel behind even when you're trying hard.",
                            "It sounds like you're being tough on yourself about this. That kind of stress is common and still really draining."),
                    List.of("Try shrinking the next step to something doable in ten minutes. Tiny starts cut through the overwhelm.",
                            "Write down only what must happen today. Everything else can wait on the list."),
                    List.of("What part of this feels heaviest right now?",
                            "Whose expectations are you carrying here, yours or someone else's?")));

    private static final List<String> GENERIC = List.of(
            "Thank you for sharing that with me. I'm here and I'm listening.",
            "I hear you. That sounds like a lot to hold.",
            "I'm glad you said it out loud. Take as much space as you need.");
    private static final List<String> GENERIC_Q = List.of(
            "Can you tell me a little more about what it's been like?",
            "What feels the heaviest about this right now?",
            "What do you most need from this moment?");

    private Map<String, Object> ruleBasedReply(String clean, int turn) {
        String m = clean.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        int t = Math.max(0, turn);

        StringBuilder sb = new StringBuilder();
        Topic hit = null;
        for (Topic tp : topics) {
            for (String trig : tp.triggers()) {
                if (m.contains(trig)) { hit = tp; break; }
            }
            if (hit != null) break;
        }

        if (hit != null) {
            sb.append(hit.openers().get(t % hit.openers().size())).append(' ')
                    .append(hit.tips().get(t % hit.tips().size())).append(' ')
                    .append(hit.questions().get(t % hit.questions().size()));
        } else {
            sb.append(GENERIC.get(t % GENERIC.size())).append(' ').append(GENERIC_Q.get(t % GENERIC_Q.size()));
        }

        return Map.of("reply", sb.toString(), "crisis", false);
    }
}
