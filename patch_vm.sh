sed -i 's/val countries: StateFlow<List<Country>> = combine(_countries, _searchQuery) .*/val countries: StateFlow<List<Country>> = _countries.asStateFlow()/' app/src/main/java/com/example/viewmodel/PredictorViewModel.kt
sed -i '52,74d' app/src/main/java/com/example/viewmodel/PredictorViewModel.kt
