cat << 'INNER' >> app/src/main/java/com/example/models/Models.kt

data class SearchItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val logoUrl: String?,
    val type: String
)
INNER
