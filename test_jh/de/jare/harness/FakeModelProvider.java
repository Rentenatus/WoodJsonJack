/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.impl.ModelProvider;
import de.jare.harness.model.Modelclass;
import java.util.List;

/**
 * Test double for {@link ModelProvider}: answers every call with a canned strict-JSON
 * response chosen by a keyword found in the assembled prompt segments. The real model call
 * is NOT tested here - the suite must stay stable offline; only input assembly (which
 * segments were sent) and result processing are under test. Register via
 * {@code factory.registerProvider("ollama", new FakeModelProvider(mc))} or by overriding
 * {@link HarnessService#createProvider(Modelclass)}.
 *
 * @author Janusch Rentenatus
 */
public class FakeModelProvider implements ModelProvider {

    private final Modelclass modelclass;

    public FakeModelProvider(Modelclass modelclass) {
        this.modelclass = modelclass;
    }

    @Override
    public Modelclass modelclass() {
        return modelclass;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String complete(List<String> segments) {
        return complete(segments, null);
    }

    @Override
    public String complete(List<String> segments, Double temperature) {
        String all = String.join("\n", segments);
        if (all.contains("How many separate questions")) {
            return "{\"status\":\"DONE\",\"decision\":\"counted\",\"score\":100,\"payload\":{\"questCount\":1}}";
        }
        if (all.contains("decompose the user prompt")) {
            return "{\"status\":\"DONE\",\"decision\":\"decomposed\",\"score\":100,\"payload\":{\"quests\":[\"the task itself\"]}}";
        }
        if (all.contains("prompt guard") || all.contains("escalation judge")) {
            return "{\"status\":\"DONE\",\"decision\":\"checked\",\"score\":100,\"payload\":{\"verdict\":\"SAFE\",\"reason\":\"fake: normal question\"}}";
        }
        if (all.contains("Ist unsere Frage komplett")) {
            return "{\"status\":\"DONE\",\"decision\":\"complete\",\"score\":100,\"payload\":{\"complete\":\"true\",\"missing\":\"\"}}";
        }
        if (all.contains("evaluator")) {
            return "{\"status\":\"DONE\",\"decision\":\"scored\",\"score\":100,\"payload\":{\"verdict\":\"fake: fine\"}}";
        }
        if (all.contains("run closer")) {
            return "{\"status\":\"DONE\",\"decision\":\"close\",\"score\":100,\"payload\":{\"close\":\"true\",\"reason\":\"fake: ok\"}}";
        }
        if (all.contains("compose the final answer")) {
            return "{\"status\":\"DONE\",\"decision\":\"summarized\",\"score\":100,\"payload\":{\"summary\":\"fake composed summary\"}}";
        }
        if (all.contains("answer the user's question")) {
            return "{\"status\":\"DONE\",\"decision\":\"answered\",\"score\":100,\"payload\":{\"answer\":\"fake answer\"}}";
        }
        return "{\"status\":\"DONE\",\"decision\":\"done\",\"score\":100,\"payload\":{}}";
    }
}
