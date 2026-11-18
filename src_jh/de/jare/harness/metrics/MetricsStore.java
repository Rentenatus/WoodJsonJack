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
        SCORE, TOKENS_OUT, DAUER_MS, FAIL_RATE, RETRY_COUNT, PARAM_ERROR
    }

    public static final int DEPTHS = 3;

    /** Metrics key prefix for loop-structure nodes: {@code "loop:<loopId>/<itemId>@<trigger>"}. */
    public static final String LOOP_KEY_PREFIX = "loop:";

    // slotSignature -> depth(1..3) -> category -> bucket
    private final Map<String, Map<Integer, Map<Category, MetricBucket>>> nodes = new LinkedHashMap<>();

    /** Records one call into the M x 3 aggregate of its slot signature. */
    public void recordCall(CallLogEntry e) {
        recordInto(e.getSlotId(), e);
        // loop-structure node: same values under the loop key, so loops are measurable like slots
        if (e.getLoopId() != null) {
            String loopKey = LOOP_KEY_PREFIX + e.getLoopId() + "/" + e.getLoopItemId() + "@" + e.getTrigger();
            recordInto(loopKey, e);
        }
        // parameter error: counted per slot AND per offending param (slotId@paramKey)
        if (e.getParamError() != null) {
            recordValue(e.getSlotId(), Category.PARAM_ERROR, 1);
            recordValue(e.getSlotId() + "@" + e.getParamError(), Category.PARAM_ERROR, 1);
            if (e.getLoopId() != null) {
                recordValue(LOOP_KEY_PREFIX + e.getLoopId() + "/" + e.getLoopItemId() + "@" + e.getTrigger(),
                        Category.PARAM_ERROR, 1);
            }
        }
    }

    private void recordInto(String key, CallLogEntry e) {
        Map<Integer, Map<Category, MetricBucket>> byDepth = nodes.computeIfAbsent(
                key, k -> new LinkedHashMap<>());
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
            // PARAM_ERROR is recorded only in the explicit block of recordCall (slot + slot@param + loop key)
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

    /** Inheritance on clone (evolution): copy all, then hard-reset the mutated slot buckets. */
    public MetricsStore cloneWithReset(java.util.Set<String> resetSlots) {
        return cloneWithInheritance(resetSlots, 0.0);
    }

    /**
     * Inheritance on clone (evolution): non-mutated nodes are inherited via their faded
     * value (EMA), mutated nodes keep a <b>prior</b> of the old rating - the old EMA with
     * the count scaled by {@code priorWeight} (0 = hard reset, 1 = full carry-over) - so a
     * mutation does not destroy the old evaluation, new measurements override it gradually.
     */
    public MetricsStore cloneWithInheritance(java.util.Set<String> mutatedNodes, double priorWeight) {
        MetricsStore copy = new MetricsStore();
        for (Map.Entry<String, Map<Integer, Map<Category, MetricBucket>>> n : nodes.entrySet()) {
            boolean mutated = mutatedNodes.contains(n.getKey());
            for (Map.Entry<Integer, Map<Category, MetricBucket>> d : n.getValue().entrySet()) {
                for (Map.Entry<Category, MetricBucket> c : d.getValue().entrySet()) {
                    MetricBucket b = c.getValue();
                    if (mutated && priorWeight <= 0.0) {
                        continue; // hard reset: mutated buckets start unmeasured
                    }
                    MetricBucket nb = new MetricBucket();
                    long inheritCount = mutated
                            ? Math.round(b.getCount() * priorWeight)
                            : b.getCount();
                    for (long i = 0; i < inheritCount; i++) {
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

    /**
     * The structure rating of a node as a computed view on the buckets (not persisted as
     * own state): {@code ema(SCORE) - 100 * (wFail * ema(FAIL_RATE) + wParam * ema(PARAM_ERROR))}.
     * Missing buckets count as 0, so a fresh node starts at 0 instead of being dragged down.
     */
    public double structureScore(String nodeKey, int depth, double wFail, double wParam) {
        Map<Category, MetricBucket> s = stats(nodeKey, depth);
        double score = s.get(Category.SCORE) == null ? 0 : s.get(Category.SCORE).getEma();
        double fail = s.get(Category.FAIL_RATE) == null ? 0 : s.get(Category.FAIL_RATE).getEma();
        double param = s.get(Category.PARAM_ERROR) == null ? 0 : s.get(Category.PARAM_ERROR).getEma();
        return score - 100.0 * (wFail * fail + wParam * param);
    }

    /** Structure rating with default weights (fail rate and param errors both weigh 1.0). */
    public double structureScore(String nodeKey, int depth) {
        return structureScore(nodeKey, depth, 1.0, 1.0);
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
