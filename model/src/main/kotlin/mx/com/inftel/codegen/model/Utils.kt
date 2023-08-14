package mx.com.inftel.codegen.model

@DslMarker
annotation class CodegenDslMarker

@CodegenDslMarker
fun CodegenExtension.entity(
    name: String,
    packageName: String,
    entityName: String? = null,
    tableName: String? = null,
    tableSchema: String? = null,
    block: CodegenEntity.() -> Unit
) {
    entities.register(name) {
        it.packageName.set(packageName)
        it.entityName.set(entityName)
        it.tableName.set(tableName)
        it.tableSchema.set(tableSchema)
        it.block()
    }
}

@CodegenDslMarker
fun CodegenEntity.attribute(
    name: String,
    columnType: CodegenType,
    columnName: String? = null,
    columnNullable: Boolean? = null,
    columnInsertable: Boolean? = null,
    columnUpdatable: Boolean? = null
) {
    attributes.register(name) {
        it.columnType.set(columnType)
        it.columnName.set(columnName)
        it.columnNullable.set(columnNullable)
        it.columnInsertable.set(columnInsertable)
        it.columnUpdatable.set(columnUpdatable)
    }
}

@CodegenDslMarker
fun CodegenEntity.attribute(
    name: String,
    columnType: CodegenType,
    columnName: String? = null,
    columnNullable: Boolean? = null,
    columnInsertable: Boolean? = null,
    columnUpdatable: Boolean? = null,
    block: CodegenAttribute.() -> Unit = {}
) {
    attributes.register(name) {
        it.columnType.set(columnType)
        it.columnName.set(columnName)
        it.columnNullable.set(columnNullable)
        it.columnInsertable.set(columnInsertable)
        it.columnUpdatable.set(columnUpdatable)
        it.block()
    }
}

@CodegenDslMarker
fun CodegenAttribute.notNull() {
    constraints.add(CodegenConstraint.Builtin.NotNull)
}

@CodegenDslMarker
fun CodegenAttribute.notBlank() {
    constraints.add(CodegenConstraint.Builtin.NotBlank)
}

@CodegenDslMarker
fun CodegenAttribute.notEmpty() {
    constraints.add(CodegenConstraint.Builtin.NotEmpty)
}