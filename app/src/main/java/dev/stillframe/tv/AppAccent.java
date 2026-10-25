package dev.stillframe.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import java.util.Locale;

/** Small, startup-only icon sample for brands outside the curated colour map. */
final class AppAccent {
    static String sample(Drawable icon){
        Bitmap bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);
        android.graphics.Rect previous=new android.graphics.Rect(icon.getBounds());
        try{
            icon.setBounds(0,0,24,24);icon.draw(new Canvas(bitmap));
            float[] weights=new float[12];float[][] sums=new float[12][3];float[] hsv=new float[3];
            for(int y=0;y<24;y++)for(int x=0;x<24;x++){
                int pixel=bitmap.getPixel(x,y);if(Color.alpha(pixel)<180)continue;
                Color.colorToHSV(pixel,hsv);if(hsv[1]<.35f||hsv[2]<.25f)continue;
                int bucket=Math.min(11,(int)(hsv[0]/30));float weight=hsv[1]*hsv[2];
                weights[bucket]+=weight;sums[bucket][0]+=Color.red(pixel)*weight;
                sums[bucket][1]+=Color.green(pixel)*weight;sums[bucket][2]+=Color.blue(pixel)*weight;
            }
            int best=0;for(int i=1;i<12;i++)if(weights[i]>weights[best])best=i;
            if(weights[best]<2)return "210,225,240";
            int rgb=Color.rgb(Math.round(sums[best][0]/weights[best]),Math.round(sums[best][1]/weights[best]),Math.round(sums[best][2]/weights[best]));
            Color.colorToHSV(rgb,hsv);hsv[1]=Math.max(.55f,hsv[1]);hsv[2]=Math.max(.8f,hsv[2]);rgb=Color.HSVToColor(hsv);
            return String.format(Locale.ROOT,"%d,%d,%d",Color.red(rgb),Color.green(rgb),Color.blue(rgb));
        }finally{icon.setBounds(previous);bitmap.recycle();}
    }
}
