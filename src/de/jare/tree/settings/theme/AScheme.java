/* <copyright> 
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.settings.theme;

import de.jare.jsoncasted.editor.core.EditNode;
import de.jare.jsoncasted.editor.core.EditNodeObject;
import de.jare.jsoncasted.editor.core.EditNodeProperty;
import de.jare.jsoncasted.editor.core.EditNodePropertyArr;
import de.jare.jsoncasted.editor.core.EditStatus;

public interface AScheme {

    public static final String LIGHT_FORE_OKAY = "light.fore." + EditStatus.OKAY.getLiteral();
    public static final String LIGHT_FORE_WARNING = "light.fore." + EditStatus.WARNING.getLiteral();
    public static final String LIGHT_FORE_ERROR = "light.fore." + EditStatus.ERROR.getLiteral();

    public static final String LIGHT_FORE_OBJECT = "light." + EditNodeObject.FOREOBJECT;
    public static final String LIGHT_FORE_PROPERTY = "light." + EditNodeProperty.FOREPROPERTY;
    public static final String LIGHT_FORE_ARRAY = "light." + EditNodePropertyArr.FOREARRAY;

    public static final String DARK_FORE_OKAY = "dark.fore." + EditStatus.OKAY.getLiteral();
    public static final String DARK_FORE_WARNING = "dark.fore." + EditStatus.WARNING.getLiteral();
    public static final String DARK_FORE_ERROR = "dark.fore." + EditStatus.ERROR.getLiteral();

    public static final String DARK_FORE_OBJECT = "dark." + EditNodeObject.FOREOBJECT;
    public static final String DARK_FORE_PROPERTY = "dark." + EditNodeProperty.FOREPROPERTY;
    public static final String DARK_FORE_ARRAY = "dark." + EditNodePropertyArr.FOREARRAY;

    final public static String[] SCHEME_LIST = new String[]{
        LIGHT_FORE_OKAY, LIGHT_FORE_WARNING, LIGHT_FORE_ERROR, LIGHT_FORE_OBJECT, LIGHT_FORE_PROPERTY, LIGHT_FORE_ARRAY,
        DARK_FORE_OKAY, DARK_FORE_WARNING, DARK_FORE_ERROR, DARK_FORE_OBJECT, DARK_FORE_PROPERTY, DARK_FORE_ARRAY};
}
