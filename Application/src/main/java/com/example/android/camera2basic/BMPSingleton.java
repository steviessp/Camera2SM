package com.example.android.camera2basic;

import android.graphics.Bitmap;

public class BMPSingleton {
    private static BMPSingleton ourInstance;// = new BMPSingleton();

    public static BMPSingleton getInstance() {
        if (ourInstance == null)
            ourInstance = new BMPSingleton();
        return ourInstance;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    private Bitmap bitmap;

    public int getCropX() {
        return CropX;
    }

    public void setCropX(int cropX) {
        CropX = cropX;
    }

    public int getCropY() {
        return CropY;
    }

    public void setCropY(int cropY) {
        CropY = cropY;
    }

    private int CropX;
    private int CropY;

    private BMPSingleton() { }
}
