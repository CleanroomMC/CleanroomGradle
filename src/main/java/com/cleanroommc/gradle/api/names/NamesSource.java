package com.cleanroommc.gradle.api.names;

import com.cleanroommc.gradle.api.util.IO;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A resolved set of SRG to name mappings plus a canonical identity string, decoupled from where the
 * names came from (MCP CSVs or a hand-edited Tiny2 file).
 *
 * <p>The <b>id</b> is the load-bearing part of the mapping-identity feature: patch sets stay at the
 * named (MCP/custom) level and record the id of the names they were generated in, so the pipeline can
 * refuse to apply patches against a base that was named differently. Ids take the form:</p>
 * <ul>
 *   <li>{@code mcp:<channel>_<version>} which is derived from the {@code mcpMappings} dependency notation</li>
 *   <li>{@code tiny2:<first-12-hex-of-sha256-of-file>} which is a sha256 content hash of the Tiny2 file.</li>
 * </ul>
 */
public record NamesSource(String id,
                          Map<String, String> methods,
                          Map<String, String> fields,
                          Map<String, String> params,
                          Map<String, String> docs) {

    /** Resolves a names source from a Tiny2 file. */
    public static NamesSource fromTiny2(File tinyFile) {
        var flat = TinyV2.read(tinyFile.toPath());
        return new NamesSource(tiny2Id(tinyFile), flat.methods(), flat.fields(), flat.params(), flat.docs());
    }

    public static String tiny2Id(File tinyFile) {
        try (var in = IO.in(tinyFile)) {
            return "tiny2:" + DigestUtils.sha256Hex(in).substring(0, 12);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to hash Tiny2 mappings " + tinyFile, e);
        }
    }

    public static String mcpId(String artifactName, String version) {
        var channel = artifactName.startsWith("mcp_") ? artifactName.substring("mcp_".length()) : artifactName;
        return "mcp:" + channel + "_" + version;
    }

    /** All member and parameter names merged into a single srg -> name lookup, for source renaming. */
    public Map<String, String> flatNames() {
        var flat = new HashMap<String, String>(this.methods.size() + this.fields.size() + this.params.size());
        flat.putAll(this.methods);
        flat.putAll(this.fields);
        flat.putAll(this.params);
        return flat;
    }

}
