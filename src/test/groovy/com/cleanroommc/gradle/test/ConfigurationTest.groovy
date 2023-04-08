package com.cleanroommc.gradle.test

import com.cleanroommc.gradle.TestFoundation
import org.junit.jupiter.api.Test

import java.nio.file.Path

class ConfigurationTest extends TestFoundation {

    @Override
    void appendBuildScript(Path buildFile) {
        buildFile <<
                """
                dependencies {
                    // Vanilla 1.12.2
                    implementation cg.vanilla('1.12.2')
                    
                    // Vanilla 1.19.4
                    implementation cg.minecraft('1.19.4')
                    
                    // Minecraft 1.12.2, configured to use Forge 2860, with mcp@stable_39
                    implementation cg.minecraft('1.12.2') { mc -> 
                        mc.loader = 'forge'
                        mc.loaderVersion = '14.23.5.2860'
                        mc.mappingProvider = 'mcp'
                        mc.mappingVersion = 'stable_39'
                    }
                    
                    // Same as above but configured via a map
                    implementation cg.minecraft('1.12.2', [loader: 'forge', loaderVersion: '14.23.5.2860', mappingProvider: 'mcp', mappingVersion: 'stable_39'])
                    
                    // Minecraft 1.12.2 with Forge 2860 with mcp@stable_39
                    implementation cg.forge('1.12.2', '14.23.5.2860', 'stable_39')
                    
                    // Cleanroom Minecraft 1.0.0 (with defaulted mcp@stable_39 mapping)
                    implementation cg.cleanroom('1.0.0')
                    
                    // TODO:
                    // Cleanroom Minecraft 1.0.0 with a hypothetical cleanroom mapping extension of version 1 described with mcp@stable_39
                    implementation cg.cleanroom('1.0.0') { mc ->
                        mc.mappingProvider = 'cleanroom'
                        mc.mappingVersion = '1'
                    }
                    
                }

                task configurationTest {
                    doLast {
                        def implementationDeps = configurations.implementation.dependencies
                        implementationDeps.each { println it }
                    }
                }
                """
    }

    @Test
    void test() {
        def result = gradleRunner().withArguments('configurationTest').build()
        isSuccessful('configurationTest', result)
    }

}
