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
import de.jare.jsoncasted.editor.core.AnnotationKeys;
import de.jare.jsoncasted.editor.core.EditNodePropertyArr;
import de.jare.jsoncasted.editor.core.EditStatus;

public interface AScheme {

    public static final String LIGHT_FORE_OKAY = "light.fore." + EditStatus.OKAY.getLiteral();
    public static final String LIGHT_FORE_WARNING = "light.fore." + EditStatus.WARNING.getLiteral();
    public static final String LIGHT_FORE_ERROR = "light.fore." + EditStatus.ERROR.getLiteral();

    public static final String LIGHT_FORE_OBJECT = "light." + EditNodeObject.FOREOBJECT;
    public static final String LIGHT_FORE_PROPERTY = "light." + EditNodeProperty.FOREPROPERTY;
    public static final String LIGHT_FORE_ARRAY = "light." + EditNodePropertyArr.FOREARRAY;
    public static final String LIGHT_FORE_ANNOTATION = "light." + AnnotationKeys.FOREANNOTATION;

    /**
     * Change-state colors of the tree diff view (doc/TREE_DIFF.md in jsonCasted): a third family next to the node
     * type and the status colors. One base key per diff mark, served with the light./dark. prefix like the others.
     */
    public static final String FORE_DIFF_ADD = "fore.diff.add";
    public static final String FORE_DIFF_REMOVE = "fore.diff.remove";
    public static final String FORE_DIFF_MODIFY = "fore.diff.modify";
    public static final String FORE_DIFF_MOVE = "fore.diff.move";
    public static final String FORE_DIFF_CONFLICT = "fore.diff.conflict";
    public static final String FORE_DIFF_RADIATING = "fore.diff.radiating";
    /** A marked node that changed after the diff ran (stale mark) - desaturated, aged tone. */
    public static final String FORE_DIFF_EDITED = "fore.diff.edited";
    /** The white text on the cherry red banner of the diff view. */
    public static final String FORE_DIFF_BANNER_TEXT = "fore.diff.banner.text";
    /** The dark cherry red of the diff view banner - identical in both schemes, the banner does not follow the system colors. */
    public static final String BACK_DIFF_BANNER = "back.diff.banner";

    public static final String LIGHT_FORE_DIFF_ADD = "light." + FORE_DIFF_ADD;
    public static final String LIGHT_FORE_DIFF_REMOVE = "light." + FORE_DIFF_REMOVE;
    public static final String LIGHT_FORE_DIFF_MODIFY = "light." + FORE_DIFF_MODIFY;
    public static final String LIGHT_FORE_DIFF_MOVE = "light." + FORE_DIFF_MOVE;
    public static final String LIGHT_FORE_DIFF_CONFLICT = "light." + FORE_DIFF_CONFLICT;
    public static final String LIGHT_FORE_DIFF_RADIATING = "light." + FORE_DIFF_RADIATING;
    public static final String LIGHT_FORE_DIFF_EDITED = "light." + FORE_DIFF_EDITED;
    public static final String LIGHT_FORE_DIFF_BANNER_TEXT = "light." + FORE_DIFF_BANNER_TEXT;
    public static final String LIGHT_BACK_DIFF_BANNER = "light." + BACK_DIFF_BANNER;

    public static final String DARK_FORE_OKAY = "dark.fore." + EditStatus.OKAY.getLiteral();
    public static final String DARK_FORE_WARNING = "dark.fore." + EditStatus.WARNING.getLiteral();
    public static final String DARK_FORE_ERROR = "dark.fore." + EditStatus.ERROR.getLiteral();

    public static final String DARK_FORE_OBJECT = "dark." + EditNodeObject.FOREOBJECT;
    public static final String DARK_FORE_PROPERTY = "dark." + EditNodeProperty.FOREPROPERTY;
    public static final String DARK_FORE_ARRAY = "dark." + EditNodePropertyArr.FOREARRAY;
    public static final String DARK_FORE_ANNOTATION = "dark." + AnnotationKeys.FOREANNOTATION;

    public static final String DARK_FORE_DIFF_ADD = "dark." + FORE_DIFF_ADD;
    public static final String DARK_FORE_DIFF_REMOVE = "dark." + FORE_DIFF_REMOVE;
    public static final String DARK_FORE_DIFF_MODIFY = "dark." + FORE_DIFF_MODIFY;
    public static final String DARK_FORE_DIFF_MOVE = "dark." + FORE_DIFF_MOVE;
    public static final String DARK_FORE_DIFF_CONFLICT = "dark." + FORE_DIFF_CONFLICT;
    public static final String DARK_FORE_DIFF_RADIATING = "dark." + FORE_DIFF_RADIATING;
    public static final String DARK_FORE_DIFF_EDITED = "dark." + FORE_DIFF_EDITED;
    public static final String DARK_FORE_DIFF_BANNER_TEXT = "dark." + FORE_DIFF_BANNER_TEXT;
    public static final String DARK_BACK_DIFF_BANNER = "dark." + BACK_DIFF_BANNER;

    final public static String[] SCHEME_LIST = new String[]{
        LIGHT_FORE_OKAY, LIGHT_FORE_WARNING, LIGHT_FORE_ERROR, LIGHT_FORE_OBJECT, LIGHT_FORE_PROPERTY,
        LIGHT_FORE_ARRAY, LIGHT_FORE_ANNOTATION,
        DARK_FORE_OKAY, DARK_FORE_WARNING, DARK_FORE_ERROR, DARK_FORE_OBJECT, DARK_FORE_PROPERTY,
        DARK_FORE_ARRAY, DARK_FORE_ANNOTATION,
        LIGHT_FORE_DIFF_ADD, LIGHT_FORE_DIFF_REMOVE, LIGHT_FORE_DIFF_MODIFY, LIGHT_FORE_DIFF_MOVE,
        LIGHT_FORE_DIFF_CONFLICT, LIGHT_FORE_DIFF_RADIATING,
        DARK_FORE_DIFF_ADD, DARK_FORE_DIFF_REMOVE, DARK_FORE_DIFF_MODIFY, DARK_FORE_DIFF_MOVE,
        DARK_FORE_DIFF_CONFLICT, DARK_FORE_DIFF_RADIATING};
}
