package lilium.beaconnavigation.Interfaces;


import android.graphics.Bitmap;

/**
 * Created by boylec on 1/29/17.
 */

public interface ImageProcessingService {
    int[][] DeduceWallPxPositions(Bitmap image);
}
