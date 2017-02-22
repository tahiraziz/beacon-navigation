package lilium.beaconnavigation.Services;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

import lilium.beaconnavigation.Interfaces.ImageProcessingService;

/**
 * Created by boylec on 2/21/17.
 */

public class BasicImageProcessingService implements ImageProcessingService {

    private final static float _wallCutoff = (float)255/2;

    public int[][] DeduceWallPxPositions(Bitmap image)
    {
        ArrayList<int[]> wallPositions = new ArrayList<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                // Get the color of a pixel within myBitmap.
                int px = image.getPixel(x, y);
                int[] pxColor = getRgb(px,image.hasAlpha());

                if(isWall(pxColor))
                {
                       wallPositions.add(new int[]{x,y});
                }
            }
        }

        return (int[][])wallPositions.toArray();
    }

    private static boolean isWall(int[] pxColor)
    {
        float avgColor = getAverage(new int[]{pxColor[0],pxColor[1],pxColor[2]});
        return avgColor >= _wallCutoff;
    }


    private static float getAverage(int[] variables)
    {
        float total = 0;
        int size = variables.length;
        for(int x = 0; x < size; x++)
        {
            total += variables[x];
        }

        return total/size;
    }

    private static int[] getRgb(int px, boolean hasAlpha) {
        if(hasAlpha)
        {
            return new int[]{
                    (px >> 16) & 0xff, //red
                    (px >> 8) & 0xff, //green
                    (px) & 0xff,  //blue
                    (px >> 24) & 0xff, //alpha

            };
        }
        else{
            return new int[]{
                    (px >> 16) & 0xff, //red
                    (px >> 8) & 0xff, //green
                    (px) & 0xff  //blue
            };
        }
    }
}
