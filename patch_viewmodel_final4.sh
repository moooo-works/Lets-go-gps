sed -i 's/import javax.inject.Inject/import javax.inject.Inject\nimport com.example.mockgps.utils.GeoDistanceMeters/g' app/src/main/java/com/example/mockgps/ui/settings/SettingsViewModel.kt
sed -i 's/com.example.mockgps.utils.GeoDistanceMeters/GeoDistanceMeters/g' app/src/main/java/com/example/mockgps/ui/settings/SettingsViewModel.kt
