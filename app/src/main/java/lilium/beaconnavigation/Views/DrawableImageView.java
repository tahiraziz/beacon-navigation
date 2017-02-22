package lilium.beaconnavigation.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

//https://github.com/davemorrissey/subsampling-scale-image-view
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import java.util.ArrayList;
import java.util.List;

import lilium.beaconnavigation.Classes.Location;
import lilium.beaconnavigation.Interfaces.Beacon;
import lilium.beaconnavigation.MainActivity;
import lilium.beaconnavigation.R;

import static lilium.beaconnavigation.MainActivity.mapHeightSeekBar;
import static lilium.beaconnavigation.MainActivity.mapWidthSeekBar;

public class DrawableImageView extends SubsamplingScaleImageView {
    Bitmap b = BitmapFactory.decodeResource(getResources(), R.mipmap.beacon);
    Bitmap marker = BitmapFactory.decodeResource(getResources(), R.mipmap.marker);
    int[][] wallPixelPositions;
    Paint p = new Paint();
    List<Location> path;

    public DrawableImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(!MainActivity.loaded) return;

        //Walking nav stuff
        int height = this.getMeasuredHeight();
        int width = this.getMeasuredWidth();

        float map_width = mapWidthSeekBar.getProgress();
        float map_height = mapHeightSeekBar.getProgress();

        float myLocationX=(width*MainActivity.position.x/map_width);
        float myLocationY=(height*MainActivity.position.y/map_height);

        Paint p=new Paint();
        p.setColor(Color.BLUE);

        for(int x = 0; x < wallPixelPositions.length; x++)
        {
            for(int y = 0; y < wallPixelPositions[x].length; y++)
            {
                canvas.drawPoint(x,y,p);
            }
        }


        canvas.drawCircle(myLocationX,myLocationY,10,p);

        if(path!=null){
            Location prev=path.get(0);

            p.setAntiAlias(true);

            if (path != null && path.size() >= 2) {
                Path vPath = new Path();

                PointF vPrev = sourceToViewCoord(width* path.get(0).x/map_width, height*path.get(0).y/map_height);

                vPath.moveTo(vPrev.x, vPrev.y);
                for (int i = 1; i < path.size(); i++) {
                    PointF vPoint = sourceToViewCoord(path.get(i).x*width/map_width, path.get(i).y*height/map_height);

                    vPath.lineTo(vPoint.x,vPoint.y);
                    vPrev = vPoint;
                }

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(10);
                p.setColor(Color.argb(150,255,255,255));
                canvas.drawPath(vPath, p);
                p.setStrokeWidth(5);
                p.setColor(Color.argb(175, 51, 181, 229));
                canvas.drawPath(vPath, p);
            }
//            for(int i=1;i<path.size();i++){
//                Location cur=path.get(i);
//
//                PointF startLinePos = new PointF (width*prev.x/map_width,height*prev.y/map_height);
//                PointF endLinePos = new PointF (width*cur.x/map_width, height*cur.y/map_height);
//
//                startLinePos = sourceToViewCoord(startLinePos);
//                endLinePos = sourceToViewCoord(endLinePos);
//
//                canvas.drawLine(startLinePos.x,startLinePos.y,endLinePos.x,endLinePos.y,p);
//
//                prev=cur;
//            }

//            path=null;
        }

        //End walking nav stuff

        ArrayList<Beacon> beacons = MainActivity.beaconKeeper.clonePlaced();
        for(int i = 0; i < beacons.size(); i++) {
            PointF offset = sourceToViewCoord(beacons.get(i).getX(), beacons.get(i).getY());
            if (offset != null) {
                Matrix matrix = new Matrix();
                matrix.postTranslate(offset.x - b.getWidth() / 2, offset.y - b.getHeight() / 2);
                canvas.drawBitmap(b, matrix, p);
            }
        }

        PointF offset = sourceToViewCoord(MainActivity.position.x, MainActivity.position.y);

        if (offset != null && beacons.size() > 1) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(offset.x - marker.getWidth() / 2, offset.y - marker.getHeight() / 2);
            canvas.drawBitmap(marker, matrix, p);
        }
    }

    public void setImage(ImageSource image, int[][] wallPixelPositions)
    {
        super.setImage(image);
        this.wallPixelPositions = wallPixelPositions;
    }
    public void setPath(List<Location> path){
        this.path=path;
    }
}
