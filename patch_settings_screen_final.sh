cat << 'INNER_EOF' > app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt.patch
--- app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt
+++ app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt
@@ -36,13 +36,13 @@
         contract = ActivityResultContracts.OpenDocument()
     ) { uri: Uri? ->
         uri?.let {
-            viewModel.parseImportData(it) { success, preview, message ->
+            viewModel.parseImportData(it) { success, preview, _ ->
                 if (success) {
                     importPreview = preview
                     showImportDialog = true
                 } else {
-                    Toast.makeText(context, "Import failed: \$message", Toast.LENGTH_LONG).show()
+                    Toast.makeText(context, "Import failed", Toast.LENGTH_LONG).show()
                 }
             }
         }
@@ -51,11 +51,11 @@
         contract = ActivityResultContracts.CreateDocument("application/json")
     ) { uri: Uri? ->
         uri?.let {
-            viewModel.exportDataToUri(it, exportSavedLocations, exportRoutes) { success, error ->
+            viewModel.exportDataToUri(it, exportSavedLocations, exportRoutes) { success, _ ->
                 if (success) {
                     Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                 } else {
-                    Toast.makeText(context, "Export failed: \$error", Toast.LENGTH_LONG).show()
+                    Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                 }
             }
         }
@@ -70,8 +70,8 @@
             onDismiss = { showExportDialog = false },
             onConfirm = {
                 showExportDialog = false
-                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
-                exportLauncher.launch("mockgps_export_\$dateStr.json")
+                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date())
+                exportLauncher.launch("mockgps_export_\${dateStr}.json")
             }
         )
     }
@@ -82,7 +82,7 @@
             onDismiss = { showImportDialog = false },
             onConfirm = {
                 showImportDialog = false
-                viewModel.applyImportData(importPreview!!) { success, message ->
+                viewModel.applyImportData(importPreview!!) { _, message ->
                     Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                 }
             }
INNER_EOF
patch -p0 < app/src/main/java/com/example/mockgps/ui/settings/SettingsScreen.kt.patch
