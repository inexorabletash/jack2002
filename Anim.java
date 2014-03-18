import java.awt.*;
import java.awt.image.*;
import java.applet.*;
import java.io.*;
import java.net.URL;
import java.util.*;

public class Anim extends Applet implements Runnable
{
    static final boolean DEBUG = true; 
        
    //----------------------------------------------------------------------
    //
    // Constants
    //
    //----------------------------------------------------------------------
    
    // NOTE: These constants are not exposed externally; they can be changed
    final static int TYPE_UNKNOWN = -1;
    final static int TYPE_PAR     =  0;
    final static int TYPE_SEQ     =  1;
    final static int TYPE_SCENE   =  2;
    final static int TYPE_BITMAP  =  3;
    final static int TYPE_VECTOR  =  4;
    final static int TYPE_TEXT    =  5;
    final static int TYPE_SOUND   =  6;
    final static int TYPE_PATH    =  7;
    final static int TYPE_SCALE   =  8;
    final static int TYPE_ROTATE  =  9;
    final static int TYPE_COLOR   = 10;

    final static int GA_INIT = 0;
    final static int GA_CURR = 1;

    //final static long MILLIS_PER_FRAME = 50;
    final static long MILLIS_PER_FRAME = 10;
    
    //----------------------------------------------------------------------
    //
    // Globals
    //
    //----------------------------------------------------------------------
    
    static Hashtable g_typeHash = null;
    static 
    {
        g_typeHash = new Hashtable();
        g_typeHash.put( "par",    new Integer( TYPE_PAR    ) );
        g_typeHash.put( "seq",    new Integer( TYPE_SEQ    ) );
        g_typeHash.put( "scene",  new Integer( TYPE_SCENE  ) );
        g_typeHash.put( "bitmap", new Integer( TYPE_BITMAP ) );
        g_typeHash.put( "vector", new Integer( TYPE_VECTOR ) );
        g_typeHash.put( "text",   new Integer( TYPE_TEXT   ) );
        g_typeHash.put( "sound",  new Integer( TYPE_SOUND  ) );
        g_typeHash.put( "path",   new Integer( TYPE_PATH   ) );
        g_typeHash.put( "scale",  new Integer( TYPE_SCALE  ) );
        g_typeHash.put( "rotate", new Integer( TYPE_ROTATE ) );
        g_typeHash.put( "color",  new Integer( TYPE_COLOR  ) );
    }
    

    public  static Applet        g_applet            = null;
    public  static Hashtable     g_idTable           = null;
    private static MediaTracker  g_mediaTracker      = null; 
    private static Thread        g_animThread        = null;
    private static Image         g_offscreenImage    = null;
    private static Graphics      g_offscreenGraphics = null;
    private static long          g_tmStart           = 0;

    
    //----------------------------------------------------------------------
    //
    // Members
    //
    //----------------------------------------------------------------------
    
    // Data Structure
    public Hashtable m_properties        = new Hashtable();
    public Hashtable m_currentProperties = null;
    
    public Vector    m_children   = new Vector();
    public Anim      m_parent     = null;
    public int       m_type       = TYPE_UNKNOWN;

    public boolean   m_fActive    = false;

    
    //----------------------------------------------------------------------
    //
    // Applet/Component methods
    //
    //   void init()
    //   void start()   
    //   void stop()
    //   void destroy()
    //   void update()
    //   void paint()
    //   void processMouseEvent()
    //   void processMouseMotionEvent()
    //
    //----------------------------------------------------------------------

    //------------------------------------------------------------
    public void init()
    //------------------------------------------------------------
    {
        if( DEBUG ) System.out.println("init");
        
        g_applet = this;
        g_mediaTracker = new MediaTracker( this );
        g_animThread = new Thread( this );
        g_idTable = new Hashtable();

        try  
        {   
            URL url = new URL( getDocumentBase(), getParameter( "href" ) );    
            InputStream stream = url.openStream();

            //File file = new File( "test.jck.html" );
            //InputStream stream = new FileInputStream( file );
                
            PushbackReader reader = 
                new PushbackReader(
                    new BufferedReader( 
                        new InputStreamReader( stream ) ), 2 );
            
            parseElement( reader );
            reader.close();  

            // Add root to the ID table (special case, see parser)
            String id = getAttribString( "id", null, GA_INIT );
            if( id != null )
                g_idTable.put( id, this );
            
            if( DEBUG ) dump( "\t" );
            
            int width  = (int)getAttribLong( "width",  100, GA_INIT );
            int height = (int)getAttribLong( "height", 100, GA_INIT );

            setSize( width, height );
            
            onLoad();
            g_mediaTracker.waitForAll();
            enableEvents( AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK );               
            
            g_animThread.start();
        }
        catch ( Exception e ) 
        {
            if( DEBUG ) e.printStackTrace();
        }
        
        if( DEBUG ) System.out.println("/init");
        
    } // init

    
    //------------------------------------------------------------
    public void start()
    //------------------------------------------------------------
    {
        if( DEBUG ) System.out.println("start");
        
        // TODO: Should not use resume/suspend/stop - use flags to allow thread to self-terminate
        if( g_animThread != null )
            g_animThread.resume();

        if( DEBUG ) System.out.println("/start");
    }

    //------------------------------------------------------------
    public void stop()
    //------------------------------------------------------------
    {
        if( DEBUG ) System.out.println("stop");
        
        // TODO: Should not use resume/suspend/stop - use flags to allow thread to self-terminate
        if( g_animThread != null )
            g_animThread.suspend();

        if( DEBUG ) System.out.println("/stop");
    }
    
    //------------------------------------------------------------
    public void destroy()
    //------------------------------------------------------------
    {
        if( DEBUG ) System.out.println("destroy");

        // TODO: Should not use resume/suspend/stop - use flags to allow thread to self-terminate
        if( g_animThread != null )
            g_animThread.stop();
        
        if( DEBUG ) System.out.println("/destroy");
    }

    //------------------------------------------------------------
    public void update( Graphics g )
    //------------------------------------------------------------
    {
        // override to stop erasing background; paint() will do all the right work
        paint( g );
    }

    
    //------------------------------------------------------------
    public void paint( Graphics g )
    //------------------------------------------------------------
    {
        Dimension dim = getSize();
        
        if( g_offscreenImage == null )
        {
            g_offscreenImage    = createImage( dim.width, dim.height );
            g_offscreenGraphics = g_offscreenImage.getGraphics();
        }
        
        g_offscreenGraphics.setColor( Color.black );
        g_offscreenGraphics.fillRect( 0, 0, dim.width, dim.height );

        onPaint( g_offscreenGraphics );

        g.drawImage( g_offscreenImage, 0, 0, this );
    
    } // paint

    //------------------------------------------------------------
    public void processMouseEvent( java.awt.event.MouseEvent e )
    //------------------------------------------------------------
    {
        if( e.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED )
            onMouseEvent( "click", e.getX(), e.getY() );
        
    } // processMouseEvent
        
    //------------------------------------------------------------
    public void processMouseMotionEvent( java.awt.event.MouseEvent e )
    //------------------------------------------------------------
    {
        if( e.getID() == java.awt.event.MouseEvent.MOUSE_MOVED )
            onMouseEvent( "move", e.getX(), e.getY() );
        
    } // processMouseMotionEvent
    
    
    
    //----------------------------------------------------------------------
    // Runnable methods
    //----------------------------------------------------------------------
    
    //------------------------------------------------------------
    public void run()
    //------------------------------------------------------------
    {
        if( DEBUG ) System.out.println("run");
        
        try
        {
            g_tmStart = System.currentTimeMillis();
            for(;;)
            {
                Thread.sleep( MILLIS_PER_FRAME ); 
                long m_tmCur = System.currentTimeMillis() - g_tmStart;
                
                //System.out.println("tick");

                onTick( m_tmCur / 1000.0 );
                
                Graphics g = this.getGraphics();
                if( g != null )
                    paint( g );
            }
        }
        catch( Exception e ) 
        {
            if( DEBUG ) e.printStackTrace();
        }
        
        if( DEBUG ) System.out.println("/run");
    } // run

    
    //----------------------------------------------------------------------
    //
    // Anim object methods
    //
    //   void onLoad();
    //   void onTick( double t );
    //   void onPaint( Graphics g );
    //   void onMouseEvent( int x, int y );
    //   void onEvent( String eventName, String param );
    //
    //----------------------------------------------------------------------
    
    //------------------------------------------------------------
    public void onLoad() throws InterruptedException
    //------------------------------------------------------------
    {
        switch( m_type )
        {
        case TYPE_BITMAP:
            {
                if( m_properties.containsKey("src") )
                {
                    // Load image
                    Image image = g_applet.getImage( g_applet.getDocumentBase(), (String)m_properties.get("src") );
                    g_mediaTracker.addImage( image, 0 );
                    g_mediaTracker.waitForAll();
                    g_mediaTracker.removeImage( image );

                    // And split into individual frames; this makes rendering easier
                    // later when dealing with rotation
                    int hframes = (int)getAttribLong( "hframes", 1, GA_INIT );
                    int vframes = (int)getAttribLong( "vframes", 1, GA_INIT );
                    int frames  = hframes * vframes;

                    Image[] images = new Image[ frames ];

                    int dxImg   = image.getWidth( null );
                    int dyImg   = image.getHeight( null );
                    int dxFrame = (int)Math.round( dxImg / hframes );
                    int dyFrame = (int)Math.round( dyImg / vframes );
                    
                    int index = 0;
                    for( int y = 0; y < vframes; y++ )
                    {
                        for( int x = 0; x < hframes; x++ )
                        {
                            int xSrc  = x * dxFrame;
                            int ySrc  = y * dyFrame;


                            Image imgFrame = g_applet.createImage( dxFrame, dyFrame );
                            Graphics g = imgFrame.getGraphics();
                            g.drawImage( image, -xSrc, -ySrc, null );
                            
                            images[ index++ ] = imgFrame;
                        }
                    }
                
                    m_properties.put( "_images", images );
                }
                break;
            }
            
        case TYPE_SOUND:
            {
                if( m_properties.containsKey("src") )
                {
                    AudioClip audio = g_applet.getAudioClip( g_applet.getDocumentBase(), (String)m_properties.get("src") );
                    if( audio != null )
                    {
                        m_properties.put( "_audio", audio );
                    }
                }
                break;
            }
            
        case TYPE_VECTOR:
            {
                String points = getAttribString( "points", "", GA_INIT );
                Vector xsv = new Vector();
                Vector ysv = new Vector();
                pathToPoints( points, xsv, ysv );
                int nPoints = xsv.size();
                int[] xs = new int[ nPoints ];
                int[] ys = new int[ nPoints ];

                for( int i = 0; i < nPoints; i++ )
                {
                    xs[i] = (int)Math.round( ((Double)xsv.elementAt( i )).doubleValue() );
                    ys[i] = (int)Math.round( ((Double)ysv.elementAt( i )).doubleValue() );
                }
                        
                m_properties.put( "_xs", xs );
                m_properties.put( "_ys", ys );
                
                break;
            }
            
        case TYPE_PATH:
            {
                String points = getAttribString( "points", "", GA_INIT );
                Vector xsv = new Vector();
                Vector ysv = new Vector();
                pathToPoints( points, xsv, ysv );
                int nPoints = xsv.size();
                double[] xs = new double[ nPoints ];
                double[] ys = new double[ nPoints ];

                for( int i = 0; i < nPoints; i++ )
                {
                    xs[i] = ((Double)xsv.elementAt( i )).doubleValue();
                    ys[i] = ((Double)ysv.elementAt( i )).doubleValue();
                }
                        
                m_properties.put( "_xs", xs );
                m_properties.put( "_ys", ys );
                
                break;
            }
        }
        
        // recurse
        for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
            ((Anim)e.nextElement()).onLoad();
                 
    } // onLoad
    
    
    //------------------------------------------------------------
    public void onTick( double t )
    //------------------------------------------------------------
    {
        m_properties.put( "_tick", Double.toString( t ) );
        
        double tmCur  = Double.NaN;
        m_fActive     = false;
        
        {
            double tmBegin     = getAttribDouble( "begin",       0.0, GA_INIT );
            double tmEnd       = getAttribDouble( "end",         Double.POSITIVE_INFINITY, GA_INIT );
            double tmDur       = getAttribDouble( "dur",         Double.POSITIVE_INFINITY, GA_INIT );
            double repeatCount = getAttribDouble( "repeatCount", 1.0, GA_INIT );
            String fill        = getAttribString( "fill",        "remove", GA_INIT );

            double tmForceBegin     = getAttribDouble( "_forceBegin",       Double.NEGATIVE_INFINITY, GA_INIT );
            double tmForceEnd       = getAttribDouble( "_forceEnd",         Double.NEGATIVE_INFINITY, GA_INIT );
            
            if( tmForceBegin != Double.NEGATIVE_INFINITY /*&& (tmBegin == Double.POSITIVE_INFINITY || tmForceBegin > tmBegin)*/ )
                tmBegin = tmForceBegin;
            if( tmForceEnd   != Double.NEGATIVE_INFINITY && tmForceEnd   < tmEnd )
                tmEnd = tmForceEnd;
                        
            // special case - sound
            if( m_type == TYPE_SOUND )
            {
                AudioClip audio = (AudioClip)m_properties.get( "_audio" );
                if( audio == null )
                    return;
        
                long lastIter      = getAttribLong( "_lastIter", 1, GA_INIT );
                boolean fPlaying   = "playing".equals( getAttribString( "_status", "stopped", GA_INIT ) );
                
                boolean fShouldPlay = false;
                boolean fRestart    = false;
                long    iter = 1;
                
                // If not explicitly ended...
                if( t < tmEnd )
                {
                    double tm = t - tmBegin;
                    if( tm < 0 )
                    {
                        // Before start - therefore not playing
                        fShouldPlay = false;
                    }
                    else if( tmDur == Double.POSITIVE_INFINITY )
                    {
                        // No duration specified - therefore, playing 
                        fShouldPlay = true;
                    }
                    else
                    {
                        iter = (long)Math.floor( tm / tmDur );
                        fShouldPlay = iter < repeatCount;
                        fRestart = ( iter != lastIter );
                    }
                }
                
                if( fRestart )
                    resetTiming( false );
                
                if( fShouldPlay )
                {
                    if( !fPlaying || fRestart )
                        audio.play();
                }
                else
                {
                    if( fPlaying )
                        audio.stop();
                }

                m_properties.put( "_lastIter", Double.toString( iter ) );
                m_properties.put( "_status",   fShouldPlay ? "playing" : "stopped" );
                
                m_fActive = fShouldPlay;
            }
            else // non-Sounds
            {
                // Compute the (local) current time of this node
                
                if( tmDur == 0 )
                    return;
                
                double tm = t - tmBegin;
                if( tm < 0 )
                    return;

                double iter = tm / tmDur;
                if( iter >= repeatCount || t >= tmEnd )
                {
                    if( "hold".equals( fill ) )
                    {
                        tmCur = tmDur;
                    }
                    else
                    {
                        return;
                    }
                }
                else
                {
                    tmCur = tm % tmDur;
                }
                    
                double lastIter = getAttribDouble( "_lastIter", 1, GA_INIT );
                if( Math.floor( lastIter ) != Math.floor( iter ) )
                   resetTiming( false );
                m_properties.put( "_lastIter", Double.toString( iter ) );
                
                m_fActive = true;
            }
        }
        
        m_properties.put( "_tmCur", Double.toString( tmCur ) );
        
        // This is relevant only for behaviors
        Anim m_actor = m_parent;
        String actorID = getAttribString( "actor", null, GA_INIT );
        if( actorID != null )
            m_actor = (Anim)g_idTable.get( actorID );
        
        switch( m_type )
        {
        case TYPE_PAR:
        case TYPE_SEQ:
            // no-op
            break;
            
        case TYPE_SCENE:
        case TYPE_BITMAP:
        case TYPE_VECTOR:
        case TYPE_TEXT:
        case TYPE_UNKNOWN: // Not necessary for behavior extensions, but required for actor extensions
            {
                // Make a transient copy of the properties, for modification by behaviors
                m_currentProperties = (Hashtable)m_properties.clone();
                break;
            }
            
        case TYPE_PATH:
            {
                double[] xs = (double[])m_properties.get( "_xs" );
                double[] ys = (double[])m_properties.get( "_ys" );
                int nPoints = xs.length;
                        
                double tmDur = getAttribDouble( "dur", Double.POSITIVE_INFINITY, GA_INIT );
                double frac = tmCur / tmDur;
                int interval = (int)Math.floor( frac * (nPoints-1) );

                if( interval >= 0 && interval < nPoints-1 )
                {
                    double lastX = xs[ interval ];
                    double lastY = ys[ interval ];
                            
                    double nextX = xs[ interval + 1 ];
                    double nextY = ys[ interval + 1 ];

                    double tmIntervalDur   = tmDur / nPoints;
                    double tmIntervalStart = interval * tmIntervalDur;
                            
                    double p = ( tmCur - tmIntervalStart ) / tmIntervalDur;
                            
                    double x = ( (nextX - lastX) * p ) + lastX;
                    double y = ( (nextY - lastY) * p ) + lastY;
                            
                    x += m_actor.getAttribDouble( "x", 0, GA_CURR );
                    y += m_actor.getAttribDouble( "y", 0, GA_CURR );
                                
                    m_actor.m_currentProperties.put( "x", Double.toString( x ) );
                    m_actor.m_currentProperties.put( "y", Double.toString( y ) );
                }
                break;
            }
            
        case TYPE_SCALE:
            {
                double initialScale = getAttribDouble( "initialScale", 1.0, GA_INIT );
                double finalScale   = getAttribDouble( "finalScale",   1.0, GA_INIT );
                
                double tmDur = getAttribDouble( "dur", Double.POSITIVE_INFINITY, GA_INIT );
                double frac = tmCur / tmDur;
                
                double scale = ( (finalScale - initialScale) * frac + initialScale )
                               * m_actor.getAttribDouble( "scale", 1.0, GA_CURR );
                
                m_actor.m_currentProperties.put( "scale", Double.toString( scale ) );
                
                break;
            }
            
        case TYPE_ROTATE:
            {
                double initialAngle = getAttribDouble( "initialAngle", 0.0, GA_INIT );
                double finalAngle   = getAttribDouble( "finalAngle",   0.0, GA_INIT );
                
                double tmDur = getAttribDouble( "dur", Double.POSITIVE_INFINITY, GA_INIT );
                double frac = tmCur / tmDur;
                
                double angle = ( (finalAngle - initialAngle) * frac + initialAngle )
                               + m_actor.getAttribDouble( "angle", 0.0, GA_CURR );
                
                m_actor.m_currentProperties.put( "angle", Double.toString( angle ) );
                
                break;
            }
            
        case TYPE_COLOR:
            {
                String colorProp   = getAttribString( "colorProp", "color", GA_INIT );
                
                Color curColor = m_actor.getAttribColor( colorProp, Color.black, GA_CURR );
                
                Color initialColor = getAttribColor( "initialColor", curColor, GA_INIT );
                Color finalColor   = getAttribColor( "finalColor",   curColor, GA_INIT );

                double tmDur = getAttribDouble( "dur", Double.POSITIVE_INFINITY, GA_INIT );
                double frac = tmCur / tmDur;

                int r1 = initialColor.getRed();
                int r2 = finalColor  .getRed();
                int g1 = initialColor.getGreen();
                int g2 = finalColor  .getGreen();
                int b1 = initialColor.getBlue();
                int b2 = finalColor  .getBlue();

                int r = (int)( r1 + (r2 - r1) * frac );
                int g = (int)( g1 + (g2 - g1) * frac );
                int b = (int)( b1 + (b2 - b1) * frac );
                
                int rgb = ((r&0xff)<<16) | ((g&0xff)<<8) | (b&0xff);
                
                m_actor.m_currentProperties.put( colorProp, Integer.toHexString( rgb ) );
                
                break;
            }
        }

        if( m_type == TYPE_SEQ )
        {
            double tm = tmCur;
            for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
            {
                Anim child = (Anim)e.nextElement();
                
                double tmBegin     = child.getAttribDouble( "begin",       0.0, GA_INIT );
                double tmDur       = child.getAttribDouble( "dur",         Double.POSITIVE_INFINITY, GA_INIT );
                double repeatCount = child.getAttribDouble( "repeatCount", 1.0, GA_INIT );
                double tmFullDur   = tmBegin + (tmDur * repeatCount);
                
                if( tm < tmFullDur )
                {
                    child.onTick( tm );
                    break;
                }
                
                tm -= tmFullDur;
            }
        }
        else // PAR-type
        {
            // Recurse
            for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
                ((Anim)e.nextElement()).onTick( tmCur );
        }
        
    } // onTick

    
    //------------------------------------------------------------
    public void onPaint( Graphics g )
    //------------------------------------------------------------
    {
        if( !m_fActive )
            return;

        switch( m_type )
        {
        case TYPE_PAR:
        case TYPE_SEQ:
            {
                // recurse
                for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
                    ((Anim)e.nextElement()).onPaint( g );
                return;
            }
            
        case TYPE_SCENE:
        case TYPE_BITMAP:
        case TYPE_VECTOR:
        case TYPE_TEXT:
            // no-op
            break;

        // Behaviors - bail now
        default:
            return;
        }

        // Slurp in common attributes
        int x       = (int)getAttribLong( "x",      0, GA_CURR );
        int y       = (int)getAttribLong( "y",      0, GA_CURR );
        int width   = (int)getAttribLong( "width",  0, GA_CURR );
        int height  = (int)getAttribLong( "height", 0, GA_CURR );

        double scale = getAttribDouble( "scale", 1.0, GA_CURR );
        double angle = getAttribDouble( "angle", 0.0, GA_CURR ) * Math.PI / 180.0;
        
        switch( m_type )
        {
        case TYPE_SCENE:
            {
                g.translate( x, y );

                Color bgcolor = getAttribColor( "bgcolor", null, GA_CURR );
                
                Shape clip = null;
                if( width != 0 && height != 0 )
                {
                    // Scale
                    width  = (int)Math.round( width  * scale );
                    height = (int)Math.round( height * scale );
                    
                    clip = g.getClip();
                    g.clipRect( 0, 0, width, height );

                    if( bgcolor != null )
                    {
                        g.setColor( bgcolor );
                        g.fillRect( 0, 0, width, height );
                    }
                }
                
                // recurse
                for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
                    ((Anim)e.nextElement()).onPaint( g );
        
                if( clip != null )
                    g.setClip( clip );
                
                g.translate( -x, -y );
                
                Polygon hitRegion = new Polygon( new int[] {x, x+width, x+width, x }, 
                                                 new int[] {y, y, y+height, y+height }, 4 );
                m_properties.put( "_hitPoly", hitRegion );
                m_properties.put( "_offsetX", Double.toString( x ) );
                m_properties.put( "_offsetY", Double.toString( y ) );
                
                break;
            }

        case TYPE_BITMAP:
            {
                Image[] images = (Image[])m_currentProperties.get( "_images" );
                
                double tmCur = getAttribDouble( "_tmCur", 1, GA_INIT );
                double rate  = getAttribDouble( "rate",   1, GA_CURR );
                int frames   = images.length;
                int frame    = (int)Math.floor( (tmCur * rate) % frames );
                Image image  = images[frame];

                // Rotation
                image = rotateImage( g_applet, image, angle );
                
                // Scale
                int dxSrc = image.getWidth( null );
                int dySrc = image.getHeight( null );
                int dxDst = (int)Math.round( dxSrc * scale );
                int dyDst = (int)Math.round( dySrc * scale );

                // Render
                int xOffset = dxDst/2;
                int yOffset = dyDst/2;              
                g.drawImage( image, x-xOffset, y-yOffset, dxDst, dyDst, g_applet );

                // Compute bounds for hit testing
                int[] xs = new int[] { x-xOffset, x-xOffset+dxDst, x-xOffset+dxDst, x-xOffset       };
                int[] ys = new int[] { y-yOffset, y-yOffset,       y-yOffset+dyDst, y-yOffset+dyDst };
                rotateScalePoints( xs, ys, 4, angle, 1.0 );
                Polygon hitRegion = new Polygon( xs, ys, 4 );
                m_properties.put( "_hitPoly", hitRegion );
                
                break;
            }
            
        case TYPE_VECTOR:
            {
                int[] xs = (int[])m_properties.get( "_xs" );
                int[] ys = (int[])m_properties.get( "_ys" );
                int nPoints = xs.length;
                
                // Clone for scaling & rotation
                xs = (int[])xs.clone();
                ys = (int[])ys.clone();

                rotateScalePoints( xs, ys, nPoints, angle, scale );
                
                Color lineColor = getAttribColor( "linecolor", null, GA_CURR );
                Color fillColor = getAttribColor( "fillcolor", null, GA_CURR );

                g.translate( x, y );
                
                if( fillColor != null )
                {
                    g.setColor( fillColor );
                    g.fillPolygon( xs, ys, nPoints );
                }
                
                if( lineColor != null )
                {
                    g.setColor( lineColor );
                    g.drawPolygon( xs, ys, nPoints );
                }
                
                g.translate( -x, -y );

                // Compute bounds for hit testing
                Polygon hitRegion = new Polygon( xs, ys, nPoints );
                hitRegion.translate( x, y );
                m_properties.put( "_hitPoly", hitRegion );
        
                break;
            }

        case TYPE_TEXT:
            {
                String text = getAttribString( "text", "", GA_CURR );

                String family = getAttribString( "family", "SansSerif",  GA_CURR );
                double size   = getAttribDouble( "size",   12.0f,        GA_CURR );
                String weight = getAttribString( "weight", "normal",     GA_CURR );
                String style  = getAttribString( "style",  "normal",     GA_CURR );
                Color color   = getAttribColor(  "color",  Color.white,  GA_CURR );

                // Scale
                size  *= scale;
                
                Font font = new Font( family,
                                      Font.PLAIN |
                                      ( ( "bold"  .equals( weight ) ) ? Font.BOLD   : 0 ) |
                                      ( ( "italic".equals( style  ) ) ? Font.ITALIC : 0 ),
                                      (int)Math.round(size) );

                FontMetrics fm = g.getFontMetrics(font);
                width  = fm.stringWidth(text);
                height = fm.getHeight();

                Color bgcolor = getAttribColor( "bgcolor", null, GA_CURR );

                int xOffset = width / 2;
                int yOffset = height / 2;
                
                // Rotation
                if( angle == 0 )
                {
                    if( bgcolor != null )
                    {
                        g.setColor( bgcolor );
                        g.fillRect( x-xOffset, y-yOffset, width, height );
                    }
                    g.setColor( color );
                    g.setFont( font );
                    g.drawString( text, x-xOffset, y+fm.getAscent()-yOffset );
                }
                else
                {
                    // PERF: This runs every frame; should cache the surface
                    Image imgText = g_applet.createImage( width, height );

                    Graphics gText = imgText.getGraphics();
                    if( bgcolor != null )
                    {
                        gText.setColor( bgcolor );
                        gText.fillRect( 0, 0, width, height );
                        gText.setColor( color );
                    }
                    else
                    {
                        gText.setColor( Color.black );
                        gText.fillRect( 0, 0, width, height );
                        gText.setColor( Color.white );
                    }
                    gText.setFont( font );
                    gText.drawString( text, 0, fm.getAscent() );

                    // PERF: Try and cache the chroma image if not scaled
                    if( bgcolor == null )
                        imgText = chromaImage( g_applet, imgText, color );
                    
                    imgText = rotateImage( g_applet, imgText, angle );
                    int dx = imgText.getWidth( null );
                    int dy = imgText.getHeight( null );
                    g.drawImage( imgText, x-dx/2, y-dy/2, null );
                }               

                // Compute bounds for hit testing
                int[] xs = new int[] { x-xOffset, x-xOffset+width, x-xOffset+width,  x-xOffset       };
                int[] ys = new int[] { y-yOffset, y-yOffset,       y-yOffset+height, y-yOffset+height };
                rotateScalePoints( xs, ys, 4, angle, 1.0 );
                Polygon hitRegion = new Polygon( xs, ys, 4 );
                m_properties.put( "_hitPoly", hitRegion );
                break;
            }
        }
        
    } // onPaint


    //------------------------------------------------------------
    public void onMouseEvent( String type, int x, int y )
    //------------------------------------------------------------
    {
        if( !m_fActive )
            return;
    
        // BUG: Not working for rotating bitmaps    
        Polygon hitRegion = (Polygon)m_properties.get( "_hitPoly" );
        if( hitRegion == null )
            return;
            
        if( hitRegion.contains( x, y ) )
        {
            // TODO: Should the event be consumed?
            
            if( type == "click" )
            {
                String onClick = getAttribString( "onclick", null, GA_INIT );
                if( onClick != null )
                {
                    evaluateScriptlet( onClick );
                }
            }
            else if( type == "move" && !m_properties.containsKey( "_hover" ) )
            {
                m_properties.put( "_hover", true );     
                String onEnter = getAttribString( "onenter", null, GA_INIT );
                if( onEnter != null )
                {
                    evaluateScriptlet( onEnter );
                }           
            }
        }
        else if( type == "move" && m_properties.containsKey( "_hover" ) )
        {
            m_properties.remove( "_hover" );
            String onExit = getAttribString( "onexit", null, GA_INIT );
            if( onExit != null )
            {
                evaluateScriptlet( onExit );
            }           
        }

        // Recurse, offset appropriately
        x += (int)getAttribLong( "_offsetX", 0, GA_INIT );
        y += (int)getAttribLong( "_offsetY", 0, GA_INIT );
        for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
        {
            // TODO: This descends in the same order as rendering; needs to be in reverse order
            // so top-most item gets the event first if we consume events
            ((Anim)e.nextElement()).onMouseEvent( type, x, y );
        }
        
    } // onMouseEvent
    
    
    //------------------------------------------------------------
    private void evaluateScriptlet( String script )
    //------------------------------------------------------------
    {
        // scriptlet ::= statement [ ";" scriptlet ]
        // statement ::= object "." member [ "=" parameter ]
        
        StringTokenizer statements = new StringTokenizer( script, ";" );
        while( statements.hasMoreTokens() )
        {
            StringTokenizer tokens = new StringTokenizer( statements.nextToken(), ".=" );
            if( tokens.countTokens() >= 2 )
            {
                String object = tokens.nextToken().trim();
                String event  = tokens.nextToken( ".=" ).trim();
                String param  = tokens.hasMoreTokens() ? tokens.nextToken( "" ).trim() : null;

                if( object == "window" )
                {   
                    if( event == "href" )
                    {
                        g_applet.getAppletContext().showDocument( getDocumentBase(), param );
                    }
                }
                else
                {
                    Anim target = (Anim)g_idTable.get( object );
                    if( target != null )
                    {
                        target.onEvent( event, param );
                        if( DEBUG ) System.out.println( "target: " + object + ", event: " + event + ", param: " + param );
                    }
                }
            }
        }
    } // evaluateScriptlet
    
    //------------------------------------------------------------
    public void onEvent( String eventName, String param )
    //------------------------------------------------------------
    {
        if( "start".equals( eventName ) )
        {
            resetTiming( true );
            double tmCur = getAttribDouble( "_tick", 1, GA_INIT );
            m_properties.put( "_forceBegin", Double.toString( tmCur ) );
            m_properties.put( "_status", "stopped" ); // For sounds
        }
        else if( "stop".equals( eventName ) )
        {
            double tmCur = getAttribDouble( "_tick", 1, GA_INIT );
            m_properties.put( "_forceEnd", Double.toString( tmCur ) );
        }
        
    } // onEvent


    //------------------------------------------------------------
    private void resetTiming( boolean fSelf )
    //------------------------------------------------------------
    {
        if( fSelf )
        {
            m_properties.put( "_forceBegin", Double.toString( Double.NEGATIVE_INFINITY ) );
            m_properties.put( "_forceEnd",   Double.toString( Double.NEGATIVE_INFINITY ) );
        }
        
        for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
                ((Anim)e.nextElement()).resetTiming( true );
    }

    //----------------------------------------------------------------------
    //
    // File Parser
    //
    //----------------------------------------------------------------------

    // Grammar:
    
    //    element   ::= whitespace* ( ( opentag element* closetag ) | emptytag )?
    //    emptytag  ::= '<' alphanumeric+ attribute* '/' '>'
    //    opentag   ::= '<' alphanumeric+ attribute* '>'
    //    closetag  ::= '<' '/' alphanumeric* '>'
    //    attribute ::= whitespace* (alphanumeric+ whitespace* '=' whitespace* '"' [^"]* '"')?
    
    // NOTE: To streamline parsing, the distinction between an
    //       open tag and an empty tag is handled by the same
    //       parsing function and distinguished by return value.
    
    // NOTE: Close tags names are not checked, and the name is optional
    
    public static final int TAG_ERROR   = 0;
    public static final int TAG_OPEN    = 1;
    public static final int TAG_EMPTY   = 2;
    
    //------------------------------------------------------------
    public boolean parseElement( PushbackReader reader ) throws Exception                                                         
    //------------------------------------------------------------
    {
        //    element   ::= whitespace* ( ( opentag element* closetag ) | emptytag )?
        
        char c;
        
        //----------------------------------------
        // ' '*
        while( Character.isWhitespace( c = (char)reader.read() ) );
        reader.unread( c );

        //----------------------------------------
        // opentag
        int tagType = parseTag( reader );
        if( tagType == TAG_ERROR )
            return false;

        {       
            // Determine the type
            String typeName = getAttribString( "_type", "", GA_INIT );
            if( g_typeHash.containsKey( typeName ) )
            {
                m_type = ( (Integer)g_typeHash.get( typeName ) ).intValue();
            }
            else
            {
                m_type = TYPE_UNKNOWN;
                if( DEBUG ) System.out.println( "Unknown tag: " + typeName + " (may be an extension)" );
            }
        }
        
        
        if( tagType == TAG_OPEN )
        {
            //----------------------------------------
            // element*
            for(;;)
            {
                Anim child  = new Anim();
                if( child.parseElement( reader ) )
                {
                    // If the child specifies a class name, instantiate and copy
                    // over all of the parsed data.
                    String className = child.getAttribString( "class", null, GA_INIT );
                    if( className != null )
                    {
                        if( DEBUG ) System.out.println( "Extension requested: " + className );
                        
                        Anim child2 = (Anim)Class.forName( className ).newInstance();
                        
                        // Use the current child as a prototype, and copy properties over
                        child2.m_type       = child.m_type;
                        child2.m_properties = child.m_properties;                       
                            
                        child2.m_children   = child.m_children;
                        for( Enumeration e = child2.m_children.elements(); e.hasMoreElements(); )
                            ((Anim)e.nextElement()).m_parent = child2;
                            
                        child = child2;
                    }
                    
                    // Add to parent/child tree
                    child.m_parent = this;
                    m_children.addElement( child );

                    // Add to the ID table
                    String id = child.getAttribString( "id", null, GA_INIT );
                    if( id != null )
                        g_idTable.put( id, child );
                }
                else
                {
                    break;
                }
            }
        
            //----------------------------------------
            // closetag
            parseCloseTag( reader );
        }
        
        return true;
        
    } // parseElement
    
    //------------------------------------------------------------
    public int parseTag( PushbackReader reader ) throws Exception
    //------------------------------------------------------------
    {
        //    tag   ::= '<' alphanumeric+ attribute* '/'? '>'

        char c;
        
        //----------------------------------------
        // '<'
        parse_expect( reader, '<' );

        
        // Distinguish between another open tag and a close tag; 
        // the grammar is ambiguous here so we have to rewind by hand
        if( parse_test(reader,'/') )
        {
            reader.unread('<');
            return TAG_ERROR;
        }
        
        //----------------------------------------
        // alphanumeric+
        StringBuffer nameBuf = new StringBuffer();
        while( Character.isLetterOrDigit( c = (char)reader.read() ) )
            nameBuf.append( c );
        String name = nameBuf.toString();
        reader.unread( c );
        
        m_properties.put( "_type", name );
        
        //----------------------------------------
        // attribute*
        while( parseAttribute( reader ) );
        
        //----------------------------------------
        // '/'? '>'
        if( parse_test( reader, '/' ) )
        {
            parse_expect( reader, '/' );
            parse_expect( reader, '>' );
            return TAG_EMPTY;
        }
        else
        {
            parse_expect( reader, '>' );
            return TAG_OPEN;
        }
        
    } // parseTag

    
    //------------------------------------------------------------
    public void parseCloseTag( PushbackReader reader ) throws Exception
    //------------------------------------------------------------
    {
        //    closetag  ::= '<' '/' alphanumeric* '>'       

        char c;
        
        //----------------------------------------
        // '<'
        parse_expect( reader, '<' );

        //----------------------------------------
        // '/'
        parse_expect( reader, '/' );

        //----------------------------------------
        // alphanumeric*
        while( Character.isLetterOrDigit( c = (char)reader.read() ) );
        reader.unread( c );
        
        //----------------------------------------
        // '>'
        parse_expect( reader, '>' );
        
    } // parseCloseTag
    
    
    //------------------------------------------------------------
    public boolean parseAttribute( PushbackReader reader ) throws Exception
    //------------------------------------------------------------
    {
        //    attribute ::= whitespace* (alphanumeric+ whitespace* '=' whitespace* '"' [^"]* '"')?
        
        char c;
        
        //----------------------------------------
        // ' '*
        while( Character.isWhitespace( c = (char)reader.read() ) );
        reader.unread( c );

        // Distinguish between an attribute and the end of a tag; 
        // the grammar is ambiguous here so we have to rewind by hand
        reader.unread( c = (char)reader.read() );
        if( !Character.isLetterOrDigit( c ) )
            return false;
        
        //----------------------------------------
        // alphanumeric+
        StringBuffer attribBuf = new StringBuffer();
        while( Character.isLetterOrDigit( c = (char)reader.read() ) )
            attribBuf.append( c );
        String attrib = attribBuf.toString();
        reader.unread( c );
        
        //----------------------------------------
        // ' '*
        while( Character.isWhitespace( c = (char)reader.read() ) );
        reader.unread( c );

        //----------------------------------------
        // '='
        parse_expect( reader, '=' );
        
        //----------------------------------------
        // ' '*
        while( Character.isWhitespace( c = (char)reader.read() ) );
        reader.unread( c );
        
        //----------------------------------------
        // '"'
        parse_expect( reader, '"' );
        
        //----------------------------------------
        // [^']*
        
        StringBuffer valueBuf = new StringBuffer();
        while( ( c = (char)reader.read() ) != '"' )
            valueBuf.append( c );
        String value = valueBuf.toString();
        reader.unread( c );
        
        //----------------------------------------
        // '"'
        parse_expect( reader, '"' );

        // Decode escaped characters
        value = stringReplace( value, "&quot;", "\"" );
        value = stringReplace( value, "&lt;",   "<"  );
        value = stringReplace( value, "&gt;",   ">"  );
        value = stringReplace( value, "&amp;",  "&"  );
        
        m_properties.put( attrib, value );
        return true;
        
    } // parseAttribute
    

    //------------------------------------------------------------
    public static void parse_expect( PushbackReader reader, char chExpect ) throws Exception
    //------------------------------------------------------------
    {
        char chRead = (char)reader.read();
        if( chRead != chExpect )
        {
            reader.unread( chRead );
            throw new Exception( "Expected \""+chExpect+"\", saw \""+chRead+"\"" );
        }
    } // parse_expect
    
    
    //------------------------------------------------------------
    public static boolean parse_test( PushbackReader reader, char chExpect ) throws IOException
    //------------------------------------------------------------
    {
        char chRead = (char)reader.read();
        reader.unread( chRead );
        return chRead == chExpect;
    } // parse_test


    //----------------------------------------------------------------------
    //
    // Attributes
    //
    //----------------------------------------------------------------------
    
    //------------------------------------------------------------
    public double getAttribDouble( String attrib, double defVal, int flags )
    //------------------------------------------------------------
    {
        // BUG: There's a null pointer dereference here on Applet Restart
        Hashtable properties = ((flags & GA_CURR)!=0) ? m_currentProperties : m_properties;
        try 
        { 
            if( properties.containsKey( attrib ) ) 
            {
                String value = (String)properties.get( attrib );
                return "Infinity".equals(value) 
                    ? Double.POSITIVE_INFINITY
                    : Double.valueOf( value ).doubleValue();
            }
        }
        catch( NumberFormatException e ) {}

        return defVal;
        
    } // getAttribDouble
    
    
    //------------------------------------------------------------
    public long getAttribLong( String attrib, long defVal, int flags )
    //------------------------------------------------------------
    {
        return Math.round( getAttribDouble( attrib, defVal, flags ) );
        
    } // getAttribLong
    
    
    //------------------------------------------------------------
    public String getAttribString( String attrib, String defVal, int flags )
    //------------------------------------------------------------
    {
        Hashtable properties = ((flags & GA_CURR)!=0) ? m_currentProperties : m_properties;
        if( properties.containsKey( attrib ) ) 
            return properties.get( attrib ).toString();
        
        return defVal;
        
    } // getAttribString

    
    //------------------------------------------------------------
    public Color getAttribColor( String attrib, Color defVal, int flags )
    //------------------------------------------------------------
    {
        Hashtable properties = ((flags & GA_CURR)!=0) ? m_currentProperties : m_properties;
        try
        {
            if( properties.containsKey( attrib ) ) 
            {
                String val = properties.get( attrib ).toString();
                return new Color( Integer.parseInt( val, 16 ) );
            }
        }
        catch( NumberFormatException e ) {}

        return defVal;
            
    } // getAttribColor
    
    
    //----------------------------------------------------------------------
    //
    // Utility Functions
    //
    //----------------------------------------------------------------------
    
    //------------------------------------------------------------
    public static String stringReplace( String orig, String match, String repl )
    //------------------------------------------------------------
    {
        int idx;
        while( -1 != ( idx = orig.indexOf( match ) ) )
            orig = orig.substring( 0, idx ) + repl + orig.substring( idx + match.length() );
        
        return orig;
    }
    
    
    // For evaluating Bezier curves; smaller = more points
    public static double TIME_STEP = 0.025;
    
    //------------------------------------------------------------
    public static void pathToPoints( String path, Vector xs, Vector ys )
    //------------------------------------------------------------
    {
        double curX = 0.0;
        double curY = 0.0;
        
        try
        {
            StringTokenizer tokens = new StringTokenizer( path, " \t\r\n,;()[]{}" );
            while( tokens.hasMoreTokens() )
            {
                String tok = tokens.nextToken();
                if( "m".equals(tok) || "l".equals(tok) )
                {
                    curX = Double.valueOf( tokens.nextToken() ).doubleValue();
                    curY = Double.valueOf( tokens.nextToken() ).doubleValue();
                    
                    xs.addElement( new Double( curX ) );
                    ys.addElement( new Double( curY ) );
                }
                else if( "c".equals(tok) || "v".equals(tok) )
                {
                    double x0 = curX;
                    double y0 = curY;
                    double x1 = Double.valueOf( tokens.nextToken() ).doubleValue();
                    double y1 = Double.valueOf( tokens.nextToken() ).doubleValue();
                    double x2 = Double.valueOf( tokens.nextToken() ).doubleValue();
                    double y2 = Double.valueOf( tokens.nextToken() ).doubleValue();
                    double x3 = Double.valueOf( tokens.nextToken() ).doubleValue();
                    double y3 = Double.valueOf( tokens.nextToken() ).doubleValue();

                    for( double t = TIME_STEP; t <= 1+TIME_STEP ; t += TIME_STEP )
                    {
                        // use Berstein polynomials 

                        //                 3                2         2               3  
                        // p(t)  =  (1 - t) p   +  3t(1 - t) p   +  3t (1 - t)p   +  t p 
                        //                   0                1                2        3

                        
                        curX =
                              (1-t)*(1-t)*(1-t)*x0
                              +
                              3*t*(1-t)*(1-t)*x1
                              +
                              3*t*t*(1-t)*x2
                              +
                              t*t*t*x3;
                        
                        curY = 
                              (1-t)*(1-t)*(1-t)*y0
                              +
                              3*t*(1-t)*(1-t)*y1
                              +
                              3*t*t*(1-t)*y2
                              +
                              t*t*t*y3;
                        
                        xs.addElement( new Double( curX ) );
                        ys.addElement( new Double( curY ) );
                    }
                    
                }
                else if( "x".equals(tok) || "e".equals(tok) )
                {
                    break;
                }
                else
                {
                    if( DEBUG ) System.out.println("Unknown curve token: " + tok );

                    break;
                }
            }
        }
        catch( NumberFormatException e ) 
        {
            if( DEBUG ) e.printStackTrace();
        }
        
    } // pathToPoints

    //------------------------------------------------------------
    public static Image rotateImage( Component comp, Image srcImg, double angle )
    //------------------------------------------------------------
    {
        if( angle == 0 )
            return srcImg;
        
        int dxSrc = srcImg.getWidth( null );
        int dySrc = srcImg.getHeight( null );

        double sa = Math.sin( angle );
        double ca = Math.cos( angle );
            
        int dxDst = (int)Math.round( Math.abs( ca * dxSrc ) + Math.abs( sa * dySrc ) );
        int dyDst = (int)Math.round( Math.abs( ca * dySrc ) + Math.abs( sa * dxSrc ) );
    
        int[] srcPixels = null;
        try
        {
            // PERF: Consider caching pixel array
            PixelGrabber g = new PixelGrabber( srcImg, 0, 0, dxSrc, dySrc, true );
            if( g.grabPixels() )
                srcPixels = (int[])g.getPixels();
            else
                return null;
        }
        catch( InterruptedException e )
        {
            e.printStackTrace();
            return null;
        }
        
        int[] dstPixels = new int[ dxDst * dyDst ];

        int index = 0;

        double cxSrc = dxSrc / 2;
        double cySrc = dySrc / 2;
        double cxDst = dxDst / 2;
        double cyDst = dyDst / 2;
        
        double xIncr =  ca;
        double yIncr = -sa;
        
        for( int y = 0; y < dyDst; y++ )
        {
            double xDst = 0 - cxDst;
            double yDst = y - cyDst;
            double xSrc = ( xDst * ca + yDst * sa ) + cxSrc;
            double ySrc = ( yDst * ca - xDst * sa ) + cySrc;
                
            for( int x = 0; x < dxDst; x++ )
            {
                xSrc += xIncr;
                ySrc += yIncr;

                // NOTE: Rounding would be more accurate, but slower

                int pxSrc = (int)xSrc;
                int pySrc = (int)ySrc;
                
                if( pxSrc < 0 || pySrc < 0 || pxSrc >= dxSrc || pySrc >= dySrc )
                {
                    dstPixels[ index++ ] = 0x00000000; // Transparent
                }
                else
                {
                    int idx = pxSrc + dxSrc * pySrc;
                    int argb = srcPixels[ idx ];
                    dstPixels[ index++ ] = argb;
                }
            }
        }
        

        return comp.createImage( new MemoryImageSource( dxDst, dyDst, dstPixels, 0, dxDst ) );
        
    } // rotateImage

    
    //------------------------------------------------------------
    public static Image chromaImage( Component comp, Image srcImg, Color fgColor )
    //------------------------------------------------------------
    {
        int dx = srcImg.getWidth( null );
        int dy = srcImg.getHeight( null );
        
        int[] pixels = null;
        try
        {
            PixelGrabber g = new PixelGrabber( srcImg, 0, 0, dx, dy, true );

            if( g.grabPixels() )
                pixels = (int[])g.getPixels();
            else
                return null;
        }
        catch( InterruptedException e )
        {
            e.printStackTrace();
            return null;
        }
        
        int rgb = fgColor.getRGB() & 0x00ffffff;
        int nPixels = pixels.length;
        for( int i = nPixels-1; i >= 0; i-- )
        {
            pixels[i] = ((pixels[i] & 0xff) << 24) | rgb;
        }
        

        return comp.createImage( new MemoryImageSource( dx, dy, pixels, 0, dx ) );
        
    } // chromaImage
    

    //------------------------------------------------------------
    public static Image createTransparentImage( Component comp, int width, int height )
    //------------------------------------------------------------
    {
        int nPixels = width * height;
        int[] dstPixels = new int[ nPixels ];
        for( int i = 0; i < nPixels; i++ )
        {
            dstPixels[ i ] = 0x00000000; 
        }

        return comp.createImage( new MemoryImageSource( width, height, dstPixels, 0, width ) );

    } // createTransparentImage

    

    //------------------------------------------------------------
    public static void rotateScalePoints( int[] xs, int[] ys, int nPoints, 
                                          double angle, double scale )
    //------------------------------------------------------------
    {
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        for( int i = 0; i < nPoints; i++ )
        {
            double x = xs[i] * scale;
            double y = ys[i] * scale;
                    
            xs[i] = (int)Math.round( x * ca + y * sa );
            ys[i] = (int)Math.round( y * ca - x * sa );
        }
        
    } // rotateScalePoints
    
    
    //------------------------------------------------------------
    public void dump( String prefix )
    //------------------------------------------------------------
    {
        if( DEBUG )
        {
            String tagName = m_properties.get( "_type" ).toString();
            System.out.println( prefix + "<" + tagName );
    
            for( Enumeration e = m_properties.keys(); e.hasMoreElements(); )
            {
                String key = (String)e.nextElement();
                System.out.println( prefix + "  " + key + "=\"" + m_properties.get(key) + "\"" );
            }
            
            System.out.println( prefix + ">" );
    
            for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
            {
                ((Anim)e.nextElement()).dump( prefix + "    " );
            }
            
            System.out.println( prefix + "</" + tagName + ">" );
        }
        
    } // dump
    
}

