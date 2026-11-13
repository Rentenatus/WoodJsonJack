/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.metrics;

import de.jare.harness.CallLogEntry;
import de.jare.harness.Status;
import de.jare.jsoncasted.io.JsonNodeWriter;
import de.jare.jsoncasted.io.JsonParseException;
import de.jare.jsoncasted.io.parserservice.JsonParserService;
import de.jare.jsoncasted.lang.JsonNode;
import de.jare.debug.JsonDebugLevel;
import de.jare.harness.io.AtomicFileWrite;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The aggregated metrics store (CONCEPT_Harness.md §6, §8): per harness node
 * (slot signature) exactly one inheritable metrics list of size M × 3 - M metric
 * categories (score, tokensOut, dauerMs, failRate, retryCount), each at 3 depths
 * of the call chain (parent1/2/3). Global store {@code ~/.wood/harness/metrics.json}
 * keeps the same aggregate shape - no event log, no generation filtering.
 *
 * @author Janusch Rentenatus
 */
public class MetricsStore {

    /** The M metric categories. */
    public enum Category {
        SCORE, TOKENS_OUT, DAUER_MS, FAIL_RATE, RETRY_COUNT
    }

    public static final int DEPTHS = 3;

    // slotSignature -> depth(1..3) -> category -> bucket
    private final Map<String, Map<Integer, Map<Category, MetricBucket>>> nodes = new LinkedHashMap<>();

    /** Records one call into the M x 3 aggregate of its slot signature. */
    public void recordCall(CallLogEntry e) {
        Map<Integer, Map<Category, MetricBucket>> byDepth = nodes.computeIfAbsent(
                e.getSlotId(), k -> new LinkedHashMap<>());
        for (int depth = 1; depth <= DEPTHS; depth++) {
            Map<Category, MetricBucket> buckets = byDepth.computeIfAbsent(depth, k -> new EnumMap<>(Category.class));
            // SCORE only when the call actually carried a score - a null score must not
            // drag the average down (it would trigger false swap proposals in §7.1)
            if (e.getScore() != null) {
                update(buckets, Category.SCORE, e.getScore());
            }
            update(buckets, Category.TOKENS_OUT, e.getTokensOut());
            update(buckets, Category.DAUER_MS, e.getDauerMs());
            update(buckets, Category.FAIL_RATE, e.getStatus() == Status.FAILED || e.getStatus() == Status.ABORTED ? 1 : 0);
            update(buckets, Category.RETRY_COUNT, e.getAttempt());
        }
    }

    private static void update(Map<Category, MetricBucket> buckets, Category cat, double wert) {
        buckets.computeIfAbsent(cat, k -> new MetricBucket()).update(wert);
    }

    /**
     * §17: records a single value into the M x 3 aggregate of a slot signature (same depth
     * fan-out as {@link #recordCall}) - used for the nugget feedback of §17, keyed by the
     * producing calculator ({@code "nugget:" + source}), so every calculator is scoreable.
     */
    public void recordValue(String slotSignature, Category cat, double value) {
        Map<Integer, Map<Category, MetricBucket>> byDepth = nodes.computeIfAbsent(
                slotSignature, k -> new LinkedHashMap<>());
        for (int depth = 1; depth <= DEPTHS; depth++) {
            Map<Category, MetricBucket> buckets = byDepth.computeIfAbsent(depth, k -> new EnumMap<>(Category.class));
            update(buckets, cat, value);
        }
    }

    /** Local view: the metrics list of one node at one depth. */
    public Map<Category, MetricBucket> stats(String slotSignature, int depth) {
        Map<Integer, Map<Category, MetricBucket>> byDepth = nodes.get(slotSignature);
        if (byDepth == null) {
            return Map.of();
        }
        return byDepth.getOrDefault(depth, Map.of());
    }

    public Map<String, Map<Integer, Map<Category, MetricBucket>>> getNodes() {
        return nodes;
    }

    /** Inheritance on clone (evolution): copy all, then reset the mutated slot buckets. */
    public MetricsStore cloneWithReset(java.util.Set<String> resetSlots) {
        MetricsStore copy = new MetricsStore();
        for (Map.Entry<String, Map<Integer, Map<Category, MetricBucket>>> n : nodes.entrySet()) {
            boolean reset = resetSlots.contains(n.getKey());
            for (Map.Entry<Integer, Map<Category, MetricBucket>> d : n.getValue().entrySet()) {
                for (Map.Entry<Category, MetricBucket> c : d.getValue().entrySet()) {
                    MetricBucket b = c.getValue();
                    if (reset) {
                        continue; // mutated buckets start unmeasured
                    }
                    MetricBucket nb = new MetricBucket();
                    for (long i = 0; i < b.getCount(); i++) {
                        nb.update(b.getEma()); // inherit via the faded value, keeps M x 3 shape
                    }
                    copy.nodes.computeIfAbsent(n.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(d.getKey(), k -> new EnumMap<>(Category.class))
                            .put(c.getKey(), nb);
                }
            }
        }
        return copy;
    }

    public void save(File file) throws IOException, JsonParseException {
        AtomicFileWrite.writeString(file.toPath(), JsonNodeWriter.writeToString(toJson()));
    }

    public static MetricsStore load(File file) throws IOException, JsonParseException {
        if (!file.exists()) {
            return new MetricsStore();
        }
        return fromJson(JsonParserService.parse(file, JsonDebugLevel.SIMPLE).getRoot());
    }

    public JsonNode toJson() {
        JsonNode root = JsonNode.objectNode();
        for (Map.Entry<String, Map<Integer, Map<Category, MetricBucket>>> n : nodes.entrySet()) {
            JsonNode node = JsonNode.objectNode();
            for (Map.Entry<Integer, Map<Category, MetricBucket>> d : n.getValue().entrySet()) {
                JsonNode depth = JsonNode.objectNode();
                for (Map.Entry<Category, MetricBucket> c : d.getValue().entrySet()) {
                    MetricBucket b = c.getValue();
                    JsonNode bn = JsonNode.objectNode();
                    bn.put("count", JsonNode.longNode(b.getCount()));
                    bn.put("sum", JsonNode.numberNode(b.getSum()));
                    bn.put("max", JsonNode.numberNode(b.getMax()));
                    bn.put("min", JsonNode.numberNode(b.getMin()));
                    bn.put("ema", JsonNode.numberNode(b.getEma()));
                    depth.put(c.getKey().name(), bn);
                }
                node.put(String.valueOf(d.getKey()), depth);
            }
            root.put(n.getKey(), node);
        }
        return root;
    }

    public static MetricsStore fromJson(JsonNode root) {
        MetricsStore store = new MetricsStore();
        if (root == null || !root.isObject()) {
            return store;
        }
        for (Map.Entry<String, JsonNode> n : root.asObjectValues().entrySet()) {
            for (Map.Entry<String, JsonNode> d : n.getValue().asObjectValues().entrySet()) {
                for (Map.Entry<String, JsonNode> c : d.getValue().asObjectValues().entrySet()) {
                    JsonNode b = c.getValue();
                    MetricBucket bucket = new MetricBucket();
                    long count = b.asObjectValues().get("count").asLong();
                    double ema = b.asObjectValues().get("ema").asNumber();
                    for (long i = 0; i < count; i++) {
                        bucket.update(ema);
                    }
                    store.nodes.computeIfAbsent(n.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(Integer.parseInt(d.getKey()), k -> new EnumMap<>(Category.class))
                            .put(Category.valueOf(c.getKey()), bucket);
                }
            }
        }
        return store;
    }
}
