# Remove HighlightedText completely
sed -i '318,357d' app/src/main/java/com/example/MainActivity.kt

# Revert HighlightedText calls
sed -i 's/HighlightedText(/Text(/g' app/src/main/java/com/example/MainActivity.kt
sed -i '/query = searchQuery,/d' app/src/main/java/com/example/MainActivity.kt

# Remove searchQuery param from headers
sed -i 's/searchQuery: String = "",//g' app/src/main/java/com/example/MainActivity.kt
sed -i 's/searchQuery: String = ""//g' app/src/main/java/com/example/MainActivity.kt
sed -i 's/searchQuery = searchQuery,//g' app/src/main/java/com/example/MainActivity.kt
