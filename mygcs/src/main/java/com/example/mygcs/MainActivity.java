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

import com.example.mygcs.Connect.ApManager;
import com.example.mygcs.Log.LogTags;
import com.example.mygcs.Math.MyUtil;
import com.example.mygcs.RecyclerView.SimpleTextAdapter;
import com.example.mygcs.TakeOffAltitude.TakeOffAltitude;
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

    private TakeOffAltitude mTakeOffAltitude = new TakeOffAltitude();

    private final int RECYCLER_COUNT = 0;
    private int mAutoDistance = 50;
    private double mGapDistance = 5.0;
    private int mGapCount = 0;

    private boolean mGuidedArrived = false;

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
        this.mModeSelector = (Spinner) findViewById(R.id.spinnerFlightMode);
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
        connectedDroneOnHotspot();

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
                onMapLongClicked(pointF, coord);
            }
        });

        //새로운 branch에서 작업을 시작하였습니다.

        // 클릭 시
        naverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                final Button BtnFlightMode = (Button) findViewById(R.id.btnAuto);
                if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_basic))) {
                    // nothing
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_path))) {
                    makePathFlight(latLng);
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_gap))) {
                    makeGapPolygon(latLng);
                } else if (BtnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {
                    makeAreaPolygon(latLng);
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

    private void showSatelliteCount() {
        // [UI] 잡히는 GPS 개수
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        int satellite = droneGps.getSatellitesCount();
        TextView textView_gps = (TextView) findViewById(R.id.textviewSatellite);
        textView_gps.setText(getString(R.string.show_satellite) + " " + satellite);

        Log.d(LogTags.TAG_SATELLITE, "satellite : " + satellite);

        if(satellite < 10) {
            textView_gps.setBackgroundColor(getResources().getColor(R.color.colorPink_gps));
        } else if(satellite >= 10) {
            textView_gps.setBackgroundColor(getResources().getColor(R.color.colorBlue_gps));
        }
    }

    private void showTakeOffAltitude() {
        final Button BtnTakeOffAltitude = (Button) findViewById(R.id.btnTakeOffAltitude);
        BtnTakeOffAltitude.setText(mTakeOffAltitude.getTakeOffAltitude() + getString(R.string.show_take_off));
    }

    private void updateYaw() {
        // Attitude 받아오기
        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();

        // yaw 값을 양수로
        if ((int) yaw < 0) {
            yaw += 360;
        }

        // [UI] yaw 보여주기
        TextView textView_yaw = (TextView) findViewById(R.id.textviewYaw);
        textView_yaw.setText(getString(R.string.show_degree) + " " + (int) yaw + getString(R.string.show_degree_deg));
    }

    private void batteryUpdate() {
        TextView textView_Vol = (TextView) findViewById(R.id.textviewVoltage);
        Battery battery = this.mDrone.getAttribute(AttributeType.BATTERY);
        double batteryVoltage = Math.round(battery.getBatteryVoltage() * 10) / 10.0;
        textView_Vol.setText(getString(R.string.show_voltage) + " " + batteryVoltage + getString(R.string.show_voltage_V));
        Log.d(LogTags.TAG_BETTERY, "Battery : " + batteryVoltage);
    }

    public void setDronePosition() {
        // 드론 위치 받아오기
        Gps droneGps = this.mDrone.getAttribute(AttributeType.GPS);
        LatLong dronePosition = droneGps.getPosition();

        Log.d(LogTags.TAG_DRONE_POSITION, "droneGps : " + droneGps);
        Log.d(LogTags.TAG_DRONE_POSITION, "dronePosition : " + dronePosition);

        // 이동했던 위치 맵에서 지워주기
        if (mDroneMarkers.size() - 2 >= 0) {
            mDroneMarkers.get(mDroneMarkers.size() - 2).setMap(null);
        }

        // 마커 리스트에 추가
        mDroneMarkers.add(new Marker(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude())));

        // yaw 에 따라 네비게이션 마커 회전
        Attitude attitude = this.mDrone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();
        Log.d(LogTags.TAG_YAW, "yaw : " + yaw);
        if ((int) yaw < 0) {
            yaw += 360;
        }
        mDroneMarkers.get(mDroneMarkers.size() - 1).setAngle((float) yaw);

        // 마커 크기 지정
        mDroneMarkers.get(mDroneMarkers.size() - 1).setHeight(400);
        mDroneMarkers.get(mDroneMarkers.size() - 1).setWidth(80);

        // 마커 아이콘 지정
        mDroneMarkers.get(mDroneMarkers.size() - 1).setIcon(OverlayImage.fromResource(R.drawable.marker_icon));

        // 마커 위치를 중심점으로 지정
        mDroneMarkers.get(mDroneMarkers.size() - 1).setAnchor(new PointF(0.5F, 0.9F));

        // 마커 띄우기
        mDroneMarkers.get(mDroneMarkers.size() - 1).setMap(mNaverMap);

        // 카메라 위치 설정
        Button btnMapMoveLock = (Button) findViewById(R.id.btnMapMove);
        String text = (String) btnMapMoveLock.getText();

        if (text.equals(getString(R.string.map_move_lock))) {
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude()));
            mNaverMap.moveCamera(cameraUpdate);
        }

        // 지나간 길 Polyline
        Collections.addAll(mDronePolylineCoords, mDroneMarkers.get(mDroneMarkers.size() - 1).getPosition());
        mDronePolyline.setCoords(mDronePolylineCoords);

        // 선 예쁘게 설정
        mDronePolyline.setWidth(15);
        mDronePolyline.setCapType(PolylineOverlay.LineCap.Round);
        mDronePolyline.setJoinType(PolylineOverlay.LineJoin.Round);
        mDronePolyline.setColor(Color.GREEN);

        mDronePolyline.setMap(mNaverMap);

        Log.d(LogTags.TAG_DRONE_MARKER, "mDronePolylineCoords.size() : " + mDronePolylineCoords.size());
        Log.d(LogTags.TAG_DRONE_MARKER, "mDroneMarkers.size() : " + mDroneMarkers.size());

        // 가이드 모드일 때 지정된 좌표와 드론 사이의 거리 측정
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        if (vehicleMode == VehicleMode.COPTER_GUIDED) {
            LatLng droneLatLng = new LatLng(mDroneMarkers.get(mDroneMarkers.size() - 1).getPosition().latitude, mDroneMarkers.get(mDroneMarkers.size() - 1).getPosition().longitude);
            LatLng goalLatLng = new LatLng(mMarkerGoal.getPosition().latitude, mMarkerGoal.getPosition().longitude);

            double distance = droneLatLng.distanceTo(goalLatLng);

            Log.d(LogTags.TAG_DISTANCE_BETWEEN_DRONE_AND_GOAL, "distance : " + distance);

            if (distance < 1.0) {
                if(mGuidedArrived == false) {
                    alertUser(getString(R.string.arrive_at_goal));
                    mMarkerGoal.setMap(mNaverMap);
                    mGuidedArrived = true;
                }
            }
        }

        // [UI] 잡히는 GPS 개수
        showSatelliteCount();
    }

    private void updateAltitude() {
        Altitude currentAltitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = currentAltitude.getRelativeAltitude();
        double doubleAltitude = (double) Math.round(mRecentAltitude * 10) / 10.0;

        TextView textView = (TextView) findViewById(R.id.textviewAltitude);
//        Altitude altitude = this.mDrone.getAttribute(AttributeType.ALTITUDE);
//        int intAltitude = (int) Math.round(altitude.getAltitude());

        textView.setText(getString(R.string.show_altitude) + " " + doubleAltitude + getString(R.string.show_altitude_m));
        Log.d(LogTags.TAG_ALTITUDE, "Altitude : " + doubleAltitude);
    }

    private void updateSpeed() {
        TextView textView = (TextView) findViewById(R.id.textviewSpeed);
        Speed speed = this.mDrone.getAttribute(AttributeType.SPEED);
        int doubleSpeed = (int) Math.round(speed.getGroundSpeed());
        // double doubleSpeed = Math.round(speed.getGroundSpeed()*10)/10.0; 소수점 첫째자리까지

        textView.setText(getString(R.string.show_speed) + " " + doubleSpeed + getString(R.string.show_speed_m));
        Log.d(LogTags.TAG_SPEED, "Speed : " + this.mDrone.getAttribute(AttributeType.SPEED));
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

    private void onMapLongClicked(@NonNull PointF pointF, @NonNull final LatLng coord) {
        Button BtnFlightMode = (Button) findViewById(R.id.btnAuto);
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

                    mGuidedArrived = false;

                    // Guided 모드로 변환
                    changeToGuideMode();

                    // 지정된 위치로 이동
                    goToTarget();
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

    private void goToTarget() {
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
        final Button btnMapMove = (Button) findViewById(R.id.btnMapMove);
        final Button btnMapType = (Button) findViewById(R.id.btnMapType);
        final Button btnCadastre = (Button) findViewById(R.id.btnCadastre);
        final Button BtnClear = (Button) findViewById(R.id.btnClear);
        // Map 잠금 버튼
        final Button btnMapMoveLock = (Button) findViewById(R.id.btnMapMoveLock);
        final Button btnMapMoveUnLock = (Button) findViewById(R.id.btnMapMoveUnlock);
        // Map Type 버튼
        final Button btnMapTypeBasic = (Button) findViewById(R.id.btnMapTypeBasic);
        final Button btnMapTypeTerrain = (Button) findViewById(R.id.btnMapTypeTerrain);
        final Button btnMapTypeSatellite = (Button) findViewById(R.id.btnMapTypeSatellite);
        // 지적도 버튼
        final Button btnCadastreOn = (Button) findViewById(R.id.btnCadastreOn);
        final Button btnCadastreOff = (Button) findViewById(R.id.btnCadastreOff);
        // 이륙고도 버튼
        final Button btnTakeOffAltitude = (Button) findViewById(R.id.btnTakeOffAltitude);
        // 이륙고도 Up / Down 버튼
        final Button btnTakeOffUp = (Button) findViewById(R.id.btnTakeOffUp);
        final Button btnTakeOffDown = (Button) findViewById(R.id.btnTakeOffDown);
        // 비행 모드 버튼
        final Button btnFlightMode = (Button) findViewById(R.id.btnAuto);
        // 비행 모드 설정 버튼
        final Button btnAutoBasic = (Button) findViewById(R.id.btnAutoBasic);
        final Button btnAutoPath = (Button) findViewById(R.id.btnAutoPath);
        final Button btnAutoGap = (Button) findViewById(R.id.btnAutoGap);
        final Button btnAutoArea = (Button) findViewById(R.id.btnAutoArea);

        // 임무 전송 / 임무 시작/ 임무 중지 버튼
        final Button btnSendMission = (Button) findViewById(R.id.btnMission);

        // 그리기 버튼
        final Button btnDraw = (Button) findViewById(R.id.btnDraw);

        final UiSettings uiSettings = mNaverMap.getUiSettings();

        // ############################## 기본 UI 버튼 제어 #######################################
        // 맵 이동 / 맵 잠금
        btnMapMove.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (btnMapTypeSatellite.getVisibility() == view.VISIBLE) {
                    btnMapTypeBasic.setVisibility(View.INVISIBLE);
                    btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                    btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (btnCadastreOn.getVisibility() == view.VISIBLE) {
                    btnCadastreOn.setVisibility(View.INVISIBLE);
                    btnCadastreOff.setVisibility(View.INVISIBLE);
                }

                if (btnMapMoveLock.getVisibility() == view.INVISIBLE) {
                    btnMapMoveLock.setVisibility(View.VISIBLE);
                    btnMapMoveUnLock.setVisibility(View.VISIBLE);
                } else if (btnMapMoveLock.getVisibility() == view.VISIBLE) {
                    btnMapMoveLock.setVisibility(View.INVISIBLE);
                    btnMapMoveUnLock.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지도 모드
        btnMapType.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (btnMapMoveUnLock.getVisibility() == view.VISIBLE) {
                    btnMapMoveUnLock.setVisibility(View.INVISIBLE);
                    btnMapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (btnCadastreOn.getVisibility() == view.VISIBLE) {
                    btnCadastreOn.setVisibility(View.INVISIBLE);
                    btnCadastreOff.setVisibility(View.INVISIBLE);
                }
                if (btnMapTypeSatellite.getVisibility() == view.INVISIBLE) {
                    btnMapTypeSatellite.setVisibility(View.VISIBLE);
                    btnMapTypeTerrain.setVisibility(View.VISIBLE);
                    btnMapTypeBasic.setVisibility(View.VISIBLE);
                } else {
                    btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                    btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                    btnMapTypeBasic.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지적도
        btnCadastre.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (btnMapTypeSatellite.getVisibility() == view.VISIBLE) {
                    btnMapTypeBasic.setVisibility(View.INVISIBLE);
                    btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                    btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (btnMapMoveUnLock.getVisibility() == view.VISIBLE) {
                    btnMapMoveUnLock.setVisibility(View.INVISIBLE);
                    btnMapMoveLock.setVisibility(View.INVISIBLE);
                }

                if (btnCadastreOff.getVisibility() == view.INVISIBLE) {
                    btnCadastreOff.setVisibility(View.VISIBLE);
                    btnCadastreOn.setVisibility(View.VISIBLE);
                } else {
                    btnCadastreOff.setVisibility(View.INVISIBLE);
                    btnCadastreOn.setVisibility(View.INVISIBLE);
                }
            }
        });

        // ############################### 맵 이동 관련 제어 ######################################
        // 맵잠금
        btnMapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnMapMoveUnLock.setBackgroundResource(R.drawable.mybutton_dark);
                btnMapMoveLock.setBackgroundResource(R.drawable.mybutton);

                btnMapMove.setText(getString(R.string.map_move_lock));

                uiSettings.setScrollGesturesEnabled(false);

                btnMapMoveLock.setVisibility(View.INVISIBLE);
                btnMapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // 맵 이동
        btnMapMoveUnLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnMapMoveUnLock.setBackgroundResource(R.drawable.mybutton);
                btnMapMoveLock.setBackgroundResource(R.drawable.mybutton_dark);

                btnMapMove.setText(getString(R.string.map_move_unlock));

                uiSettings.setScrollGesturesEnabled(true);

                btnMapMoveLock.setVisibility(View.INVISIBLE);
                btnMapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // ################################## 지도 모드 제어 ######################################

        // 위성 지도
        btnMapTypeSatellite.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                btnMapTypeSatellite.setBackgroundResource(R.drawable.mybutton);
                btnMapTypeBasic.setBackgroundResource(R.drawable.mybutton_dark);
                btnMapTypeTerrain.setBackgroundResource(R.drawable.mybutton_dark);

                btnMapType.setText(getString(R.string.map_type_satellite));

                mNaverMap.setMapType(NaverMap.MapType.Satellite);

                // 다시 닫기
                btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                btnMapTypeBasic.setVisibility(View.INVISIBLE);
            }
        });

        // 지형도
        btnMapTypeTerrain.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                btnMapTypeSatellite.setBackgroundResource(R.drawable.mybutton_dark);
                btnMapTypeBasic.setBackgroundResource(R.drawable.mybutton_dark);
                btnMapTypeTerrain.setBackgroundResource(R.drawable.mybutton);

                btnMapType.setText(getString(R.string.map_type_terrain));

                mNaverMap.setMapType(NaverMap.MapType.Terrain);

                btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                btnMapTypeBasic.setVisibility(View.INVISIBLE);
            }
        });

        // 일반지도
        btnMapTypeBasic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnMapTypeSatellite.setBackgroundResource(R.drawable.mybutton_dark);
                btnMapTypeBasic.setBackgroundResource(R.drawable.mybutton);
                btnMapTypeTerrain.setBackgroundResource(R.drawable.mybutton_dark);

                btnMapType.setText(getString(R.string.map_type_basic));

                mNaverMap.setMapType(NaverMap.MapType.Basic);

                btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                btnMapTypeBasic.setVisibility(View.INVISIBLE);
            }
        });

        // ################################ 지적도 On / Off 제어 ##################################

        btnCadastreOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnCadastreOn.setBackgroundResource(R.drawable.mybutton);
                btnCadastreOff.setBackgroundResource(R.drawable.mybutton_dark);

                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);

                btnCadastreOn.setVisibility(View.INVISIBLE);
                btnCadastreOff.setVisibility(View.INVISIBLE);
            }
        });

        btnCadastreOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnCadastreOn.setBackgroundResource(R.drawable.mybutton_dark);
                btnCadastreOff.setBackgroundResource(R.drawable.mybutton);

                mNaverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);

                btnCadastreOn.setVisibility(View.INVISIBLE);
                btnCadastreOff.setVisibility(View.INVISIBLE);
            }
        });

        // ###################################### Clear ###########################################
        BtnClear.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (btnMapMoveUnLock.getVisibility() == view.VISIBLE) {
                    btnMapMoveUnLock.setVisibility(View.INVISIBLE);
                    btnMapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (btnMapTypeSatellite.getVisibility() == view.VISIBLE) {
                    btnMapTypeBasic.setVisibility(View.INVISIBLE);
                    btnMapTypeTerrain.setVisibility(View.INVISIBLE);
                    btnMapTypeSatellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (btnCadastreOn.getVisibility() == view.VISIBLE) {
                    btnCadastreOn.setVisibility(View.INVISIBLE);
                    btnCadastreOff.setVisibility(View.INVISIBLE);
                }

                // 이전 마커 지우기
                if (mDroneMarkers.size() - 2 >= 0) {
                    mDroneMarkers.get(mDroneMarkers.size() - 2).setMap(null);
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
                if (btnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {
                    btnDraw.setVisibility(View.VISIBLE);
                }

                // 리스트 값 지우기
                mDronePolylineCoords.clear();
                mAutoMarkers.clear();
                mAutoPolylineCoords.clear();
                mAutoPolygonCoords.clear();

                // Top 변수 초기화
                mGapCount = 0;

                mReachedCount = 1;

                // btnFlightMode 버튼 초기화
                btnSendMission.setText(getString(R.string.btn_mission_transmition));
            }
        });

        // ###################################### 이륙 고도 설정 #################################
        // 이륙고도 버튼
        btnTakeOffAltitude.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (btnTakeOffUp.getVisibility() == view.VISIBLE) {
                    btnTakeOffUp.setVisibility(View.INVISIBLE);
                    btnTakeOffDown.setVisibility(View.INVISIBLE);
                } else if (btnTakeOffUp.getVisibility() == view.INVISIBLE) {
                    btnTakeOffUp.setVisibility(View.VISIBLE);
                    btnTakeOffDown.setVisibility(View.VISIBLE);
                }
            }
        });

        // 이륙고도 Up 버튼
        btnTakeOffUp.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
//                mTakeOffAltitude++;
                mTakeOffAltitude.setTakeOffAltitude(mTakeOffAltitude.getTakeOffAltitude()+1);
                showTakeOffAltitude();
            }
        });

        // 이륙고도 Down 버튼
        btnTakeOffDown.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
//                mTakeOffAltitude--;
                mTakeOffAltitude.setTakeOffAltitude(mTakeOffAltitude.getTakeOffAltitude()-1);
                showTakeOffAltitude();
            }
        });

        // #################################### 비행 모드 설정 ####################################
        // 비행 모드 버튼
        btnFlightMode.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (btnAutoBasic.getVisibility() == view.VISIBLE) {
                    btnAutoBasic.setVisibility(view.INVISIBLE);
                    btnAutoPath.setVisibility(view.INVISIBLE);
                    btnAutoGap.setVisibility(view.INVISIBLE);
                    btnAutoArea.setVisibility(view.INVISIBLE);
                } else if (btnAutoBasic.getVisibility() == view.INVISIBLE) {
                    btnAutoBasic.setVisibility(view.VISIBLE);
                    btnAutoPath.setVisibility(view.VISIBLE);
                    btnAutoGap.setVisibility(view.VISIBLE);
                    btnAutoArea.setVisibility(view.VISIBLE);
                }
            }
        });

        // 일반 모드
        btnAutoBasic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnFlightMode.setText(getString(R.string.auto_mode_basic));

                // 그리기 버튼 제어
                controlBtnDraw();

                btnSendMission.setVisibility(view.INVISIBLE);

                btnAutoBasic.setVisibility(view.INVISIBLE);
                btnAutoPath.setVisibility(view.INVISIBLE);
                btnAutoGap.setVisibility(view.INVISIBLE);
                btnAutoArea.setVisibility(view.INVISIBLE);
            }
        });

        // 경로 비행 모드
        btnAutoPath.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnFlightMode.setText(getString(R.string.auto_mode_path));

                btnSendMission.setVisibility(View.VISIBLE);

                // 그리기 버튼 제어
                controlBtnDraw();

                btnAutoBasic.setVisibility(view.INVISIBLE);
                btnAutoPath.setVisibility(view.INVISIBLE);
                btnAutoGap.setVisibility(view.INVISIBLE);
                btnAutoArea.setVisibility(view.INVISIBLE);
            }
        });

        // 간격 감시 모드
        btnAutoGap.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnFlightMode.setText(getString(R.string.auto_mode_gap));

                btnSendMission.setVisibility(View.VISIBLE);

                // 그리기 버튼 제어
                controlBtnDraw();

                alertUser(getString(R.string.alert_a_b_latlng));

                dialogGapFirst();

                btnAutoBasic.setVisibility(view.INVISIBLE);
                btnAutoPath.setVisibility(view.INVISIBLE);
                btnAutoGap.setVisibility(view.INVISIBLE);
                btnAutoArea.setVisibility(view.INVISIBLE);
            }
        });

        // 면적 감시 모드
        btnAutoArea.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnFlightMode.setText(getString(R.string.auto_mode_area));

                // 그리기 버튼 제어
                controlBtnDraw();

                btnDraw.setVisibility(view.VISIBLE);

                btnAutoBasic.setVisibility(view.INVISIBLE);
                btnAutoPath.setVisibility(view.INVISIBLE);
                btnAutoGap.setVisibility(view.INVISIBLE);
                btnAutoArea.setVisibility(view.INVISIBLE);
            }
        });

        // #################################### 그리기 설정 #######################################
        btnDraw.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAutoPolygonCoords.size() >= 3) {
                    btnDraw.setVisibility(view.INVISIBLE);
                    mAutoPolygon.setCoords(mAutoPolygonCoords);

                    int colorLightBlue = getResources().getColor(R.color.colorLightBlue);

                    mAutoPolygon.setColor(colorLightBlue);
                    mAutoPolygon.setMap(mNaverMap);

                    computeLargeLength();

                } else {
                    alertUser(getString(R.string.alert_three_more_latlng));
                }
            }
        });
    }

    private void controlBtnDraw() {
        Button btnFlightMode = (Button) findViewById(R.id.btnAuto);
        Button btnDraw = (Button) findViewById(R.id.btnDraw);
        if (btnFlightMode.getText().equals(getString(R.string.auto_mode_area))) {

        } else {
            btnDraw.setVisibility(View.INVISIBLE);
        }
    }

    // ################################### 미션 수행 Mission ######################################

    private void makeWaypoint() {
        final Mission mission = new Mission();

        for (int i = 0; i < mAutoPolylineCoords.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(1);

            LatLongAlt latLongAlt = new LatLongAlt(mAutoPolylineCoords.get(i).latitude, mAutoPolylineCoords.get(i).longitude, mRecentAltitude);
            waypoint.setCoordinate(latLongAlt);

            mission.addMissionItem(waypoint);
        }

        final Button btnSendMission = (Button) findViewById(R.id.btnMission);
        final Button btnFlightMode = (Button) findViewById(R.id.btnAuto);

        btnSendMission.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(btnFlightMode.getText().equals(getString(R.string.auto_mode_gap))) {
                    if (btnSendMission.getText().equals(getString(R.string.btn_mission_transmition))) {
                        if (mAutoPolygonCoords.size() == 4) {
                            setMission(mission);
                        } else {
                            alertUser(getString(R.string.alert_a_b_latlng));
                        }
                    } else if (btnSendMission.getText().equals(getString(R.string.btn_mission_start))) {
                        // Auto모드로 전환
                        changeToAutoMode();
                        btnSendMission.setText(getString(R.string.btn_mission_stop));
                    } else if (btnSendMission.getText().equals(getString(R.string.btn_mission_stop))) {
                        pauseMission();
                        changeToLoiterMode();
                        btnSendMission.setText(getString(R.string.btn_mission_restart));
                    } else if (btnSendMission.getText().equals(getString(R.string.btn_mission_restart))) {
                        changeToAutoMode();
                        btnSendMission.setText(getString(R.string.btn_mission_stop));
                    }
                } else if(btnFlightMode.getText().equals(getString(R.string.auto_mode_path))) {
                    if(btnSendMission.getText().equals(getString(R.string.btn_mission_transmition))) {
                        if (mAutoPolylineCoords.size() > 0) {
                            setMission(mission);
                        } else {
                            alertUser(getString(R.string.alert_one_more_latlng));
                        }
                    } else if(btnSendMission.getText().equals(getString(R.string.btn_mission_start))) {
                        // Auto모드로 전환
                        changeToAutoMode();
                        btnSendMission.setText(getString(R.string.btn_mission_stop));
                    } else if(btnSendMission.getText().equals(getString(R.string.btn_mission_stop))) {
                        changeToLoiterMode();
                        btnSendMission.setText(getString(R.string.btn_mission_restart));
                    } else if(btnSendMission.getText().equals(getString(R.string.btn_mission_restart))) {
                        changeToAutoMode();
                        btnSendMission.setText(getString(R.string.btn_mission_stop));
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

    private void sentMission() {
        alertUser(getString(R.string.alert_mission_upload));
        Button btnSendMission = (Button) findViewById(R.id.btnMission);
        btnSendMission.setText(getString(R.string.btn_mission_start));
    }

    // ################################## 비행 모드 변경 ##########################################

    public void changeToLoiterMode() {
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

    public void changeToAutoMode() {
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

    public void changeToGuideMode() {
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

    // ###################################### 경로 비행 ###########################################

    private void makePathFlight(LatLng latLng)  {
        mAutoPolylineCoords.add(latLng);

        Marker marker = new Marker();
        marker.setPosition(latLng);
        mAutoMarkers.add(marker);

        mAutoMarkers.get(mAutoMarkers.size() - 1).setHeight(100);
        mAutoMarkers.get(mAutoMarkers.size() - 1).setWidth(100);

        mAutoMarkers.get(mAutoMarkers.size() - 1).setAnchor(new PointF(0.5F, 0.9F));
        mAutoMarkers.get(mAutoMarkers.size() - 1).setIcon(OverlayImage.fromResource(R.drawable.area_marker));

        mAutoMarkers.get(mAutoMarkers.size() - 1).setMap(mNaverMap);

        makeWaypoint();
    }

    // ################################# 간격 감시 ################################################

    private void makeGapPolygon(LatLng latLng) {
        if (mGapCount < 2) {
            Marker marker = new Marker();
            marker.setPosition(latLng);
            mAutoPolygonCoords.add(latLng);

            // mAutoMarkers에 넣기 위해 marker 생성..
            mAutoMarkers.add(marker);
            mAutoMarkers.get(mAutoMarkers.size() - 1).setMap(mNaverMap);

            if (mGapCount == 0) {
                mAutoMarkers.get(0).setIcon(OverlayImage.fromResource(R.drawable.number1));
                mAutoMarkers.get(0).setWidth(80);
                mAutoMarkers.get(0).setHeight(80);
                mAutoMarkers.get(0).setAnchor(new PointF(0.5F, 0.5F));
            } else if (mGapCount == 1) {
                mAutoMarkers.get(1).setIcon(OverlayImage.fromResource(R.drawable.number2));
                mAutoMarkers.get(1).setWidth(80);
                mAutoMarkers.get(1).setHeight(80);
                mAutoMarkers.get(1).setAnchor(new PointF(0.5F, 0.5F));
            }

            mGapCount++;
        }
        if (mAutoMarkers.size() == 2) {
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

            Log.d(LogTags.TAG_AUTO_POLYLINE, "LatLng[0] : " + mAutoPolygonCoords.get(0).latitude + " / " + mAutoPolygonCoords.get(0).longitude);
            Log.d(LogTags.TAG_AUTO_POLYLINE, "LatLng[1] : " + mAutoPolygonCoords.get(1).latitude + " / " + mAutoPolygonCoords.get(1).longitude);
            Log.d(LogTags.TAG_AUTO_POLYLINE, "LatLng[2] : " + mAutoPolygonCoords.get(2).latitude + " / " + mAutoPolygonCoords.get(2).longitude);
            Log.d(LogTags.TAG_AUTO_POLYLINE, "LatLng[3] : " + mAutoPolygonCoords.get(3).latitude + " / " + mAutoPolygonCoords.get(3).longitude);

            int colorLightBlue = getResources().getColor(R.color.colorLightBlue);

            mAutoPolygon.setColor(colorLightBlue);
            mAutoPolygon.setMap(mNaverMap);

            // 내부 길 생성
            makeGapPath();
        }
    }

    private void makeGapPath() {
        double heading = MyUtil.computeHeading(mAutoMarkers.get(0).getPosition(), mAutoMarkers.get(1).getPosition());

        mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(0).getPosition().latitude, mAutoMarkers.get(0).getPosition().longitude));
        mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(1).getPosition().latitude, mAutoMarkers.get(1).getPosition().longitude));

        for (double sum = mGapDistance; sum + mGapDistance <= mAutoDistance + mGapDistance; sum = sum + mGapDistance) {
            LatLng latLng1 = MyUtil.computeOffset(mAutoMarkers.get(mAutoMarkers.size() - 1).getPosition(), mGapDistance, heading + 90);
            LatLng latLng2 = MyUtil.computeOffset(mAutoMarkers.get(mAutoMarkers.size() - 2).getPosition(), mGapDistance, heading + 90);

            mAutoMarkers.add(new Marker(latLng1));
            mAutoMarkers.add(new Marker(latLng2));
//            mAutoMarkers.size() - 1 += 2;

            mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(mAutoMarkers.size() - 2).getPosition().latitude, mAutoMarkers.get(mAutoMarkers.size() - 2).getPosition().longitude));
            mAutoPolylineCoords.add(new LatLng(mAutoMarkers.get(mAutoMarkers.size() - 1).getPosition().latitude, mAutoMarkers.get(mAutoMarkers.size() - 1).getPosition().longitude));
        }

        mAutoPolylinePath.setColor(Color.WHITE);
        mAutoPolylinePath.setCoords(mAutoPolylineCoords);
        mAutoPolylinePath.setMap(mNaverMap);

        // WayPoint
        makeWaypoint();
    }

    // ################################## 간격 감시 시 Dialog #####################################

    private void dialogGapFirst() {
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
                        dialogGapSecond();
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

    private void dialogGapSecond() {
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

    private void makeAreaPolygon(LatLng latLng) {
        Button btnDraw = (Button) findViewById(R.id.btnDraw);
        if (btnDraw.getVisibility() == View.VISIBLE) {
            mAutoPolygonCoords.add(latLng);

            Marker marker = new Marker();
            marker.setPosition(latLng);
            mAutoMarkers.add(marker);

            mAutoMarkers.get(mAutoMarkers.size() - 1).setHeight(100);
            mAutoMarkers.get(mAutoMarkers.size() - 1).setWidth(100);

            mAutoMarkers.get(mAutoMarkers.size() - 1).setAnchor(new PointF(0.5F, 0.9F));
            mAutoMarkers.get(mAutoMarkers.size() - 1).setIcon(OverlayImage.fromResource(R.drawable.area_marker));

            mAutoMarkers.get(mAutoMarkers.size() - 1).setMap(mNaverMap);
        }
    }

    private void computeLargeLength() {
        // 가장 긴 변 계산
        double max = 0.0;
        double computeValue = 0.0;
        int firstIndex = 0;
        int secondIndex = 0;
        for(int i=0; i<mAutoPolygonCoords.size(); i++) {
            if(i == mAutoPolygonCoords.size() - 1) {
                computeValue = mAutoPolygonCoords.get(i).distanceTo(mAutoPolygonCoords.get(0));
                Log.d(LogTags.TAG_COMPUTE_LARGE_LENGTH, "computeValue : [" + i + " , 0 ] : " + computeValue);
                if(max < computeValue) {
                    max = computeValue;
                    firstIndex = i;
                    secondIndex = 0;
                }
            } else {
                computeValue = mAutoPolygonCoords.get(i).distanceTo(mAutoPolygonCoords.get(i+1));
                Log.d(LogTags.TAG_COMPUTE_LARGE_LENGTH, "computeValue : [" + i + " , " + (i+1) + "] : " + computeValue);
                if(max < computeValue) {
                    max = computeValue;
                    firstIndex = i;
                    secondIndex = i+1;
                }
            }
            Log.d(LogTags.TAG_COMPUTE_LARGE_LENGTH, "max : " + max);
            Log.d(LogTags.TAG_COMPUTE_LARGE_LENGTH, "firstIndex : " + firstIndex + " / secondIndex : " + secondIndex);
        }

        makeAreaPath(firstIndex, secondIndex);
    }

    private void makeAreaPath(int firstIndex, int secondIndex) {
        double heading = MyUtil.computeHeading(mAutoPolygonCoords.get(firstIndex), mAutoPolygonCoords.get(secondIndex));

        Log.d(LogTags.TAG_HEADING, "heading : " + heading);

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
                setDronePosition();
                break;

            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                batteryUpdate();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                updateYaw();
                break;

            case AttributeEvent.GPS_COUNT:
                showSatelliteCount();
                break;

            case AttributeEvent.MISSION_SENT:
                sentMission();
                break;

            case AttributeEvent.MISSION_ITEM_REACHED:
                alertUser(mReachedCount + getString(R.string.alert_compute_waypoint_count) + mReachedCount + getString(R.string.slash) + mAutoPolylineCoords.size());
                mReachedCount++;
                break;

            default:
                makeRecyclerView();
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    // ################################### 아밍 Arming ############################################

    private void updateArmButton() {
        State vehicleState = this.mDrone.getAttribute(AttributeType.STATE);
        Button ArmBtn = (Button) findViewById(R.id.btnArm);

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
            ControlApi.getApi(this.mDrone).takeoff(mTakeOffAltitude.getTakeOffAltitude(), new AbstractCommandListener() {
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
                    arming();
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

    public void arming() {
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
                alertUser(getString(R.string.alert_fail_to_connect) + msg);
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

    public void connectedDroneOnHotspot() {
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

    private void makeRecyclerView() {
        LocalTime localTime = LocalTime.now();

        // recycler view 시간 지나면 제거
        if (mRecyclerList.size() > 0) {
            Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "---------------------------------------------------");
            Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[Minute] recycler time : " + mRecyclerTime.get(RECYCLER_COUNT).getMinute());
            Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[Minute] Local time : " + localTime.getMinute());
            if (mRecyclerTime.get(RECYCLER_COUNT).getMinute() == localTime.getMinute()) {
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "recycler time : " + mRecyclerTime.get(RECYCLER_COUNT).getSecond());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "Local time : " + localTime.getSecond());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[★] recycler size() : " + mRecyclerList.size());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[★] RECYCLER_COUNT : " + RECYCLER_COUNT);
                if (localTime.getSecond() >= mRecyclerTime.get(RECYCLER_COUNT).getSecond() + 3) {
                    removeRecyclerView();
                }
            } else {
                // 3초가 지났을 때 1분이 지나감
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "recycler time : " + mRecyclerTime.get(RECYCLER_COUNT).getSecond());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "Local time : " + localTime.getSecond());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[★] recycler size() : " + mRecyclerList.size());
                Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "[★] RECYCLER_COUNT : " + RECYCLER_COUNT);
                if (localTime.getSecond() + 60 >= mRecyclerTime.get(RECYCLER_COUNT).getSecond() + 3) {
                    removeRecyclerView();
                }
            }
            Log.d(LogTags.TAG_RECYCLERVIEW_TIME, "---------------------------------------------------");
        }
    }

    private void removeRecyclerView() {
        mRecyclerList.remove(RECYCLER_COUNT);
        mRecyclerTime.remove(RECYCLER_COUNT);
        if (mRecyclerList.size() > RECYCLER_COUNT) {
            LocalTime localTime = LocalTime.now();
            mRecyclerTime.set(RECYCLER_COUNT, localTime);
        }

        RecyclerView recyclerView = findViewById(R.id.recyclerviewAlerting);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        SimpleTextAdapter adapter = new SimpleTextAdapter(mRecyclerList);
        recyclerView.setAdapter(adapter);

        // 리사이클러뷰에 애니메이션 추가.
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.item_animation_down_to_up);
        recyclerView.startAnimation(animation);
    }

    // ######################################## 알림창 ############################################

    public void alertUser(String message) {
        // 5개 이상 삭제
        if (mRecyclerList.size() > 3) {
            mRecyclerList.remove(RECYCLER_COUNT);
        }

        LocalTime localTime = LocalTime.now();
        mRecyclerList.add(String.format("  ★  " + message));
        mRecyclerTime.add(localTime);

        // 리사이클러뷰에 LinearLayoutManager 객체 지정.
        RecyclerView recyclerView = findViewById(R.id.recyclerviewAlerting);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 리사이클러뷰에 SimpleAdapter 객체 지정.
        SimpleTextAdapter adapter = new SimpleTextAdapter(mRecyclerList);
        recyclerView.setAdapter(adapter);
    }
}