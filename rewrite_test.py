with open("app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt", "r") as f:
    content = f.read()

content = content.replace(
    """        viewModel.exportDataToUri(uri) { success, error ->
            successResult = success
            errorResult = error
        }

        advanceUntilIdle()""",
    """        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.exportDataToUri(uri) { success, error ->
            successResult = success
            errorResult = error
            latch.countDown()
        }
        latch.await()"""
)

content = content.replace(
    """        viewModel.importDataFromUri(uri) { success, msg ->
            successResult = success
            msgResult = msg
        }

        advanceUntilIdle()""",
    """        val latch = java.util.concurrent.CountDownLatch(1)
        viewModel.importDataFromUri(uri) { success, msg ->
            successResult = success
            msgResult = msg
            latch.countDown()
        }
        latch.await()"""
)

with open("app/src/test/java/com/example/mockgps/ui/settings/SettingsViewModelTest.kt", "w") as f:
    f.write(content)
