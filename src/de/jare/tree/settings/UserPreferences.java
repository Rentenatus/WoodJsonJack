/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings;

public class UserPreferences {

    private String uiLanguage;      // "de", "en"
    private boolean showTips;
    private boolean autoSave;

    public UserPreferences() {
        this.uiLanguage = "de";
        this.showTips = true;
        this.autoSave = true;
    }

    public String getUiLanguage() {
        return uiLanguage;
    }

    public void setUiLanguage(String uiLanguage) {
        this.uiLanguage = uiLanguage;
    }

    public boolean isShowTips() {
        return showTips;
    }

    public void setShowTips(boolean showTips) {
        this.showTips = showTips;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }
}
