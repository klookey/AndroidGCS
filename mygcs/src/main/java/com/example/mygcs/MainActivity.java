package com.example.mygcs;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    MapFragment mNaverMapFragment = null;

    private Drone drone;

    NaverMap naverMap;

    List<Marker> markers = new ArrayList<>();

    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;

    private Spinner modeSelector;

    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private int Marker_Count = 0;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) { // 맵 실행 되기 전
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        // 소프트바 없애기
        deleteStatusBar();
        // 상태바 없애기
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        // 지도 띄우기
        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }

        // 모드 변경 스피너
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView)parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }
            @Override
            public void onNothingSelected(AdapterView<?> prent) {
                // Do nothing
            }
        });

        // UI상 버튼 제어
        ControlButton();

        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        // onMapReady는 지도가 불러와지면 그때 한번 실행
        this.naverMap=naverMap;

        // 켜지자마자 드론 연결
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
        this.drone.connect(params);

        // 네이버 로고 위치 변경
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setLogoMargin(32,0,0,925);

        // 축척 바 제거
        uiSettings.setScaleBarEnabled(false);

        // 줌 버튼 제거
        uiSettings.setZoomControlEnabled(false);
    }

    public void ControlButton() {
        final Button BtnMapMoveLock = (Button) findViewById(R.id.BtnMapMoveLock);
        final Button BtnMapType = (Button) findViewById(R.id.BtnMapType);
        final Button BtnLandRegistrationMap = (Button) findViewById(R.id.BtnLandRegistrationMap);
        final Button BtnClear = (Button) findViewById(R.id.BtnClear);
        final Button MapMoveLock = (Button) findViewById(R.id.MapMoveLock);
        final Button MapMoveUnLock = (Button) findViewById(R.id.MapMoveUnLock);
        final Button MapType_Basic = (Button) findViewById(R.id.MapType_Basic);
        final Button MapType_Terrain = (Button) findViewById(R.id.MapType_Terrain);
        final Button MapType_Satellite = (Button) findViewById(R.id.MapType_Satellite);
        final Button LandRegistrationOn = (Button) findViewById(R.id.LandRegistrationOn);
        final Button LandRegistrationOff = (Button) findViewById(R.id.LandRegistrationOff);

        // ###################### 기본 UI 버튼 제어 ##############################

        BtnMapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(MapMoveLock.getVisibility()==view.INVISIBLE) {
                    MapMoveLock.setVisibility(View.VISIBLE);
                    MapMoveUnLock.setVisibility(View.VISIBLE);
                }
                else if (MapMoveLock.getVisibility()==view.VISIBLE){
                    MapMoveLock.setVisibility(View.INVISIBLE);
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                }
            }
        });

        BtnMapType.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(MapType_Satellite.getVisibility()==view.INVISIBLE) {
                    MapType_Satellite.setVisibility(View.VISIBLE);
                    MapType_Terrain.setVisibility(View.VISIBLE);
                    MapType_Basic.setVisibility(View.VISIBLE);
                }
                else {
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Basic.setVisibility(View.INVISIBLE);
                }
            }
        });

        BtnLandRegistrationMap.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(LandRegistrationOff.getVisibility() == view.INVISIBLE) {
                    LandRegistrationOff.setVisibility(View.VISIBLE);
                    LandRegistrationOn.setVisibility(View.VISIBLE);
                }
                else {
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                }
            }
        });

        // ################### 맵 이동 관련 제어 ##########################

        // ################### 지도 모드 제어 ###########################

        // 위성 지도
        MapType_Satellite.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapType.setText("위성지도");

                naverMap.setMapType(NaverMap.MapType.Satellite);

                // 다시 닫기
                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // 지형도
        MapType_Terrain.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton);

                BtnMapType.setText("지형도");

                naverMap.setMapType(NaverMap.MapType.Terrain);

                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // 일반지도
        MapType_Basic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapType.setText("일반지도");

                naverMap.setMapType(NaverMap.MapType.Basic);

                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // ############### 지적도 On / Off 제어 ###################

        LandRegistrationOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton_dark);

                BtnLandRegistrationMap.setText("지적도 ON");

                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });

        LandRegistrationOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton_dark);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton);

                BtnLandRegistrationMap.setText("지적도 OFF");

                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL,false);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });
    }



    private void deleteStatusBar(){
        View decorView = getWindow().getDecorView();
        int uiOption = decorView.getSystemUiVisibility();
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH )
            uiOption |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN )
            uiOption |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT )
            uiOption |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility( uiOption );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        deleteStatusBar();
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            //updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                //updateConnectedButton(this.drone.isConnected());
                //updateArmButton();
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                //updateConnectedButton(this.drone.isConnected());
                //updateArmButton();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.GPS_POSITION:
                SetDronePosition();
                break;

            case AttributeEvent.SPEED_UPDATED:
                SpeedUpdate();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                AltitudeUpdate();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                BatteryUpdate();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                ArmBtnUpdate();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void ArmBtnUpdate() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button ArmBtn = (Button) findViewById(R.id.ArmBtn);

        if (vehicleState.isFlying()) {
            // Land
            ArmBtn.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            ArmBtn.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            ArmBtn.setText("ARM");
        }
    }

    private void BatteryUpdate() {
        TextView textView_Vol = (TextView) findViewById(R.id.Voltage);
        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        double batteryVoltage = Math.round(battery.getBatteryVoltage()*10)/10.0;
        textView_Vol.setText("전압 " + batteryVoltage + "V");
        Log.d("Position8", "Battery : " + batteryVoltage);
    }

    public void SetDronePosition() {
        // 드론 위치 받아오기
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        int Satellite = droneGps.getSatellitesCount();
        LatLong dronePosition = droneGps.getPosition();

        Log.d("Position1","droneGps : " + droneGps);
        Log.d("Position1","dronePosition : " + dronePosition);

        // 이동했던 위치 맵에서 지워주기
        if(Marker_Count-1 >= 0)
        {
            markers.get(Marker_Count-1).setMap(null);
        }

        // 마커 리스트에 추가
        markers.add(new Marker(new LatLng(dronePosition.getLatitude(),dronePosition.getLongitude())));

        // yaw 에 따라 네비게이션 마커 회전
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();
        markers.get(Marker_Count).setAngle((float)attitude.getYaw());

        // 마커 크기 지정
        markers.get(Marker_Count).setHeight(80);
        markers.get(Marker_Count).setWidth(80);

        // 마커 아이콘 지정
        markers.get(Marker_Count).setIcon(OverlayImage.fromResource(R.drawable.marker_icon));

        // 마커 위치를 중심점으로 지정
        markers.get(Marker_Count).setAnchor(new PointF(0.5F,0.5F));

        // 마커 띄우기
        markers.get(Marker_Count).setMap(naverMap);
        Marker_Count++;

        // 카메라 위치 설정
        CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(dronePosition.getLatitude(),dronePosition.getLongitude()));
        naverMap.moveCamera(cameraUpdate);

        // [메뉴 바] yaw 보여주기
        TextView textView_yaw = (TextView) findViewById(R.id.yaw);
        if((int)yaw < 0)
        {
            yaw+=360;
        }
        textView_yaw.setText("YAW " + (int)yaw + "deg");

        // [메뉴 바] 잡히는 GPS 개수
        TextView textView_gps = (TextView) findViewById(R.id.GPS_state);
        textView_gps.setText("위성 " + Satellite);
    }

    private void AltitudeUpdate() {
        TextView textView = (TextView) findViewById(R.id.Altitude);
        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        int intAltitude = (int)Math.round(altitude.getAltitude());
        textView.setText("고도 " + intAltitude + "m");
        Log.d("Position7","Altitude : " + altitude);
    }

    private void SpeedUpdate() {
        TextView textView = (TextView) findViewById(R.id.Speed);
        Speed speed = this.drone.getAttribute(AttributeType.SPEED);
        int doubleSpeed = (int)Math.round(speed.getGroundSpeed());
        // double doubleSpeed = Math.round(speed.getGroundSpeed()*10)/10.0; 소수점 첫째자리까지
        textView.setText("속도 " + doubleSpeed + "m/s");
        Log.d("Position6", "Speed : " + this.drone.getAttribute(AttributeType.SPEED));
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode()) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    private void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }


}