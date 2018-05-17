package com.example.android.camera2basic;

import android.graphics.Color;
import android.support.v4.graphics.ColorUtils;

public class Spectrum {

    double X, Y, Z;
    double x, y;
    double luminance;

    private Color _color;

    public double getX() {
        return X;
    }

    public double getY() {
        return Y;
    }

    public double getZ() {
        return Z;
    }

    public Spectrum () {

    }

    public Spectrum(Color inColor) {
        _color = inColor;
        double[] XYZ = new double[3];
        ColorUtils.colorToXYZ(inColor.hashCode(), XYZ);
        X = XYZ[0];
        Y = XYZ[1];
        Z = XYZ[2];
    }

    public Spectrum(int R, int G, int B) {

        double[] XYZ = new double[3];
        ColorUtils.RGBToXYZ(R, G, B, XYZ);
        X = XYZ[0];
        Y = XYZ[1];
        Z = XYZ[2];
    }

    public double[] GetChromacityCoordinates(){
        x = X / (X+Y+Z);
        y = Y / (X+Y+Z);
        return new double[]{x,y};
    }

    public int GetCCT(){
        if (x==0 || y == 0)
        return 0;

        double n = (x - 0.3320)/(0.1858 - y);
        double dCCT = (449 * Math.pow(n, 3)) + (3525 * Math.pow(n, 2)) + (6823.3 * n) + 5520.33;
        return (int)dCCT;
    }

    public int GetCCT(int R, int G, int B){

        double[] XYZ = new double[3];
        ColorUtils.RGBToXYZ(R, G, B, XYZ);
        X = XYZ[0];
        Y = XYZ[1];
        Z = XYZ[2];

        x = X / (X+Y+Z);
        y = Y / (X+Y+Z);

        double n = (x - 0.3320)/(0.1858 - y);
        double dCCT = (449 * Math.pow(n, 3)) + (3525 * Math.pow(n, 2)) + (6823.3 * n) + 5520.33;
        return (int)dCCT;
    }

    public double GetLuminance(int R, int G, int B){
        luminance = ((0.2126 * R) + (0.7152 * G) + (0.0722 * B));
        return luminance;
    }

}
