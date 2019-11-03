package com.example.mygcs.Math;

import com.naver.maps.geometry.LatLng;

import java.util.Iterator;
import java.util.List;

public class MyUtil {
    static double wrap(double n, double min, double max) {
        return n >= min && n < max ? n : mod(n - min, max - min) + min;
    }

    static double mod(double x, double m) {
        return (x % m + m) % m;
    }

    static double arcHav(double x) {
        return 2.0D * Math.asin(Math.sqrt(x));
    }

    public static double computeHeading(LatLng from, LatLng to) {
        double fromLat = Math.toRadians(from.latitude);
        double fromLng = Math.toRadians(from.longitude);
        double toLat = Math.toRadians(to.latitude);
        double toLng = Math.toRadians(to.longitude);
        double dLng = toLng - fromLng;
        double heading = Math.atan2(Math.sin(dLng) * Math.cos(toLat), Math.cos(fromLat) * Math.sin(toLat) - Math.sin(fromLat) * Math.cos(toLat) * Math.cos(dLng));
        return wrap(Math.toDegrees(heading), -180.0D, 180.0D);
    }

    public static LatLng computeOffset(LatLng from, double distance, double heading) {
        distance /= 6371009.0D;
        heading = Math.toRadians(heading);
        double fromLat = Math.toRadians(from.latitude);
        double fromLng = Math.toRadians(from.longitude);
        double cosDistance = Math.cos(distance);
        double sinDistance = Math.sin(distance);
        double sinFromLat = Math.sin(fromLat);
        double cosFromLat = Math.cos(fromLat);
        double sinLat = cosDistance * sinFromLat + sinDistance * cosFromLat * Math.cos(heading);
        double dLng = Math.atan2(sinDistance * cosFromLat * Math.sin(heading), cosDistance - sinFromLat * sinLat);
        return new LatLng(Math.toDegrees(Math.asin(sinLat)), Math.toDegrees(fromLng + dLng));
    }

    // ########################## computeLength #################################

    public static double computeLength(List<LatLng> path) {
        if (path.size() < 2) {
            return 0.0D;
        } else {
            double length = 0.0D;
            LatLng prev = (LatLng)path.get(0);
            double prevLat = Math.toRadians(prev.latitude);
            double prevLng = Math.toRadians(prev.longitude);

            double lng;
            for(Iterator i$ = path.iterator(); i$.hasNext(); prevLng = lng) {
                LatLng point = (LatLng)i$.next();
                double lat = Math.toRadians(point.latitude);
                lng = Math.toRadians(point.longitude);
                length += distanceRadians(prevLat, prevLng, lat, lng);
                prevLat = lat;
            }

            return length * 6371009.0D;
        }
    }

    private static double distanceRadians(double lat1, double lng1, double lat2, double lng2) {
        return arcHav(havDistance(lat1, lat2, lng1 - lng2));
    }

    static double havDistance(double lat1, double lat2, double dLng) {
        return hav(lat1 - lat2) + hav(dLng) * Math.cos(lat1) * Math.cos(lat2);
    }

    static double hav(double x) {
        double sinHalf = Math.sin(x * 0.5D);
        return sinHalf * sinHalf;
    }

    // ############################ computeDistance ##################################

    public static double computeDistanceBetween(LatLng from, LatLng to) {
        return computeAngleBetween(from, to) * 6371009.0D;
    }

    public static double computeAngleBetween(LatLng from, LatLng to) {
        return distanceRadians(Math.toRadians(from.latitude), Math.toRadians(from.longitude), Math.toRadians(to.latitude), Math.toRadians(to.longitude));
    }
}
