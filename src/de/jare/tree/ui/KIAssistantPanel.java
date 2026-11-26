/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.StyledDocument;

/**
 * The "AI Assistant" bottom tab (placeholder layout): a scrollable, formattable output
 * pane (JTextPane with a StyledDocument), below it a narrower non-scrollable input area
 * that auto-grows between 1 and 5 rows with its content, and below that a status panel
 * (models, memory, steps, evolution, time - placeholders only for now).
 *
 * @author Janusch Rentenatus
 */
public class KIAssistantPanel extends JPanel {

    private static final int INPUT_MIN_ROWS = 1;
    private static final int INPUT_MAX_ROWS = 5;

    private JTextPane outputPane;
    private JTextArea inputArea;
    private JPanel statusPanel;
    private JLabel statusModels;
    private JLabel statusMemory;
    private JLabel statusSteps;
    private JLabel statusEvolution;
    private JLabel statusTime;

    public KIAssistantPanel() {
        super(new BorderLayout(0, 4));

        // scrollable, formattable output
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setContentType("text/html");
        outputPane.setText("<i>AI output appears here...</i>");   // setText parses HTML (insertString would not)
        JScrollPane outputScroll = new JScrollPane(outputPane);
        add(outputScroll, BorderLayout.CENTER);

        // south: input row + status panel
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(createInputRow(), BorderLayout.CENTER);
        south.add(createStatusPanel(), BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private JPanel createInputRow() {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        inputArea = new JTextArea(INPUT_MIN_ROWS, 20);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setText("AI prompt here...");
        // auto-height 1..5 rows with content, never scrollable
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { adjustRows(); }
            @Override public void removeUpdate(DocumentEvent e) { adjustRows(); }
            @Override public void changedUpdate(DocumentEvent e) { adjustRows(); }
        });
        // Enter abfangen: kein Zeilenumbruch, sondern Thinking auslösen (Shift+Enter = Zeilenumbruch)
        inputArea.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
                "harness.thinking");
        inputArea.getActionMap().put("harness.thinking", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                onThinking();
            }
        });
        row.add(inputArea, BorderLayout.CENTER);
        JButton askBtn = new JButton("Ask AI");
        askBtn.addActionListener(e -> onThinking());
        row.add(askBtn, BorderLayout.EAST);
        attachCommandPopup(inputArea);
        return row;
    }

    /**
     * §17: right-click popup with the chat commands - the items only hand the command text
     * ("/clear", "/memory", "/help") to the installed thinking handler, the decision
     * command-vs-run stays in the harness (ChatCommandService via HarnessController).
     */
    private void attachCommandPopup(java.awt.Component c) {
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        addCommandItem(popup, "Clear memory (/clear)", "/clear");
        addCommandItem(popup, "Show decisions (/decisions)", "/decisions");
        addCommandItem(popup, "Show memory (/memory)", "/memory");
        addCommandItem(popup, "Help (/help)", "/help");
        // /decide needs the decision text: put the command into the input, the user completes it
        javax.swing.JMenuItem decideItem = new javax.swing.JMenuItem("Record decision (/decide ...)");
        decideItem.addActionListener(e -> {
            inputArea.setText("/decide ");
            inputArea.requestFocusInWindow();
        });
        popup.add(decideItem);
        c.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) { maybeShow(e); }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) { maybeShow(e); }
            private void maybeShow(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private void addCommandItem(javax.swing.JPopupMenu popup, String label, String command) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        item.addActionListener(e -> {
            if (thinkingHandler != null) {
                thinkingHandler.accept(command);
            }
        });
        popup.add(item);
    }

    private java.util.function.Consumer<String> thinkingHandler;

    // §18.6: the orators (CommandHistory/WorkResponse) hold listeners WEAKLY - this pane is the
    // subscriber, so it holds its own listeners in fields to keep them alive
    java.util.function.Consumer<de.jare.harness.memory.CommandHistory.Entry> commandListener;
    java.util.function.Consumer<de.jare.harness.memory.WorkResponse> responseListener;

    /**
     * §18.6: creates this pane's harness-orator listeners AND stores them in fields (the
     * orators are weak). The caller registers the returned listeners on the controller
     * pass-through. EDT marshalling happens here.
     */
    public void installHarnessOrators() {
        commandListener = entry -> {
            String t = actionText(entry);   // [DIAG] temporary: which entries would render as action lines
            if (t != null) {
                System.out.println("[DIAG] action line seq=" + entry.seq + ": " + t);
            }
            javax.swing.SwingUtilities.invokeLater(() -> appendCommandEntry(entry));
        };
        responseListener = resp -> {
            System.out.println("[DIAG] response event status=" + resp.getStatus() + " text=" + resp.getText());
            javax.swing.SwingUtilities.invokeLater(() -> appendResponse(resp));
        };
    }

    /** The host (JackMainMenu via HarnessController) installs this: input text -> WoodHarness AskAi run. */
    public void setThinkingHandler(java.util.function.Consumer<String> thinkingHandler) {
        this.thinkingHandler = thinkingHandler;
    }

    /**
     * "Thinking" action - Enter in the input and "Ask AI" both land here. The input text is
     * handed to the installed handler, which starts the WoodHarness AskAi run (the harness core
     * creates the MemoryQuest/WorkOrder; this pane knows no TaskData logic).
     */
    private void onThinking() {
        if (thinkingHandler != null) {
            thinkingHandler.accept(inputArea.getText());
        }
    }

    /** Recompute the input height: clamped to 1..5 rows, then revalidate. */
    private void adjustRows() {
        SwingUtilities.invokeLater(() -> {
            int lines = Math.max(1, inputArea.getLineCount());
            int rows = Math.min(INPUT_MAX_ROWS, Math.max(INPUT_MIN_ROWS, lines));
            if (inputArea.getRows() != rows) {
                inputArea.setRows(rows);   // JTextArea derives its preferred height from rows
                inputArea.revalidate();
            }
        });
    }

    private JPanel createStatusPanel() {
        // one line: Models: —  Memory: —  Steps: —  Evolution: —  Time: —
        statusPanel = new JPanel(new GridLayout(1, 10, 8, 0));
        statusModels = addStatus(statusPanel, "Models");
        statusMemory = addStatus(statusPanel, "Memory");
        statusSteps = addStatus(statusPanel, "Steps");
        statusEvolution = addStatus(statusPanel, "Evolution");
        statusTime = addStatus(statusPanel, "Time");
        return statusPanel;
    }

    private static JLabel addStatus(JPanel panel, String label) {
        panel.add(new JLabel(label + ":"));
        JLabel value = new JLabel("—");   // placeholder until the harness reports real values
        panel.add(value);
        return value;
    }

    /**
     * §17: appends a line to the output area (chat command replies like /clear, /memory).
     * Plain text is HTML-escaped; multi-line replies keep their line breaks.
     */
    public void appendOutput(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        appendHtml(html + "<br>");
    }

    /**
     * Appends an HTML fragment at the end of the output pane. IMPORTANT: a plain
     * {@code StyledDocument.insertString} does NOT parse HTML on a text/html JTextPane -
     * the tags would show up as literal text. {@link javax.swing.text.html.HTMLEditorKit#insertHTML}
     * parses the fragment in the document context.
     */
    private void appendHtml(String html) {
        try {
            javax.swing.text.html.HTMLEditorKit kit =
                    (javax.swing.text.html.HTMLEditorKit) outputPane.getEditorKit();
            javax.swing.text.html.HTMLDocument doc =
                    (javax.swing.text.html.HTMLDocument) outputPane.getDocument();
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            outputPane.setCaretPosition(doc.getLength());
        } catch (Exception ignored) {
            // output is best-effort
        }
    }

    /**
     * Appends one LOOP ACTION as a short gray line to the output area: 60% foreground +
     * 40% background of the current theme, blended per channel (CONCEPT: actions stay
     * visually subordinate to the AI answer).
     */
    public void appendAction(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String html = "<span style=\"color:" + grayHex() + "\">"
                + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>") + "</span><br>";
        appendHtml(html);
    }

    /** Appends the final AI ANSWER in normal foreground color (after the loop closed). */
    public void appendAnswer(String text) {
        appendOutput(text);
    }

    /**
     * Renders one CommandHistory entry as a gray action line - but only real flow actions:
     * a non-CONTINUE {@code LoopAction} of an appended calculator call, or a composite
     * {@code SplitInto}. Plain mutations (setStatus, logCall, …) stay invisible here.
     */
    public void appendCommandEntry(de.jare.harness.memory.CommandHistory.Entry e) {
        String text = actionText(e);
        System.out.println("[DIAG] appendCommandEntry seq=" + e.seq + " render=" + (text != null));
        if (text != null) {
            appendAction(text);
        }
    }

    /** Short text for the loop action an entry carries (null = not an action line). */
    private static String actionText(de.jare.harness.memory.CommandHistory.Entry e) {
        if (e.command instanceof de.jare.harness.TaskCommands.SplitInto sp) {
            return "→ SPLIT into " + sp.children().size() + " parts (loop \"" + sp.loopId() + "\")";
        }
        if (e.command instanceof de.jare.harness.TaskCommands.AppendCalcCall acc) {
            de.jare.harness.LoopAction la = acc.res().getLoopAction();
            if (la != null && la.flow() != de.jare.harness.LoopAction.Flow.CONTINUE) {
                String detail = "";
                if (la instanceof de.jare.harness.main.actions.LoopSplit split) {
                    detail = " → loop \"" + split.loopId() + "\", " + split.prompts().size() + " prompts";
                } else if (la instanceof de.jare.harness.main.actions.LoopSubLoop sub) {
                    detail = " → loop \"" + sub.loopId() + "\"";
                }
                return "→ " + la.flow() + detail;
            }
        }
        return null;
    }

    /**
     * WorkResponse orator: when the run reaches a terminal status, the answer is appended
     * in normal color (the gray action lines above stay subordinate).
     */
    public void appendResponse(de.jare.harness.memory.WorkResponse r) {
        de.jare.harness.Status s = r.getStatus();
        if (s == de.jare.harness.Status.DONE || s == de.jare.harness.Status.FAILED
                || s == de.jare.harness.Status.ABORTED) {
            if (r.getText() != null && !r.getText().isBlank()) {
                appendAnswer(r.getText());
            }
        }
    }

    /** 60% Label.foreground + 40% Label.background, blended per RGB channel, as HTML hex. */
    private static String grayHex() {
        java.awt.Color fg = javax.swing.UIManager.getColor("Label.foreground");
        java.awt.Color bg = javax.swing.UIManager.getColor("Label.background");
        if (fg == null) fg = java.awt.Color.BLACK;
        if (bg == null) bg = java.awt.Color.WHITE;
        int r = (int) (fg.getRed() * 0.6 + bg.getRed() * 0.4);
        int g = (int) (fg.getGreen() * 0.6 + bg.getGreen() * 0.4);
        int b = (int) (fg.getBlue() * 0.6 + bg.getBlue() * 0.4);
        return String.format("#%02x%02x%02x", r, g, b);
    }

    public JTextPane getOutputPane() {
        return outputPane;
    }

    public JTextArea getInputArea() {
        return inputArea;
    }

    /** Placeholder status update - later fed by the WoodHarness run (models, memory, steps, evolution, time). */
    public void setStatus(String models, String memory, String steps, String evolution, String time) {
        statusModels.setText(models);
        statusMemory.setText(memory);
        statusSteps.setText(steps);
        statusEvolution.setText(evolution);
        statusTime.setText(time);
    }
}
