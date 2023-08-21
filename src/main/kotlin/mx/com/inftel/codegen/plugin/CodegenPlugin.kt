/*
 * Copyright 2023 Santos Zatarain Vera
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mx.com.inftel.codegen.plugin

import mx.com.inftel.codegen.model.CodegenExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.tasks.JvmConstants
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

@Suppress("unused")
class CodegenPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("codegen", CodegenExtension::class.java)
        val outputDirectory = target.layout.buildDirectory.dir("codegen/generated")
        //
        val codegenJpaTask = target.tasks.register("codegen", CodegenTask::class.java) {
            it.group = "codegen"
            it.generateComposableData.set(extension.generateComposableData)
            it.generateConstrainedData.set(extension.generateConstrainedData)
            it.generateJsonAwareData.set(extension.generateJsonAwareData)
            it.generateOnlyData.set(extension.generateOnlyData)
            it.entities.addAllLater(target.provider { extension.entities })
            it.outputDirectory.set(outputDirectory)
        }
        //
        target.plugins.withType(JavaPlugin::class.java) {
            val sourceSetContainer = target.extensions.getByType(SourceSetContainer::class.java)
            val mainSourceSet = sourceSetContainer.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
            mainSourceSet.java.srcDir(outputDirectory)
            val compileJavaTask = target.tasks.getByName(JvmConstants.COMPILE_JAVA_TASK_NAME)
            compileJavaTask.dependsOn(codegenJpaTask)
        }
    }
}