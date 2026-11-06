/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness;

import de.jare.harness.model.CalculatorNode;

/**
 * Resolves a modelled calculator node to a runnable {@link HarnessCalculator}
 * (CONCEPT_Harness.md §2, §5). Puzzle nodes are expanded by the engine itself;
 * the factory only creates the atomic leaves.
 *
 * @author Janusch Rentenatus
 */
public interface CalculatorFactory {

    HarnessCalculator createAtomic(CalculatorNode node, Kind kind);
}
