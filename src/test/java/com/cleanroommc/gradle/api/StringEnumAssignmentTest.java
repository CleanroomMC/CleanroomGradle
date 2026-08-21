package com.cleanroommc.gradle.api;

import com.cleanroommc.gradle.api.ext.CleanroomExtension;
import com.cleanroommc.gradle.api.ext.ProjectMode;
import com.cleanroommc.gradle.api.task.CleanroomInfo;
import com.cleanroommc.gradle.api.task.mc.RunMinecraft;
import com.cleanroommc.gradle.api.task.mcp.WriteMappings;
import com.cleanroommc.gradle.api.task.sas.StripSideOnlyJar;
import com.cleanroommc.gradle.api.util.Environment;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringEnumAssignmentTest {

    @TempDir
    Path projectDir;

    @Test
    void extensionAndTasksAcceptStringEnums() {
        var project = ProjectBuilder.builder().withProjectDir(this.projectDir.toFile()).build();
        var ext = project.getExtensions().create("cleanroom", CleanroomExtension.class);
        ext.setMode("loader");
        assertEquals(ProjectMode.LOADER, ext.getMode().get());

        var run = project.getTasks().register("run", RunMinecraft.class).get();
        run.setSide("server");
        run.setEnv("reobf-srg");
        assertEquals(Side.SERVER, run.getSide().get());
        assertEquals(Environment.REOBF_SRG, run.getEnv().get());

        var strip = project.getTasks().register("strip", StripSideOnlyJar.class).get();
        strip.setTargetSide("client");
        assertEquals(Side.CLIENT, strip.getTargetSide().get());

        var write = project.getTasks().register("write", WriteMappings.class).get();
        write.setDirection("mcp-to-srg");
        write.setFormat("tsrg");
        assertEquals(WriteMappings.Direction.MCP_TO_SRG, write.getDirection().get());
        assertEquals(IMappingFile.Format.TSRG, write.getFormat().get());

        var info = project.getTasks().register("info", CleanroomInfo.class).get();
        info.setMode("vanilla");
        assertEquals(ProjectMode.VANILLA, info.getMode().get());
    }

}
