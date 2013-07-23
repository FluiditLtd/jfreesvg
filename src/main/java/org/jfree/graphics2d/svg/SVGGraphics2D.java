/**
 * (C)opyright 2013, by Object Refinery Limited.  All rights reserved.
 */
package org.jfree.graphics2d.svg;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.AttributedCharacterIterator;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import org.jfree.graphics2d.GraphicsUtils;

/**
 * A Graphics2D implementation that creates SVG output.  Some implementation
 * notes:
 * <ul>
 * <li>Images are supported, but for methods with an ImageObserver parameter 
 * note that the observer is ignored completely.  In any case, using images
 * that are not fully loaded already would not be a good idea in the 
 * context of generating SVG data/files;</li>
 * 
 * <li>the getFontMetrics() and getFontRenderContext() methods return values
 * that come from an internal BufferedImage, this is a short-cut and we don't
 * know if there are any negative consequences (if you know of any, please
 * let us know and we'll add the info here);</li>
 * </ul>
 */
public class SVGGraphics2D extends Graphics2D {

    /** Rendering hints (see SVGHints). */
    private RenderingHints hints;
    
    private int width;
    
    private int height;
    
    /** 
     * The number of decimal places to use when writing the matrix values
     * for transformations. 
     */
    private int transformDP;
    
    private DecimalFormat transformFormat = new DecimalFormat("0.######");
    
    /**
     * The number of decimal places to use when writing coordinates for
     * geometrical shapes.
     */
    private int geometryDP;

    private DecimalFormat geometryFormat = new DecimalFormat("0.##");
    
    /** The buffer that accumulates the SVG output. */
    private StringBuilder sb;

    /** 
     * A map of all the gradients used, and the corresponding id.  When 
     * generating the SVG file, all the gradient paints used must be defined
     * in the defs element.
     */
    private Map<GradientPaintKey, String> gradientPaints 
            = new HashMap<GradientPaintKey, String>();
    
    /**
     * The clip paths that are used, and their reference ids. 
     */
    private Map<Shape, String> clipPaths = new HashMap<Shape, String>();
    
    /** 
     * A list of images that are referenced but not embedded in the SVG.
     * After the SVG is generated, the caller can make use of this list to
     * write PNG files if they don't already exist.  
     */
    private List<ImageElement> imageElements;
    
    /** The user clip (can be null). */
    private Shape clip;
    
    private Shape registeredClip;
    
    /** The current transform. */
    private AffineTransform transform = new AffineTransform();

    private Paint paint = Color.BLACK;
    
    private Color color = Color.BLACK;
    
    private Composite composite = AlphaComposite.getInstance(
            AlphaComposite.SRC_OVER, 1.0f);
    
    private Stroke stroke = new BasicStroke(1.0f);
    
    private Font font = new Font("SansSerif", Font.PLAIN, 12);
    
    /** The background color, used by clearRect(). */
    private Color background = Color.BLACK;

    /** A hidden image used for font metrics. */
    private BufferedImage fmImage = new BufferedImage(10, 10, 
            BufferedImage.TYPE_INT_RGB);;

    /**
     * An instance that is lazily instantiated in drawLine and then 
     * subsequently reused to avoid creating a lot of garbage.
     */
    private Line2D line;

    /**
     * An instance that is lazily instantiated in fillRect and then 
     * subsequently reused to avoid creating a lot of garbage.
     */
    Rectangle2D rect;

    /**
     * An instance that is lazily instantiated in draw/fillRoundRect and then
     * subsequently reused to avoid creating a lot of garbage.
     */
    private RoundRectangle2D roundRect;
    
    /**
     * An instance that is lazily instantiated in draw/fillOval and then
     * subsequently reused to avoid creating a lot of garbage.
     */
    private Ellipse2D oval;
 
    /**
     * An instance that is lazily instantiated in draw/fillArc and then
     * subsequently reused to avoid creating a lot of garbage.
     */
    private Arc2D arc;
 
    /** 
     * If the current paint is an instance of {@link GradientPaint}, this
     * field will contain the reference id that is used in the DEFS element
     * for that linear gradient.
     */
    private String gradientPaintRef = null;

    /**
     * Creates a new instance.
     * 
     * @param width  the width.
     * @param height  the height.
     */
    public SVGGraphics2D(int width, int height) {
        this.width = width;
        this.height = height;
        this.clip = null;
        this.imageElements = new ArrayList<ImageElement>();
        this.sb = new StringBuilder();
        this.hints = new RenderingHints(SVGHints.KEY_IMAGE_HANDLING, 
                SVGHints.VALUE_IMAGE_HANDLING_EMBED);
    }

    /**
     * Returns the width for the SVG element, specified in the constructor.
     * 
     * @return The width for the SVG element. 
     */
    public int getWidth() {
        return this.width;
    }
    
    /**
     * Returns the height for the SVG element, specified in the constructor.
     * 
     * @return The height for the SVG element. 
     */
    public int getHeight() {
        return this.height;
    }
    
    /**
     * Returns the number of decimal places used to write the transformation
     * matrices in the SVG output.  The default value is 6.
     * 
     * @return The number of decimal places.
     */
    public int getTransformDP() {
        return this.transformDP;    
    }
    
    /**
     * Sets the number of decimal places used to write the transformation
     * matrices in the SVG output.  Values in the range 1 to 10 will be used
     * to configure a formatter to that number of decimal places, for all other
     * values we revert to the normal String conversion of double primitives
     * (approximately 16 decimals places).
     * 
     * @param dp  the number of decimal places (normally 1 to 10). 
     */
    public void setTransformDP(int dp) {
        this.transformDP = dp;
        if (dp < 1 || dp > 10) {
            this.transformFormat = null;
            return;
        }
        this.transformFormat = new DecimalFormat("0." 
                + "##########".substring(0, dp));
    }
    
    /**
     * Returns the number of decimal places used to write the coordinates
     * of geometrical shapes.  The default value is 2.
     * 
     * @return The number of decimal places.
     */
    public int getGeometryDP() {
        return this.geometryDP;    
    }
    
    /**
     * Sets the number of decimal places used to write the coordinates of
     * geometrical shapes in the SVG output.  Values in the range 1 to 10 will 
     * be used to configure a formatter to that number of decimal places, for 
     * all other values we revert to the normal String conversion of double 
     * primitives (approximately 16 decimals places).
     * 
     * @param dp  the number of decimal places (normally 1 to 10). 
     */
    public void setGeometryDP(int dp) {
        this.geometryDP = dp;
        if (dp < 1 || dp > 10) {
            this.geometryFormat = null;
            return;
        }
        this.geometryFormat = new DecimalFormat("0." 
                + "##########".substring(0, dp));
    }
    
    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        throw new UnsupportedOperationException("Not supported yet."); //TODO
    }

    /**
     * Creates a new graphics object that is a copy of this graphics object
     * (except that it has not accumulated the drawing operations).  Not sure
     * yet when or why this would be useful when creating SVG output.
     * 
     * @return A new graphics object.
     */
    @Override
    public Graphics create() {
        SVGGraphics2D copy = new SVGGraphics2D(this.width, this.height);
        copy.setRenderingHints(getRenderingHints());
        copy.setClip(getClip());
        copy.setPaint(getPaint());
        copy.setColor(getColor());
        copy.setComposite(getComposite());
        copy.setStroke(getStroke());
        copy.setFont(getFont());
        copy.setTransform(getTransform());
        copy.setBackground(getBackground());
        return copy;
    }

    /**
     * Returns the paint.  The default value is {@link Color#BLACK}.
     * 
     * @return The paint (never <code>null</code>). 
     */
    @Override
    public Paint getPaint() {
        return this.paint;
    }
    
    /**
     * Sets the paint.  If you pass <code>null</code> to this method, it does 
     * nothing (in accordance with the JDK specification).
     * 
     * @param paint  the paint (<code>null</code> is permitted but ignored). 
     */
    @Override
    public void setPaint(Paint paint) {
        if (paint == null) {
            return;
        }
        this.paint = paint;
        this.gradientPaintRef = null;
        if (paint instanceof Color) {
            setColor((Color) paint);
        } else if (paint instanceof GradientPaint) {
            GradientPaint gp = (GradientPaint) paint;
            GradientPaintKey key = new GradientPaintKey(gp);
            String ref = this.gradientPaints.get(key);
            if (ref == null) {
                int count = this.gradientPaints.keySet().size();
                this.gradientPaints.put(key, "gp" + count);
                this.gradientPaintRef = "gp" + count;
            } else {
                this.gradientPaintRef = ref;
            }
        }
    }

    /**
     * Returns the foreground color.  This method exists for backwards
     * compatibility in AWT, you should use the {@link #getPaint()} method.
     * 
     * @return The foreground color (never <code>null</code>).
     * 
     * @see #getPaint() 
     */
    @Override
    public Color getColor() {
        return this.color;
    }

    /**
     * Sets the foreground color.  This method exists for backwards 
     * compatibility in AWT, you should use the 
     * {@link #setPaint(java.awt.Paint)} method.
     * 
     * @param c  the color (<code>null</code> permitted but ignored). 
     * 
     * @see #setPaint(java.awt.Paint) 
     */
    @Override
    public void setColor(Color c) {
        if (c == null) {
            return;
        }
        this.color = c;
        this.paint = c;
    }

    /**
     * Returns the background color.  The default value is Color.BLACK.
     * This is used by the {@link #clearRect(int, int, int, int)} method.
     * 
     * @return The background color (possibly <code>null</code>). 
     * 
     * @see #setBackground(java.awt.Color) 
     */
    @Override
    public Color getBackground() {
        return this.background;
    }

    /**
     * Sets the background color.  This is used by the 
     * {@link #clearRect(int, int, int, int)} method.  The reference 
     * implementation allows <code>null</code> for the background color so
     * we allow that too (but for that case, the clearRect method will do 
     * nothing).
     * 
     * @param color  the color (<code>null</code> permitted).
     */
    @Override
    public void setBackground(Color color) {
        this.background = color;
    }

    /**
     * Returns the current composite.
     * 
     * @return The current composite (never <code>null</code>).
     */
    @Override
    public Composite getComposite() {
        return this.composite;
    }
    
    /**
     * Sets the composite (only AlphaComposite is handled).
     * 
     * @param comp  the composite (<code>null<code> not permitted).
     */
    @Override
    public void setComposite(Composite comp) {
        if (comp == null) {
            throw new IllegalArgumentException("Null 'comp' argument.");
        }
        this.composite = comp;
    }

    /**
     * Returns the current stroke.
     * 
     * @return The current stroke (never <code>null</code>). 
     */
    @Override
    public Stroke getStroke() {
        return this.stroke;
    }

    /**
     * Sets the stroke (only BasicStroke is handled at present).
     * 
     * @param s  the stroke (<code>null</code> not permitted).
     */
    @Override
    public void setStroke(Stroke s) {
        if (s == null) {
            throw new IllegalArgumentException("Null 's' argument.");
        }
        this.stroke = s;
    }

    /**
     * Returns the current value for the specified hint.  See the 
     * {@link SVGHints} class for information about the hints that can be
     * used with this implementation.
     * 
     * @param hintKey  the hint key (<code>null</code> permitted, but the
     *     result will be <code>null</code> also).
     * 
     * @return The current value for the specified hint 
     *     (possibly <code>null</code).
     */
    @Override
    public Object getRenderingHint(RenderingHints.Key hintKey) {
        return this.hints.get(hintKey);
    }

    /**
     * Sets the value for a hint.    See the {@link SVGHints} class for 
     * information about the hints that can be used with this implementation.
     * 
     * @param hintKey  the hint key.
     * @param hintValue  the hint value.
     */
    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        this.hints.put(hintKey, hintValue);
    }

    /**
     * Returns a copy of the rendering hints.  Modifying the returned copy
     * will have no impact on the state of this Graphics2D instance.
     * 
     * @return The rendering hints (never <code>null</code>). 
     */
    @Override
    public RenderingHints getRenderingHints() {
        return (RenderingHints) this.hints.clone();
    }

    /**
     * Sets the rendering hints.
     * 
     * @param hints  the new set of hints (<code>null</code> not permitted).
     */
    @Override
    public void setRenderingHints(Map<?, ?> hints) {
        this.hints.clear();
        addRenderingHints(hints);
    }

    /**
     * Adds all the supplied hints.
     * 
     * @param hints  the hints (<code>null</code> not permitted).
     */
    @Override
    public void addRenderingHints(Map<?, ?> hints) {
        this.hints.putAll(hints);
    }

    /**
     * Draws the specified shape.  There is direct handling for Line2D,
     * Rectangle2D and Path2D.  All other shapes are mapped to a GeneralPath
     * and then drawn (effectively as Path2D objects).
     * 
     * @param s  the shape (<code>null</code> not permitted). 
     */
    @Override
    public void draw(Shape s) {
        if (s instanceof Line2D) {
            Line2D l = (Line2D) s;
            this.sb.append("<line x1=\"").append(geomDP(l.getX1()))
                    .append("\" y1=\"").append(geomDP(l.getY1()))
                    .append("\" x2=\"").append(geomDP(l.getX2()))
                    .append("\" y2=\"").append(geomDP(l.getY2()))
                    .append("\" ");
            this.sb.append("style=\"").append(strokeStyle()).append("\" ");
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append(getClipPathRef());
            this.sb.append("/>");
        } else if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            this.sb.append("<rect x=\"").append(geomDP(r.getX()))
                    .append("\" y=\"").append(geomDP(r.getY()))
                    .append("\" width=\"").append(geomDP(r.getWidth()))
                    .append("\" height=\"").append(geomDP(r.getHeight()))
                    .append("\" ");
            this.sb.append("style=\"").append(strokeStyle())
                    .append("; fill: none").append("\" ");
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append(getClipPathRef());
            this.sb.append("/>");
        } else if (s instanceof Path2D) {
            Path2D path = (Path2D) s;
            this.sb.append("<g style=\"").append(strokeStyle())
                    .append("; fill: none").append("\" ");
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append(getClipPathRef());
            this.sb.append(">");
            this.sb.append("<path ").append(getSVGPathData(path)).append("/>");
            this.sb.append("</g>");
        } else {
            draw(new GeneralPath(s)); // handled as a Path2D next time through
        }
    }

    /**
     * Fills the specified shape.  There is direct handling for Rectangle2D and
     * Path2D.  All other shapes are mapped to a GeneralPath and filled that 
     * way.
     * 
     * @param s  the shape (<code>null</code> not permitted). 
     */
    @Override
    public void fill(Shape s) {
        if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            if (r.isEmpty()) {
                return;
            }
            this.sb.append("<rect x=\"").append(geomDP(r.getX()))
                    .append("\" y=\"").append(geomDP(r.getY()))
                    .append("\" width=\"").append(geomDP(r.getWidth()))
                    .append("\" height=\"").append(geomDP(r.getHeight()))
                    .append("\" ");
            this.sb.append("style=\"").append(getSVGFillStyle()).append("\" ");
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append(getClipPathRef());
            this.sb.append("/>");
        } else if (s instanceof Path2D) {
            Path2D path = (Path2D) s;
            this.sb.append("<g style=\"").append(getSVGFillStyle());
            this.sb.append("; stroke: none").append("\" ");
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append(getClipPathRef());
            this.sb.append(">");
            this.sb.append("<path ").append(getSVGPathData(path)).append("/>");
            this.sb.append("</g>");
        }  else {
            fill(new GeneralPath(s));  // handled as a Path2D next time through
        }
    }
    
    /**
     * Creates an SVG path string for the supplied Java2D path.
     * 
     * @param path  the path (<code>null</code> not permitted).
     * 
     * @return An SVG path string. 
     */
    private String getSVGPathData(Path2D path) {
        StringBuilder b = new StringBuilder("d=\"");
        float[] coords = new float[6];
        double[] closePt = null;
        boolean first = true;
        PathIterator iterator = path.getPathIterator(null);
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coords);
            if (!first) {
                b.append(" ");
            }
            first = false;
            switch (type) {
            case (PathIterator.SEG_MOVETO):
                closePt = new double[2];
                closePt[0] = coords[0];
                closePt[1] = coords[1];
                b.append("M ").append(geomDP(coords[0])).append(" ")
                        .append(geomDP(coords[1]));
                break;
            case (PathIterator.SEG_LINETO):
                b.append("L ").append(geomDP(coords[0])).append(" ")
                        .append(geomDP(coords[1]));
                break;
            case (PathIterator.SEG_QUADTO):
                b.append("Q ").append(geomDP(coords[0]))
                        .append(" ").append(geomDP(coords[1]))
                        .append(" ").append(geomDP(coords[2]))
                        .append(" ").append(geomDP(coords[3]));
                break;
            case (PathIterator.SEG_CUBICTO):
                b.append("C ").append(geomDP(coords[0])).append(" ")
                        .append(geomDP(coords[1])).append(" ")
                        .append(geomDP(coords[2])).append(" ")
                        .append(geomDP(coords[3])).append(" ")
                        .append(geomDP(coords[4])).append(" ")
                        .append(geomDP(coords[5]));
                break;
            case (PathIterator.SEG_CLOSE):
                if (closePt != null) {
                    b.append("M ").append(geomDP(closePt[0])).append(" ")
                            .append(geomDP(closePt[1]));
                }
                break;
            default:
                break;
            }
            iterator.next();
        }  
        return b.append("\"").toString();
    }

    /**
     * Returns the current alpha (transparency) in the range 0.0 to 1.0.
     * If the current composite is an {@link AlphaComposite} we read the alpha
     * value from there, otherwise this method returns 1.0.
     * 
     * @return The current alpha (transparency) in the range 0.0 to 1.0.
     */
    private float getAlpha() {
       float alpha = 1.0f;
       if (this.composite instanceof AlphaComposite) {
           AlphaComposite ac = (AlphaComposite) this.composite;
           alpha = ac.getAlpha();
       }
       return alpha;
    }

    /**
     * Returns an SVG color string based on the current paint.  To handle
     * GradientPaint we rely on the setPaint() method having set the 
     * gradientPaintRef attribute.
     * 
     * @return An SVG color string. 
     */
    private String getSVGColor() {
        String result = "black;";
        if (this.paint instanceof Color) {
            return getSVGColor((Color) this.paint);
        } else if (this.paint instanceof GradientPaint) {
            return "url(#" + this.gradientPaintRef + ")";
        }
        return result;
    }
    
    /**
     * Returns the SVG RGB color string for the specified color.
     * 
     * @param c  the color.
     * 
     * @return The SVG RGB color string.
     */
    private String getSVGColor(Color c) {
        StringBuilder b = new StringBuilder("rgb(");
        b.append(c.getRed()).append(",").append(c.getGreen()).append(",")
                .append(c.getBlue()).append(")");
        return b.toString();
    }
    
    /**
     * Returns a stroke style string based on the current stroke and
     * alpha settings.
     * 
     * @return A stroke style string.
     */
    private String strokeStyle() {
        float strokeWidth = 1.0f;
        float[] dashArray = new float[0];
        if (this.stroke instanceof BasicStroke) {
            BasicStroke bs = (BasicStroke) this.stroke;
            strokeWidth = bs.getLineWidth();
            dashArray = bs.getDashArray();
        }
        StringBuilder b = new StringBuilder();
        b.append("stroke-width: ").append(strokeWidth).append(";");
        b.append("stroke: ").append(getSVGColor()).append(";");
        b.append("stroke-opacity: ").append(getAlpha()).append(";");
        if (dashArray != null && dashArray.length != 0) {
            b.append("stroke-dasharray: ");
            for (int i = 0; i < dashArray.length; i++) {
                if (i != 0) b.append(", ");
                b.append(dashArray[i]);
            }
            b.append(";");
        }
        return b.toString();
    }
    
    /**
     * Returns a fill style string based on the current paint and
     * alpha settings.
     * 
     * @return A fill style string.
     */
    private String getSVGFillStyle() {
        StringBuilder b = new StringBuilder();
        b.append("fill: ").append(getSVGColor()).append(";");
        b.append("fill-opacity: ").append(getAlpha());
        return b.toString();
    }

    /**
     * Returns the current font.
     * 
     * @return The current font (never <code>null</code>). 
     */
    @Override
    public Font getFont() {
        return this.font;
    }

    /**
     * Sets the current font.
     * 
     * @param font  the font (<code>null</code> is permitted but ignored). 
     */
    @Override
    public void setFont(Font font) {
        if (font == null) {
            return;
        }
        this.font = font;
    }
    
    /**
     * Returns a string containing font style info.
     * 
     * @return A string containing font style info.
     */
    private String getSVGFontStyle() {
         StringBuilder b = new StringBuilder();
         b.append("fill: ").append(getSVGColor()).append("; ");
         b.append("font-family: ").append(this.font.getFamily()).append("; ");
         b.append("font-size: ").append(this.font.getSize()).append("; ");
         if (this.font.isBold()) {
             b.append("font-weight: bold; ");
         }
         if (this.font.isItalic()) {
             b.append("font-style: italic; ");
         }
         return b.toString();
    }

    /**
     * Returns the font metrics for the specified font.
     * 
     * @param f  the font.
     * 
     * @return The font metrics. 
     */
    @Override
    public FontMetrics getFontMetrics(Font f) {
        return this.fmImage.createGraphics().getFontMetrics(f);
    }
    
    /**
     * Returns the font render context.  The implementation here returns the
     * FontRenderContext for an image that is maintained internally (as for
     * {@link #getFontMetrics}.
     * 
     * @return The font render context.
     */
    @Override
    public FontRenderContext getFontRenderContext() {
        return this.fmImage.createGraphics().getFontRenderContext();
    }

    /**
     * Draws a string at (x, y).
     * 
     * @param str  the string (<code>null</code> not permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * 
     * @see #drawString(java.lang.String, float, float) 
     */
    @Override
    public void drawString(String str, int x, int y) {
        drawString(str, (float) x, (float) y);
    }

    /**
     * Draws a string at (x, y).
     * 
     * @param str  the string (<code>null</code> not permitted).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    @Override
    public void drawString(String str, float x, float y) {
        if (str == null) {
            throw new NullPointerException("Null 'str' argument.");
        }
        this.sb.append("<g ");
        this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\">");
        this.sb.append("<text x=\"").append(geomDP(x))
                .append("\" y=\"").append(geomDP(y))
                .append("\"");
        this.sb.append(" style=\"").append(getSVGFontStyle()).append("\" ");
        this.sb.append(getClipPathRef());
        this.sb.append(">");
        this.sb.append(str).append("</text>");
        this.sb.append("</g>");
    }

    /**
     * Draws a string of attributed characters.  The call is delegated to
     * {@link #drawString(java.text.AttributedCharacterIterator, float, float)}. 
     * 
     * @param iterator  an iterator for the characters.
     * @param x  the x-coordinate.
     * @param y  the x-coordinate.
     */
    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        drawString(iterator, (float) x, (float) y); 
    }

    /**
     * Draws a string of attributed characters.  LIMITATION: in the current
     * implementation, the string is drawn but the formatting is ignored.
     * 
     * @param iterator  an iterator over the characters.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, 
            float y) {
        StringBuilder builder = new StringBuilder();
        int count = iterator.getEndIndex() - iterator.getBeginIndex();
        char c = iterator.first();
        for (int i = 0; i < count; i++) {
            builder.append(c);
            c = iterator.next();
        }
        drawString(builder.toString(), x, y);
    }

    /**
     * Draws the specified glyph vector at the location (x, y).
     * 
     * @param g  the glyph vector.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
        draw(g.getOutline(x, y));
    }

    /**
     * Translates the origin to <code>(tx, ty)</code>.  This call is delegated 
     * to {@link #translate(double, double)}.
     * 
     * @param tx  the x-translation.
     * @param ty  the y-translation.
     * 
     * @see #translate(double, double) 
     */
    @Override
    public void translate(int tx, int ty) {
        translate((double) tx, (double) ty);
    }

    /**
     * Applies the translation (tx, ty).
     * 
     * @param tx  the x-translation.
     * @param ty  the y-translation.
     */
    @Override
    public void translate(double tx, double ty) {
        AffineTransform t = getTransform();
        t.translate(tx, ty);
        setTransform(t);
    }

    /**
     * Applies a rotation (anti-clockwise) about <code>(0, 0)</code>.
     * 
     * @param theta  the rotation angle (in radians). 
     */
    @Override
    public void rotate(double theta) {
        AffineTransform t = getTransform();
        t.rotate(theta);
        setTransform(t);
    }

    /**
     * Applies a rotation (anti-clockwise) about <code>(x, y)</code>.
     * 
     * @param theta  the rotation angle (in radians).
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    @Override
    public void rotate(double theta, double x, double y) {
        translate(x, y);
        rotate(theta);
        translate(-x, -y);
    }

    /**
     * Applies a scale transformation.
     * 
     * @param sx  the x-scaling factor.
     * @param sy  the y-scaling factor.
     */
    @Override
    public void scale(double sx, double sy) {
        AffineTransform t = getTransform();
        t.scale(sx, sy);
        setTransform(t);
    }

    /**
     * Applies a shear transformation.
     * 
     * @param shx  the x-shear factor.
     * @param shy  the y-shear factor.
     */
    @Override
    public void shear(double shx, double shy) {
        AffineTransform t = AffineTransform.getShearInstance(shx, shy);
        transform(t);
    }

    /**
     * Applies this transform to the existing transform.
     * 
     * @param t  the transform (<code>null</code> not permitted). 
     */
    @Override
    public void transform(AffineTransform t) {
        AffineTransform tx = getTransform();
        tx.concatenate(t);
        setTransform(tx);
    }

    /**
     * Returns a copy of the current transform.
     * 
     * @return A copy of the current transform (never <code>null</code>).
     * 
     * @see #setTransform(java.awt.geom.AffineTransform) 
     */
    @Override
    public AffineTransform getTransform() {
        return (AffineTransform) this.transform.clone();
    }

    /**
     * Sets the transform.
     * 
     * @param t  the new transform (<code>null</code> permitted, resets to the
     *     identity transform).
     */
    @Override
    public void setTransform(AffineTransform t) {
        if (t == null) {
            this.transform = new AffineTransform();
        } else {
            this.transform = new AffineTransform(t);
        }
    }

    /**
     * Returns <code>true</code> if the rectangle (in device space) intersects
     * with the shape (the interior, if onStroke is false, otherwise the 
     * stroked outline of the shape).
     * 
     * @param rect  a rectangle (in device space).
     * @param s the shape.
     * @param onStroke  test the stroked outline only?
     * 
     * @return A boolean. 
     */
    @Override
    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        Shape ts;
        if (onStroke) {
            ts = this.transform.createTransformedShape(
                    this.stroke.createStrokedShape(s));
        } else {
            ts = this.transform.createTransformedShape(s);
        }
        if (!rect.getBounds2D().intersects(ts.getBounds2D())) {
            return false;
        }
        Area a1 = new Area(rect);
        Area a2 = new Area(ts);
        a1.intersect(a2);
        return !a1.isEmpty();
    }

    /**
     * This method does nothing in this SVGGraphics2D implementation.
     */
    @Override
    public void setPaintMode() {
        // do nothing
    }

    /**
     * This method does nothing in this SVGGraphics2D implementation.
     * 
     * @param c 
     */
    @Override
    public void setXORMode(Color c) {
        // do nothing
    }

    /**
     * Returns the clip bounds.
     * 
     * @return The clip bounds (possibly <code>null</code>). 
     */
    @Override
    public Rectangle getClipBounds() {
        if (this.clip == null) {
            return null;
        }
        return getClip().getBounds();
    }

    /**
     * Returns the user clipping region.  The initial default value is 
     * <code>null</code>.
     * 
     * @return The user clipping region (possibly <code>null</code>).
     * 
     * @see #setClip(java.awt.Shape) 
     */
    @Override
    public Shape getClip() {
        if (this.clip == null) {
            return null;
        }
        AffineTransform inv;
        try {
            inv = this.transform.createInverse();
            return inv.createTransformedShape(this.clip);
        } catch (NoninvertibleTransformException ex) {
            return null;
        }
    }

    /**
     * Sets the user clipping region.
     * 
     * @param shape  the new user clipping region (<code>null</code> permitted).
     */
    @Override
    public void setClip(Shape shape) {
        // null is handled fine here...
        this.clip = this.transform.createTransformedShape(shape);
        registerClip(shape);
    }
    
    /**
     * Registers the clip so that we can later write out all the clip 
     * definitions in the DEFS element.
     * 
     * @param clip  the clip (ignored if <code>null</code>) 
     */
    private void registerClip(Shape clip) {
        if (clip == null) {
            return;  // nothing to do
        }
        int count = this.clipPaths.size();
        Shape key = GraphicsUtils.copyOf(clip);
        this.clipPaths.put(key, "clip-" + count);
        this.registeredClip = key;
    }
    
    private String transformDP(double d) {
        if (this.transformFormat != null) {
            return transformFormat.format(d);            
        } else {
            return String.valueOf(d);
        }
    }
    
    private String geomDP(double d) {
        if (this.geometryFormat != null) {
            return geometryFormat.format(d);            
        } else {
            return String.valueOf(d);
        }
    }
    
    private String getSVGTransform(AffineTransform t) {
        StringBuilder b = new StringBuilder("matrix(");
        b.append(transformDP(t.getScaleX())).append(",");
        b.append(transformDP(t.getShearY())).append(",");
        b.append(transformDP(t.getShearX())).append(",");
        b.append(transformDP(t.getScaleY())).append(",");
        b.append(transformDP(t.getTranslateX())).append(",");
        b.append(transformDP(t.getTranslateY())).append(")");
        return b.toString();
    }

    /**
     * Clips to the intersection of the current clipping region and the
     * specified shape. 
     * 
     * According to the Oracle API specification, this method will accept a 
     * <code>null</code> argument, but there is an open bug report (since 2004) 
     * that suggests this is wrong:
     * 
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6206189
     * 
     * @param s  the clip shape (<code>null</code> not permitted). 
     */
    @Override
    public void clip(Shape s) {
        if (this.clip == null) {
            setClip(s);
            return;
        }
        Shape ts = this.transform.createTransformedShape(s);
        if (!ts.intersects(this.clip.getBounds2D())) {
            setClip(new Rectangle2D.Double());
        } else {
          Area a1 = new Area(ts);
          Area a2 = new Area(this.clip);
          a1.intersect(a2);
          this.clip = new Path2D.Double(a1);
        }
        registerClip(s);  // SVG is expecting the non-transformed clip area
    }

    /**
     * Clips to the intersection of the current clipping region and the 
     * specified rectangle.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    @Override
    public void clipRect(int x, int y, int width, int height) {
        setRect(x, y, width, height);
        clip(this.rect);
    }

    /**
     * Sets the user clipping region to the specified rectangle.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * 
     * @see #getClip() 
     */
    @Override
    public void setClip(int x, int y, int width, int height) {
        setRect(x, y, width, height);
        setClip(this.rect);
    }

    /**
     * Draws a line from (x1, y1) to (x2, y2).
     * 
     * @param x1  the x-coordinate of the start point.
     * @param y1  the y-coordinate of the start point.
     * @param x2  the x-coordinate of the end point.
     * @param y2  the x-coordinate of the end point.
     */
    @Override
    public void drawLine(int x1, int y1, int x2, int y2) {
        if (this.line == null) {
            this.line = new Line2D.Double(x1, y1, x2, y2);
        } else {
            this.line.setLine(x1, y1, x2, y2);
        }
        draw(this.line);
    }

    /**
     * Fills a rectangle with the current paint.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the rectangle width.
     * @param height  the rectangle height.
     */
    @Override
    public void fillRect(int x, int y, int width, int height) {
        setRect(x, y, width, height);
        fill(this.rect);
    }

    /**
     * Fills the specified rectangle with the current background color.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * 
     * @see #getBackground() 
     */
    @Override
    public void clearRect(int x, int y, int width, int height) {
        if (getBackground() == null) {
            return;  // we can't do anything
        }
        Paint saved = getPaint();
        setPaint(getBackground());
        fillRect(x, y, width, height);
        setPaint(saved);
    }
    
    /**
     * Draws a rectangle with rounded corners.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc-width.
     * @param arcHeight  the arc-height.
     */
    @Override
    public void drawRoundRect(int x, int y, int width, int height, 
            int arcWidth, int arcHeight) {
        setRoundRect(x, y, width, height, arcWidth, arcHeight);
        draw(this.roundRect);
    }

    /**
     * Fills a rectangle with rounded corners.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc-width.
     * @param arcHeight  the arc-height.
     */
    @Override
    public void fillRoundRect(int x, int y, int width, int height, 
            int arcWidth, int arcHeight) {
        setRoundRect(x, y, width, height, arcWidth, arcHeight);
        fill(this.roundRect);
    }

    /**
     * Draws an oval framed by the rectangle (x, y, width, height).
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    @Override
    public void drawOval(int x, int y, int width, int height) {
        setOval(x, y, width, height);
        draw(this.oval);
    }

    /**
     * Fills an oval framed by the rectangle (x, y, width, height).
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    @Override
    public void fillOval(int x, int y, int width, int height) {
        setOval(x, y, width, height);
        fill(this.oval);
    }

    /**
     * Draws an arc.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     */
    @Override
    public void drawArc(int x, int y, int width, int height, int startAngle, 
            int arcAngle) {
        setArc(x, y, width, height, startAngle, arcAngle);
        draw(this.arc);
    }

    /**
     * Fills an arc.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     */
    @Override
    public void fillArc(int x, int y, int width, int height, int startAngle, 
            int arcAngle) {
        setArc(x, y, width, height, startAngle, arcAngle);
        fill(this.arc);
    }

    /**
     * A utility method used to create a polygon for rendering.
     * 
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polyline.
     * @param close  closed?
     * 
     * @return A polygon.
     */
    private GeneralPath createPolygon(int[] xPoints, int[] yPoints, 
            int nPoints, boolean close) {
        GeneralPath p = new GeneralPath();
        p.moveTo(xPoints[0], yPoints[0]);
        for (int i = 1; i < nPoints; i++) {
            p.lineTo(xPoints[i], yPoints[i]);
        }
        if (close) {
            p.closePath();
        }
        return p;
    }
    
    /**
     * Draws the specified multi-segment line.
     * 
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polyline.
     */
    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        GeneralPath p = createPolygon(xPoints, yPoints, nPoints, false);
        draw(p);
    }

    /**
     * Draws the specified polygon.
     * 
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polygon.
     */
    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        GeneralPath p = createPolygon(xPoints, yPoints, nPoints, true);
        draw(p);
    }

    /**
     * Fills the specified polygon.
     * 
     * @param xPoints  the x-points.
     * @param yPoints  the y-points.
     * @param nPoints  the number of points to use for the polygon.
     */
    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        GeneralPath p = createPolygon(xPoints, yPoints, nPoints, true);
        fill(p);
    }

    /**
     * Returns the bytes representing a PNG format image.
     * 
     * @param img  the image to encode.
     * 
     * @return The bytes representing a PNG format image. 
     */
    private byte[] getPNGBytes(Image img) {
        RenderedImage ri;
        if (img instanceof RenderedImage) {
            ri = (RenderedImage) img;
        } else {
            BufferedImage bi = new BufferedImage(img.getWidth(null), 
                    img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.drawImage(img, 0, 0, null);
            ri = bi;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(ri, "png", baos);
        } catch (IOException ex) {
            Logger.getLogger(SVGGraphics2D.class.getName()).log(Level.SEVERE, 
                    "IOException while writing PNG data.", ex);
        }
        return baos.toByteArray();
    }  
    
    /**
     * Draws an image.
     * 
     * @param img  the image.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param observer  ignored.
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        int w = img.getWidth(observer);
        if (w < 0) {
            return false;
        }
        int h = img.getHeight(observer);
        if (h < 0) {
            return false;
        }
        return drawImage(img, x, y, w, h, observer);
    }

    /**
     * Draws the image into the specified rectangle defined by x, y, w and h.
     * The observer is ignored (it is not useful in this context).
     * 
     * @param img  the image.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param w  the width.
     * @param h  the height.
     * @param observer  ignored.
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, 
            ImageObserver observer) {
        
        // the rendering hints control whether the image is embedded or
        // referenced...
        Object hint = this.getRenderingHint(SVGHints.KEY_IMAGE_HANDLING);
        if (SVGHints.VALUE_IMAGE_HANDLING_EMBED.equals(hint)) {
            this.sb.append("<image xlink:href=\"data:image/png;base64,");
            this.sb.append(DatatypeConverter.printBase64Binary(getPNGBytes(
                    img)));
            this.sb.append("\" ");
            String clipRef = this.clipPaths.get(this.registeredClip);
            if (clipRef != null) {
                this.sb.append("clip-path=\"url(#").append(clipRef)
                        .append(")\" ");
            }
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");            
            this.sb.append("x=\"").append(geomDP(x))
                    .append("\" y=\"").append(geomDP(y))
                    .append("\" ");
            this.sb.append("width=\"").append(geomDP(w)).append("\" height=\"")
                    .append(geomDP(h)).append("\"/>\n");
            return true;
        } else { // here for SVGHints.VALUE_IMAGE_HANDLING_REFERENCE
            int count = this.imageElements.size();
            String fileName = "image-" + count + ".png";
            ImageElement imageElement = new ImageElement(fileName, img);
            this.imageElements.add(imageElement);
            // write an SVG element for the img
            this.sb.append("<image xlink:href=\"");
            this.sb.append(fileName).append("\" ");
            String clipRef = this.clipPaths.get(this.registeredClip);
            if (clipRef != null) {
                this.sb.append("clip-path=\"url(#").append(clipRef)
                        .append(")\" ");
            }
            this.sb.append("transform=\"").append(getSVGTransform(
                    this.transform)).append("\" ");
            this.sb.append("x=\"").append(geomDP(x))
                    .append("\" y=\"").append(geomDP(y))
                    .append("\" ");
            this.sb.append("width=\"").append(geomDP(w)).append("\" height=\"")
                    .append(geomDP(h)).append("\"/>\n");
            return true;
        }
    }

    /**
     * Draws an image.
     * 
     * @param img  the image.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param bgcolor  the background color.
     * @param observer  ignored.
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, int x, int y, Color bgcolor, 
            ImageObserver observer) {
        int w = img.getWidth(null);
        if (w < 0) {
            return false;
        }
        int h = img.getHeight(null);
        if (h < 0) {
            return false;
        }
        return drawImage(img, x, y, w, h, bgcolor, observer);
    }

    /**
     * Draws an image to the rectangle (x, y, w, h), first filling the 
     * background with the specified color.
     * 
     * @param img  the image.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param w  the width.
     * @param h  the height.
     * @param bgcolor  the background color.
     * @param observer  ignored.
     * 
     * @return {@code true} if the image is drawn.      
     */
    @Override
    public boolean drawImage(Image img, int x, int y, int w, int h, 
            Color bgcolor, ImageObserver observer) {
        Paint saved = getPaint();
        setPaint(bgcolor);
        fillRect(x, y, w, h);
        setPaint(saved);
        return drawImage(img, x, y, w, h, observer);
    }

    /**
     * Draws an image.
     * 
     * @param img  the image.
     * @param dx1  the x-coordinate for the top left of the destination.
     * @param dy1  the y-coordinate for the top left of the destination.
     * @param dx2  the x-coordinate for the bottom right of the destination.
     * @param dy2  the y-coordinate for the bottom right of the destination.
     * @param sx1 the x-coordinate for the top left of the source.
     * @param sy1 the y-coordinate for the top left of the source.
     * @param sx2 the x-coordinate for the bottom right of the source.
     * @param sy2 the y-coordinate for the bottom right of the source.
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, 
            int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        int w = dx2 - dx1;
        int h = dy2 - dy1;
        BufferedImage img2 = new BufferedImage(BufferedImage.TYPE_INT_ARGB, 
                w, h);
        Graphics2D g2 = img2.createGraphics();
        g2.drawImage(img, 0, 0, w, h, sx1, sy1, sx2, sy2, null);
        return drawImage(img2, dx1, dx2, null);
    }

    /**
     * Draws an image.
     * 
     * @param img  the image.
     * @param dx1  the x-coordinate for the top left of the destination.
     * @param dy1  the y-coordinate for the top left of the destination.
     * @param dx2  the x-coordinate for the bottom right of the destination.
     * @param dy2  the y-coordinate for the bottom right of the destination.
     * @param sx1 the x-coordinate for the top left of the source.
     * @param sy1 the y-coordinate for the top left of the source.
     * @param sx2 the x-coordinate for the bottom right of the source.
     * @param sy2 the y-coordinate for the bottom right of the source.
     * @param bgcolor  the background color.
     * @param observer  ignored.
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, 
            int sx1, int sy1, int sx2, int sy2, Color bgcolor, 
            ImageObserver observer) {
        Paint saved = getPaint();
        setPaint(bgcolor);
        fillRect(dx1, dy1, dx2 - dx1, dy2 - dy1);
        setPaint(saved);
        return drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }

    /**
     * Draws the rendered image.
     * 
     * @param img  the image.
     * @param xform  the transform.
     */
    @Override
    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        BufferedImage bi = GraphicsUtils.convertRenderedImage(img);
        drawImage(bi, xform, null);
    }

    /**
     * Draws the renderable image.
     * 
     * @param img  the renderable image.
     * @param xform  the transform.
     */
    @Override
    public void drawRenderableImage(RenderableImage img, 
            AffineTransform xform) {
        RenderedImage ri = img.createDefaultRendering();
        drawRenderedImage(ri, xform);
    }

    /**
     * Draws an image with the specified transform.
     * 
     * @param img  the image.
     * @param xform  the transform.
     * @param obs  the image observer (ignored).
     * 
     * @return {@code true} if the image is drawn. 
     */
    @Override
    public boolean drawImage(Image img, AffineTransform xform, 
            ImageObserver obs) {
        AffineTransform savedTransform = getTransform();
        transform(xform);
        boolean result = drawImage(img, 0, 0, obs);
        setTransform(savedTransform);
        return result;
    }

    /**
     * Draws the image resulting from applying the BufferedImageOp to the 
     * specified image.
     * 
     * @param img  the image.
     * @param op  the operation.
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     */
    @Override
    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        BufferedImage imageToDraw = op.filter(img, null);
        drawImage(imageToDraw, new AffineTransform(1f, 0f, 0f, 1f, x, y), null);
    }

    /**
     * This method does nothing.  The operation assumes that the output is in 
     * bitmap form, which is not the case for SVG, so we silently ignore
     * this method call.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width of the area.
     * @param height  the height of the area.
     * @param dx  the delta x.
     * @param dy  the delta y.
     */
    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        // do nothing, this operation is silently ignored.
    }

    /**
     * This method does nothing - there is nothing to dispose.
     */
    @Override
    public void dispose() {
        // nothing to do
    }

    /**
     * Returns the SVG element that has been generated by calls to this 
     * Graphics2D implementation.
     * 
     * @return The SVG element.
     */
    public String getSVGElement() {
        StringBuilder svg = new StringBuilder("<svg width=\"").append(width)
                .append("\" height=\"").append(height).append("\">\n");
        StringBuilder defs = new StringBuilder("<defs>");
        for (GradientPaintKey key : this.gradientPaints.keySet()) {
            defs.append(getLinearGradientElement(this.gradientPaints.get(key), 
                    key.getPaint()));
            defs.append("\n");
        }
        for (Shape s : this.clipPaths.keySet()) {
            defs.append(getClipPathElement(this.clipPaths.get(s), s));
            defs.append("\n");
        }
        defs.append("</defs>\n");
        svg.append(defs);
        svg.append(this.sb);
        svg.append("</svg>");        
        return svg.toString();
    }
    
    /**
     * Returns an SVG document.
     * 
     * @return An SVG document.
     */
    public String getSVGDocument() {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\"?>\n");
        b.append("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.0//EN\" ");
        b.append("\"http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd\">\n");
        b.append(getSVGElement());
        return b.append("\n").toString();
    }
    
    /**
     * Returns the list of image elements that have been referenced in the 
     * SVG output but not embedded.  If the image files don't already exist,
     * you can use this list as the basis for creating the image files.
     * 
     * @return The list of image elements.
     * 
     * @see SVGHints#KEY_IMAGE_HANDLING
     */
    public List<ImageElement> getSVGImages() {
        return this.imageElements;
    }
    
    private static final double EPSILON = 0.00000001;
    
    /**
     * Returns an element to represent a linear gradient.  All the linear
     * gradients that are used get written to the DEFS element in the SVG.
     * 
     * @param id  the reference id.
     * @param paint  the gradient.
     * 
     * @return The SVG element.
     */
    private String getLinearGradientElement(String id, GradientPaint paint) {
        StringBuilder b = new StringBuilder("<lineargradient id=\"").append(id)
                .append("\" ");
        Point2D p1 = paint.getPoint1();
        Point2D p2 = paint.getPoint2();
        boolean h = Math.abs(p1.getX() - p2.getX()) > EPSILON;
        boolean v = Math.abs(p1.getY() - p2.getY()) > EPSILON;
        b.append("x1=\"").append(h ? "0%" : "50%").append("\" ");
        b.append("y1=\"").append(v ? "0%" : "50%").append("\" ");
        b.append("x2=\"").append(h ? "100%" : "50%").append("\" ");
        b.append("y2=\"").append(v ? "100%" : "50%").append("\">");
        b.append("<stop offset=\"0%\" style=\"stop-color: ").append(
                getSVGColor(paint.getColor1())).append(";\"/>");
        b.append("<stop offset=\"100%\" style=\"stop-color: ").append(
                getSVGColor(paint.getColor2())).append(";\"/>");
        return b.append("</lineargradient>").toString();
    }
    
    /**
     * Returns an element to represent a clip path.  All the clip paths that
     * are used are written to the DEFS element in the SVG.
     * 
     * @param refID  the reference id.
     * @param s  the clip region.
     * 
     * @return The SVG element.
     */
    private String getClipPathElement(String refID, Shape s) {
        StringBuilder b = new StringBuilder("<clipPath id=\"").append(refID)
                .append("\">");
        b.append("<path ").append(getSVGPathData(new Path2D.Double(s)))
                .append("/>");
        return b.append("</clipPath>").toString();
    }

    /**
     * Returns a clip path reference for the current user clip.  This is 
     * written out on all SVG elements that draw or fill shapes or text.
     * 
     * @return A clip path reference. 
     */
    private String getClipPathRef() {
        StringBuilder b = new StringBuilder();
        String clipRef = this.clipPaths.get(this.registeredClip);
        if (clipRef != null) {
            b.append("clip-path=\"url(#").append(clipRef).append(")\"");
        }
        return b.toString();
    }
    
    /**
     * Sets the attributes of the reusable {@link Rectangle2D} object that is
     * used by the {@link SVGGraphics2D#drawRect(int, int, int, int)} and 
     * {@link SVGGraphics2D#fillRect(int, int, int, int)} methods.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    private void setRect(int x, int y, int width, int height) {
        if (this.rect == null) {
            this.rect = new Rectangle2D.Double(x, y, width, height);
        } else {
            this.rect.setRect(x, y, width, height);
        }
    }
    
    /**
     * Sets the attributes of the reusable {@link RoundRectangle2D} object that
     * is used by the {@link #drawRoundRect(int, int, int, int, int, int)} and
     * {@link #fillRoundRect(int, int, int, int, int, int)} methods.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param arcWidth  the arc width.
     * @param arcHeight  the arc height.
     */
    private void setRoundRect(int x, int y, int width, int height, int arcWidth, 
            int arcHeight) {
        if (this.roundRect == null) {
            this.roundRect = new RoundRectangle2D.Double(x, y, width, height, 
                    arcWidth, arcHeight);
        } else {
            this.roundRect.setRoundRect(x, y, width, height, 
                    arcWidth, arcHeight);
        }        
    }

    /**
     * Sets the attributes of the reusable {@link Arc2D} object that is used by
     * {@link #drawArc(int, int, int, int, int, int)} and 
     * {@link #fillArc(int, int, int, int, int, int)} methods.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     * @param startAngle  the start angle in degrees, 0 = 3 o'clock.
     * @param arcAngle  the angle (anticlockwise) in degrees.
     */
    private void setArc(int x, int y, int width, int height, int startAngle, 
            int arcAngle) {
        if (this.arc == null) {
            this.arc = new Arc2D.Double(x, y, width, height, startAngle, 
                    arcAngle, Arc2D.OPEN);
        } else {
            this.arc.setArc(x, y, width, height, startAngle, arcAngle, 
                    Arc2D.OPEN);
        }        
    }
    
    /**
     * Sets the attributes of the reusable {@link Ellipse2D} object that is 
     * used by the {@link #drawOval(int, int, int, int)} and
     * {@link #fillOval(int, int, int, int)} methods.
     * 
     * @param x  the x-coordinate.
     * @param y  the y-coordinate.
     * @param width  the width.
     * @param height  the height.
     */
    private void setOval(int x, int y, int width, int height) {
        if (this.oval == null) {
            this.oval = new Ellipse2D.Double(x, y, width, height);
        } else {
            this.oval.setFrame(x, y, width, height);
        }
    }

}
