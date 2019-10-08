package com.example.mygcs.Drone.DroneMission;

import com.naver.maps.geometry.LatLng;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;

import java.util.List;

public class DroneMission {

    public static void makeWaypoint(List<LatLng> mAutoPolylineCoords, double mRecentAltitude, Mission mission) {

        for (int i = 0; i < mAutoPolylineCoords.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(1);

            LatLongAlt latLongAlt = new LatLongAlt(mAutoPolylineCoords.get(i).latitude, mAutoPolylineCoords.get(i).longitude, mRecentAltitude);
            waypoint.setCoordinate(latLongAlt);

            mission.addMissionItem(waypoint);
        }
    }
}
