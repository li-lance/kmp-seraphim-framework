package __PACKAGE_NAME__.taskboard

private val q = '"'.toString()

private fun String.jsonEscaped(): String = buildString {
    for (character in this@jsonEscaped) {
        when (character) {
            '"' -> append('\\').append('"')
            '\\' -> append('\\').append('\\')
            '\n' -> append('\\').append('n')
            '\r' -> append('\\').append('r')
            '\t' -> append('\\').append('t')
            '\b' -> append('\\').append('b')
            '\u000C' -> append('\\').append('f')
            else -> if (character.code < 0x20) {
                append('\\').append('u')
                append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
}

fun BoardSnapshot.toJson(): String {
    val columnsJson = columns.joinToString(",") { column ->
        "{" + q + "id" + q + ":" + column.id.value +
            "," + q + "name" + q + ":" + q + column.name.jsonEscaped() + q +
            "," + q + "kind" + q + ":" + q + column.kind.name + q +
            "," + q + "rank" + q + ":" + column.rank + "}"
    }
    val tasksJson = columns.joinToString(",") { column ->
        val items = tasks[column.id].orEmpty().joinToString(",") { it.toJson() }
        "{" + q + "columnId" + q + ":" + column.id.value + "," + q + "tasks" + q + ":[" + items + "]}"
    }
    val archivedJson = archivedTasks.joinToString(",") { it.toJson() }
    return "{" + q + "columns" + q + ":[" + columnsJson + "]," +
        q + "tasks" + q + ":[" + tasksJson + "]," +
        q + "archivedTasks" + q + ":[" + archivedJson + "]}"
}

private fun TaskItem.toJson(): String {
    val notesJson = notes?.let { q + it.jsonEscaped() + q } ?: "null"
    return "{" + q + "id" + q + ":" + id.value +
        "," + q + "columnId" + q + ":" + columnId.value +
        "," + q + "title" + q + ":" + q + title.jsonEscaped() + q +
        "," + q + "notes" + q + ":" + notesJson +
        "," + q + "dueDate" + q + ":" + (dueDate?.value ?: "null") +
        "," + q + "rank" + q + ":" + rank +
        "," + q + "createdAt" + q + ":" + createdAt.toEpochMilliseconds() +
        "," + q + "archivedAt" + q + ":" + (archivedAt?.toEpochMilliseconds() ?: "null") + "}"
}
