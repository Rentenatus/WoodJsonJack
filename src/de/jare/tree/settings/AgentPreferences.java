/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Jansuch Rentenatus
 */
public class AgentPreferences {

    private String apiKey;
    private String defaultBehavior;
    private int maxRetries;
    private List<String> modelList;
    private String prioritizedModel;

    public AgentPreferences() {
        this.apiKey = "";
        this.defaultBehavior = "balanced";
        this.maxRetries = 3;
        this.modelList = new ArrayList<>();
        this.prioritizedModel = "";
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultBehavior() {
        return defaultBehavior;
    }

    public void setDefaultBehavior(String defaultBehavior) {
        this.defaultBehavior = defaultBehavior;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public List<String> getModelList() {
        return modelList;
    }

    public void setModelList(List<String> modelList) {
        this.modelList = modelList;
    }

    public String getPrioritizedModel() {
        return prioritizedModel;
    }

    public void setPrioritizedModel(String prioritizedModel) {
        this.prioritizedModel = prioritizedModel;
    }
}
