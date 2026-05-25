package de.jare.jsoncasted.editor.core;

/**
 *
 * @author Janusch Rentenatus
 */
public class EditTime {

    private long millis = System.currentTimeMillis();

    public void update() {
        millis = System.currentTimeMillis();
    }

    public long getMillis() {
        return millis;
    }

}
