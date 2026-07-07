/* <copyright> 
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.jsoncasted.editor.events;

/**
 *
 * @author Jansuch Rentenatus
 */
public interface HistoryListener {

    public void onClear(HistoryEvent event);

    public void onAction(HistoryEvent event);

}
