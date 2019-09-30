package org.ros.android.view.camera;

public class YamlCamera {
    private int image_width;
    private int image_height;
    private String camera_name;
    private String distortion_model;
    private BaseMatrix camera_matrix = new BaseMatrix();
    private BaseMatrix distortion_coefficients = new BaseMatrix();
    private BaseMatrix rectification_matrix = new BaseMatrix();
    private BaseMatrix projection_matrix = new BaseMatrix();

    public int getImageWidth() {
        return this.image_width;
    }

    public void setImageWidth(int width) {
        this.image_width = width;
    }

    public int getImageHeight() {
        return this.image_height;
    }

    public void setImageHeight(int height) {
        this.image_height = height;
    }

    public String getCameraName() {
        return this.camera_name;
    }

    public void setCameraName(String cameraName) {
        this.camera_name = cameraName;
    }

    public BaseMatrix getCameraMatrix() {
        return camera_matrix;
    }

    public void setCameraMatrix(BaseMatrix camera_matrix) {
        this.camera_matrix = camera_matrix;
    }

    public String getDistortionModel() {
        return this.distortion_model;
    }

    public void setDistortionModel(String model) {
        this.distortion_model = model;
    }

    public BaseMatrix getDistortionCoefficients() {
        return distortion_coefficients;
    }

    public void setDistortionCoefficients(BaseMatrix distortion_coefficients) {
        this.distortion_coefficients = distortion_coefficients;
    }

    public BaseMatrix getRectificationMatrix() {
        return rectification_matrix;
    }

    public void setRectificationMatrix(BaseMatrix rectification_matrix) {
        this.rectification_matrix = rectification_matrix;
    }

    public BaseMatrix getProjectionMatrix() {
        return projection_matrix;
    }

    public void setProjectionMatrix(BaseMatrix projection_matrix) {
        this.projection_matrix = projection_matrix;
    }

    private String setDataArray(double[] data) {
        int len = data.length;
        String msg = "  data: [";
        for(int i = 0; i < len - 1; i++) {
            msg += String.format("%.20f, ", data[i]);
        }
        msg += String.format("%.20f]\n", data[len - 1]);
        return msg;
    }

    @Override
    public String toString() {
        String yaml;
        yaml =  "image_width: " + this.image_width + "\n" +
                "image_height: " + this.image_height + "\n" +
                "camera_name: " + this.camera_name + "\n" +
                "camera_matrix:\n" +
                "  rows: 3\n" +
                "  cols: 3\n" +
                setDataArray(camera_matrix.getData()) +
                "distortion_model: " + this.distortion_model + "\n" +
                "distortion_coefficients:\n" +
                "  rows: 1\n" +
                "  cols: "+ this.distortion_coefficients.getData().length + "\n" +
                setDataArray(distortion_coefficients.getData()) +
                "rectification_matrix:\n" +
                "  rows: 3\n" +
                "  cols: 3\n" +
                setDataArray(rectification_matrix.getData()) +
                "projection_matrix:\n" +
                "  rows: 3\n" +
                "  cols: 4\n" +
                setDataArray(projection_matrix.getData());
        return yaml;
    }
}