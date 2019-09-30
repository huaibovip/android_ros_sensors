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
package org.ros.android.view.camera;

import com.google.common.base.Preconditions;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.util.Log;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.ros.exception.ServiceException;
import org.ros.internal.message.MessageBuffers;
import org.ros.message.Time;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.service.ServiceResponseBuilder;
import org.ros.node.service.ServiceServer;
import org.ros.node.topic.Publisher;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;


/**
 * Publishes preview frames.
 *
 * @author huaibovip@gmail.com (Charles)
 */

class CompressedImagePublisher implements RawImageListener {
    private static final String TAG = "CompressedImage";

    private final ConnectedNode connectedNode;
    private final Publisher<sensor_msgs.CompressedImage> imagePublisher;
    private final Publisher<sensor_msgs.CameraInfo> cameraInfoPublisher;
    private final ServiceServer<sensor_msgs.SetCameraInfoRequest, sensor_msgs.SetCameraInfoResponse> setCameraInfoService;

    private Time lastTime;
    private YamlCamera yamlCamera;
    private String yamlFile;
    private boolean loadStatus;
    private byte[] rawImageBuffer;
    private Size rawImageSize;
    private YuvImage yuvImage;
    private Rect rect;
    private ChannelBufferOutputStream stream;

    public CompressedImagePublisher(ConnectedNode connectedNode) {
        this.connectedNode = connectedNode;
        this.loadStatus = false;
        this.yamlFile = "camera.yaml";
        this.lastTime = connectedNode.getCurrentTime();

        NameResolver resolver = connectedNode.getResolver().newChild("camera");
        imagePublisher =
                connectedNode.newPublisher(resolver.resolve("image/compressed"), sensor_msgs.CompressedImage._TYPE);
        cameraInfoPublisher =
                connectedNode.newPublisher(resolver.resolve("camera_info"), sensor_msgs.CameraInfo._TYPE);
        cameraInfoPublisher.setLatchMode(true);
        setCameraInfoService =
                connectedNode.newServiceServer(resolver.resolve("set_camera_info"), sensor_msgs.SetCameraInfo._TYPE,
                        new ServiceResponseBuilder<sensor_msgs.SetCameraInfoRequest, sensor_msgs.SetCameraInfoResponse>() {
                            @Override
                            public void build(sensor_msgs.SetCameraInfoRequest request, sensor_msgs.SetCameraInfoResponse response) throws ServiceException {
                                if(saveCameraInfoYaml(request.getCameraInfo(), yamlFile)) {
                                    response.setStatusMessage("Succeed to save camera.yaml");
                                    response.setSuccess(true);
                                }else {
                                    response.setStatusMessage("Fail to save camera.yaml");
                                    response.setSuccess(false);
                                }
                            }
                        });
        stream = new ChannelBufferOutputStream(MessageBuffers.dynamicBuffer());
        loadStatus = loadCameraInfoYaml(yamlFile);
    }

    @Override
    public void onNewRawImage(byte[] data, Size size) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(size);

        if (data != rawImageBuffer || !size.equals(rawImageSize)) {
            rawImageBuffer = data;
            rawImageSize = size;
            yuvImage = new YuvImage(rawImageBuffer, ImageFormat.NV21, size.width, size.height, null);
            rect = new Rect(0, 0, size.width, size.height);
        }

        Time currentTime = connectedNode.getCurrentTime();
        String frameId = "camera";

        sensor_msgs.CompressedImage image = imagePublisher.newMessage();
        image.setFormat("jpeg");
        image.getHeader().setStamp(currentTime);
        image.getHeader().setFrameId(frameId);

        Preconditions.checkState(yuvImage.compressToJpeg(rect, 20, stream));
        image.setData(stream.buffer().copy());
        stream.buffer().clear();
        imagePublisher.publish(image);

        if(currentTime.subtract(lastTime).secs == 1)
        {
            sensor_msgs.CameraInfo cameraInfo = cameraInfoPublisher.newMessage();
            cameraInfo.getHeader().setStamp(currentTime);
            cameraInfo.getHeader().setFrameId(frameId);
            cameraInfo.setWidth(size.width);
            cameraInfo.setHeight(size.height);

            if(loadStatus == true) {
                cameraInfo.setDistortionModel(yamlCamera.getDistortionModel());
                cameraInfo.setD(yamlCamera.getDistortionCoefficients().getData());
                cameraInfo.setK(yamlCamera.getCameraMatrix().getData());
                cameraInfo.setR(yamlCamera.getRectificationMatrix().getData());
                cameraInfo.setP(yamlCamera.getProjectionMatrix().getData());
            }
            cameraInfoPublisher.publish(cameraInfo);
            lastTime = currentTime;
        }
    }

    public boolean loadCameraInfoYaml(String fileName) {
        Yaml yaml = new Yaml();
        File file = new File(Environment.getExternalStorageDirectory() + "/RosCameraInfo",  fileName);
        if(file.exists()) {
            try{
                //InputStream reader = CompressedImagePublisher.class.getResourceAsStream("/camera.yaml");
                InputStream reader = new FileInputStream(file);
                this.yamlCamera = yaml.loadAs(reader, YamlCamera.class);

                if(yamlCamera.getCameraName() == null || yamlCamera.getDistortionModel() == null) {
                    Log.i(TAG, "Fail to load camera.yaml.");
                    return false;
                }else {
                    Log.i(TAG, "Succeed to load camera.yaml.");
                    return true;
                }
            } catch (Exception e){
                e.printStackTrace();
                Log.e(TAG, "Fail to load yaml file!");
                return false;
            }
        } else {
            Log.e(TAG, "The camera.yaml isn't exist!");
            return false;
        }
    }

    public boolean saveCameraInfoYaml(sensor_msgs.CameraInfo cameraInfo, String fileName) {
        try{
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File file = makeDirAndFile(Environment.getExternalStorageDirectory() + "/RosCameraInfo", fileName);
                if (file != null) {
                    YamlCamera yamlCameraInfo = new YamlCamera();
                    yamlCameraInfo.setImageHeight(cameraInfo.getHeight());
                    yamlCameraInfo.setImageWidth(cameraInfo.getWidth());
                    yamlCameraInfo.setCameraName(cameraInfo.getHeader().getFrameId());
                    yamlCameraInfo.setDistortionModel(cameraInfo.getDistortionModel());

                    yamlCameraInfo.getCameraMatrix().setData(cameraInfo.getK());
                    yamlCameraInfo.getDistortionCoefficients().setData(cameraInfo.getD());
                    yamlCameraInfo.getRectificationMatrix().setData(cameraInfo.getR());
                    yamlCameraInfo.getProjectionMatrix().setData(cameraInfo.getP());

                    FileOutputStream writer = new FileOutputStream(file);
                    writer.write(yamlCameraInfo.toString().getBytes());
                    writer.close();
                    return true;
                } else {
                    Log.e(TAG, "Can't Create camera.yaml!");
                    return false;
                }
            }else {
                Log.e(TAG, "Can't read external storage!");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Can't save camera.yaml!");
            return false;
        }
    }

    //create dir and file
    private File makeDirAndFile(String filePath, String fileName) {
        File dir = null;
        try {
            dir = new File(filePath);
            if(!dir.exists()) {
                dir.mkdir();
            }
        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        File file = null;
        try{
            file = new File(filePath, fileName);
            if(!file.exists()) {
                file.createNewFile();
            }
        }catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return file;
    }
}