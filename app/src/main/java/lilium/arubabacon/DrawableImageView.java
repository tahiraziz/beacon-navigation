package lilium.arubabacon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;

//https://github.com/davemorrissey/subsampling-scale-image-view
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import static lilium.arubabacon.MainActivity.beacons;

public class DrawableImageView extends SubsamplingScaleImageView {
    Bitmap b = BitmapFactory.decodeResource(getResources(), R.mipmap.beacon);
    Bitmap marker = BitmapFactory.decodeResource(getResources(), R.mipmap.marker);
    Paint p = new Paint();

    public DrawableImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for(int i = 0; i < beacons.size(); i++) {
            PointF offset = sourceToViewCoord(beacons.get(i).x, beacons.get(i).y);
            if (offset != null) {
                Matrix matrix = new Matrix();
                matrix.postTranslate(offset.x - b.getWidth() / 2, offset.y - b.getHeight() / 2);
                canvas.drawBitmap(b, matrix, p);
                canvas.drawText(String.valueOf(beacons.get(i).cummulativeRssi / beacons.get(i).numRssi),offset.x - b.getWidth() / 8,offset.y - b.getHeight() / 4, p);
                canvas.drawText(String.valueOf(Math.sqrt(Math.pow(10.0, (-61 - (beacons.get(i).cummulativeRssi / beacons.get(i).numRssi)) / (10.0)))),offset.x - b.getWidth() / 8,offset.y + b.getHeight() / 4, p);
            }
        }

        PointF offset = sourceToViewCoord(MainActivity.position.x, MainActivity.position.y);
        if (offset != null) {
            Matrix matrix = new Matrix();
            matrix.postTranslate(offset.x - marker.getWidth() / 2, offset.y - marker.getHeight() / 2);
            canvas.drawBitmap(marker, matrix, p);
        }
    }
}
