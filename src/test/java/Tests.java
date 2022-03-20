import com.cleanroommc.gradle.extensions.MinecraftExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Tests {

    @Test
    public void main() {
        Project project = ProjectBuilder.builder().build();

        // Load
        project.getPluginManager().apply("com.cleanroommc.gradle");

        // Assert default maven repos
        Assertions.assertEquals(1, project.getRepositories().stream().filter(ar -> ar.getName().equals("minecraft")).count());
        Assertions.assertEquals(1, project.getRepositories().stream().filter(ar -> ar.getName().equals("cleanroom")).count());

        // Check default runDir
        Assertions.assertEquals("run", MinecraftExtension.get(project).getRunDir());

        Assertions.assertNotNull(project.getTasks().findByPath("runClient"));
        Assertions.assertNotNull(project.getTasks().findByPath("runServer"));
    }

}
