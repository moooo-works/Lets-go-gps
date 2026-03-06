sed -i 's/Text("Schema Version: \\${preview.schemaVersion}")/Text("Schema Version: ${preview.schemaVersion}")/g' app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt
sed -i 's/Text("Saved Locations: \\${preview.savedLocationsCount}")/Text("Saved Locations: ${preview.savedLocationsCount}")/g' app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt
sed -i 's/Text("Routes: \\${preview.routesCount}")/Text("Routes: ${preview.routesCount}")/g' app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt
