/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.metrics;

/**
 * One aggregated metric bucket (CONCEPT_Harness.md §8): count/sum/max/min accumulate,
 * and the EMA fades - {@code ema = 0.9 * ema_alt + 0.1 * wert_neu}. Only ever
 * added/maximized/minimized/faded, never stored which generation produced the value.
 *
 * @author Janusch Rentenatus
 */
public class MetricBucket {

    public static final double FADE_OLD = 0.9;
    public static final double FADE_NEW = 0.1;

    private long count;
    private double sum;
    private double max = Double.MIN_VALUE;
    private double min = Double.MAX_VALUE;
    private double ema;

    public void update(double wert) {
        count++;
        sum += wert;
        max = Math.max(max, wert);
        min = Math.min(min, wert);
        ema = count == 1 ? wert : FADE_OLD * ema + FADE_NEW * wert;
    }

    public long getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public double getMax() {
        return count == 0 ? 0 : max;
    }

    public double getMin() {
        return count == 0 ? 0 : min;
    }

    public double getEma() {
        return ema;
    }

    public double getAvg() {
        return count == 0 ? 0 : sum / count;
    }
}
