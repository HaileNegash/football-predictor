cat << 'INNER' >> app/src/main/java/com/example/MainActivity.kt

@Composable
fun SearchItemRow(item: com.example.models.SearchItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TBD */ }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.logoUrl != null) {
                AsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Icon(
            imageVector = if (item.type == "LEAGUE") Icons.Filled.PushPin else Icons.Outlined.StarOutline,
            contentDescription = "Favorite",
            tint = Color.Gray,
            modifier = Modifier.size(24.dp)
        )
    }
}
INNER
