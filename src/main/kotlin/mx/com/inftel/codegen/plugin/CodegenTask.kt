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

@file:Suppress("DuplicatedCode")

package mx.com.inftel.codegen.plugin

import mx.com.inftel.codegen.model.CodegenAttribute
import mx.com.inftel.codegen.model.CodegenConstraint
import mx.com.inftel.codegen.model.CodegenEntity
import mx.com.inftel.codegen.model.CodegenType
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.BufferedWriter
import kotlin.io.path.createDirectories

abstract class CodegenTask : DefaultTask() {

    @get:Internal
    abstract val generateComposableData: Property<Boolean>

    @get:Internal
    abstract val generateConstrainedData: Property<Boolean>

    @get:Internal
    abstract val generateOnlyData: Property<Boolean>

    @get:Internal
    abstract val entities: NamedDomainObjectContainer<CodegenEntity>

    @get:Internal
    abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun generate() {
        for (entity in entities) {
            generateFullInterface(entity)
            generateData(entity)
            if (generateOnlyData.getOrElse(false)) return
            generateMetamodel(entity)
            generateEntity(entity)
            generateOrdersPredicates(entity)
            generateExtensions(entity)
        }
    }

    private fun generateMetamodel(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "${capitalized(entity.name)}Entity_.java"
        //
        val importStatements = buildSet {
            add("import jakarta.persistence.metamodel.*;")
            addImportsJava(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.mkdirs()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package ${packageName};")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            writer.appendLine("@StaticMetamodel(${capitalized(entity.name)}Entity.class)")
            writer.appendLine("public class ${capitalized(entity.name)}Entity_ {")
            writer.appendLine()
            for (attribute in entity.attributes) {
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Integer> ${decapitalized(attribute.name)};")
                    is CodegenType.Id.Long -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity,Long> ${decapitalized(attribute.name)};")
                    is CodegenType.Id.UUID -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, UUID> ${decapitalized(attribute.name)};")
                    CodegenType.Version.Integer -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Integer> ${decapitalized(attribute.name)};")
                    CodegenType.Version.Long -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Long> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.Integer -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Integer> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.Long -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Long> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.Boolean -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, Boolean> ${decapitalized(attribute.name)};")
                    is CodegenType.Basic.String -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, String> ${decapitalized(attribute.name)};")
                    is CodegenType.Basic.BigDecimal -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, BigDecimal> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.ByteArray -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, byte[]> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.UUID -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, UUID> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.LocalDateTime -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, LocalDateTime> ${decapitalized(attribute.name)};")
                    CodegenType.Basic.ZonedDateTime -> writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, ZonedDateTime> ${decapitalized(attribute.name)};")

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        writer.appendLine("    public static volatile SingularAttribute<${capitalized(entity.name)}Entity, ${capitalized(targetEntity.name)}Entity> ${decapitalized(attribute.name)};")
                    }
                }
                writer.appendLine()
            }
            writer.appendLine("}")
        }
    }

    private fun generateEntity(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "${capitalized(entity.name)}Entity.kt"
        //
        val importStatements = buildSet {
            add("import jakarta.persistence.*")
            addImportsKotlin(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.toPath().createDirectories()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package $packageName")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            generateEntityAnn(writer, entity)
            generateTableAnn(writer, entity)
            writer.appendLine("open class ${capitalized(entity.name)}Entity {")
            writer.appendLine()
            for (attribute in entity.attributes) {
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> {
                        writer.appendLine("    @get:Id")
                        if (type.autoGenerated) {
                            writer.appendLine("    @get:GeneratedValue(strategy = GenerationType.IDENTITY)")
                        }
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Int? = null")
                    }

                    is CodegenType.Id.Long -> {
                        writer.appendLine("    @get:Id")
                        if (type.autoGenerated) {
                            writer.appendLine("    @get:GeneratedValue(strategy = GenerationType.IDENTITY)")
                        }
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Long? = null")
                    }

                    is CodegenType.Id.UUID -> {
                        writer.appendLine("    @get:Id")
                        if (type.autoGenerated) {
                            writer.appendLine("    @get:GeneratedValue(strategy = GenerationType.UUID)")
                        }
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: UUID? = null")
                    }

                    CodegenType.Version.Integer -> {
                        writer.appendLine("    @get:Version")
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Int? = null")
                    }

                    CodegenType.Version.Long -> {
                        writer.appendLine("    @get:Version")
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Long? = null")
                    }

                    CodegenType.Basic.Integer -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Int? = null")
                    }

                    CodegenType.Basic.Long -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Long? = null")
                    }

                    CodegenType.Basic.Boolean -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: Boolean? = null")
                    }

                    is CodegenType.Basic.String -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: String? = null")
                    }

                    is CodegenType.Basic.BigDecimal -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: BigDecimal? = null")
                    }

                    CodegenType.Basic.ByteArray -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: ByteArray? = null")
                    }

                    CodegenType.Basic.UUID -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: UUID? = null")
                    }

                    CodegenType.Basic.LocalDateTime -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: LocalDateTime? = null")
                    }

                    CodegenType.Basic.ZonedDateTime -> {
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: ZonedDateTime? = null")
                    }

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        generateColumnAnn(writer, entity, attribute)
                        writer.appendLine("    open var ${decapitalized(attribute.name)}: ${capitalized(targetEntity.name)}Entity? = null")
                    }
                }
                writer.appendLine()
            }
            writer.appendLine("}")
        }
    }

    private fun generateEntityAnn(writer: BufferedWriter, entity: CodegenEntity) {
        val entityName = entity.entityName.orNull
        val params = buildList {
            if (!entityName.isNullOrBlank()) {
                add("name = \"${entityName}\"")
            }
        }
        val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
        writer.appendLine("@Entity$s")
    }

    private fun generateTableAnn(writer: BufferedWriter, entity: CodegenEntity) {
        val tableName = entity.tableName.orNull
        val tableSchema = entity.tableSchema.orNull
        val params = buildList {
            if (!tableName.isNullOrBlank()) {
                add("name = \"${tableName}\"")
            }
            if (!tableSchema.isNullOrBlank()) {
                add("schema = \"${tableSchema}\"")
            }
        }
        val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
        writer.appendLine("@Table$s")
    }

    private fun generateColumnAnn(writer: BufferedWriter, entity: CodegenEntity, attribute: CodegenAttribute) {
        val columnName = attribute.columnName.orNull
        val columnUnique = attribute.columnUnique.orNull
        val columnNullable = attribute.columnNullable.orNull
        val columnInsertable = attribute.columnInsertable.orNull
        val columnUpdatable = attribute.columnUpdatable.orNull
        val params = buildList {
            if (!columnName.isNullOrBlank()) {
                add("name = \"${columnName}\"")
            }
            if (columnUnique != null) {
                add("unique = $columnUnique")
            }
            if (columnNullable != null) {
                add("nullable = $columnNullable")
            }
            if (columnInsertable != null) {
                add("insertable = $columnInsertable")
            }
            if (columnUpdatable != null) {
                add("updatable = $columnUpdatable")
            }
            when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                is CodegenType.Id.Integer -> Unit
                is CodegenType.Id.Long -> Unit
                is CodegenType.Id.UUID -> Unit
                CodegenType.Version.Integer -> Unit
                CodegenType.Version.Long -> Unit
                CodegenType.Basic.Integer -> Unit
                CodegenType.Basic.Long -> Unit
                CodegenType.Basic.Boolean -> Unit

                is CodegenType.Basic.String -> {
                    val length = type.length
                    if (length != null) {
                        add("length = $length")
                    }
                }

                is CodegenType.Basic.BigDecimal -> {
                    val precision = type.precision
                    val scale = type.scale
                    if (precision != null) {
                        add("precision = $precision")
                    }
                    if (scale != null) {
                        add("scale = $scale")
                    }
                }

                CodegenType.Basic.ByteArray -> Unit
                CodegenType.Basic.UUID -> Unit
                CodegenType.Basic.LocalDateTime -> Unit
                CodegenType.Basic.ZonedDateTime -> Unit
                is CodegenType.OwningSide.ManyToOne -> Unit
            }
        }
        when (attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
            is CodegenType.Id.Integer -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            is CodegenType.Id.Long -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            is CodegenType.Id.UUID -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Version.Integer -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Version.Long -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.Integer -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.Long -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.Boolean -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            is CodegenType.Basic.String -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            is CodegenType.Basic.BigDecimal -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.ByteArray -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.UUID -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.LocalDateTime -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            CodegenType.Basic.ZonedDateTime -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:Column${s}")
            }

            is CodegenType.OwningSide.ManyToOne -> {
                val s = if (params.isNotEmpty()) params.joinToString(prefix = "(", postfix = ")") else ""
                writer.appendLine("    @get:ManyToOne")
                writer.appendLine("    @get:JoinColumn${s}")
            }
        }
    }

    private fun generateFullInterface(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "I${capitalized(entity.name)}.kt"
        val generateConstrainedData = generateConstrainedData.getOrElse(true)
        //
        val importStatements = buildSet {
            if (generateConstrainedData) {
                add("import jakarta.validation.constraints.*")
            }
            addImportsKotlin(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.toPath().createDirectories()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package $packageName")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            writer.appendLine("interface I${capitalized(entity.name)} {")
            writer.appendLine()
            for (attribute in entity.attributes) {
                val constraints = attribute.constraints.orNull ?: emptyList()
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Int?")
                    }

                    is CodegenType.Id.Long -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Long?")
                    }

                    is CodegenType.Id.UUID -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: UUID?")
                    }

                    CodegenType.Version.Integer -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Int?")
                    }

                    CodegenType.Version.Long -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Long?")
                    }

                    CodegenType.Basic.Integer -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Int?")
                    }

                    CodegenType.Basic.Long -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Long?")
                    }

                    CodegenType.Basic.Boolean -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: Boolean?")
                    }

                    is CodegenType.Basic.String -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: String?")
                    }

                    is CodegenType.Basic.BigDecimal -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: BigDecimal?")
                    }

                    CodegenType.Basic.ByteArray -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: ByteArray?")
                    }

                    CodegenType.Basic.UUID -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: UUID?")
                    }

                    CodegenType.Basic.LocalDateTime -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: LocalDateTime?")
                    }

                    CodegenType.Basic.ZonedDateTime -> {
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        writer.appendLine("    var ${decapitalized(attribute.name)}: ZonedDateTime?")
                    }

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        if (generateConstrainedData) {
                            for (constraint in constraints) {
                                generateConstraintAnn(writer, constraint)
                            }
                        }
                        when (targetId.columnType.get()) {
                            is CodegenType.Id.Integer -> writer.appendLine("    var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Int?")
                            is CodegenType.Id.Long -> writer.appendLine("    var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Long?")
                            is CodegenType.Id.UUID -> writer.appendLine("    var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: UUID?")
                            else -> throw RuntimeException("This code should not be reached")
                        }
                    }
                }
                writer.appendLine()
            }
            writer.appendLine("}")
        }
    }

    private fun generateData(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "${capitalized(entity.name)}Data.kt"
        val generateComposableData = generateComposableData.getOrElse(false)
        //
        val importStatements = buildSet {
            if (generateComposableData) {
                add("import androidx.compose.runtime.getValue")
                add("import androidx.compose.runtime.mutableStateOf")
                add("import androidx.compose.runtime.setValue")
            }
            addImportsKotlin(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.toPath().createDirectories()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package $packageName")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            writer.appendLine("open class ${capitalized(entity.name)}Data : I${capitalized(entity.name)} {")
            writer.appendLine()
            for (attribute in entity.attributes) {
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? = null")
                        }
                    }

                    is CodegenType.Id.Long -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? = null")
                        }
                    }

                    is CodegenType.Id.UUID -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: UUID? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: UUID? = null")
                        }
                    }

                    CodegenType.Version.Integer -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? = null")
                        }
                    }

                    CodegenType.Version.Long -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? = null")
                        }
                    }

                    CodegenType.Basic.Integer -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Int? = null")
                        }
                    }

                    CodegenType.Basic.Long -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Long? = null")
                        }
                    }

                    CodegenType.Basic.Boolean -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Boolean? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: Boolean? = null")
                        }
                    }

                    is CodegenType.Basic.String -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: String? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: String? = null")
                        }
                    }

                    is CodegenType.Basic.BigDecimal -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: BigDecimal? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: BigDecimal? = null")
                        }
                    }

                    CodegenType.Basic.ByteArray -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: ByteArray? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: ByteArray? = null")
                        }
                    }

                    CodegenType.Basic.UUID -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: UUID? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: UUID? = null")
                        }
                    }

                    CodegenType.Basic.LocalDateTime -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: LocalDateTime? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: LocalDateTime? = null")
                        }
                    }

                    CodegenType.Basic.ZonedDateTime -> {
                        if (generateComposableData) {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: ZonedDateTime? by mutableStateOf(null)")
                        } else {
                            writer.appendLine("    override var ${decapitalized(attribute.name)}: ZonedDateTime? = null")
                        }
                    }

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        if (generateComposableData) {
                            when (targetId.columnType.get()) {
                                is CodegenType.Id.Integer -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Int? by mutableStateOf(null)")
                                is CodegenType.Id.Long -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Long? by mutableStateOf(null)")
                                is CodegenType.Id.UUID -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: UUID? by mutableStateOf(null)")
                                else -> throw RuntimeException("This code should not be reached")
                            }
                        } else {
                            when (targetId.columnType.get()) {
                                is CodegenType.Id.Integer -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Int? = null")
                                is CodegenType.Id.Long -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: Long? = null")
                                is CodegenType.Id.UUID -> writer.appendLine("    override var ${decapitalized(attribute.name)}${capitalized(targetId.name)}: UUID? = null")
                                else -> throw RuntimeException("This code should not be reached")
                            }
                        }
                    }
                }
                writer.appendLine()
            }
            writer.appendLine("}")
        }
    }

    private fun generateConstraintAnn(writer: BufferedWriter, constraint: CodegenConstraint) {
        when (constraint) {
            CodegenConstraint.Builtin.NotNull -> writer.appendLine("    @get:NotNull")
            CodegenConstraint.Builtin.NotEmpty -> writer.appendLine("    @get:NotEmpty")
            CodegenConstraint.Builtin.NotBlank -> writer.appendLine("    @get:NotBlank")
        }
    }

    private fun generateOrdersPredicates(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "${capitalized(entity.name)}OrdersPredicates.kt"
        //
        val importStatements = buildSet {
            addImportsKotlin(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.toPath().createDirectories()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package $packageName")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            //
            writer.appendLine("sealed class ${capitalized(entity.name)}Order {")
            for (attribute in entity.attributes) {
                writer.appendLine("    sealed class ${capitalized(attribute.name)}: ${capitalized(entity.name)}Order() {")
                writer.appendLine("        object Ascending: ${capitalized(attribute.name)}()")
                writer.appendLine("        object Descending: ${capitalized(attribute.name)}()")
                writer.appendLine("    }")
            }
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("sealed class ${capitalized(entity.name)}Predicate {")
            for (attribute in entity.attributes) {
                writer.appendLine("    sealed class ${capitalized(attribute.name)}: ${capitalized(entity.name)}Predicate() {")
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> generatePredicatesInt(writer, attribute)
                    is CodegenType.Id.Long -> generatePredicatesLong(writer, attribute)
                    is CodegenType.Id.UUID -> generatePredicatesUUID(writer, attribute)
                    CodegenType.Version.Integer -> generatePredicatesInt(writer, attribute)
                    CodegenType.Version.Long -> generatePredicatesLong(writer, attribute)
                    CodegenType.Basic.Integer -> generatePredicatesInt(writer, attribute)
                    CodegenType.Basic.Long -> generatePredicatesLong(writer, attribute)
                    CodegenType.Basic.Boolean -> generatePredicatesBoolean(writer, attribute)
                    is CodegenType.Basic.String -> generatePredicatesString(writer, attribute)
                    is CodegenType.Basic.BigDecimal -> generatePredicatesBigDecimal(writer, attribute)
                    CodegenType.Basic.ByteArray -> generatePredicatesByteArray(writer, attribute)
                    CodegenType.Basic.UUID -> generatePredicatesUUID(writer, attribute)
                    CodegenType.Basic.LocalDateTime -> generatePredicatesLocalDateTime(writer, attribute)
                    CodegenType.Basic.ZonedDateTime -> generatePredicatesZonedDateTime(writer, attribute)

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        when (targetId.columnType.get()) {
                            is CodegenType.Id.Integer -> generatePredicatesInt(writer, attribute)
                            is CodegenType.Id.Long -> generatePredicatesLong(writer, attribute)
                            is CodegenType.Id.UUID -> generatePredicatesUUID(writer, attribute)
                            else -> throw RuntimeException("This code should not be reached")
                        }
                    }
                }
                writer.appendLine("    }")
            }
            writer.appendLine("}")
            writer.appendLine()
        }
    }

    private fun generatePredicatesInt(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: Int, val y: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: Int): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: Int, val y: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: Int): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: Int): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesLong(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: Long, val y: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: Long): ${capitalized(attribute.name)}()")
        //writer.appendLine("       class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: Long, val y: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: Long): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: Long): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesBoolean(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: Boolean, val y: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: Boolean): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: Boolean, val y: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: Boolean): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: Boolean): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesString(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: String, val y: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: String, val y: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesBigDecimal(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: BigDecimal, val y: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: BigDecimal): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: BigDecimal, val y: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: BigDecimal): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: BigDecimal): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesByteArray(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: ByteArray, val y: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: ByteArray): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: ByteArray, val y: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: ByteArray): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: ByteArray): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesUUID(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: UUID, val y: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: UUID): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: UUID, val y: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: UUID): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: UUID): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesLocalDateTime(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: LocalDateTime, val y: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: LocalDateTime, val y: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: LocalDateTime): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generatePredicatesZonedDateTime(writer: BufferedWriter, attribute: CodegenAttribute) {
        writer.appendLine("        class Between(val x: ZonedDateTime, val y: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class Equal(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThan(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class GreaterThanOrEqualTo(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNotNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        object IsNull: ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThan(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class LessThanOrEqualTo(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class Like(val x: String): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotBetween(val x: ZonedDateTime, val y: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotEqual(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThan(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotGreaterThanOrEqualTo(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThan(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        writer.appendLine("        class NotLessThanOrEqualTo(val x: ZonedDateTime): ${capitalized(attribute.name)}()")
        //writer.appendLine("        class NotLike(val x: String): ${capitalized(attribute.name)}()")
    }

    private fun generateExtensions(entity: CodegenEntity) {
        val packageName = entity.packageName.getOrElse("")
        val packageDir = packageName.replace('.', '/')
        val outputFilename = "${capitalized(entity.name)}Extension.kt"
        //
        val importStatements = buildSet {
            add("import jakarta.persistence.*")
            add("import jakarta.persistence.criteria.*")
            addImportsKotlin(entity)
        }
        //
        val outputDirectory = outputDirectory.dir(packageDir).get()
        val outputFile = outputDirectory.file(outputFilename)
        outputDirectory.asFile.toPath().createDirectories()
        outputFile.asFile.bufferedWriter().use { writer ->
            if (packageName.isNotBlank()) {
                writer.appendLine("package $packageName")
                writer.appendLine()
            }
            if (importStatements.isNotEmpty()) {
                for (importStatement in importStatements.sorted()) {
                    writer.appendLine(importStatement)
                }
                writer.appendLine()
            }
            //
            writer.appendLine("fun ${capitalized(entity.name)}Order.toOrder(criteriaBuilder: CriteriaBuilder, root: From<*, ${capitalized(entity.name)}Entity>): Order {")
            writer.appendLine("    return when (this) {")
            for (attribute in entity.attributes) {
                val relId = when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        "[${capitalized(targetEntity.name)}Entity_.${decapitalized(targetId.name)}]"
                    }

                    else -> ""
                }
                writer.appendLine("        ${capitalized(entity.name)}Order.${capitalized(attribute.name)}.Ascending -> criteriaBuilder.asc(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId})")
                writer.appendLine("        ${capitalized(entity.name)}Order.${capitalized(attribute.name)}.Descending -> criteriaBuilder.desc(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId})")
            }
            writer.appendLine("    }")
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("fun ${capitalized(entity.name)}Predicate.toPredicate(criteriaBuilder: CriteriaBuilder, root: From<*, ${capitalized(entity.name)}Entity>): Predicate {")
            writer.appendLine("    return when (this) {")
            for (attribute in entity.attributes) {
                val relId = when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        "[${capitalized(targetEntity.name)}Entity_.${decapitalized(targetId.name)}]"
                    }

                    else -> ""
                }
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.Between -> criteriaBuilder.between(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x, this.y)")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.Equal -> criteriaBuilder.equal(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.GreaterThan -> criteriaBuilder.greaterThan(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.GreaterThanOrEqualTo -> criteriaBuilder.greaterThanOrEqualTo(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                writer.appendLine("        ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.IsNotNull -> criteriaBuilder.isNotNull(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId})")
                writer.appendLine("        ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.IsNull -> criteriaBuilder.isNull(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId})")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.LessThan -> criteriaBuilder.lessThan(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.LessThanOrEqualTo -> criteriaBuilder.lessThanOrEqualTo(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                if (attribute.columnType.get() is CodegenType.Basic.String) {
                    writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.Like -> criteriaBuilder.like(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                }
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotBetween -> criteriaBuilder.not(criteriaBuilder.between(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x, this.y))")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotEqual -> criteriaBuilder.notEqual(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotGreaterThan -> criteriaBuilder.not(criteriaBuilder.greaterThan(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x))")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotGreaterThanOrEqualTo -> criteriaBuilder.not(criteriaBuilder.greaterThanOrEqualTo(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x))")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotLessThan -> criteriaBuilder.not(criteriaBuilder.lessThan(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x))")
                writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotLessThanOrEqualTo -> criteriaBuilder.not(criteriaBuilder.lessThanOrEqualTo(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x))")
                if (attribute.columnType.get() is CodegenType.Basic.String) {
                    writer.appendLine("        is ${capitalized(entity.name)}Predicate.${capitalized(attribute.name)}.NotLike -> criteriaBuilder.notLike(root[${capitalized(entity.name)}Entity_.${decapitalized(attribute.name)}]${relId}, this.x)")
                }
            }
            writer.appendLine("    }")
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("fun EntityManager.count${capitalized(entity.name)}(distinct: Boolean = false, filtering: List<${capitalized(entity.name)}Predicate> = emptyList()): Long {")
            writer.appendLine("    val criteriaBuilder = this.criteriaBuilder")
            writer.appendLine("    val criteriaQuery = criteriaBuilder.createQuery(Long::class.java)")
            writer.appendLine("    val root = criteriaQuery.from(${capitalized(entity.name)}Entity::class.java)")
            writer.appendLine("    criteriaQuery.select(criteriaBuilder.count(root))")
            writer.appendLine("    criteriaQuery.distinct(distinct)")
            writer.appendLine("    val predicates = buildList {")
            writer.appendLine("        for (predicate in filtering) {")
            writer.appendLine("            add(predicate.toPredicate(criteriaBuilder, root))")
            writer.appendLine("        }")
            writer.appendLine("    }")
            writer.appendLine("    if (predicates.isNotEmpty()) {")
            writer.appendLine("        criteriaQuery.where(*predicates.toTypedArray())")
            writer.appendLine("    }")
            writer.appendLine("    val typedQuery = this.createQuery(criteriaQuery)")
            writer.appendLine("    return typedQuery.singleResult")
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("fun EntityManager.list${capitalized(entity.name)}(distinct: Boolean = false, filtering: List<${capitalized(entity.name)}Predicate> = emptyList(), ordering: List<${capitalized(entity.name)}Order> = emptyList(), lockMode: LockModeType = LockModeType.NONE, firstResult: Int = -1, maxResults: Int = -1): List<${capitalized(entity.name)}Entity> {")
            writer.appendLine("    val criteriaBuilder = this.criteriaBuilder")
            writer.appendLine("    val criteriaQuery = criteriaBuilder.createQuery(${capitalized(entity.name)}Entity::class.java)")
            writer.appendLine("    val root = criteriaQuery.from(${capitalized(entity.name)}Entity::class.java)")
            writer.appendLine("    criteriaQuery.select(root)")
            writer.appendLine("    criteriaQuery.distinct(distinct)")
            writer.appendLine("    val predicates = buildList {")
            writer.appendLine("        for (predicate in filtering) {")
            writer.appendLine("            add(predicate.toPredicate(criteriaBuilder, root))")
            writer.appendLine("        }")
            writer.appendLine("    }")
            writer.appendLine("    val orders = buildList {")
            writer.appendLine("        for (order in ordering) {")
            writer.appendLine("            add(order.toOrder(criteriaBuilder, root))")
            writer.appendLine("        }")
            writer.appendLine("    }")
            writer.appendLine("    if (predicates.isNotEmpty()) {")
            writer.appendLine("        criteriaQuery.where(*predicates.toTypedArray())")
            writer.appendLine("    }")
            writer.appendLine("    if (ordering.isNotEmpty()) {")
            writer.appendLine("        criteriaQuery.orderBy(*orders.toTypedArray())")
            writer.appendLine("    }")
            writer.appendLine("    val typedQuery = this.createQuery(criteriaQuery)")
            writer.appendLine("    typedQuery.setLockMode(lockMode)")
            writer.appendLine("    if (firstResult >= 0) {")
            writer.appendLine("        typedQuery.setFirstResult(firstResult)")
            writer.appendLine("    }")
            writer.appendLine("    if (maxResults >= 0) {")
            writer.appendLine("        typedQuery.setMaxResults(maxResults)")
            writer.appendLine("    }")
            writer.appendLine("    return typedQuery.resultList")
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("@Suppress(\"UNUSED_PARAMETER\")")
            writer.appendLine("fun I${capitalized(entity.name)}.copyInsertablePropertiesTo(entityManager: EntityManager, entity: ${capitalized(entity.name)}Entity) {")
            for (attribute in entity.attributes) {
                val columnInsertable = attribute.columnInsertable.getOrElse(true)
                if (!columnInsertable) continue
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> Unit
                    is CodegenType.Id.Long -> Unit
                    is CodegenType.Id.UUID -> Unit
                    CodegenType.Version.Integer -> Unit
                    CodegenType.Version.Long -> Unit
                    CodegenType.Basic.Integer -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Long -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Boolean -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.String -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.BigDecimal -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ByteArray -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.UUID -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.LocalDateTime -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ZonedDateTime -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        writer.appendLine("    entity.${decapitalized(attribute.name)} = if (this.${decapitalized(attribute.name)}${capitalized(targetId.name)} == null) {")
                        writer.appendLine("        null")
                        writer.appendLine("    } else {")
                        writer.appendLine("        entityManager.find(${capitalized(targetEntity.name)}Entity::class.java, this.${decapitalized(attribute.name)}${capitalized(targetId.name)})")
                        writer.appendLine("    }")
                    }
                }
            }
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("@Suppress(\"UNUSED_PARAMETER\")")
            writer.appendLine("fun I${capitalized(entity.name)}.copyUpdatablePropertiesTo(entityManager: EntityManager, entity: ${capitalized(entity.name)}Entity) {")
            for (attribute in entity.attributes) {
                val columnUpdatable = attribute.columnUpdatable.getOrElse(true)
                if (!columnUpdatable) continue
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> Unit
                    is CodegenType.Id.Long -> Unit
                    is CodegenType.Id.UUID -> Unit
                    CodegenType.Version.Integer -> Unit
                    CodegenType.Version.Long -> Unit
                    CodegenType.Basic.Integer -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Long -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Boolean -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.String -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.BigDecimal -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ByteArray -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.UUID -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.LocalDateTime -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ZonedDateTime -> writer.appendLine("    entity.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        writer.appendLine("    entity.${decapitalized(attribute.name)} = if (this.${decapitalized(attribute.name)}${capitalized(targetId.name)} == null) {")
                        writer.appendLine("        null")
                        writer.appendLine("    } else {")
                        writer.appendLine("        entityManager.find(${capitalized(targetEntity.name)}Entity::class.java, this.${decapitalized(attribute.name)}${capitalized(targetId.name)})")
                        writer.appendLine("    }")
                    }
                }
            }
            writer.appendLine("}")
            writer.appendLine()
            //
            writer.appendLine("fun ${capitalized(entity.name)}Entity.copyPropertiesTo(data: I${capitalized(entity.name)}) {")
            for (attribute in entity.attributes) {
                when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                    is CodegenType.Id.Integer -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Id.Long -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Id.UUID -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Version.Integer -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Version.Long -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Integer -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Long -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.Boolean -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.String -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    is CodegenType.Basic.BigDecimal -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ByteArray -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.UUID -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.LocalDateTime -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")
                    CodegenType.Basic.ZonedDateTime -> writer.appendLine("    data.${decapitalized(attribute.name)} = this.${decapitalized(attribute.name)}")

                    is CodegenType.OwningSide.ManyToOne -> {
                        val targetEntity = try {
                            entities.getByName(type.target)
                        } catch (e: UnknownDomainObjectException) {
                            throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                        }
                        val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                        when (targetId.columnType.get()) {
                            is CodegenType.Id.Integer -> writer.appendLine("    data.${decapitalized(attribute.name)}${capitalized(targetId.name)} = this.${decapitalized(attribute.name)}?.${decapitalized(targetId.name)}")
                            is CodegenType.Id.Long -> writer.appendLine("    data.${decapitalized(attribute.name)}${capitalized(targetId.name)} = this.${decapitalized(attribute.name)}?.${decapitalized(targetId.name)}")
                            is CodegenType.Id.UUID -> writer.appendLine("    data.${decapitalized(attribute.name)}${capitalized(targetId.name)} = this.${decapitalized(attribute.name)}?.${decapitalized(targetId.name)}")
                            else -> throw RuntimeException("This code should not be reached")
                        }
                    }
                }
            }
            writer.appendLine("}")
            writer.appendLine()
        }
    }

    private fun MutableSet<String>.addImportsJava(entity: CodegenEntity) {
        for (attribute in entity.attributes) {
            when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                is CodegenType.Id.Integer -> Unit
                is CodegenType.Id.Long -> Unit
                is CodegenType.Id.UUID -> add("import java.util.UUID;")
                CodegenType.Version.Integer -> Unit
                CodegenType.Version.Long -> Unit
                CodegenType.Basic.Integer -> Unit
                CodegenType.Basic.Long -> Unit
                CodegenType.Basic.Boolean -> Unit
                is CodegenType.Basic.String -> Unit
                is CodegenType.Basic.BigDecimal -> add("import java.math.BigDecimal;")
                CodegenType.Basic.ByteArray -> Unit
                CodegenType.Basic.UUID -> add("import java.util.UUID;")
                CodegenType.Basic.LocalDateTime -> add("import java.time.LocalDateTime;")
                CodegenType.Basic.ZonedDateTime -> add("import java.time.ZonedDateTime;")
                is CodegenType.OwningSide.ManyToOne -> {
                    val targetEntity = try {
                        entities.getByName(type.target)
                    } catch (e: UnknownDomainObjectException) {
                        throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                    }
                    val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                    if (targetId.columnType.get() is CodegenType.Id.UUID) {
                        add("import java.util.UUID;")
                    }
                    val targetEntityPackage = targetEntity.packageName.getOrElse("")
                    if (entity.packageName.getOrElse("") != targetEntityPackage) {
                        if (targetEntityPackage.isBlank()) {
                            add("import ${capitalized(targetEntity.name)}Entity;")
                        } else {
                            add("import ${targetEntityPackage}.${capitalized(targetEntity.name)}Entity;")
                        }
                    }
                }
            }
        }
    }

    private fun MutableSet<String>.addImportsKotlin(entity: CodegenEntity) {
        for (attribute in entity.attributes) {
            when (val type = attribute.columnType.orNull ?: throw GradleException("'columnType' of attribute '${attribute.name}' in entity '${entity.name}' is not declared")) {
                is CodegenType.Id.Integer -> Unit
                is CodegenType.Id.Long -> Unit
                is CodegenType.Id.UUID -> add("import java.util.UUID")
                CodegenType.Version.Integer -> Unit
                CodegenType.Version.Long -> Unit
                CodegenType.Basic.Integer -> Unit
                CodegenType.Basic.Long -> Unit
                CodegenType.Basic.Boolean -> Unit
                is CodegenType.Basic.String -> Unit
                is CodegenType.Basic.BigDecimal -> add("import java.math.BigDecimal")
                CodegenType.Basic.ByteArray -> Unit
                CodegenType.Basic.UUID -> add("import java.util.UUID")
                CodegenType.Basic.LocalDateTime -> add("import java.time.LocalDateTime")
                CodegenType.Basic.ZonedDateTime -> add("import java.time.ZonedDateTime")
                is CodegenType.OwningSide.ManyToOne -> {
                    val targetEntity = try {
                        entities.getByName(type.target)
                    } catch (e: UnknownDomainObjectException) {
                        throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' is a unknown entity")
                    }
                    val targetId = targetEntity.attributes.firstOrNull { it.columnType.orNull is CodegenType.Id } ?: throw GradleException("'target' of attribute '${attribute.name}' in entity '${entity.name}' does not declare an identifier")
                    if (targetId.columnType.get() is CodegenType.Id.UUID) {
                        add("import java.util.UUID")
                    }
                    val targetEntityPackage = targetEntity.packageName.getOrElse("")
                    if (entity.packageName.getOrElse("") != targetEntityPackage) {
                        if (targetEntityPackage.isBlank()) {
                            add("import ${capitalized(targetEntity.name)}Entity")
                        } else {
                            add("import ${targetEntityPackage}.${capitalized(targetEntity.name)}Entity")
                        }
                    }
                }
            }
        }
    }
}