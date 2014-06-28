import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class volume {
    int width, height, depth;

    RenderMode renderMode;

    long drawTime = 0;
    long totalSamples = 0;

    double totalSamplesAlpha =0;
    double dataMax=0;
    double dataMaxVolumetric=0;
    double dataMaxReset = 0;

    double surfaceArea=0;

    public smartVolume volume;

    public double XYProjection[][];

    public double ZBuffer[][];

    public double RBuffer[][];
    public double GBuffer[][];
    public double BBuffer[][];

    //public long ZBufferTime[][];
    public long dataPoints = 0;

    public int depthLeanX, depthLeanY;

    double camRoll;
    double camYaw;
    double camPitch;
    double camScale;
    double camCenterX;
    double camCenterY;
    double camCenterZ;

    double savedPitch;
    double savedYaw;
    double savedRoll;

    ifsPt centroid;
    ifsPt highPt;
    ifsPt highPtVolumetric;

    boolean antiAliasing;
    boolean usePerspective;

    public volume(int w, int h, int d){
        width = w;
        height = h;
        depth = d;
        depthLeanX = 0;
        depthLeanY = 0;
        renderMode = RenderMode.PROJECT_ONLY;
        antiAliasing = true;
        XYProjection = new double[width][height];
        ZBuffer = new double[width][height];
        RBuffer = new double[width][height];
        GBuffer = new double[width][height];
        BBuffer = new double[width][height];
        camPitch=0;
        camRoll=0;
        camYaw=0;
        usePerspective=true;

        camScale=2.0;

        camCenterX=512.0;
        camCenterY=512.0;
        camCenterZ=512.0;

        volume = new smartVolume(width);
        centroid = new ifsPt(0,0,0);
    }

    public void reset(){
        drawTime=System.currentTimeMillis();
        totalSamples=1;
        totalSamplesAlpha =1;
        centroid = new ifsPt(0,0,0);
        dataMax=dataMaxReset;
        dataMaxVolumetric=dataMaxReset;

        if(renderMode == renderMode.VOLUMETRIC){
            volume.reset();
        }
    }

    public void clear(){
        reset();
        clearProj(0);
    }

    public void clear(double a){
        reset();
        clearProj(a);
    }

    public void clearProj(double a){
        dataPoints*=a;
        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                XYProjection[x][y]*=a;
                ZBuffer[x][y]*=a;
                RBuffer[x][y]*=a;
                GBuffer[x][y]*=a;
                BBuffer[x][y]*=a;
            }
        }
    }

    public boolean volumeContains(ifsPt pt){
        return (pt.x>1 && pt.y>1 && pt.z>1 && pt.x<width-1 && pt.y<height-1 && pt.z<depth-1);
    }

    public ifsPt getCameraDistortedPt(ifsPt _pt){
        ifsPt pt = _pt
                .subtract(new ifsPt(camCenterX, camCenterY, camCenterZ))
                .getRotatedPt(camPitch / 180.0 * Math.PI, camYaw / 180.0 * Math.PI, camRoll / 180.0 * Math.PI)
                .scale(camScale)
                .add(new ifsPt(camCenterX, camCenterY, camCenterZ));

        //pt.x += Math.random()*Math.random()*(pt.z-depth/2)/(depth/2)*250;
        //pt.y += Math.random()*Math.random()*(pt.z-depth/2)/(depth/2)*250;

        double vx = 512.0; //vanishing pt onscreen
        double vy = 512.0;
       // pt.z = Math.sqrt(pt.z)*16;

        if(usePerspective){
            pt.x = (pt.x-vx)/Math.sqrt(1024-pt.z)*16.0+vx;
            pt.y = (pt.y-vy)/Math.sqrt(1024-pt.z)*16.0+vy;
        }

        pt.z /= 8.0;
        pt.z = Math.min(pt.z, 1020);
        pt.z = Math.max(pt.z, 4);

        return pt;
    }

    public boolean putPixel(ifsPt _pt, double alpha){

        boolean maxMode = true;

        ifsPt pt = getCameraDistortedPt(_pt);

        centroid.x+=pt.x*alpha;
        centroid.y+=pt.y*alpha;
        centroid.z+=pt.z*alpha;

        dataPoints++;

        if(volumeContains(pt)){
            if(renderMode==renderMode.VOLUMETRIC){
                if(antiAliasing){
                    double xDec = pt.x - (int)pt.x;
                    double yDec = pt.y - (int)pt.y;
                    double zDec = pt.z - (int)pt.z;

                    volume.putData((int) pt.x, (int) pt.y, (int) pt.z, alpha * (1 - xDec) * (1 - yDec) * (1 - zDec));
                    volume.putData((int) pt.x + 1, (int) pt.y, (int) pt.z, alpha * xDec * (1 - yDec) * (1 - zDec));
                    volume.putData((int) pt.x, (int) pt.y + 1, (int) pt.z, alpha * (1 - xDec) * yDec * (1 - zDec));
                    volume.putData((int) pt.x + 1, (int) pt.y + 1, (int) pt.z, alpha * xDec * yDec * (1 - zDec));

                    volume.putData((int) pt.x, (int) pt.y, (int) pt.z + 1, alpha * (1 - xDec) * (1 - yDec) * zDec);
                    volume.putData((int) pt.x + 1, (int) pt.y, (int) pt.z + 1, alpha * xDec * (1 - yDec) * zDec);
                    volume.putData((int) pt.x, (int) pt.y + 1, (int) pt.z + 1, alpha * (1 - xDec) * yDec * zDec);
                    volume.putData((int) pt.x + 1, (int) pt.y + 1, (int) pt.z + 1, alpha * xDec * yDec * zDec);

                }else{
                    volume.putData((int) pt.x, (int) pt.y, (int) pt.z + 1, alpha);
                }

                if(volume.getData((int)pt.x, (int)pt.y, (int)pt.y)>dataMaxVolumetric){
                    dataMaxVolumetric= volume.getData((int)pt.x, (int)pt.y, (int)pt.y);//volume[(int)pt.x][(int)pt.y][(int)pt.z];
                    highPtVolumetric = new ifsPt(pt);
                }
            }

            totalSamples++;
            totalSamplesAlpha +=alpha;

            XYProjection[(int)pt.x][(int)pt.y]+=alpha;

            if(XYProjection[(int)pt.x][(int)pt.y]>dataMax){
                dataMax= XYProjection[(int)pt.x][(int)pt.y];
                highPt = new ifsPt(pt);
            }

            if(maxMode){
                double xDec = pt.x - (int)pt.x;
                double yDec = pt.y - (int)pt.y;

                double rs=1, gs=1/2, bs=3;

                boolean res=false;

                if(pt.z * (1 - xDec) * (1 - yDec) > ZBuffer[(int) pt.x][(int) pt.y]){
                    res=true;
                    ZBuffer[(int)pt.x][(int)pt.y] = pt.z * (1 - xDec) * (1 - yDec);
                    //RBuffer[(int)pt.x][(int)pt.y] = pt.z * rs * (1 - xDec) * (1 - yDec);
                    //GBuffer[(int)pt.x][(int)pt.y] = pt.z * gs * (1 - xDec) * (1 - yDec);
                   // BBuffer[(int)pt.x][(int)pt.y] = pt.z * bs * (1 - xDec) * (1 - yDec);
                }

                ZBuffer[(int)pt.x+1][(int)pt.y] = Math.max(pt.z * xDec * (1 - yDec), ZBuffer[(int) pt.x + 1][(int) pt.y]);
                ZBuffer[(int)pt.x][(int)pt.y+1] = Math.max(pt.z * (1 - xDec) * yDec, ZBuffer[(int) pt.x][(int) pt.y + 1]);
                ZBuffer[(int)pt.x+1][(int)pt.y+1] = Math.max(pt.z * xDec * yDec, ZBuffer[(int) pt.x + 1][(int) pt.y + 1]);
/*
                RBuffer[(int)pt.x+1][(int)pt.y] = Math.max(pt.z* rs * xDec * (1 - yDec), RBuffer[(int) pt.x + 1][(int) pt.y]);
                RBuffer[(int)pt.x][(int)pt.y+1] = Math.max(pt.z* rs * (1 - xDec) * yDec, RBuffer[(int) pt.x][(int) pt.y + 1]);
                RBuffer[(int)pt.x+1][(int)pt.y+1] = Math.max(pt.z* rs * xDec * yDec, RBuffer[(int) pt.x + 1][(int) pt.y + 1]);

                GBuffer[(int)pt.x+1][(int)pt.y] = Math.max(pt.z* gs * xDec * (1 - yDec), GBuffer[(int) pt.x + 1][(int) pt.y]);
                GBuffer[(int)pt.x][(int)pt.y+1] = Math.max(pt.z* gs * (1 - xDec) * yDec, GBuffer[(int) pt.x][(int) pt.y + 1]);
                GBuffer[(int)pt.x+1][(int)pt.y+1] = Math.max(pt.z* gs * xDec * yDec, GBuffer[(int) pt.x + 1][(int) pt.y + 1]);

                BBuffer[(int)pt.x+1][(int)pt.y] = Math.max(pt.z* bs * xDec * (1 - yDec), BBuffer[(int) pt.x + 1][(int) pt.y]);
                BBuffer[(int)pt.x][(int)pt.y+1] = Math.max(pt.z* bs * (1 - xDec) * yDec, BBuffer[(int) pt.x][(int) pt.y + 1]);
                BBuffer[(int)pt.x+1][(int)pt.y+1] = Math.max(pt.z* bs * xDec * yDec, BBuffer[(int) pt.x + 1][(int) pt.y + 1]);
*/


                return res;
            }
        }

        return false;
    }

    public void saveCam(){
        savedPitch = camPitch;
        savedYaw = camYaw;
        savedRoll = camRoll;
    }

    public ifsPt getCentroid(){
        return new ifsPt(centroid.x/ totalSamplesAlpha, centroid.y/ totalSamplesAlpha, centroid.z/ totalSamplesAlpha);
    }

    public double[][] getScaledDepthProjection(double brightness){
        double[][] scaled = new double[width][height];

        double scaler = brightness;

        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                scaled[x][y]= Math.min((int)(scaler*ZBuffer[x][y]), 255);
            }
        }

        return scaled;
    }

    public double[][] getScaledRedProjection(double brightness){
        double[][] scaled = new double[width][height];

        double scaler = brightness;

        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                scaled[x][y]= Math.min((int)(scaler*RBuffer[x][y]), 255);
            }
        }

        return scaled;
    }

    public double[][] getScaledGreenProjection(double brightness){
        double[][] scaled = new double[width][height];

        double scaler = brightness;

        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                scaled[x][y]= Math.min((int)(scaler*GBuffer[x][y]), 255);
            }
        }

        return scaled;
    }

    public double[][] getScaledBlueProjection(double brightness){
        double[][] scaled = new double[width][height];

        double scaler = brightness;

        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                scaled[x][y]= Math.min((int)(scaler*BBuffer[x][y]), 255);
            }
        }

        return scaled;
    }

    public double[][] getScaledProjection(double brightness){
        double[][] scaled = new double[width][height];

        double scaler = 255.0/dataMax * brightness;

        for(int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                scaled[x][y]= Math.min((int)(scaler*XYProjection[x][y]), 255);
            }
        }

        return scaled;
    }

    public static double[][] getPotential2D(double[][] map, int radius){ // map must be square!
        int width = map.length;
        double[][] res = new double[width][width];
        double invDistance[][] = new double[radius*2][radius*2];
        int x,y;
        int x2,y2;
        double d2;

        for(x2=-radius; x2<radius; x2++){
            for(y2=-radius; y2<radius; y2++){
                d2 = (x2*x2+y2*y2);
                if(d2<1){d2=1;}
                invDistance[x2+radius][y2+radius] = 1.0/d2;
                if(d2>radius){invDistance[x2+radius][y2+radius]=0;}
            }
        }

        for(x=radius; x<width-radius; x++){
            for(y=radius; y<width-radius; y++){
                res[x][y]=0;
                for(x2=-radius; x2<radius; x2++){
                    for(y2=-radius; y2<radius; y2++){
                        res[x][y]+=map[(x+x2)][(y+y2)]*invDistance[x2+radius][y2+radius]/2;
                    }
                }
                res[x][y]=Math.min(res[x][y],255);
            }
        }
        return res;
    }

    public static void saveToAscii(smartVolume map){
        BufferedWriter writer = null;
        try {
            //create a temporary file
            String timeLog = new SimpleDateFormat("yyyy_MM_dd_HHmmss").format(Calendar.getInstance().getTime()) + ".txt";
            File logFile = new File(timeLog);

            // This will output the full path where the file will be written to...
            System.out.println(logFile.getCanonicalPath());

            writer = new BufferedWriter(new FileWriter(logFile));
            writer.write("Hello world!");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                // Close the writer regardless of what happens...
                writer.close();
            } catch (Exception e) {
            }
        }
    }

    public static smartVolume getPotential3D(smartVolume map, int radius){ // map must be square!
        int size = map.size;

        smartVolume res = new smartVolume(size);

        double invDistance[][][] = new double[radius*2][radius*2][radius*2];
        int x,y,z;
        int x2,y2,z2;

        double d2;

        for(x2=-radius; x2<radius; x2++){
            for(y2=-radius; y2<radius; y2++){
                for(z2=-radius; z2<radius; z2++){
                    d2 = (x2*x2 + y2*y2 + z2*z2);
                    if(d2<1){d2=1;}
                    invDistance[x2+radius][y2+radius][z2+radius] = 1.0/d2;
                    if(d2>radius){invDistance[x2+radius][y2+radius][z2+radius]=0;}
                }
            }
        }

        double addition;
        int x1,y1,z1;
        //iterate through sub-domains, skipping empty ones

        for(x1=0; x1<map.subRes; x1++){
            for(y1=0; y1<map.subRes; y1++){
                for(z1=0; z1<map.subRes; z1++){

                    if(map.isNotEmpty(x1,y1,z1)){ //skip empty domains
                        for(x=x1*subVolume.size; x<(x1+1)*subVolume.size; x++){
                            for(y=y1*subVolume.size; y<(y1+1)*subVolume.size; y++){
                                for(z=z1*subVolume.size; z<(z1+1)*subVolume.size; z++){
                                    res.clearData(x,y,z);
                                    addition=0;
                                    for(x2=-radius; x2<radius; x2++){
                                        for(y2=-radius; y2<radius; y2++){
                                            for(z2=-radius; z2<radius; z2++){
                                                addition+=map.getData(x+x2,y+y2,z+z2)*invDistance[x2+radius][y2+radius][z2+radius];
                                            }
                                        }
                                    }
                                    if(addition>0){
                                        res.putData(x,y,z,addition/2);
                                        res.clipData(x, y, z);
                                    }
                                }
                            }
                        }
                    }


                }
            }
            if(x1%10==0)
                System.out.println("3D POTENTIAL " + x1 + "/" + map.subRes);
        }



        return res;
    }

    public static double[][] getProjectionCopy(double[][] map){
        int width = map.length;

        double[][] res = new double[width][width];
        int x,y;

        for(x=0; x<width; x++){
            for(y=0; y<width; y++){
                res[x][y]=map[x][y]+0;
            }
        }

        return res;
    }

    public double[][] findEdges2D(double[][] map){
        int width = map.length;

        double total =0;

        double[][] res = new double[width][width];
        int x,y;

        for(x=3; x<width-3; x++){
            for(y=3; y<width-3; y++){

                double edges1 = Math.max(
                                Math.max(
                                        (map[x][y]-map[x-1][y]),
                                        (map[x][y] - map[x + 1][y])),
                                Math.max((map[x][y]-map[x][y-1]),
                                        (map[x][y]-map[x][y+1]))
                );

                total+=edges1/255.0;

                edges1 = Math.min(edges1, 255);
                res[x][y]=edges1;
            }
        }

        surfaceArea = total;

        return res;
    }

    public static double[][] getThreshold2D(double[][] map, int threshold){
        int width = map.length;

        double[][] res = new double[width][width];
        int x,y;

        for(x=0; x<width; x++){
            for(y=0; y<width; y++){
                res[x][y]=map[x][y]>threshold?255:0;
            }
        }

        return res;
    }

    public static enum RenderMode {
        VOLUMETRIC, PROJECT_ONLY
    }
}
