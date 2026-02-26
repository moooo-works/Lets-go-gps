package com.example.mockgps.ui.map

sealed class MockError {
    object NotMockAppSelected : MockError()
    data class ProviderSetupFailed(val message: String) : MockError()
    data class SetLocationFailed(val message: String) : MockError()
    data class ProviderTeardownFailed(val message: String) : MockError()
    data class InvalidInput(val message: String) : MockError()
    data class Unknown(val message: String) : MockError()
}
