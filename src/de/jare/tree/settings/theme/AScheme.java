/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import de.jare.jsoncasted.editor.core.EditNode;

public interface AScheme {

    public static final String LIGHT_FORE_OKAY = "light.fore." + EditNode.EDIT_OKAY;
    public static final String LIGHT_FORE_WARNING = "light.fore." + EditNode.EDIT_WARNING;
    public static final String LIGHT_FORE_ERROR = "light.fore." + EditNode.EDIT_ERROR;

    public static final String LIGHT_FORE_OBJECT = "light.fore.object";
    public static final String LIGHT_FORE_PROPERTY = "light.fore.property";

    public static final String DARK_FORE_OKAY = "dark.fore." + EditNode.EDIT_OKAY;
    public static final String DARK_FORE_WARNING = "dark.fore." + EditNode.EDIT_WARNING;
    public static final String DARK_FORE_ERROR = "dark.fore." + EditNode.EDIT_ERROR;

    public static final String DARK_FORE_OBJECT = "dark.fore.object";
    public static final String DARK_FOREP_ROPERTY = "dark.fore.property";

    final public static String[] SCHEME_LIST = new String[]{
        LIGHT_FORE_OKAY, LIGHT_FORE_WARNING, LIGHT_FORE_ERROR, LIGHT_FORE_OBJECT, LIGHT_FORE_PROPERTY,
        DARK_FORE_OKAY, DARK_FORE_WARNING, DARK_FORE_ERROR, DARK_FORE_OBJECT, DARK_FOREP_ROPERTY};
}
