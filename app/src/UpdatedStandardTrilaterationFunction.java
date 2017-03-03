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
    double[] x;                                 //holds x coordinate values to be used for trilateration
    double[] y;                                 //holds y coordinate values to be used for trilateration
    double[][] UpperMatrixForX;                 //will be used to hold X's numerator matrix in cramer's rule
    double[][] UpperMatrixForY;                 //be used to hold Y's numerator matrix in cramer's rule
    double[][] LowerMatrix;                     //will be used to hold both X's and Y's denominator matrix in cramer's rule
    double UpperXDeterminant;                   //holds the numerator determinant value for X
    double X;                                   //holds the X coordinate value of the user
    double UpperYDeterminant;                   //holds the numerator determinant value for Y
    double Y;                                   //holds the Y coordinate value of the user
    double LowerDeterminant;                    //holds the denominator value for both X and Y

    public UpdatedStandardTrilaterationFunction(double positions[][], double distances[])
    {
        /*if(positions.length < 3) //checks to see if the rssi queue holds at least beacons.  3 distances and x/y coordinates of beacons are needed for trilateration function to work.
        {
            throw new IllegalArgumentException("Need at least three positions.");
        }*/

        if(positions.length != distances.length) //checks to see if for every position, an X/Y coordinate is given.  If not, the function will be given wrong information leading to errors in user placement
        {
            throw new IllegalArgumentException("The number of positions you provided, " + positions.length + ", does not match the number of distances, " + distances.length + ".");
        }

        for(int i = 0; i < distances.length; i++) //for loop to assign distances and x/y coordinate points to correct matricies that will be used to calculate matricies that are used in cramer's rule
        {
            distance[i] = distances[i];
            x[i] = positions[i][0];
            y[i] = positions[i][1];
        }

        //calculates numerator matrix for X to be used in cramer's rule
        UpperMatrixForX[0][0] =((Math.pow(distance[0], 2)-Math.pow(distance[1],2))-(Math.pow(x[0],2)-Math.pow(x[1],2))-(Math.pow(y[0],2)-Math.pow(y[1],2)));
        UpperMatrixForX[0][1] = (2*(y[1]-y[0]));
        UpperMatrixForX[1][0] = ((Math.pow(distance[0], 2)-Math.pow(distance[2],2))-(Math.pow(x[0],2)-Math.pow(x[2],2))-(Math.pow(y[0],2)-Math.pow(y[2],2)));
        UpperMatrixForX[1][1] = (2*(y[2]-y[0]));

        //calculates numerator value for X to be used to find X coordinate value
        UpperXDeterminant = (UpperMatrixForX[0][0]*UpperMatrixForX[1][1])-(UpperMatrixForX[0][1]*UpperMatrixForX[1][0]);

        //calculates numerator matrix for Y to be used in cramer's rule
        UpperMatrixForY[0][0] = (2*(x[1]-x[0]));
        UpperMatrixForY[0][1] = ((Math.pow(distance[0], 2)-Math.pow(distance[1],2))-(Math.pow(x[0],2)-Math.pow(x[1],2))-(Math.pow(y[0],2)-Math.pow(y[1],2)));
        UpperMatrixForY[1][0] = (2*(x[2]-x[0]));
        UpperMatrixForY[1][1] = ((Math.pow(distance[0], 2)-Math.pow(distance[2],2))-(Math.pow(x[0],2)-Math.pow(x[2],2))-(Math.pow(y[0],2)-Math.pow(y[2],2)));

        //calculates numerator value for Y to be used to find Y coordinate value
        UpperYDeterminant = (UpperMatrixForY[0][0]*UpperMatrixForY[1][1]) - (UpperMatrixForY[0][1]*UpperMatrixForY[1][0]);

        //calculates the denominator matrix to be used in cramer's rule
        LowerMatrix[0][0] = (2*(x[1]-x[0]));
        LowerMatrix[0][1] = (2*(y[1]-y[0]));
        LowerMatrix[1][0] = (2*(x[2]-x[0]));
        LowerMatrix[1][1] = (2*(y[2]-y[0]));

        //calculates teh denominator value to be used to find both X and Y coordinate value
        LowerDeterminant = (LowerDeterminant[0][0]*LowerMatrix[1][1])-(LowerMatrix[0][1]*LowerMatrix[1][0]);

        X = UpperXDeterminant/LowerDeterminant;         //finds X coordinate value
        Y = UpperYDeterminant/LowerDeterminant;         //finds Y coordinate value
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
