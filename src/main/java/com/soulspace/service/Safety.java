package com.soulspace.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Psychological safety and crisis screening used to surface emergency helplines.
 * Fast, non-blocking, and privacy-preserving.
 */
public final class Safety {
    private Safety() {}

    private static final List<String> TERMS = List.of(
            "kill myself", "suicid", "end my life", "want to die", "wanna die", "hurt myself",
            "harm myself", "self harm", "self-harm", "better off dead", "no reason to live",
            "end it all", "take my own life", "don't want to live", "dont want to live",
            "cut my wrist", "slit my wrist", "slit my throat", "overdose", "overdosing",
            "hang myself", "hanging myself", "jump off", "jumping off", "swallow pills",
            "swallowing pills", "take all my pills", "goodbye cruel world", "can't go on anymore",
            "cant go on anymore", "nothing left to live for", "want to disappear forever");

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\b(suicid(e|al|ing)?|kill(ing)?\\s+(my|one)?self)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(end(ing)?\\s+(it\\s+all|my\\s+life))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(want|wanna|wish)\\s+to\\s+(die|disappear|sleep\\s+and\\s+never\\s+wake)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(hang|slit|overdose|harm|cut|poison)\\s+(my|one)?self\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(no\\s+(reason|point)\\s+to\\s+(live|stay\\s+alive))\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(better\\s+off\\s+(dead|gone))\\b", Pattern.CASE_INSENSITIVE)
    );

    public static boolean isCrisis(String text) {
        if (text == null || text.isBlank()) return false;
        String s = text.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        for (String t : TERMS) {
            if (s.contains(t)) return true;
        }
        for (Pattern p : PATTERNS) {
            if (p.matcher(s).find()) return true;
        }
        return false;
    }
}
