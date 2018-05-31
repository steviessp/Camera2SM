package com.example.android.camera2basic;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.apache.commons.math.ArgumentOutsideDomainException;
import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;

import static android.view.MotionEvent.ACTION_MASK;
import static com.example.android.camera2basic.Constants.SIGHTVIEW_HEIGHT;
import static com.example.android.camera2basic.Constants.SIGHTVIEW_WIDTH;
import static java.lang.Math.PI;

public class SpectrumActivity extends AppCompatActivity implements View.OnTouchListener {

    private TextView colorRedTextView;
    private TextView colorGreenTextView;
    private TextView colorBlueTextView;
    private TextView hueView;
    private TextView saturationView;
    private TextView valueView;
    private TextView labView;
    private TextView colorTextView;
    private TextView CCTTextView;
    private ImageView histogramView;
    private Bitmap croppedBitmap;

    double f144B;
    int DataY;
    double f145G;
    double L1;
    double L2;
    double Ri;
    double f146X;
    double f147Y;
    double f148Z;
    double a1;
    double a2;
    double b1;
    double b2;
    //    TextView bluer;


    int chave;
    String coName;
    //    TextView colorName;
//    TextView deltae;
    GraphView graph;
    int graphRaw;
    //    TextView green;
//    TextView hex;
//    TextView hhue;
//    TextView lab2;
//    String light;
    int porcentagem;
    //    TextView red;
    byte[] segura;
    LineGraphSeries<DataPoint> series;
    int seris;
    double var_B;
    double var_G;
    double var_R;
    double var_X;
    double var_Y;
    double var_Z;
    ArrayList<Double> xP = new ArrayList();
    ArrayList<Double> yP = new ArrayList();
    ArrayList<Double> yPn = new ArrayList();
    int cropX, cropY;
    Bitmap cameraBitmap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spectrum);

        if (OpenCVLoader.initDebug()) {
            Log.i("OPEN_CV", "OpenCV initialize success");
        } else {
            Log.i("OPEN_CV", "OpenCV initialize failed");
        }

        colorRedTextView = (TextView) findViewById(R.id.colorRed);
        colorGreenTextView = (TextView) findViewById(R.id.colorGreen);
        colorBlueTextView = (TextView) findViewById(R.id.colorBlue);
        colorTextView = (TextView) findViewById(R.id.color);

        hueView = (TextView) findViewById(R.id.hue);
        saturationView = (TextView) findViewById(R.id.saturation);
        valueView = (TextView) findViewById(R.id.value);
        labView = (TextView) findViewById(R.id.CieLab);
        histogramView = (ImageView) findViewById(R.id.histogram);
        CCTTextView = (TextView) findViewById(R.id.CCT);


        BMPSingleton bmpSingleton = BMPSingleton.getInstance();
        cameraBitmap = bmpSingleton.getBitmap();
        cropX = (cameraBitmap.getWidth() / 2) - (SIGHTVIEW_WIDTH / 2);
        cropY = (cameraBitmap.getHeight() / 2) - (SIGHTVIEW_HEIGHT / 2);

        if (cameraBitmap == null || cropX == 0 || cropY == 0) {
            return;
        }

        graph = (GraphView) findViewById(R.id.graph);
        graph.setOnTouchListener(this);
        analizeSpectrum();



        histogramView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                drawHistogram(croppedBitmap);
            }
        });



    }


    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

//        switch ((view.getId())) {
//            case R.id.graph: {
//                if (croppedBitmap != null)
//                    drawHistogram(croppedBitmap);
//            }
//        }
        return false;
    }

    void analizeSpectrum() {
        croppedBitmap = Bitmap.createBitmap(cameraBitmap, cropX, cropY, SIGHTVIEW_WIDTH, SIGHTVIEW_HEIGHT);


        int color = getDominantColor(croppedBitmap);
        float alpha = Color.alpha(color);
        int redValue = Color.red(color);
        int greenValue = Color.green(color);
        int blueValue = Color.blue(color);

        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        double[] lab = new double[3];
        ColorUtils.colorToLAB(color, lab);

        colorRedTextView.setText(String.valueOf(redValue));
        colorGreenTextView.setText(String.valueOf(greenValue));
        colorBlueTextView.setText(String.valueOf(blueValue));
        colorTextView.setBackgroundColor(Color.rgb(redValue, greenValue, blueValue));

        DecimalFormat dm = new DecimalFormat("#,##0");

        hueView.setText(String.format("%1$3.2f˚", hsv[0]));
        saturationView.setText(String.format("%1$1.3f", hsv[1]));
        valueView.setText(String.format("%1$1.3f", hsv[2]));
        labView.setText(String.format("%1$1.2f, %2$+3.2f, %3$+3.2f", lab[0], lab[1], lab[2]));

        Spectrum spctr = new Spectrum();
        int spctrValue = spctr.GetCCT(redValue, greenValue, blueValue);

        double calcLuminance = spctr.GetLuminance(redValue, greenValue, blueValue);
//        float libLuminance = Color.luminance(color);

        CCTTextView.setText(String.valueOf(spctrValue));
//        histogramView.setImageBitmap(croppedBitmap);
        onPictureTaken(cameraBitmap);
//        drawHistogram(croppedBitmap);
    }

    public static int getDominantColor(Bitmap bitmap) {
        if (null == bitmap) return Color.TRANSPARENT;

        int redBucket = 0;
        int greenBucket = 0;
        int blueBucket = 0;
        int alphaBucket = 0;

        boolean hasAlpha = bitmap.hasAlpha();
        int pixelCount = bitmap.getWidth() * bitmap.getHeight();
        int[] pixels = new int[pixelCount];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int y = 0, h = bitmap.getHeight(); y < h; y++) {
            for (int x = 0, w = bitmap.getWidth(); x < w; x++) {
                int color = pixels[x + y * w]; // x + y * width
                redBucket += (color >> 16) & 0xFF; // Color.red
                greenBucket += (color >> 8) & 0xFF; // Color.greed
                blueBucket += (color & 0xFF); // Color.blue
                if (hasAlpha) alphaBucket += (color >>> 24); // Color.alpha
            }
        }

        return Color.rgb(
                redBucket / pixelCount,
                greenBucket / pixelCount,
                blueBucket / pixelCount);

//        return Color.argb(
//                        (hasAlpha) ? (alphaBucket / pixelCount) : 255,
//                        redBucket / pixelCount,
//                        greenBucket / pixelCount,
//                        blueBucket / pixelCount);

    }

    public void onPictureTaken(Bitmap bitmap) {
        this.porcentagem = 20;
        if (bitmap != null) {
            double var_R1;
            int measureX = cropX + 25;  //dpToPx(5);
            int measureY = cropY + 25; // dpToPx(5);
            int index = bitmap.getPixel(measureX, measureY);

            int R1 = (index >> 16) & ACTION_MASK;  //MotionEventCompat.ACTION_MASK;
            int G1 = (index >> 8) & ACTION_MASK; // MotionEventCompat.ACTION_MASK;
            int B1 = index & ACTION_MASK; // MotionEventCompat.ACTION_MASK;
            this.Ri = (double) R1;
            this.f145G = (double) G1;
            this.f144B = (double) B1;
            DecimalFormat decimalFormat = new DecimalFormat("#,##0");
            String Rkk = decimalFormat.format(this.Ri);
            String Gkk = decimalFormat.format(this.f145G);
            String Bkk = decimalFormat.format(this.f144B);
            int Rkk2 = Integer.parseInt(String.valueOf(Rkk));
            int Gkk2 = Integer.parseInt(String.valueOf(Gkk));
            int Bkk2 = Integer.parseInt(String.valueOf(Bkk));
//            this.red.setBackgroundColor(index);
            float[] hsv = new float[3];
            Color.RGBToHSV(Rkk2, Gkk2, Bkk2, hsv);
            float huekk = hsv[0];
            float sat = hsv[1];
            float val = hsv[2];
//            this.green.setText(String.valueOf("R: " + Rkk2 + " G: " + Gkk2 + " B: " + Bkk2));
            String finHEX = "#" + Integer.toHexString(Color.rgb(Rkk2, Gkk2, Bkk2)).substring(2, 8);
//            this.hex.setText(String.valueOf("HEX: " + finHEX));
            this.var_R = this.Ri / 255.0d;
            this.var_G = this.f145G / 255.0d;
            this.var_B = this.f144B / 255.0d;
            if (this.var_R > 0.04045d) {
                var_R1 = (this.var_R + 0.055d) / 1.055d;
                this.var_R = Math.pow(var_R1, 2.4d);
            } else {
                this.var_R /= 12.92d;
            }
            if (this.var_G > 0.04045d) {
                var_R1 = (this.var_G + 0.055d) / 1.055d;
                this.var_G = Math.pow(var_R1, 2.4d);
            } else {
                this.var_G /= 12.92d;
            }
            if (this.var_B > 0.04045d) {
                var_R1 = (this.var_B + 0.055d) / 1.055d;
                this.var_B = Math.pow(var_R1, 2.4d);
            } else {
                this.var_B /= 12.92d;
            }
            this.var_R *= 100.0d;
            this.var_G *= 100.0d;
            this.var_B *= 100.0d;
            this.f146X = ((this.var_R * 0.4124d) + (this.var_G * 0.3576d)) + (this.var_B * 0.1805d);
            this.f147Y = ((this.var_R * 0.2126d) + (this.var_G * 0.7152d)) + (this.var_B * 0.0722d);
            this.f148Z = ((this.var_R * 0.0193d) + (this.var_G * 0.1192d)) + (this.var_B * 0.9505d);
            decimalFormat = new DecimalFormat("#,##0.000");
            String Xiz = decimalFormat.format(this.f146X);
            String Yiz = decimalFormat.format(this.f147Y);
            String Ziz = decimalFormat.format(this.f148Z);
            this.porcentagem = 30;
            this.var_X = this.f146X / 95.047d;
            this.var_Y = this.f147Y / 100.0d;
            this.var_Z = this.f148Z / 108.883d;
            if (this.var_X > 0.008856d) {
                this.var_X = Math.pow(this.var_X, 0.33333d);
            } else {
                this.var_X = (7.787d * this.var_X) + 0.0d;
            }
            if (this.var_Y > 0.008856d) {
                this.var_Y = Math.pow(this.var_Y, 0.33333d);
            } else {
                this.var_Y = (7.787d * this.var_Y) + 0.0d;
            }
            if (this.var_Z > 0.008856d) {
                this.var_Z = Math.pow(this.var_Z, 0.33333d);
            } else {
                this.var_Z = (7.787d * this.var_Z) + 0.0d;
            }
            double CIEL = Math.max(0.0d, (116.0d * this.var_Y) - 16.0d);
            double CIEa = 500.0d * (this.var_X - this.var_Y);
            double CIEb = 200.0d * (this.var_Y - this.var_Z);
            decimalFormat = new DecimalFormat("#,##0.0");
            String eli = decimalFormat.format(CIEL);
            String aa = decimalFormat.format(CIEa);
            String bb = decimalFormat.format(CIEb);
//            this.lab2.setText(String.valueOf("CIE L: " + eli + " a*: " + aa + " b*: " + bb));
            double cr = Math.sqrt(Math.pow(CIEa, 2.0d) + Math.pow(CIEb, 2.0d));
            String crom = new DecimalFormat("#,##0.00").format(cr);
            this.L1 = CIEL;
            this.a1 = CIEa;
            this.b1 = CIEb;
            double Var_H = Math.atan2(CIEb, CIEa);
            if (Var_H > 0.0d) {
                Var_H = (Var_H / PI) * 180.0d;
            } else {
                Var_H = 360.0d - ((Math.abs(Var_H) / PI) * 180.0d);
            }
            if (Var_H < 0.0d) {
                Var_H += 360.0d;
            } else if (Var_H >= 360.0d) {
                Var_H -= 360.0d;
            }
            this.porcentagem = 40;
            String hueei = String.format("%.1f", new Object[]{Float.valueOf(huekk)});
            if (huekk <= 6.0f) {
                this.coName = "Red";
            } else if (huekk > 350.0f) {
                this.coName = "Red";
            } else if (huekk > 6.0f && huekk <= 10.0f) {
                this.coName = "Red-Orange";
            } else if (huekk >= 340.0f && huekk <= 350.0f) {
                this.coName = "Pink-red";
            } else if (huekk >= 320.0f && huekk < 340.0f) {
                this.coName = "Magenta-Pink";
            } else if (huekk >= 290.0f && huekk < 320.0f) {
                this.coName = "Magenta";
            } else if (huekk >= 270.0f && huekk < 290.0f) {
                this.coName = "Blue-Magenta";
            } else if (huekk > 200.0f && huekk < 270.0f) {
                this.coName = "Blue";
            } else if (huekk > 177.0f && huekk <= 200.0f) {
                this.coName = "Cyan-Blue";
            } else if (huekk >= 155.0f && huekk <= 177.0f) {
                this.coName = "Cyan-Green";
            } else if (huekk >= 73.0f && huekk < 155.0f) {
                this.coName = "Green";
            } else if (huekk >= 55.0f && huekk < 73.0f) {
                this.coName = "Yellow-Green";
            } else if (huekk >= 47.0f && huekk < 55.0f) {
                this.coName = "Yellow";
            } else if (huekk >= 17.0f && huekk < 47.0f) {
                this.coName = "Orange-Brown";
            } else if (huekk < 10.0f || huekk >= 17.0f) {
                this.coName = "Color name";
            } else {
                this.coName = "Red-Orange-Brown";
            }
            if (cr <= 5.0d && CIEL > 40.0d && CIEL < 88.0d) {
                this.coName = "Light-Gray";
//                this.light = " ";
            } else if (cr <= 5.0d && CIEL >= 17.0d && CIEL <= 40.0d) {
                this.coName = "Dark-Gray";
//                this.light = " ";
            } else if (cr >= 4.0d && CIEL > 58.0d && CIEL <= 100.0d) {
//                this.light = "Light-";
            } else if (cr >= 6.0d && CIEL > 8.0d && CIEL <= 47.0d) {
//                this.light = "Dark-";
            } else if (cr <= 3.0d && CIEL > 88.0d) {
                this.coName = "White";
//                this.light = " ";
            } else if (cr <= 4.0d && CIEL >= 0.0d && CIEL <= 23.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (cr <= 5.0d && CIEL >= 0.0d && CIEL <= 18.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (cr <= 6.0d && CIEL >= 0.0d && CIEL < 17.0d) {
                this.coName = "Black";
//                this.light = " ";
            } else if (CIEL == 0.0d) {
//                this.light = "Very Dark- ";
            } else {
//                this.light = " ";
            }
//            String NomeDaCor = this.light + this.coName;
//            this.colorName.setText(String.valueOf(NomeDaCor));
            decimalFormat = new DecimalFormat("#,##0.0");
//            this.hhue.setText(String.valueOf("HUE: " + hueei + "º " + "Chroma: " + crom));
//            this.deltae.setText(String.valueOf("ΔE*: " + new DecimalFormat("#,##0.00").format(Math.sqrt((Math.pow(Math.max(this.L1, this.L2) - Math.min(this.L1, this.L2), 2.0d) + Math.pow(Math.max(this.a1, this.a2) - Math.min(this.a1, this.a2), 2.0d)) + Math.pow(Math.max(this.b1, this.b2) - Math.min(this.b1, this.b2), 2.0d)))));
//                Person person = new Person(NomeDaCor, eli, aa, bb, crom, hueei, String.valueOf(Rkk2), String.valueOf(Gkk2), String.valueOf(Bkk2), finHEX);
//                this.dbhelper = new DatabaseHelper(this.getApplicationContext());
//                this.dbhelper.addPersonData(person);
            this.porcentagem = 50;
            this.xP = new ArrayList();
            this.yP = new ArrayList();
//            r77 = new double[5];

            try {
                PolynomialSplineFunction splines = new SplineInterpolator().interpolate(new double[]{450.0d, 470.0d, 525.0d, 665.0d, 715.0d}, new double[]{0.0d, (double) Bkk2, (double) Gkk2, (double) Rkk2, 0.0d});
                for (int i = 450; i < 715; i++) {
//                    double interpolationX = (double) i;
                    this.xP.add(Double.valueOf((double) i));
                    this.yP.add(Double.valueOf(splines.value((double) i)));
                }
                for (int norm = 0; norm < this.yP.size(); norm++) {
                    double eMax = ((Double) Collections.max(this.yP)).doubleValue();
                    double eMin = ((Double) Collections.min(this.yP)).doubleValue();
                    this.yPn.add(Double.valueOf((((Double) this.yP.get(norm)).doubleValue() - eMin) / (eMax - eMin)));
                }
            } catch (ArgumentOutsideDomainException aode) {
                aode.printStackTrace();
            }
            NumberFormat nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(0);
            this.graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter(nf, nf));
            this.graph.getGridLabelRenderer().setLabelFormatter(new C03511());
            this.graph.getGridLabelRenderer().setHorizontalAxisTitle("nm");
            this.graph.getGridLabelRenderer().setNumVerticalLabels(3);
            this.series = new LineGraphSeries(this.generateData());
            this.yPn.clear();
            this.series.setThickness(2);
            this.series.setColor(Color.rgb(Rkk2, Gkk2, Bkk2));
            if (this.seris == 3) {
                this.graph.removeAllSeries();
                this.seris = 0;
            }
            this.graph.addSeries(this.series);
            this.seris++;
//            this.graph.getViewport().setScalable(true);
//            this.graph.getViewport().setXAxisBoundsStatus(Viewport.AxisBoundsStatus.AUTO_ADJUSTED);
//            this.graph.getViewport().setYAxisBoundsManual(true);
//            this.graph.getViewport().setMaxY((double) this.graphRaw);
//            this.graph.getViewport().setMinY(0.0d);
            this.L2 = 0.0d;
            this.a2 = 0.0d;
            this.b2 = 0.0d;
            this.porcentagem = 75;
            this.porcentagem = 100;


        }

        this.L2 = this.L1;
        this.a2 = this.a1;
        this.b2 = this.b1;
        this.L1 = 0.0d;
        this.a1 = 0.0d;
        this.b1 = 0.0d;

        this.segura = null;

        /////




        //////
    }

    public void drawHistogram(Bitmap bitmap) {
        try {
            Mat rgba = new Mat();
            Utils.bitmapToMat(bitmap, rgba);

            org.opencv.core.Size rgbaSize = new org.opencv.core.Size(histogramView.getMeasuredWidth(), histogramView.getMeasuredHeight());
            int histSize = 256;
            MatOfInt histogramSize = new MatOfInt(histSize);

            int histogramHeight = histogramView.getMeasuredHeight();
            int binWidth = Math.round(histogramView.getMeasuredWidth() / histSize);
            MatOfFloat histogramRange = new MatOfFloat(0f, 256f);

            Scalar[] colorsRgb = new Scalar[]{new Scalar(200, 0, 0, 255), new Scalar(0, 200, 0, 255), new Scalar(0, 0, 200, 255)};
            MatOfInt[] channels = new MatOfInt[]{new MatOfInt(0), new MatOfInt(1), new MatOfInt(2)};

            Mat[] histograms = new Mat[]{new Mat(), new Mat(), new Mat()};


            Mat histMatBitmap = new Mat(new org.opencv.core.Size(histogramView.getMeasuredWidth(), histogramView.getMeasuredHeight()), rgba.type());

            for (int i = 0; i < channels.length; i++) {
                Imgproc.calcHist(Collections.singletonList(rgba), channels[i], new Mat(), histograms[i], histogramSize, histogramRange);
                Core.normalize(histograms[i], histograms[i], 0, histogramHeight, Core.NORM_MINMAX);  //NORM_INF);
                for (int j = 0; j < histSize; j++) {
                    org.opencv.core.Point p1 = new org.opencv.core.Point(binWidth * (j - 1), histogramHeight - Math.round(histograms[i].get(j - 1, 0)[0]));
                    org.opencv.core.Point p2 = new org.opencv.core.Point(binWidth * j, histogramHeight - Math.round(histograms[i].get(j, 0)[0]));
                    Imgproc.line(histMatBitmap, p1, p2, colorsRgb[i], 3, 8, 0);
                }
            }

            Bitmap histBitmap = Bitmap.createBitmap(histMatBitmap.cols(), histMatBitmap.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(histMatBitmap, histBitmap);

            BitmapHelper.showBitmap(SpectrumActivity.this, histBitmap, histogramView);
            rgba.release();
            for (int g = 0; g < histograms.length; g++) {
                histograms[g].release();
            }

            histMatBitmap.release();
            histBitmap.recycle();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class C03511 extends DefaultLabelFormatter {
        C03511() {
        }

        public String formatLabel(double value, boolean isValueX) {
            NumberFormat nf;
            if (isValueX) {
                nf = NumberFormat.getInstance();
                nf.setMaximumFractionDigits(0);
                nf.setMaximumIntegerDigits(2);
                return super.formatLabel(value, isValueX);
            }
            nf = NumberFormat.getInstance();
            nf.setMaximumFractionDigits(0);
            nf.setMaximumIntegerDigits(2);
            return super.formatLabel(value, isValueX);
        }
    }

    private DataPoint[] generateData() {
        this.xP.size();
        this.yP.size();
        DataPoint[] values;
        int i;
        if (this.DataY == 1) {
            values = new DataPoint[this.xP.size()];
            for (i = 0; i < this.xP.size(); i++) {
                values[i] = new DataPoint(((Double) this.xP.get(i)).doubleValue(), ((Double) this.yPn.get(i)).doubleValue());
            }
            return values;
        }
        values = new DataPoint[this.xP.size()];
        for (i = 0; i < this.xP.size(); i++) {
            values[i] = new DataPoint(((Double) this.xP.get(i)).doubleValue(), ((Double) this.yPn.get(i)).doubleValue());
        }
        return values;
    }

}
