package com.cleanroommc.gradle.util.json.deserialization.manifest;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ManifestVersionsAdapter implements JsonDeserializer<Map<String, ManifestVersion>> {

    public static final Type TYPE = new TypeToken<Map<String, ManifestVersion>>() { }.getType();

    @Override
    public Map<String, ManifestVersion> deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) throws JsonParseException {
        Map<String, ManifestVersion> out = new HashMap<>();
        for (JsonElement element : json.getAsJsonObject().get("versions").getAsJsonArray()) {
            ManifestVersion version = ctx.deserialize(element, ManifestVersion.class);
            out.put(version.id, version);
        }
        return out;
    }

}
