import java.awt.*;

public class Extension extends Anim
{
    public Extension() 
    {
    }
    
    public void onLoad() throws InterruptedException
    {
        super.onLoad();
    }
    
    public void onTick( double t )
    {
        super.onTick( t );
    }
    
    public void onPaint( Graphics g )
    {
        if( !m_fActive )
            return;

        int x       = (int)getAttribLong( "x",      0, GA_CURR );
        int y       = (int)getAttribLong( "y",      0, GA_CURR );
        int width   = (int)getAttribLong( "width",  0, GA_CURR );
        int height  = (int)getAttribLong( "height", 0, GA_CURR );

        // NOTE: this sample ignores these common properties
        //
        // double scale = getAttribDouble( "scale", 1.0, GA_CURR );
        // double angle = getAttribDouble( "angle", 0.0, GA_CURR ) * Math.PI / 180.0;

        Color lineColor = getAttribColor( "linecolor", Color.black, GA_CURR );
        Color fillColor = getAttribColor( "fillcolor", Color.white, GA_CURR );
        
        // NOTE: fillOval/drawOval has x, y as the center of the oval
        //
        if( fillColor != null )
        {
            g.setColor( fillColor );
            g.fillOval( x, y, width, height );
        }
        
        if( lineColor != null )
        {
            g.setColor( lineColor );
            g.drawOval( x, y, width, height );
        }
        
        // NOTE: for container elements, recurse
        //
        // for( Enumeration e = m_children.elements(); e.hasMoreElements(); )
        //   ((Anim)e.nextElement()).onPaint( g );
        // return;

        // NOTE: Don't need to call this
        // super.onPaint( g );
    }
    
    public void onMouseEvent( String type, int x, int y )
    {
        super.onMouseEvent( type, x, y );
    }
    
    public void onEvent( String event, String param ) 
    {
        super.onEvent( event, param );
    }
}