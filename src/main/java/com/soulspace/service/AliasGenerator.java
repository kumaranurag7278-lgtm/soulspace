package com.soulspace.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AliasGenerator {
    private AliasGenerator() {}

    private static final List<String> ADJ = List.of("Quiet", "Soft", "Gentle", "Amber", "Midnight", "Silver",
            "Drifting", "Calm", "Hopeful", "Velvet", "Sleepy", "Wandering", "Tender", "Lunar", "Misty", "Kind");
    private static final List<String> NOUN = List.of("Lantern", "Ember", "Comet", "River", "Moth", "Willow",
            "Harbor", "Meadow", "Owl", "Cloud", "Pebble", "Fern", "Aurora", "Sparrow", "Tide", "Orchid");

    public static String next() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return ADJ.get(r.nextInt(ADJ.size())) + " " + NOUN.get(r.nextInt(NOUN.size()));
    }
}
