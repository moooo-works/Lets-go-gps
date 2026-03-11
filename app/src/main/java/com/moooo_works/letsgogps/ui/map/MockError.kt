package com.moooo_works.letsgogps.ui.map

sealed class MockError {
    object NotMockAppSelected : MockError()
    object LocationPermissionMissing : MockError()
    object NotificationPermissionMissing : MockError()
    object FloatingWindowPermissionMissing : MockError()
    data class ProviderSetupFailed(val message: String) : MockError()
    data class SetLocationFailed(val message: String) : MockError()
    data class ProviderTeardownFailed(val message: String) : MockError()
    data class InvalidInput(val message: String) : MockError()
    data class PermissionCheckFailed(val message: String) : MockError()
    data class Unknown(val message: String) : MockError()
}
