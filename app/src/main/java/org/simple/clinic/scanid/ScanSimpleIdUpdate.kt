package org.simple.clinic.scanid

import com.spotify.mobius.Next
import com.spotify.mobius.Next.noChange
import com.spotify.mobius.Update
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.businessid.Identifier
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.BpPassport
import org.simple.clinic.patient.businessid.Identifier.IdentifierType.IndiaNationalHealthId
import org.simple.clinic.platform.crash.CrashReporter
import org.simple.clinic.scanid.EnteredCodeValidationResult.Failure
import org.simple.clinic.scanid.EnteredCodeValidationResult.Success
import java.util.UUID
import javax.inject.Inject

class ScanSimpleIdUpdate @Inject constructor(
    private val crashReporter: CrashReporter,
    private val isIndianNHIDSupportEnabled: Boolean
) : Update<ScanSimpleIdModel, ScanSimpleIdEvent, ScanSimpleIdEffect> {

  override fun update(model: ScanSimpleIdModel, event: ScanSimpleIdEvent): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    return when (event) {
      ShowKeyboard -> dispatch(HideQrCodeScannerView)
      HideKeyboard -> dispatch(ShowQrCodeScannerView)
      EnteredCodeChanged -> dispatch(HideEnteredCodeValidationError)
      is EnteredCodeValidated -> shortCodeValidated(model, event)
      is EnteredCodeSearched -> next(model.shortCodeChanged(event.enteredCode), ValidateEnteredCode(event.enteredCode))
      is ScanSimpleIdScreenQrCodeScanned -> simpleIdQrScanned(model, event)
      is PatientSearchByIdentifierCompleted -> patientSearchByIdentifierCompleted(model, event)
      is ScannedQRCodeJsonParsed -> scannedQRCodeParsed(model, event)
    }
  }

  private fun scannedQRCodeParsed(model: ScanSimpleIdModel, event: ScannedQRCodeJsonParsed): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    return if (event.patientPrefillInfo != null && event.healthIdNumber != null) {
      val identifier = Identifier(event.healthIdNumber, IndiaNationalHealthId)
      next(model = model.searching(), SearchPatientByIdentifier(identifier))
    } else {
      noChange()
    }
  }

  private fun patientSearchByIdentifierCompleted(
      model: ScanSimpleIdModel,
      event: PatientSearchByIdentifierCompleted
  ): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    val effect = if (event.patients.isEmpty()) {
      OpenPatientSearch(event.identifier)
    } else {
      patientFoundByIdentifierSearch(patients = event.patients, identifier = event.identifier)
    }

    return next(model = model.notSearching(), effect)
  }

  private fun patientFoundByIdentifierSearch(patients: List<Patient>, identifier: Identifier): ScanSimpleIdEffect {
    return if (patients.size > 1) {
      try {
        OpenShortCodeSearch(BpPassport.shortCode(identifier))
      } catch (e: Exception) {
        OpenShortCodeSearch(identifier.value)
      }
    } else {
      val patientId = patients.first().uuid
      OpenPatientSummary(patientId)
    }
  }

  private fun simpleIdQrScanned(model: ScanSimpleIdModel, event: ScanSimpleIdScreenQrCodeScanned): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    if (model.isSearching) return noChange()

    return try {
      val bpPassportCode = UUID.fromString(event.text)
      val identifier = Identifier(bpPassportCode.toString(), BpPassport)
      next(model = model.searching(), SearchPatientByIdentifier(identifier))
    } catch (e: Exception) {
      searchPatientWhenNHIDEnabled(model, event, e)
    }
  }

  private fun searchPatientWhenNHIDEnabled(
      model: ScanSimpleIdModel,
      event: ScanSimpleIdScreenQrCodeScanned,
      e: Exception
  ): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    return if (isIndianNHIDSupportEnabled) {
      searchPatientWithNhid(model, event)
    } else {
      crashReporter.report(e)
      noChange()
    }
  }

  private fun searchPatientWithNhid(
      model: ScanSimpleIdModel,
      event: ScanSimpleIdScreenQrCodeScanned
  ): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    return try {
      next(model = model.searching(), ParseScannedJson(event.text))
    } catch (e: IllegalArgumentException) {
      crashReporter.report(e)
      noChange()
    }
  }

  private fun shortCodeValidated(model: ScanSimpleIdModel, event: EnteredCodeValidated): Next<ScanSimpleIdModel, ScanSimpleIdEffect> {
    val effect = when (event.result) {
      Success -> OpenShortCodeSearch(model.enteredCode!!.enteredCodeText)
      is Failure -> ShowEnteredCodeValidationError(event.result)
    }

    return dispatch(effect)
  }
}
