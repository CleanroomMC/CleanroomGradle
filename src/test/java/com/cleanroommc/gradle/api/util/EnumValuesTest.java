package com.cleanroommc.gradle.api.util;

import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.InvalidUserDataException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumValuesTest {

    @Test
    void parsesIgnoreCaseHyphensAndPadding() {
        assertEquals(ProjectMode.USERDEV, EnumValues.parse(ProjectMode.class, "userdev"));
        assertEquals(ProjectMode.LOADER, EnumValues.parse(ProjectMode.class, " Loader "));
        assertEquals(Environment.REOBF_SRG, EnumValues.parse(Environment.class, "reobf-srg"));
        assertEquals(Side.CLIENT, EnumValues.parse(Side.class, "client"));
        assertEquals(WriteMappings.Direction.MCP_TO_SRG, EnumValues.parse(WriteMappings.Direction.class, "mcp-to-srg"));
        assertEquals(IMappingFile.Format.TSRG, EnumValues.parse(IMappingFile.Format.class, "tsrg"));
    }

    @Test
    void unknownValueListsConstants() {
        var error = assertThrows(InvalidUserDataException.class, () -> EnumValues.parse(ProjectMode.class, "nope"));
        assertTrue(error.getMessage().contains("Unknown ProjectMode 'nope'"));
        assertTrue(error.getMessage().contains("VANILLA, LOADER, USERDEV"));
    }

    @Test
    void blankValueListsConstants() {
        var error = assertThrows(InvalidUserDataException.class, () -> EnumValues.parse(ProjectMode.class, "  "));
        assertTrue(error.getMessage().contains("Missing ProjectMode"));
        assertTrue(error.getMessage().contains("VANILLA, LOADER, USERDEV"));
    }

}
