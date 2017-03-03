package lilium.beaconnavigation.Implementations;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

import lilium.beaconnavigation.AppConfig;
import lilium.beaconnavigation.Interfaces.TrilaterationFunction;

/**
 * Takes rssi from beacons and gets distance from user.
 * Using trilateration, we can get user position relative to beacons
 *
 * @author Alejandro
 *
 */
public class UpdatedStandardTrilaterationFunction implements TrilaterationFunction
{
    protected final double[] distance;          //holds distance values gained from rssi
    protected final double[][] positions;       //holds x,y coordinates of beacons
    double[] x;                                 //holds x coordinate value to be used for trilateration
    double[] y;                                 //holds y coordinate value to be used for trilateration
    double[][] UpperMatrixForX;                 //will be used to hold X's numerator value in cramer's rule
    double[][] UpperMatrixForY;                 //be used to hold Y's numerator value in cramer's rule
    double[][] LowerMatrix;                     //will be used to hold both X's and Y's denominator value in cramer's rule
    double UpperXDeterminant;
    double X;
    double UpperYDeterminant;
    double Y;
    double LowerDeterminant;

    public UpdatedStandardTrilaterationFunction(double positions[][], double distances[])
    {
        if(positions.length < 3)
        {
            throw new IllegalArgumentException("Need at least two positions.");
        }

        if(positions.length != distances.length)
        {
            throw new IllegalArgumentException("The number of positions you provided, " + positions.length + ", does not match the number of distances, " + distances.length + ".");
        }

        for(int i = 0; i < distances.length; i++)
        {
            distance[i] = distances[i];
            x[i] = positions[i][0];
            y[i] = positions[i][1];
        }

        UpperMatrixForX[0][0] =((Math.pow(distance[0], 2)-Math.pow(distance[1],2))-(Math.pow(x[0],2)-Math.pow(x[1],2))-(Math.pow(y[0],2)-Math.pow(y[1],2)));
        UpperMatrixForX[0][1] = (2*(y[1]-y[0]));
        UpperMatrixForX[1][0] = ((Math.pow(distance[0], 2)-Math.pow(distance[2],2))-(Math.pow(x[0],2)-Math.pow(x[2],2))-(Math.pow(y[0],2)-Math.pow(y[2],2)));
        UpperMatrixForX[1][1] = (2*(y[2]-y[0]));
        UpperXDeterminant = (UpperMatrixForX[0][0]*UpperMatrixForX[1][1])-(UpperMatrixForX[0][1]*UpperMatrixForX[1][0]);

        UpperMatrixForY[0][0] = (2*(x[1]-x[0]));
        UpperMatrixForY[0][1] = ((Math.pow(distance[0], 2)-Math.pow(distance[1],2))-(Math.pow(x[0],2)-Math.pow(x[1],2))-(Math.pow(y[0],2)-Math.pow(y[1],2)));
        UpperMatrixForY[1][0] = (2*(x[2]-x[0]));
        UpperMatrixForY[1][1] = ((Math.pow(distance[0], 2)-Math.pow(distance[2],2))-(Math.pow(x[0],2)-Math.pow(x[2],2))-(Math.pow(y[0],2)-Math.pow(y[2],2)));
        UpperYDeterminant = (UpperMatrixForY[0][0]*UpperMatrixForY[1][1]) - (UpperMatrixForY[0][1]*UpperMatrixForY[1][0]);

        LowerMatrix[0][0] = (2*(x[1]-x[0]));
        LowerMatrix[0][1] = (2*(y[1]-y[0]));
        LowerMatrix[1][0] = (2*(x[2]-x[0]));
        LowerMatrix[1][1] = (2*(y[2]-y[0]));
        LowerDeterminant = (LowerDeterminant[0][0]*LowerMatrix[1][1])-(LowerMatrix[0][1]*LowerMatrix[1][0]);

        X = UpperXDeterminant/LowerDeterminant;
        Y = UpperYDeterminant/LowerDeterminant;
        this.positions = positions;
    }

    public final double[] getDistances()
    {
        return distance;
    }

    public final double[][] getPositions()
    {
        return positions;
    }
}
