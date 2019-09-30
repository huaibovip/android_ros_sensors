/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.ros.android.android_tutorial_camera_imu;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import org.ros.address.InetAddressFactory;
import org.ros.android.RosActivity;
import org.ros.android.view.camera.RosCameraPreviewView;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

/**
 * @author ethan.rublee@gmail.com (Ethan Rublee)
 * @author damonkohler@google.com (Damon Kohler)
 * @author huaibovip@gmail.com (Charles)
 */

public class MainActivity extends RosActivity {

    private int cameraId = 0;
    private RosCameraPreviewView rosCameraPreviewView;
    private NavSatFixPublisher fix_pub;
    private ImuPublisher imu_pub;

    private NodeMainExecutor nodeMainExecutor;
    private LocationManager mLocationManager;
    private SensorManager mSensorManager;

    public MainActivity() {
        super("ROS", "Camera & Imu");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        rosCameraPreviewView = findViewById(R.id.ros_camera_preview_view);
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int numberOfCameras = Camera.getNumberOfCameras();
                final Toast toast;
                if (numberOfCameras > 1) {
                    cameraId = (cameraId + 1) % numberOfCameras;
                    rosCameraPreviewView.releaseCamera();
                    rosCameraPreviewView.setCamera(getCamera());
                    toast = Toast.makeText(this, "Switching cameras.", Toast.LENGTH_SHORT);
                } else {
                    toast = Toast.makeText(this, "No alternative cameras to switch to.", Toast.LENGTH_SHORT);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast.show();
                    }
                });
            }
        }
        return true;
    }

    @Override @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) //API = 15
    protected void init(NodeMainExecutor nodeMainExecutor) {
        this.nodeMainExecutor = nodeMainExecutor;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] PERMISSIONS = {"", "", "", ""};
            PERMISSIONS[0] = Manifest.permission.ACCESS_FINE_LOCATION;
            PERMISSIONS[1] = Manifest.permission.CAMERA;
            PERMISSIONS[2] = Manifest.permission.READ_EXTERNAL_STORAGE;
            PERMISSIONS[3] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
        }else {
            NodeConfiguration nodeConfiguration1 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration1.setMasterUri(getMasterUri());
            nodeConfiguration1.setNodeName("android_sensors_driver_nav_sat_fix");
            this.fix_pub = new NavSatFixPublisher(mLocationManager);
            nodeMainExecutor.execute(this.fix_pub, nodeConfiguration1);

            rosCameraPreviewView.setCamera(getCamera());
            NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration2.setMasterUri(getMasterUri());
            nodeConfiguration2.setNodeName("android_sensors_driver_camera");
            nodeMainExecutor.execute(this.rosCameraPreviewView, nodeConfiguration2);
        }

        NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfiguration3.setMasterUri(getMasterUri());
        nodeConfiguration3.setNodeName("android_sensors_driver_imu");
        this.imu_pub = new ImuPublisher(mSensorManager);
        nodeMainExecutor.execute(this.imu_pub, nodeConfiguration3);
    }

    private void executeGPS() {
        NodeConfiguration nodeConfiguration1 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfiguration1.setMasterUri(getMasterUri());
        nodeConfiguration1.setNodeName("android_sensors_driver_nav_sat_fix");
        this.fix_pub = new NavSatFixPublisher(mLocationManager);
        nodeMainExecutor.execute(this.fix_pub, nodeConfiguration1);
    }

    private void executeCamera() {
        rosCameraPreviewView.setCamera(getCamera());
        NodeConfiguration nodeConfiguration2 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
        nodeConfiguration2.setMasterUri(getMasterUri());
        nodeConfiguration2.setNodeName("android_sensors_driver_camera");
        nodeMainExecutor.execute(this.rosCameraPreviewView, nodeConfiguration2);
    }

    private Camera getCamera() {
        Camera cam = Camera.open(cameraId);
        Camera.Parameters camParams = cam.getParameters();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            if (camParams.getSupportedFocusModes().contains(
                    Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else {
                camParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
        }
        cam.setParameters(camParams);
        return cam;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                executeGPS();
            }
            if (grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                executeCamera();
            }

            if (grantResults.length > 2 && grantResults[2] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[3] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
            }
        }
    }
}
