/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.harness.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic persistence for all harness files (CONCEPT_Harness.md §5): write-temp-rename -
 * the content lands in a temp file next to the target which is then atomically moved
 * over the target, so a crash never leaves a half-written harness.json / lineage.json /
 * metrics.json / RAG file behind.
 *
 * @author Janusch Rentenatus
 */
public final class AtomicFileWrite {

    private AtomicFileWrite() {
    }

    public static void writeString(Path target, String content) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Streaming variant: the JsonNode is written directly to the temp file (never as one
     * big string in the heap - the size guard of JsonNodeWriter still applies), then
     * atomically moved over the target.
     */
    public static void writeNode(Path target, de.jare.jsoncasted.lang.JsonNode node) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            de.jare.jsoncasted.io.JsonNodeWriter.write(node, tmp.toFile());
        } catch (de.jare.jsoncasted.io.JsonParseException e) {
            throw new IOException("cannot serialize node to " + tmp, e);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
