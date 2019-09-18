package com.example.mygcs;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.overlay.PolylineOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
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
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource mLocationSourc;

    MapFragment mNaverMapFragment = null;

    private Drone mDrone;

    NaverMap mNaverMap;

    List<Marker> mDroneMarkers = new ArrayList<>();
    List<LatLng> mDronePolylineCoords = new ArrayList<>(); // 폴리라인
    ArrayList<String> mRecyclerList = new ArrayList<>();// 리사이클러뷰
    List<LocalTime> mRecyclerTime = new ArrayList<>();          // 리사이클러뷰 시간
    List<Marker> mAutoMarkers = new ArrayList<>();               // 간격감시 마커
    List<LatLng> mAutoPolygonCoords = new ArrayList<>();             // 간격감시 폴리곤
    List<LatLng> mAutoPolylineCoords = new ArrayList<>();             // 간격감시 폴리라인

    Marker mMarkerGoal = new Marker(); // Guided 모드 마커

    PolylineOverlay mDronePolyline = new PolylineOverlay();           // 마커 지나간 길
    PolygonOverlay mAutoPolygon = new PolygonOverlay();              // 간격 감시 시 뒤 사각형 (하늘)
    PolylineOverlay mAutoPolylinePath = new PolylineOverlay();       // 간격 감시 시 Path (하양)

    private int mDroneType = Type.TYPE_UNKNOWN;
    private ControlTower mControlTower;

    private Spinner mModeSelector;

    private int mMarkerCount = 0;
    private int mRecyclerCount = 0;
    private int mTakeOffAltitude = 3;
    private int mAutoMarkersCount = 0;
    private int mAutoDistance = 50;
    private int mGapDistance = 5;
    private int mGapTop = 0;
    private int mGuidedCount = 0;

    protected double mRecentAltitude = 0;

    private int mReachedCount = 1;

    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) { // 맵 실행 되기 전
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        // 소프트바 없애기
        deleteStatusBar();
        // 상태바 없애기
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final Context context = getApplicationContext();
        this.mControlTower = new ControlTower(context);
        this.mDrone = new Drone(context);

        // 지도 띄우기
        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }

        // 모드 변경 스피너
        this.mModeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.mModeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
//                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> prent) {
                // Do nothing
            }
        });

        // 내 위치
        mLocationSourc = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);

        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (mLocationSourc.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        // onMapReady는 지도가 불러와지면 그때 한번 실행
        this.mNaverMap = naverMap;

        // 켜지자마자 드론 연결
        connectedDroneOnHopspot();

        // 네이버 로고 위치 변경
        UiSettings uiSettings = naverMap.getUiSettings();
        uiSettings.setLogoMargin(2080, 0, 0, 925);

        // 나침반 제거
        uiSettings.setCompassEnabled(false);

        // 축척 바 제거
        uiSettings.setScaleBarEnabled(false);

        // 줌 버튼 제거
        uiSettings.setZoomControlEnabled(false);

        // 이륙고도 표시
        showTakeOffAltitude();

        // 초기 상태를 맵 잠금으로 설정
        uiSettings.setScrollGesturesEnabled(false);

        // UI상 버튼 제어
        controlButtons();

        // 내 위치
        naverMap.setLocationSource(mLocationSourc);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

        // 롱 클릭 시 경고창
        naverMap.setOnMapLongClickListener(new NaverMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull PointF pointF, @NonNull LatLng coord) {
                LongClickWarning(pointF, coord);
            }
        });

        //새로운 branch에서 작업을 시작하였습니다.

        // 클릭 시
        naverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                final Button BtnFlightMode = (Button) findViewById(R.id.BtnFlightMode);
                if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_basic))) {
                    // nothing
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_path))) {
                    MakePathFlight(latLng);
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_gap))) {
                    MakeGapPolygon(latLng);
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {
                    MakeAreaPolygon(latLng);
                }
            }
        });
    }

    private void deleteStatusBar() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
        );
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        deleteStatusBar();
        return super.onTouchEvent(event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.mControlTower.connect(this);
        updateVehicleModesForType(this.mDroneType);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.mDrone.isConnected()) {
            this.mDrone.disconnect();
            //updateConnectedButton(false);
        }

        this.mControlTower.unregisterDrone(this.mDrone);
        this.mControlTower.disconnect();
    }

    // #################################### UI ####################################################

    private void ShowSatelliteCount() {
        // [UI] 잡히는 GPS 개수
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        int Satellite = droneGps.getSatellitesCount();
        TextView textView_gps = (TextView) findViewById(R.id.GPS_state);
        textView_gps.setText(getString(R.string.show_satellite) + " " + Satellite);

        Log.d("Position13", "satellite : " + Satellite);

        if(Satellite < 10) {
            textView_gps.setBackgroundColor(getResources().getColor(R.color.colorPink_gps));
        } else if(Satellite >= 10) {
            textView_gps.setBackgroundColor(getResources().getColor(R.color.colorBlue_gps));
        }
    }

    private void showTakeOffAltitude() {
        final Button BtnmTakeOffAltitude = (Button) findViewById(R.id.BtnTakeOffAltitude);
        BtnmTakeOffAltitude.setText(mTakeOffAltitude + getString(R.string.show_take_off));
    }

    private void UpdateYaw() {
        // Attitude 받아오기
        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();

        // yaw 값을 양수로
        if ((int) yaw < 0) {
            yaw += 360;
        }

        // [UI] yaw 보여주기
        TextView textView_yaw = (TextView) findViewById(R.id.yaw);
        textView_yaw.setText(getString(R.string.show_degree) + " " + (int) yaw + getString(R.string.show_degree_deg));
    }

    private void BatteryUpdate() {
        TextView textView_Vol = (TextView) findViewById(R.id.Voltage);
        Battery battery = this.mDrone.getAttribute(AttributeType.BATTERY);
        double batteryVoltage = Math.round(battery.getBatteryVoltage() * 10) / 10.0;
        textView_Vol.setText(getString(R.string.show_voltage) + " " + batteryVoltage + getString(R.string.show_voltage_V));
        Log.d("Position8", "Battery : " + batteryVoltage);
    }

    public void SetDronePosition() {
        // 드론 위치 받아오기
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        LatLong dronePosition = droneGps.getPosition();

        Log.d("Position1", "droneGps : " + droneGps);
        Log.d("Position1", "dronePosition : " + dronePosition);

        // 이동했던 위치 맵에서 지워주기
        if (mMarkerCount - 1 >= 0) {
            mDroneMarkers.get(mMarkerCount - 1).setMap(null);
        }

        // 마커 리스트에 추가
        mDroneMarkers.add(new Marker(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude())));

        // yaw 에 따라 네비게이션 마커 회전
        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();
        Log.d("Position4", "yaw : " + yaw);
        if ((int) yaw < 0) {
            yaw += 360;
        }
        mDroneMarkers.get(mMarkerCount).setAngle((float) yaw);

        // 마커 크기 지정
        mDroneMarkers.get(mMarkerCount).setHeight(400);
        mDroneMarkers.get(mMarkerCount).setWidth(80);

        // 마커 아이콘 지정
        mDroneMarkers.get(mMarkerCount).setIcon(OverlayImage.fromResource(R.drawable.marker_icon));

        // 마커 위치를 중심점으로 지정
        mDroneMarkers.get(mMarkerCount).setAnchor(new PointF(0.5F, 0.9F));

        // 마커 띄우기
        mDroneMarkers.get(mMarkerCount).setMap(mNaverMap);

        // 카메라 위치 설정
        Button BtnMapMoveLock = (Button) findViewById(R.id.BtnMapMoveLock);
        String text = (String) BtnMapMoveLock.getText();

        if (text.equals(getString(R.string.map_move_lock))) {
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude()));
            mNaverMap.moveCamera(cameraUpdate);
        }

        // 지나간 길 Polyline
        Collections.addAll(mDronePolylineCoords, mDroneMarkers.get(mMarkerCount).getPosition());
        mDronePolyline.setCoords(mDronePolylineCoords);

        // 선 예쁘게 설정
        mDronePolyline.setWidth(15);
        mDronePolyline.setCapType(PolylineOverlay.LineCap.Round);
        mDronePolyline.setJoinType(PolylineOverlay.LineJoin.Round);
        mDronePolyline.setColor(Color.GREEN);

        mDronePolyline.setMap(mNaverMap);

        Log.d("Position3", "mDronePolylineCoords.size() : " + mDronePolylineCoords.size());
        Log.d("Position3", "mDroneMarkers.size() : " + mDroneMarkers.size());

        // 가이드 모드일 때 지정된 좌표와 드론 사이의 거리 측정
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        if (vehicleMode == VehicleMode.COPTER_GUIDED) {
            LatLng droneLatLng = new LatLng(mDroneMarkers.get(mMarkerCount).getPosition().latitude, mDroneMarkers.get(mMarkerCount).getPosition().longitude);
            LatLng goalLatLng = new LatLng(mMarkerGoal.getPosition().latitude, mMarkerGoal.getPosition().longitude);

            double distance = droneLatLng.distanceTo(goalLatLng);

            Log.d("Position9", "distance : " + distance);

            if (distance < 1.0) {
                if(mGuidedCount == 0) {
                    alertUser(getString(R.string.arrive_at_goal));
                    mMarkerGoal.setMap(mNaverMap);
                    mGuidedCount = 1;
                }
            }
        }

        // [UI] 잡히는 GPS 개수
        ShowSatelliteCount();

        mMarkerCount++;
    }

    private void AltitudeUpdate() {
        Altitude currentAltitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = currentAltitude.getRelativeAltitude();
        double DoubleAltitude = (double) Math.round(mRecentAltitude * 10) / 10.0;

        TextView textView = (TextView) findViewById(R.id.Altitude);
        Altitude altitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
        int intAltitude = (int) Math.round(altitude.getAltitude());

        textView.setText(getString(R.string.show_altitude) + " " + DoubleAltitude + getString(R.string.show_altitude_m));
        Log.d("Position7", "Altitude : " + DoubleAltitude);
    }

    private void SpeedUpdate() {
        TextView textView = (TextView) findViewById(R.id.Speed);
        Speed speed = this.mDrone.getAttribute(AttributeType.SPEED);
        int doubleSpeed = (int) Math.round(speed.getGroundSpeed());
        // double doubleSpeed = Math.round(speed.getGroundSpeed()*10)/10.0; 소수점 첫째자리까지
        textView.setText(getString(R.string.show_speed) + " " + doubleSpeed + getString(R.string.show_speed_m));
        Log.d("Position6", "Speed : " + this.mDrone.getAttribute(AttributeType.SPEED));
    }

    public void onFlightModeSelected(View view) {
        final VehicleMode vehicleMode = (VehicleMode) this.mModeSelector.getSelectedItem();

        VehicleApi.getApi(this.mDrone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser(getString(R.string.alert_mode_select) + " " + vehicleMode.toString() + " " + getString(R.string.alert_mode_changed));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.alert_fail_mode_changed) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.alert_fail_mode_changed));
            }
        });
    }

    // ############################ [일반 모드] 롱클릭 Guided Mode ################################

    private void LongClickWarning(@NonNull PointF pointF, @NonNull final LatLng coord) {
        Button BtnFlightMode = (Button) findViewById(R.id.BtnFlightMode);
        if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_basic))) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.dialog_guide_title));
            builder.setMessage(getString(R.string.guide_ask_question));
            builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // 도착지 마커 생성
                    mMarkerGoal.setMap(null);
                    mMarkerGoal.setPosition(new LatLng(coord.latitude, coord.longitude));
                    mMarkerGoal.setIcon(OverlayImage.fromResource(R.drawable.final_flag));
                    mMarkerGoal.setWidth(70);
                    mMarkerGoal.setHeight(70);
                    mMarkerGoal.setMap(mNaverMap);

                    mGuidedCount = 0;

                    // Guided 모드로 변환
                    ChangeToGuidedMode();

                    // 지정된 위치로 이동
                    GotoTartget();
                }
            });
            builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        }
    }

    private void GotoTartget() {
        ControlApi.getApi(this.mDrone).goTo(
                new LatLong(mMarkerGoal.getPosition().latitude, mMarkerGoal.getPosition().longitude),
                true, new AbstractCommandListener() {
                    @Override
                    public void onSuccess() {
                        alertUser(getString(R.string.alert_head_the_goal));
                    }

                    @Override
                    public void onError(int executionError) {
                        alertUser(getString(R.string.alert_cannot_move) + " " + executionError);
                    }

                    @Override
                    public void onTimeout() {
                        alertUser(getString(R.string.alert_cannot_move));
                    }
                });
    }

    // ################################## 버튼 컨트롤 #############################################

    public void controlButtons() {
        // 기본 UI 4개 버튼
        final Button BtnMapMoveLock = (Button) findViewById(R.id.BtnMapMoveLock);
        final Button BtnMapType = (Button) findViewById(R.id.BtnMapType);
        final Button BtnLandRegistrationMap = (Button) findViewById(R.id.BtnLandRegistrationMap);
        final Button BtnClear = (Button) findViewById(R.id.BtnClear);
        // Map 잠금 버튼
        final Button MapMoveLock = (Button) findViewById(R.id.MapMoveLock);
        final Button MapMoveUnLock = (Button) findViewById(R.id.MapMoveUnLock);
        // Map Type 버튼
        final Button MapType_Basic = (Button) findViewById(R.id.MapType_Basic);
        final Button MapType_Terrain = (Button) findViewById(R.id.MapType_Terrain);
        final Button MapType_Satellite = (Button) findViewById(R.id.MapType_Satellite);
        // 지적도 버튼
        final Button LandRegistrationOn = (Button) findViewById(R.id.LandRegistrationOn);
        final Button LandRegistrationOff = (Button) findViewById(R.id.LandRegistrationOff);
        // 이륙고도 버튼
        final Button BtnmTakeOffAltitude = (Button) findViewById(R.id.BtnTakeOffAltitude);
        // 이륙고도 Up / Down 버튼
        final Button TakeOffUp = (Button) findViewById(R.id.TakeOffUp);
        final Button TakeOffDown = (Button) findViewById(R.id.TakeOffDown);
        // 비행 모드 버튼
        final Button BtnFlightMode = (Button) findViewById(R.id.BtnFlightMode);
        // 비행 모드 설정 버튼
        final Button FlightMode_Basic = (Button) findViewById(R.id.FlightMode_Basic);
        final Button FlightMode_Path = (Button) findViewById(R.id.FlightMode_Path);
        final Button FlightMode_Gap = (Button) findViewById(R.id.FlightMode_Gap);
        final Button FlightMode_Area = (Button) findViewById(R.id.FlightMode_Area);

        // 임무 전송 / 임무 시작/ 임무 중지 버튼
        final Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);

        // 그리기 버튼
        final Button BtnDraw = (Button) findViewById(R.id.BtnDraw);

        final UiSettings uiSettings = mNaverMap.getUiSettings();

        // ############################## 기본 UI 버튼 제어 #######################################
        // 맵 이동 / 맵 잠금
        BtnMapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }

                if (MapMoveLock.getVisibility() == view.INVISIBLE) {
                    MapMoveLock.setVisibility(View.VISIBLE);
                    MapMoveUnLock.setVisibility(View.VISIBLE);
                } else if (MapMoveLock.getVisibility() == view.VISIBLE) {
                    MapMoveLock.setVisibility(View.INVISIBLE);
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지도 모드
        BtnMapType.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }
                if (MapType_Satellite.getVisibility() == view.INVISIBLE) {
                    MapType_Satellite.setVisibility(View.VISIBLE);
                    MapType_Terrain.setVisibility(View.VISIBLE);
                    MapType_Basic.setVisibility(View.VISIBLE);
                } else {
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Basic.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지적도
        BtnLandRegistrationMap.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }

                if (LandRegistrationOff.getVisibility() == view.INVISIBLE) {
                    LandRegistrationOff.setVisibility(View.VISIBLE);
                    LandRegistrationOn.setVisibility(View.VISIBLE);
                } else {
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                }
            }
        });

        // ############################### 맵 이동 관련 제어 ######################################
        // 맵잠금
        MapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapMoveUnLock.setBackgroundResource(R.drawable.mybutton_dark);
                MapMoveLock.setBackgroundResource(R.drawable.mybutton);

                BtnMapMoveLock.setText(getString(R.string.map_move_lock));

                uiSettings.setScrollGesturesEnabled(false);

                MapMoveLock.setVisibility(View.INVISIBLE);
                MapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // 맵 이동
        MapMoveUnLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapMoveUnLock.setBackgroundResource(R.drawable.mybutton);
                MapMoveLock.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapMoveLock.setText(getString(R.string.map_move_unlock));

                uiSettings.setScrollGesturesEnabled(true);

                MapMoveLock.setVisibility(View.INVISIBLE);
                MapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // ################################## 지도 모드 제어 ######################################

        // 위성 지도
        MapType_Satellite.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapType.setText(getString(R.string.map_type_satellite));

                mNaverMap.setMapType(NaverMap.MapType.Satellite);

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

                BtnMapType.setText(getString(R.string.map_type_terrain));

                mNaverMap.setMapType(NaverMap.MapType.Terrain);

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

                BtnMapType.setText(getString(R.string.map_type_basic));

                mNaverMap.setMapType(NaverMap.MapType.Basic);

                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // ################################ 지적도 On / Off 제어 ##################################

        LandRegistrationOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton_dark);

                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });

        LandRegistrationOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton_dark);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton);

                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });

        // ###################################### Clear ###########################################
        BtnClear.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }

                // 이전 마커 지우기
                if (mMarkerCount - 1 >= 0) {
                    mDroneMarkers.get(mMarkerCount - 1).setMap(null);
                }

                // 폴리라인 / 폴리곤 지우기
                mDronePolyline.setMap(null);
                mAutoPolygon.setMap(null);
                mAutoPolylinePath.setMap(null);

                // mAutoMarkers 지우기
                if (mAutoMarkers.size() != 0) {
                    for (int i = 0; i < mAutoMarkers.size(); i++) {
                        mAutoMarkers.get(i).setMap(null);
                    }
                }

                // mMarkerGoal 지우기
                mMarkerGoal.setMap(null);

                // 면적 감시 시
                if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {
                    BtnDraw.setVisibility(View.VISIBLE);
                }

                // 리스트 값 지우기
                mDronePolylineCoords.clear();
                mAutoMarkers.clear();
                mAutoPolylineCoords.clear();
                mAutoPolygonCoords.clear();

                // Top 변수 초기화
                mAutoMarkersCount = 0;
                mGapTop = 0;

                mReachedCount = 1;

                // BtnFlightMode 버튼 초기화
                BtnSendMission.setText(getString(R.string.btn_mission_transmition));
            }
        });

        // ###################################### 이륙 고도 설정 #################################
        // 이륙고도 버튼
        BtnmTakeOffAltitude.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (TakeOffUp.getVisibility() == view.VISIBLE) {
                    TakeOffUp.setVisibility(View.INVISIBLE);
                    TakeOffDown.setVisibility(View.INVISIBLE);
                } else if (TakeOffUp.getVisibility() == view.INVISIBLE) {
                    TakeOffUp.setVisibility(View.VISIBLE);
                    TakeOffDown.setVisibility(View.VISIBLE);
                }
            }
        });

        // 이륙고도 Up 버튼
        TakeOffUp.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                mTakeOffAltitude++;
                showTakeOffAltitude();
            }
        });

        // 이륙고도 Down 버튼
        TakeOffDown.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTakeOffAltitude--;
                showTakeOffAltitude();
            }
        });

        // #################################### 비행 모드 설정 ####################################
        // 비행 모드 버튼
        BtnFlightMode.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (FlightMode_Basic.getVisibility() == view.VISIBLE) {
                    FlightMode_Basic.setVisibility(view.INVISIBLE);
                    FlightMode_Path.setVisibility(view.INVISIBLE);
                    FlightMode_Gap.setVisibility(view.INVISIBLE);
                    FlightMode_Area.setVisibility(view.INVISIBLE);
                } else if (FlightMode_Basic.getVisibility() == view.INVISIBLE) {
                    FlightMode_Basic.setVisibility(view.VISIBLE);
                    FlightMode_Path.setVisibility(view.VISIBLE);
                    FlightMode_Gap.setVisibility(view.VISIBLE);
                    FlightMode_Area.setVisibility(view.VISIBLE);
                }
            }
        });

        // 일반 모드
        FlightMode_Basic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                BtnFlightMode.setText(getString(R.string.auto_mode_basic));

                // 그리기 버튼 제어
                ControlBtnDraw();

                BtnSendMission.setVisibility(view.INVISIBLE);

                FlightMode_Basic.setVisibility(view.INVISIBLE);
                FlightMode_Path.setVisibility(view.INVISIBLE);
                FlightMode_Gap.setVisibility(view.INVISIBLE);
                FlightMode_Area.setVisibility(view.INVISIBLE);
            }
        });

        // 경로 비행 모드
        FlightMode_Path.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                BtnFlightMode.setText(getString(R.string.auto_mode_path));

                BtnSendMission.setVisibility(View.VISIBLE);

                // 그리기 버튼 제어
                ControlBtnDraw();

                FlightMode_Basic.setVisibility(view.INVISIBLE);
                FlightMode_Path.setVisibility(view.INVISIBLE);
                FlightMode_Gap.setVisibility(view.INVISIBLE);
                FlightMode_Area.setVisibility(view.INVISIBLE);
            }
        });

        // 간격 감시 모드
        FlightMode_Gap.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                BtnFlightMode.setText(getString(R.string.auto_mode_gap));

                BtnSendMission.setVisibility(View.VISIBLE);

                // 그리기 버튼 제어
                ControlBtnDraw();

                alertUser(getString(R.string.alert_a_b_latlng));

                DialogGap();

                FlightMode_Basic.setVisibility(view.INVISIBLE);
                FlightMode_Path.setVisibility(view.INVISIBLE);
                FlightMode_Gap.setVisibility(view.INVISIBLE);
                FlightMode_Area.setVisibility(view.INVISIBLE);
            }
        });

        // 면적 감시 모드
        FlightMode_Area.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                BtnFlightMode.setText(getString(R.string.auto_mode_area));

                // 그리기 버튼 제어
                ControlBtnDraw();

                BtnDraw.setVisibility(view.VISIBLE);

                FlightMode_Basic.setVisibility(view.INVISIBLE);
                FlightMode_Path.setVisibility(view.INVISIBLE);
                FlightMode_Gap.setVisibility(view.INVISIBLE);
                FlightMode_Area.setVisibility(view.INVISIBLE);
            }
        });

        // #################################### 그리기 설정 #######################################
        BtnDraw.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAutoPolygonCoords.size() >= 3) {
                    BtnDraw.setVisibility(view.INVISIBLE);
                    mAutoPolygon.setCoords(mAutoPolygonCoords);

                    int colorLightBlue = getResources().getColor(R.color.colorLightBlue);

                    mAutoPolygon.setColor(colorLightBlue);
                    mAutoPolygon.setMap(mNaverMap);

                    ComputeLargeLength();

                } else {
                    alertUser(getString(R.string.alert_three_more_latlng));
                }
            }
        });
    }

    private void ControlBtnDraw() {
        Button BtnFlightMode = (Button) findViewById(R.id.BtnFlightMode);
        Button BtnDraw = (Button) findViewById(R.id.BtnDraw);
        if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {

        } else {
            BtnDraw.setVisibility(View.INVISIBLE);
        }
    }

    // ################################### 미션 수행 Mission ######################################

    private void MakeWayPoint() {
        final Mission mMission = new Mission();

        for (int i = 0; i < mAutoPolylineCoords.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(1);

            LatLongAlt latLongAlt = new LatLongAlt(mAutoPolylineCoords.get(i).latitude, mAutoPolylineCoords.get(i).longitude, mRecentAltitude);
            waypoint.setCoordinate(latLongAlt);

            mMission.addMissionItem(waypoint);
        }

        final Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);
        final Button BtnFlightMode = (Button) findViewById(R.id.BtnFlightMode);

        BtnSendMission.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(BtnFlightMode.getText().equals(getString(R.string.auto_mode_gap))) {
                    if (BtnSendMission.getText().equals(getString(R.string.btn_mission_transmition))) {
                        if (mAutoPolygonCoords.size() == 4) {
                            setMission(mMission);
                        } else {
                            alertUser(getString(R.string.alert_a_b_latlng));
                        }
                    } else if (BtnSendMission.getText().equals(getString(R.string.btn_mission_start))) {
                        // Auto모드로 전환
                        ChangeToAutoMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_stop));
                    } else if (BtnSendMission.getText().equals(getString(R.string.btn_mission_stop))) {
                        pauseMission();
                        ChangeToLoiterMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_restart));
                    } else if (BtnSendMission.getText().equals(getString(R.string.btn_mission_restart))) {
                        ChangeToAutoMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_stop));
                    }
                } else if(BtnFlightMode.getText().equals(getString(R.string.auto_mode_path))) {
                    if(BtnSendMission.getText().equals(getString(R.string.btn_mission_transmition))) {
                        if (mAutoPolylineCoords.size() > 0) {
                            setMission(mMission);
                        } else {
                            alertUser(getString(R.string.alert_one_more_latlng));
                        }
                    } else if(BtnSendMission.getText().equals(getString(R.string.btn_mission_start))) {
                        // Auto모드로 전환
                        ChangeToAutoMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_stop));
                    } else if(BtnSendMission.getText().equals(getString(R.string.btn_mission_stop))) {
                        ChangeToLoiterMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_restart));
                    } else if(BtnSendMission.getText().equals(getString(R.string.btn_mission_restart))) {
                        ChangeToAutoMode();
                        BtnSendMission.setText(getString(R.string.btn_mission_stop));
                    }
                }
            }
        });
    }

    private void setMission(Mission mMission) {
        MissionApi.getApi(this.mDrone).setMission(mMission, true);
    }

    private void pauseMission() {
        MissionApi.getApi(this.mDrone).pauseMission(null);
    }

    private void Mission_Sent() {
        alertUser(getString(R.string.alert_mission_upload));
        Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);
        BtnSendMission.setText(getString(R.string.btn_mission_start));
    }

    // ################################## 비행 모드 변경 ##########################################

    private void ChangeToLoiterMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser(getString(R.string.alert_changing_mode_to_loiter));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.alert_fail_change_mode_to_loiter) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.alert_fail_change_mode_to_loiter));
            }
        });
    }

    private void ChangeToAutoMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser(getString(R.string.alert_changing_mode_to_auto));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.alert_fail_change_mode_to_auto) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.alert_fail_change_mode_to_auto));
            }
        });
    }

    private void ChangeToGuidedMode() {
        VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_GUIDED, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser(getString(R.string.alert_changng_mode_to_guide));
            }

            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.alert_fail_change_mode_to_guide) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.alert_fail_change_mode_to_guide));
            }
        });
    }

    protected void updateVehicleModesForType(int mDroneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(mDroneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.mModeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    protected void updateVehicleMode() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.mModeSelector.getAdapter();
        this.mModeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    // ###################################### 경로 비행 ###########################################

    private void MakePathFlight(LatLng latLng)  {
        mAutoPolylineCoords.add(latLng);

        Marker marker = new Marker();
        marker.setPosition(latLng);
        mAutoMarkers.add(marker);
        mAutoMarkersCount++;

        mAutoMarkers.get(mAutoMarkersCount - 1).setHeight(100);
        mAutoMarkers.get(mAutoMarkersCount - 1).setWidth(100);

        mAutoMarkers.get(mAutoMarkersCount - 1).setAnchor(new PointF(0.5F, 0.9F));
        mAutoMarkers.get(mAutoMarkersCount - 1).setIcon(OverlayImage.fromResource(R.drawable.area_marker));

        mAutoMarkers.get(mAutoMarkersCount - 1).setMap(mNaverMap);

        MakeWayPoint();
    }

    // ################################# 간격 감시 ################################################

    private void MakeGapPolygon(LatLng latLng) {
        if (mGapTop < 2) {
            Marker marker = new Marker();
            marker.setPosition(latLng);
            mAutoPolygonCoords.add(latLng);

            // mAutoMarkers에 넣기 위해 marker 생성..
            mAutoMarkers.add(marker);
            mAutoMarkers.get(mAutoMarkersCount).setMap(mNaverMap);

            if (mGapTop == 0) {
                mAutoMarkers.get(0).setIcon(OverlayImage.fromResource(R.drawable.number1));
                mAutoMarkers.get(0).setWidth(80);
                mAutoMarkers.get(0).setHeight(80);
                mAutoMarkers.get(0).setAnchor(new PointF(0.5F, 0.5F));
            } else if (mGapTop == 1) {
                mAutoMarkers.get(1).setIcon(OverlayImage.fromResource(R.drawable.number2));
                mAutoMarkers.get(1).setWidth(80);
                mAutoMarkers.get(1).setHeight(80);
                mAutoMarkers.get(1).setAnchor(new PointF(0.5F, 0.5F));
            }

            mGapTop++;
            mAutoMarkersCount++;
        }
        if (mAutoMarkersCount == 2) {
            double heading = MyUtil.computeHeading(mAutoMarkers.get(0).getPosition(), mAutoMarkers.get(1).getPosition());

            LatLng latLng1 = MyUtil.computeOffset(mAutoMarkers.get(1).getPosition(), mAutoDistance, heading + 90);
            LatLng latLng2 = MyUtil.computeOffset(mAutoMarkers.get(0).getPosition(), mAutoDistance, heading + 90);

            // ############################################################################
            mAutoPolygonCoords.add(latLng1);
            mAutoPolygonCoords.add(latLng2);
            mAutoPolygon.setCoords(Arrays.asList(
                    new LatLng(mAutoPolygonCoords.get(0).latitude, mAutoPolygonCoords.get(0).longitude),
                    new LatLng(mAutoPolygonCoords.get(1).latitude, mAutoPolygonCoords.get(1).longitude),
                    new LatLng(mAutoPolygonCoords.get(2).latitude, mAutoPolygonCoords.get(2).longitude),
                    new LatLng(mAutoPolygonCoords.get(3).latitude, mAutoPolygonCoords.get(3).longitude)));

            Log.d("Position5", "LatLng[0] : " + mAutoPolygonCoords.get(0).latitude + " / " + mAutoPolygonCoords.get(0).longitude);
            Log.d("Position5", "LatLng[1] : " + mAutoPolygonCoords.get(1).latitude + " / " + mAutoPolygonCoords.get(1).longitude);
            Log.d("Position5", "LatLng[2] : " + mAutoPolygonCoords.get(2).latitude + " / " + mAutoPolygonCoords.get(2).longitude);
            Log.d("Position5", "LatLng[3] : " + mAutoPolygonCoords.get(3).latitude + " / " + mAutoPolygonCoords.get(3).longitude);

            int colorLightBlue = getResources().getColor(R.color.colorLightBlue);

            mAutoPolygon.setColor(colorLightBlue);
            mAutoPolygon.setMap(mNaverMap);

            // 내부 길 생성
            MakeGapPath();
        }
    }

    private void MakeGapPath() {
        double heading = MyUtil.computeHeading(mAutoMarkers.get(0).getPosition(), mAutoMarkers.get(1).getPosition());

        mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(0).getPosition().latitude, mAutoMarkers.get(0).getPosition().longitude));
        mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(1).getPosition().latitude, mAutoMarkers.get(1).getPosition().longitude));

        for (int sum = mGapDistance; sum + mGapDistance <= mAutoDistance + mGapDistance; sum = sum + mGapDistance) {
            LatLng latLng1 = MyUtil.computeOffset(mAutoMarkers.get(mAutoMarkersCount - 1).getPosition(), mGapDistance, heading + 90);
            LatLng latLng2 = MyUtil.computeOffset(mAutoMarkers.get(mAutoMarkersCount - 2).getPosition(), mGapDistance, heading + 90);

            mAutoMarkers.add(new Marker(latLng1));
            mAutoMarkers.add(new Marker(latLng2));
            mAutoMarkersCount += 2;

            mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(mAutoMarkersCount - 2).getPosition().latitude, mAutoMarkers.get(mAutoMarkersCount - 2).getPosition().longitude));
            mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(mAutoMarkersCount - 1).getPosition().latitude, mAutoMarkers.get(mAutoMarkersCount - 1).getPosition().longitude));
        }

        mAutoPolylinePath.setColor(Color.WHITE);
        mAutoPolylinePath.setCoords(mAutoPolylineCoords);
        mAutoPolylinePath.setMap(mNaverMap);

        // WayPoint
        MakeWayPoint();
    }

    // ################################## 간격 감시 시 Dialog #####################################

    private void DialogGap() {
        final EditText edittext1 = new EditText(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_gap_monitoring_title));
        builder.setMessage(getString(R.string.dialog_gap_monitoring_subtitle_whole));
        builder.setView(edittext1);
        builder.setPositiveButton(getString(R.string.insert),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String editTextValue = edittext1.getText().toString();
                        mAutoDistance = Integer.parseInt(editTextValue);
                        DialogGap2();
                    }
                });
        builder.setNegativeButton(getString(R.string.cancle),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.show();
    }

    private void DialogGap2() {
        final EditText edittext2 = new EditText(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_gap_monitoring_title));
        builder.setMessage(getString(R.string.dialog_gap_monitoring_subtitle_gap));
        builder.setView(edittext2);
        builder.setPositiveButton(getString(R.string.insert),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String editTextValue = edittext2.getText().toString();
                        mGapDistance = Integer.parseInt(editTextValue);
                    }
                });
        builder.setNegativeButton(getString(R.string.cancle),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.show();
    }

    // ###################################### 면적 감시 ###########################################

    private void MakeAreaPolygon(LatLng latLng) {
        Button BtnDraw = (Button) findViewById(R.id.BtnDraw);
        if (BtnDraw.getVisibility() == View.VISIBLE) {
            mAutoPolygonCoords.add(latLng);

            Marker marker = new Marker();
            marker.setPosition(latLng);
            mAutoMarkers.add(marker);
            mAutoMarkersCount++;

            mAutoMarkers.get(mAutoMarkersCount - 1).setHeight(100);
            mAutoMarkers.get(mAutoMarkersCount - 1).setWidth(100);

            mAutoMarkers.get(mAutoMarkersCount - 1).setAnchor(new PointF(0.5F, 0.9F));
            mAutoMarkers.get(mAutoMarkersCount - 1).setIcon(OverlayImage.fromResource(R.drawable.area_marker));

            mAutoMarkers.get(mAutoMarkersCount - 1).setMap(mNaverMap);
        }
    }

    private void ComputeLargeLength() {
        // 가장 긴 변 계산
        double max = 0.0;
        double computeValue = 0.0;
        int firstIndex = 0;
        int secondIndex = 0;
        for(int i=0; i<mAutoPolygonCoords.size(); i++) {
            if(i == mAutoPolygonCoords.size() - 1) {
                computeValue = mAutoPolygonCoords.get(i).distanceTo(mAutoPolygonCoords.get(0));
                Log.d("Position12", "computeValue : [" + i + " , 0 ] : " + computeValue);
                if(max < computeValue) {
                    max = computeValue;
                    firstIndex = i;
                    secondIndex = 0;
                }
            } else {
                computeValue = mAutoPolygonCoords.get(i).distanceTo(mAutoPolygonCoords.get(i+1));
                Log.d("Position12", "computeValue : [" + i + " , " + (i+1) + "] : " + computeValue);
                if(max < computeValue) {
                    max = computeValue;
                    firstIndex = i;
                    secondIndex = i+1;
                }
            }
            Log.d("Position12", "max : " + max);
            Log.d("Position12", "firstIndex : " + firstIndex + " / secondIndex : " + secondIndex);
        }

        MakeAreaPath(firstIndex, secondIndex);
    }

    private void MakeAreaPath(int firstIndex, int secondIndex) {
        double heading = MyUtil.computeHeading(mAutoPolygonCoords.get(firstIndex), mAutoPolygonCoords.get(secondIndex));

        Log.d("Position11", "heading : " + heading);

        mAutoPolylineCoords.add(mAutoPolygonCoords.get(firstIndex));
        mAutoPolylineCoords.add(mAutoPolygonCoords.get(secondIndex));

        LatLng latLng1 = MyUtil.computeOffset(mAutoPolygonCoords.get(firstIndex), mAutoDistance, heading + 90);
        LatLng latLng2 = MyUtil.computeOffset(mAutoPolygonCoords.get(secondIndex), mAutoDistance, heading + 90);
    }

    // ################################### Drone event ############################################

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser(getString(R.string.connected_drone));
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser(getString(R.string.disconnected_drone));
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newmDroneType = this.mDrone.getAttribute(AttributeType.TYPE);
                if (newmDroneType.getDroneType() != this.mDroneType) {
                    this.mDroneType = newmDroneType.getDroneType();
                    updateVehicleModesForType(this.mDroneType);
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

            case AttributeEvent.ATTITUDE_UPDATED:
                UpdateYaw();
                break;

            case AttributeEvent.GPS_COUNT:
                ShowSatelliteCount();
                break;

            case AttributeEvent.MISSION_SENT:
                Mission_Sent();
                break;

            case AttributeEvent.MISSION_ITEM_REACHED:
                alertUser(mReachedCount + getString(R.string.alert_compute_waypoint_count) + mReachedCount + getString(R.string.slash) + mAutoPolylineCoords.size());
                mReachedCount++;
                break;

            default:
                MakeRecyclerView();
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    // ################################### 아밍 Arming ############################################

    private void ArmBtnUpdate() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        Button ArmBtn = (Button) findViewById(R.id.BtnArm);

        if (vehicleState.isFlying()) {
            // Land
            ArmBtn.setText(getString(R.string.btn_land));
        } else if (vehicleState.isArmed()) {
            // Take off
            ArmBtn.setText(getString(R.string.btn_take_off));
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            ArmBtn.setText(getString(R.string.btn_arm));
        }
    }

    public void onArmButtonTap(View view) {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.mDrone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser(getString(R.string.alert_cannot_land) + " " + executionError);
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.alert_cannot_land));
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.mDrone).takeoff(mTakeOffAltitude, new AbstractCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser(getString(R.string.alert_success_take_off));
                }

                @Override
                public void onError(int executionError) {
                    alertUser(getString(R.string.alert_cannot_take_off) + " " + executionError);
                }

                @Override
                public void onTimeout() {
                    alertUser(getString(R.string.alert_cannot_take_off));
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser(getString(R.string.alert_need_to_connect_drone));
        } else {
            // Connected but not Armed
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.dialog_arming));
            builder.setMessage(getString(R.string.dialog_arming_context));
            builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Arming();
                }
            });
            builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        }
    }

    public void Arming() {
        VehicleApi.getApi(this.mDrone).arm(true, false, new SimpleCommandListener() {
            @Override
            public void onError(int executionError) {
                alertUser(getString(R.string.alert_cannot_arming) + " " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser(getString(R.string.alert_cannot_arming));
            }
        });
    }

    // ################################### 연결 Connect ###########################################

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
                alertUser("연결 실패 :" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser(getString(R.string.alert_connected_drone_and_phone));
        this.mControlTower.registerDrone(this.mDrone, this.mHandler);
        this.mDrone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser(getString(R.string.alert_disconnected_drone_and_phone));
    }

    public void connectedDroneOnHopspot() {
        if (ApManager.isApOn(this) == true) {
            State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
            if (!vehicleState.isConnected()) {
                ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
                this.mDrone.connect(params);
            }
        } else {
            alertUser(getString(R.string.alert_turn_on_hotspot));
        }
    }

    // ############################# 리사이클러뷰 RecyclerView ####################################

    private void MakeRecyclerView() {
        LocalTime localTime = LocalTime.now();

        // recycler view 시간 지나면 제거
        if (mRecyclerList.size() > 0) {
            Log.d("Position2", "---------------------------------------------------");
            Log.d("Position2", "[Minute] recycler time : " + mRecyclerTime.get(mRecyclerCount).getMinute());
            Log.d("Position2", "[Minute] Local time : " + localTime.getMinute());
            if (mRecyclerTime.get(mRecyclerCount).getMinute() == localTime.getMinute()) {
                Log.d("Position2", "recycler time : " + mRecyclerTime.get(mRecyclerCount).getSecond());
                Log.d("Position2", "Local time : " + localTime.getSecond());
                Log.d("Position2", "[★] recycler size() : " + mRecyclerList.size());
                Log.d("Position2", "[★] mRecyclerCount : " + mRecyclerCount);
                if (localTime.getSecond() >= mRecyclerTime.get(mRecyclerCount).getSecond() + 3) {
                    RemoveRecyclerView();
                }
            } else {
                // 3초가 지났을 때 1분이 지나감
                Log.d("Position2", "recycler time : " + mRecyclerTime.get(mRecyclerCount).getSecond());
                Log.d("Position2", "Local time : " + localTime.getSecond());
                Log.d("Position2", "[★] recycler size() : " + mRecyclerList.size());
                Log.d("Position2", "[★] mRecyclerCount : " + mRecyclerCount);
                if (localTime.getSecond() + 60 >= mRecyclerTime.get(mRecyclerCount).getSecond() + 3) {
                    RemoveRecyclerView();
                }
            }
            Log.d("Position2", "---------------------------------------------------");
        }
    }

    private void RemoveRecyclerView() {
        mRecyclerList.remove(mRecyclerCount);
        mRecyclerTime.remove(mRecyclerCount);
        if (mRecyclerList.size() > mRecyclerCount) {
            LocalTime localTime = LocalTime.now();
            mRecyclerTime.set(mRecyclerCount, localTime);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        SimpleTextAdapter adapter = new SimpleTextAdapter(mRecyclerList);
        recyclerView.setAdapter(adapter);

        // 리사이클러뷰에 애니메이션 추가.
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.item_animation_down_to_up);
        recyclerView.startAnimation(animation);
    }

    // ######################################## 알림창 ############################################

    private void alertUser(String message) {

        // 5개 이상 삭제
        if (mRecyclerList.size() > 3) {
            mRecyclerList.remove(mRecyclerCount);
        }

        LocalTime localTime = LocalTime.now();
        mRecyclerList.add(String.format("  ★  " + message));
        mRecyclerTime.add(localTime);

        // 리사이클러뷰에 LinearLayoutManager 객체 지정.
        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 리사이클러뷰에 SimpleAdapter 객체 지정.
        SimpleTextAdapter adapter = new SimpleTextAdapter(mRecyclerList);
        recyclerView.setAdapter(adapter);
    }
}