package org.simple.clinic.registration.location

import com.spotify.mobius.Next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.mobius.dispatch

class RegistrationLocationPermissionUpdate : Update<RegistrationLocationPermissionModel, RegistrationLocationPermissionEvent, RegistrationLocationPermissionEffect> {

  override fun update(
      model: RegistrationLocationPermissionModel,
      event: RegistrationLocationPermissionEvent
  ): Next<RegistrationLocationPermissionModel, RegistrationLocationPermissionEffect> {
    return when (event) {
      is RequestLocationPermission -> openFacilitySelectionScreen(event)
      is SkipClicked -> dispatch(OpenFacilitySelectionScreen)
    }
  }

  private fun openFacilitySelectionScreen(event: RequestLocationPermission): Next<RegistrationLocationPermissionModel, RegistrationLocationPermissionEffect> {
    return if (event.isPermissionGranted)
      dispatch(OpenFacilitySelectionScreen as RegistrationLocationPermissionEffect)
    else
      noChange()
  }
}