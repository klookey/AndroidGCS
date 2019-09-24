package com.example.mygcs.ControlFlightMode;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.example.mygcs.MainActivity;
import com.example.mygcs.R;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.List;

public class FlightMode extends Activity {

    MainActivity mainActivity;
    private Drone mDrone;
    private Spinner mModeSelector;

    public FlightMode(Drone drone, Spinner modeSelector) {
        this.mDrone = drone;
        this.mModeSelector = modeSelector;
    }

    public void changeToLoiterMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                mainActivity.alertUser(getString(R.string.alert_changing_mode_to_loiter));
            }

            @Override
            public void onError(int executionError) {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_loiter) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_loiter));
            }
        });
    }

    public void changeToAutoMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                mainActivity.alertUser(getString(R.string.alert_changing_mode_to_auto));
            }

            @Override
            public void onError(int executionError) {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_auto) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_auto));
            }
        });
    }

    public void changeToGuideMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                mainActivity.alertUser(getString(R.string.alert_changng_mode_to_guide));
            }

            @Override
            public void onError(int executionError) {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_guide) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                mainActivity.alertUser(getString(R.string.alert_fail_change_mode_to_guide));
            }
        });
    }

    public void updateVehicleModesForType(int mDroneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(mDroneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.mModeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    public void updateVehicleMode() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.mModeSelector.getAdapter();
        this.mModeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }
}
