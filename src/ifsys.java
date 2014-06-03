import java.awt.*;
import java.awt.event.*;
import java.awt.image.MemoryImageSource;

public class ifsys extends Panel
    implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, FocusListener, ActionListener
{
    mainthread game;
    boolean quit;
    int screenwidth;
    int screenheight;

    double pixelsData[];
    double dataMax = 0;
    double gamma = 0;
    int pixels[];
    long numPoints=0;
    Image render;
    Graphics rg;
    long fps;
    long framesThisSecond;
    long oneSecondAgo;

    double samplesThisFrame;
    double samplesNeeded;

    long lastMoveTime;

    pdf3D thePdf;

    //user params
        boolean framesHidden;
        boolean leavesHidden;
        boolean antiAliasing;
        boolean trailsHidden;
        boolean spokesHidden;
        boolean infoHidden;
        boolean usePDFSamples;
        boolean guidesHidden;
        boolean invertColors;
        int sampletotal;
        int iterations;
        int pointselected;
        ifsPt selectedPt;

        boolean shiftDown;
        boolean ctrlDown;
        boolean altDown;
        int mousex, mousey, mousez;
        int mouseScroll;

        int viewMode;
        int rotateMode;

    ifsShape shape;
    ifsPt centerOfGrav = new ifsPt();
    double shapeArea;
    double shapeAreaDelta;

    int maxPoints;
    int maxLineLength;

    //drag vars
        int mousemode; //current mouse button
        double startDragX, startDragY, startDragZ;
        double startDragPX, startDragPY, startDragPZ;
        double startDragDist;
        double startDragAngleYaw;
        double startDragAnglePitch;
        double startDragScale;

    boolean started;
    boolean isDragging;
    int preset;

    ifsOverlays overlays;

    public ifsys(){
        started=false;
        samplesThisFrame=0;
        numPoints=0;
        oneSecondAgo =0;
        framesThisSecond = 0;
        altDown=false;
        ctrlDown=false;
        shiftDown=false;
        game = new mainthread();
        quit = false;
        antiAliasing = true;
        framesHidden = true;
        spokesHidden = true;
        trailsHidden = true;
        leavesHidden = false;
        infoHidden = false;
        usePDFSamples = true;
        guidesHidden = false;
        invertColors = false;
        screenwidth = 1024;
        screenheight = 1024;
        pixels = new int[screenwidth * screenheight];
        pixelsData = new double[screenwidth * screenheight];
        sampletotal = 512;
        iterations = 2;
        mousemode = 0;
        samplesNeeded = 1;
        maxLineLength = screenwidth;
        maxPoints = 100;
        shape = new ifsShape(maxPoints);
        mouseScroll = 0;
        gamma = 1.0D;
        pointselected=-1;
        isDragging = false;

        thePdf = new pdf3D();
        viewMode=0;
        rotateMode=0;
        lastMoveTime=0;
    }

    public static void main(String[] args) {
        Frame f = new Frame();
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
                System.exit(0);
            };
        });
        ifsys is = new ifsys();
        is.setSize(is.screenwidth, is.screenheight); // same size as defined in the HTML APPLET
        f.add(is);
        f.pack();
        is.init();
        f.setSize(is.screenwidth, is.screenheight + 20); // add 20, seems enough for the Frame title,
        f.show();
        ifsMenu theMenu = new ifsMenu(f, is);
    }

    public void init() {
        start();
        shape.updateCenter();
        clearframe();
        gamefunc();
    }

    public void findSelectedPoint(){
        if(viewMode==0){
            pointselected = shape.getNearestPtIndexXY(mousex, mousey);
        }else if(viewMode==1){
            pointselected = shape.getNearestPtIndexXZ(mousex, mousey);
        }else if(viewMode==2){
            pointselected = shape.getNearestPtIndexZY(mousey, mousez);
        }

        selectedPt = shape.pts[pointselected];
    }

    public void actionPerformed(ActionEvent e) {

    }

    public class mainthread extends Thread{
        public void run(){
            while(!quit) 
                try{
                    gamefunc();
                    repaint();
                    sleep(1L);
                }
                catch(InterruptedException e) {
                    e.printStackTrace();
                }
        }

        public mainthread(){
        }
    }

    public void start(){
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        render = createImage(screenwidth, screenheight);
        rg = render.getGraphics();

        overlays = new ifsOverlays(this, rg);

        preset = 1;
        clearframe();
        game.start();
        shape.setToPreset(1);

        started = true;
    }

    public void update(Graphics gr){
        paint(gr);
    }

    public void paint(Graphics gr){
        framesThisSecond++;
        if(System.currentTimeMillis()- oneSecondAgo >=1000){
            oneSecondAgo = System.currentTimeMillis();
            fps= framesThisSecond;
            framesThisSecond =0;
        }

        generatePixels();

        rg.drawImage(createImage(new MemoryImageSource(screenwidth, screenheight, pixels, 0, screenwidth)), 0, 0, screenwidth, screenheight, this);
        rg.drawImage(thePdf.sampleImage, getWidth() - 50, 0, 50, 50, this);
        rg.setColor(Color.blue);

        if(!guidesHidden){
            overlays.drawArcs(rg);
            overlays.drawCenterOfGravity(rg);
        }


        if(!infoHidden && pointselected>=0){
            overlays.drawInfoBox(rg);
        }

        gr.drawImage(render, 0, 0, screenwidth, screenheight, this);
    }

    public void generatePixels(){
        double scaler = 255/dataMax;
        double area = 0;
        int scaledColor = 0;

        if(invertColors){
            for(int a = 0; a < screenwidth * screenheight; a++){
                int argb = 255;
                scaledColor = (int)(255-scaler*pixelsData[a]);
                argb = (argb << 8) + scaledColor;
                argb = (argb << 8) + scaledColor;
                argb = (argb << 8) + scaledColor;
                pixels[a] = argb;
                area+=scaler*pixelsData[a];
            }
        }else{
            for(int a = 0; a < screenwidth * screenheight; a++){
                int argb = 255;
                scaledColor = (int)(scaler*pixelsData[a]);
                argb = (argb << 8) + scaledColor;
                argb = (argb << 8) + scaledColor;
                argb = (argb << 8) + scaledColor;
                pixels[a] = argb;
                area+=scaler*pixelsData[a];
            }
        }

        shapeAreaDelta = (area - shapeArea)/shapeArea;
        shapeArea = area;
    }

    public void clearframe(){
        if(invertColors){
            for(int a = 0; a < screenwidth * screenheight; a++){
                pixels[a] = 0xffffffff;
                pixelsData[a] = 1;
            }
        }else{
            for(int a = 0; a < screenwidth * screenheight; a++){
                pixels[a] = 0xff000000;
                pixelsData[a] = 0;
            }
        }

        centerOfGrav.x=shape.pts[0].x;
        centerOfGrav.y=shape.pts[0].y;
        centerOfGrav.z=shape.pts[0].z;
        samplesThisFrame=1;
        dataMax = 0;
    }

    public void centerOnGrav(){
        shape.centerByPt(
                (int)(centerOfGrav.x/samplesThisFrame),
                (int)(centerOfGrav.y/samplesThisFrame),
                (int)(centerOfGrav.z/samplesThisFrame),
                screenwidth/2, screenwidth/2, screenwidth/2);
        clearframe();
    }

    public boolean putPixel(ifsPt pt, double alpha){

        centerOfGrav.x+=pt.x*alpha;
        centerOfGrav.y+=pt.y*alpha;
        centerOfGrav.z+=pt.z*alpha;

        double decX, decY, decZ; //decimal parts of coordinates
        double x=0,y=0,z=0;

        switch (viewMode){
            case 0:
                x = pt.x; y = pt.y; z = pt.z;
                break;
            case 1:
                x = pt.x; y = pt.z; z = pt.y;
                break;
            case 2:
                x = pt.z; y = pt.y; z = pt.x;
                break;
        }

        samplesThisFrame+=alpha;

        if(x < (double)(screenwidth - 1) &&
            y < (double)(screenheight - 1) &&
            x > 0.0D && y > 0.0D){

            decX = x - Math.floor(x);
            decY = y - Math.floor(y);

            if(antiAliasing){
                //each point contributes to 4 pixels

                pixelsData[(int)(x) + (int)(y) * screenwidth]+=alpha*(1.0-decX)*(1.0-decY);
                pixelsData[(int)(x+1) + (int)(y) * screenwidth]+=alpha*decX*(1.0-decY);
                pixelsData[(int)(x) + (int)(y+1) * screenwidth]+=alpha*decY*(1.0-decX);
                pixelsData[(int)(x+1) + (int)(y+1) * screenwidth]+=alpha*decY*decX;

                if(dataMax<pixelsData[(int)x + (int)y * screenwidth]/gamma){dataMax = pixelsData[(int)x + (int)y * screenwidth]/gamma;}
            }else{
                if(alpha>0.49)
                pixelsData[(int)(x) + (int)(y) * screenwidth]=Math.max(pixelsData[(int)(x) + (int)(y) * screenwidth], 1);
            }

            return true; //pixel is in screen bounds
        }else{
            return false; //pixel outside of screen bounds
        }

    }

    public void putPdfSample(ifsPt dpt, double cumulativeRotationYaw, double cumulativeRotationPitch, double cumulativeScale, double cumulativeOpacity, ifsPt thePt, double scaleDown){
        //generate random coords

        double x=dpt.x;
        double y=dpt.y;
        double z=dpt.z;

        double sampleX = Math.random()*thePdf.sampleWidth;
        double sampleY = Math.random()*thePdf.sampleHeight;
        double sampleZ = Math.random()*thePdf.sampleDepth;

        //modulate with image
        double exposureAdjust = cumulativeScale*thePt.scale*thePt.radius;
        double ptColor = thePdf.volume[(int)sampleX][(int)sampleY][(int)sampleZ]/255.0*cumulativeOpacity/scaleDown*exposureAdjust*exposureAdjust;

        //double ptColor = thePdf.getSliceXY_Sum((int)sampleX,(int)sampleY)/255.0*cumulativeOpacity/scaleDown*exposureAdjust*exposureAdjust;

        //rotate/scale the point
        double pointDegreesYaw = Math.atan2(sampleX - thePdf.sampleWidth/2, sampleY - thePdf.sampleHeight/2)+cumulativeRotationYaw+thePt.rotationYaw -thePt.degreesYaw;

        double pointDist = shape.distance(sampleX - thePdf.sampleWidth/2, sampleY - thePdf.sampleHeight/2, sampleZ - thePdf.sampleDepth/2)*cumulativeScale*thePt.scale*thePt.radius/thePdf.sampleWidth;

        ifsPt rpt = new ifsPt(pointDist,0,0).getRotatedPt(-0, -pointDegreesYaw);

        double placedX = rpt.x;//Math.cos(pointDegreesYaw)*pointDist;
        double placedY = rpt.y;//Math.sin(pointDegreesYaw)*pointDist;
        double placedZ = 0;

        //put pixel
        putPixel(new ifsPt(x+placedX,y+placedY, z+placedZ), ptColor);
    }

    public void putLine(ifsPt p0, ifsPt p1, double alpha){ //TODO start/end alpha values?
        double steps = (int)shape.distance(p0.x-p1.x, p0.y-p1.y, p0.z-p1.z);
        double dx, dy, dz;

        boolean startedInScreen = false;

        if(steps>maxLineLength){steps=maxLineLength;}

        samplesThisFrame++;

        for(int i=0; i<steps; i++){
            dx = p0.x + i*(p1.x-p0.x)/steps;
            dy = p0.y + i*(p1.y-p0.y)/steps;
            dz = p0.z + i*(p1.z-p0.z)/steps;

            if(putPixel(new ifsPt(dx, dy, dz), alpha)){ //stop drawing if pixel is outside bounds
                startedInScreen = true;
            }else{
                if(startedInScreen)break;
            };
        }
    }

    public void gamefunc(){

        guidesHidden = System.currentTimeMillis() - lastMoveTime > 1000;

        samplesNeeded = Math.pow(shape.pointsInUse, iterations);

        if(shape.pointsInUse != 0){


            if(!spokesHidden){ //center spokes
                for(int a=0; a<shape.pointsInUse; a++){
                    putLine(shape.pts[0], shape.pts[a], shape.pts[a].opacity);
                }
            }

            if(!framesHidden){ //center outline
                for(int a=0; a<shape.pointsInUse; a++){
                    int nextPt = (a+1)%shape.pointsInUse;
                    putLine(shape.pts[a], shape.pts[nextPt], shape.pts[nextPt].opacity);
                }
            }


            for(int a = 0; a < sampletotal; a++){
                int randomIndex = 0;
                ifsPt dpt = new ifsPt(shape.pts[randomIndex]);
                ifsPt rpt;

                double size, yaw, pitch;//, roll;

                double cumulativeScale = 1.0;
                double cumulativeOpacity = 1;

                double cumulativeRotationYaw = 0;
                double cumulativeRotationPitch = 0;
                //double cumulativeRotationRoll = 0;

                double scaleDownMultiplier = Math.pow(shape.pointsInUse,iterations-1); //this variable is used to tone down repeated pixels so leaves and branches are equally exposed

                for(int d = 0; d < iterations; d++){
                    scaleDownMultiplier/=shape.pointsInUse;

                    randomIndex = 1 + (int)(Math.random() * (double) (shape.pointsInUse-1));

                    if(d==0){randomIndex=0;}

                    if(d!=0){
                        size = shape.pts[randomIndex].radius * cumulativeScale;
                        yaw = Math.PI/2D - shape.pts[randomIndex].degreesYaw + cumulativeRotationYaw;
                        pitch = Math.PI/2D - shape.pts[randomIndex].degreesPitch + cumulativeRotationPitch;

                        rpt = new ifsPt(size,0,0).getRotatedPt(-pitch, -yaw);

                        dpt.x += rpt.x;
                        dpt.y += rpt.y;
                        dpt.z += rpt.z;
                    }

                    if(!trailsHidden && d < iterations-1)
                        putPixel(dpt, shape.pts[randomIndex].opacity);
                    if(usePDFSamples)
                        putPdfSample(dpt, cumulativeRotationYaw,cumulativeRotationPitch, cumulativeScale, cumulativeOpacity, shape.pts[randomIndex], scaleDownMultiplier);
                    cumulativeScale *= shape.pts[randomIndex].scale/shape.pts[0].scale;
                    cumulativeOpacity *= shape.pts[randomIndex].opacity;

                    cumulativeRotationYaw += shape.pts[randomIndex].rotationYaw;
                    cumulativeRotationPitch += shape.pts[randomIndex].rotationPitch;
                }
                if(!leavesHidden)
                    putPixel(dpt, cumulativeOpacity);
            }
        }
    }

    public void mouseClicked(MouseEvent mouseevent){
    }

    public void mousePressed(MouseEvent e){
        mousemode = e.getButton();

        switch (viewMode){
            case 0: //XY
                mousex = e.getX();
                mousey = e.getY();
                mousez = 0;
                break;
            case 1: //XZ
                mousex = e.getX();
                mousey = e.getY();
                mousey = 0;
                break;
            case 2: //YZ
                mousex = e.getX();
                mousey = e.getY();
                mousex = 0;
                break;
        }

        findSelectedPoint();

        if(e.getClickCount()==2){
            if(mousemode == 1){ //add point w/ double click
                shape.addPoint(mousex, mousey, mousez);
                clearframe();
                gamefunc();
            }else if(mousemode == 3){ //remove point w/ double right click
                shape.deletePoint(pointselected);
                clearframe();
                gamefunc();
            }
        }else{
            startDragX = mousex;
            startDragY = mousey;
            startDragZ = mousez;
            shape.updateCenter();

            startDragPX = selectedPt.x;
            startDragPY = selectedPt.y;
            startDragPZ = selectedPt.z;
            startDragDist = shape.distance(startDragX - selectedPt.x, startDragY - selectedPt.y, startDragZ - selectedPt.z);
            startDragAngleYaw = selectedPt.rotationYaw + Math.atan2(startDragX - selectedPt.x, startDragY - selectedPt.y);
            startDragAnglePitch = selectedPt.rotationPitch + Math.atan2(startDragX - selectedPt.x, startDragY - selectedPt.y);

            startDragScale = selectedPt.scale;

            requestFocus();
        }
    }

    public void mouseReleased(MouseEvent e){
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        mousemode = 0;
        isDragging=false;
    }

    public void mouseEntered(MouseEvent e){
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void mouseExited(MouseEvent e){
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void mouseDragged(MouseEvent e){
        isDragging=true;
        lastMoveTime = System.currentTimeMillis();
        if(mousemode == 1){ //left click to move a point/set
            setCursor (Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

            switch (viewMode){
                case 0:
                    selectedPt.x = startDragPX + (e.getX() - startDragX);
                    selectedPt.y = startDragPY + (e.getY() - startDragY);
                    selectedPt.z = startDragPZ + (mousez - startDragZ);
                    break;
                case 1:
                    selectedPt.x = startDragPX + (e.getX() - startDragX);
                    selectedPt.y = startDragPY + (mousey - startDragY);
                    selectedPt.z = startDragPZ + (e.getY() - startDragZ);
                    break;
                case 2:
                    selectedPt.x = startDragPX + (mousex - startDragX);
                    selectedPt.y = startDragPY + (e.getX() - startDragY);
                    selectedPt.z = startDragPZ + (e.getY() - startDragZ);
                    break;
            }


        }
        else if(mousemode == 3){ //right click to rotate point/set
            setCursor (Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));

            double scaleDelta = shape.distance(e.getX() - selectedPt.x, e.getY() - selectedPt.y, mousez - shape.pts[0].z)/startDragDist;
            if(rotateMode==0){
                double rotationDelta = (Math.atan2(e.getX() - selectedPt.x, e.getY() - selectedPt.y)- startDragAngleYaw);
                selectedPt.rotationYaw = Math.PI * 2 - rotationDelta;
            }else if(rotateMode==1){
                double rotationDelta = (Math.atan2(e.getX() - selectedPt.x, e.getY() - selectedPt.y)- startDragAnglePitch);
                selectedPt.rotationPitch = Math.PI * 2 - rotationDelta;
            }

            selectedPt.scale = startDragScale*scaleDelta;
        }

        shape.updateCenter();
        clearframe();
        gamefunc();
    }

    public void mouseWheelMoved(MouseWheelEvent e) {

        lastMoveTime = System.currentTimeMillis();

        mouseScroll += e.getWheelRotation();

        double changeFactor = 0.9;

        if(e.getWheelRotation()>0){ //scroll down
            if(shiftDown || ctrlDown){//decrease gamma
                gamma*=0.9;
            }else{//decrease point opacity
                selectedPt.opacity*=changeFactor;
            }
        }else{ //scroll up
            if(shiftDown || ctrlDown){//increase gamma
                gamma/=0.9;
            }else{//increase point opacity
                selectedPt.opacity/=changeFactor;

                if(selectedPt.opacity>1){ //values above 1 break the line function so instead we reduce the other points for the same effect
                    selectedPt.opacity=1.0D;
                    for(int i=0; i<shape.pointsInUse; i++){
                        shape.pts[i].opacity*=changeFactor;
                    }
                }
            }
        }

        clearframe();
        gamefunc();
    }

    public void mouseMoved(MouseEvent e){
        findSelectedPoint();
        mousex = e.getX();
        mousey = e.getY();
        mousez = 0;
        lastMoveTime = System.currentTimeMillis();
    }

    public void keyTyped(KeyEvent e){
    }

    public void keyPressed(KeyEvent e){
        if(e.getKeyCode()==KeyEvent.VK_ALT)
            altDown=true;
        if(e.getKeyCode()==KeyEvent.VK_CONTROL)
            ctrlDown=true;
        if(e.getKeyCode()==KeyEvent.VK_SHIFT)
            shiftDown=true;
        shape.updateCenter();
        clearframe();
        gamefunc();
    }

    public void keyReleased(KeyEvent e){
        if(e.getKeyCode()==KeyEvent.VK_ALT)
            altDown=false;
        if(e.getKeyCode()==KeyEvent.VK_CONTROL)
            ctrlDown=false;
        if(e.getKeyCode()==KeyEvent.VK_SHIFT)
            shiftDown=false;

        if(e.getKeyChar() == 'c')
            centerOnGrav();

        if(e.getKeyChar() == '/')
            iterations++;
        if(e.getKeyChar() == '.' && iterations > 1)
            iterations--;

        if(e.getKeyChar() == 'm')
            sampletotal *= 2;
        if(e.getKeyChar() == 'n' && sampletotal > 1)
            sampletotal /= 2;

        if(sampletotal<2){sampletotal=2;}
        if(sampletotal>32768){sampletotal=32768;}

        if(e.getKeyChar() == '1')
            shape.setToPreset(1);
        if(e.getKeyChar() == '2')
            shape.setToPreset(2);
        if(e.getKeyChar() == '3')
            shape.setToPreset(3);
        if(e.getKeyChar() == '4')
            shape.setToPreset(4);
        if(e.getKeyChar() == '5')
            shape.setToPreset(5);
        if(e.getKeyChar() == '6')
            shape.setToPreset(6);
    }

    public void focusGained(FocusEvent focusevent){}
    public void focusLost(FocusEvent focusevent){}
}
