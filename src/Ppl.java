import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Iterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.plaf.ComponentUI;

/**
 * @author Julien Cervelle (julien.cervelle@univ-mlv.fr)
 */
public class Ppl {

  private static class PplUI extends ComponentUI {

    static final PplUI INSTANCE = new PplUI();

    @Override
    public void paint(Graphics g, JComponent c) {
      ConcurrentLinkedQueue<Entry> items = ((JPpl) c).items;
      Graphics2D gg = (Graphics2D) g.create();
      gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
          RenderingHints.VALUE_ANTIALIAS_ON);
      gg.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND));
      try {
        for (Entry entry : items)
          entry.draw(gg);
      } finally {
        gg.dispose();
      }
    }
  }

  private static class Entry {
    private static int globalCount = 1;
    private final Shape shape;
    private final Color foreground, background;
    private final int count;

    Entry(Shape shape, Color foreground, Color background) {
      this.shape = shape;
      this.foreground = foreground;
      this.background = background;
      this.count = globalCount;
      globalCount++;
    }
    
    int getCount() {
      return count;
    }

    void draw(Graphics2D gg) {
      if (background != null) {
        gg.setColor(background);
        gg.fill(shape);
      }
      if (foreground != null) {
        gg.setColor(foreground);
        gg.draw(shape);
      }
    }

    private static String formatColor(Color color, String prefix) {
      if (color == null)
        return " " + prefix + "-opacity='0'";
      else
        return String.format(Locale.US,
            " %1$s-opacity='%5$f' %1$s='#%2$02x%3$02x%4$02x'", prefix, color
                .getRed(), color.getGreen(), color.getBlue(),
            color.getAlpha() / 256.);
    }

    private static final AffineTransform ID = new AffineTransform();

    private String formatPath() {
      StringBuilder builder = new StringBuilder();
      PathIterator i = shape.getPathIterator(ID);
      if (i.isDone())
        return null;
      double[] data = new double[6];
      while (!i.isDone()) {
        int element = i.currentSegment(data);
        switch (element) {
        case PathIterator.SEG_CLOSE:
          builder.append("Z ");
          break;
        case PathIterator.SEG_CUBICTO:
          builder.append("C ");
          data(data, 6, builder);
          break;
        case PathIterator.SEG_LINETO:
          builder.append("L ");
          data(data, 2, builder);
          break;
        case PathIterator.SEG_MOVETO:
          builder.append("M ");
          data(data, 2, builder);
          break;
        case PathIterator.SEG_QUADTO:
          builder.append("Q ");
          data(data, 4, builder);
          break;
        default:
          throw new IllegalStateException("Unknown path element " + element);
        }
        i.next();
      }
      builder.setLength(builder.length() - 1);
      return builder.toString();
    }

    private void data(double[] data, int length, StringBuilder builder) {
      for (int i = 0; i < length; i++)
        builder.append(String.format(Locale.US, "%f", data[i])).append(" ");
    }

    void print(PrintWriter out) {
      String path = formatPath();
      if (path == null)
        return;
      out.printf(Locale.US, "  <path%s%s d='%s'/>%n", formatColor(foreground,
          "stroke"), formatColor(background, "fill"), path);
    }
  }

  @SuppressWarnings("serial")
  private static class JPpl extends JComponent {

    ConcurrentLinkedQueue<Entry> items = new ConcurrentLinkedQueue<Entry>();

    JPpl(int width, int height) {
      setUI(PplUI.INSTANCE);
      setPreferredSize(new Dimension(width, height));
    }

    int add(Shape shape, Color foreground, Color background) {
      Entry entry = new Entry(shape, foreground, background);
      items.add(entry);
      repaint();
      return entry.getCount();
    }
    
    void removeItem(int index) {
      for(Iterator<Entry> iterator = items.iterator();iterator.hasNext();) {
        Entry entry = iterator.next();
        if (entry.getCount()==index) {
          iterator.remove();
          repaint();
          return;
        }
      }
      throw new NoSuchElementException();
    }
    
    void clear() {
      items.clear();
      repaint();
    }

    boolean save(OutputStream out) {
      OutputStreamWriter writer = new OutputStreamWriter(out, Charset
          .forName("UTF-8"));
      PrintWriter printer = new PrintWriter(writer);
      Dimension dimension = getPreferredSize();
      printer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      printer
          .printf(
              Locale.US,
              "<svg stroke-linecap=\"round\" stroke-linejoin=\"round\" width=\"%d\" height=\"%d\" xmlns=\"http://www.w3.org/2000/svg\">%n",
              dimension.width, dimension.height);
      for (Entry entry : items) {
        entry.print(printer);
      }
      printer.println("</svg>");
      printer.close();
      return !printer.checkError();
    }
    
  }

  static JPpl component = null;
  
  /**
   * Opens a drawing window. Any number of windows can be opened but 
   * only the last one can be drawn to.
   * @param s the title of the window.
   * @param x the abscissa of the upper left corner of the window.
   * @param y the ordinate of the upper left corner of the window.
   * @param w the width of the drawing area of the window.
   * @param h the height of the drawing area of the window.
   */
  public static void initDrawing(String s, int x, int y, int w, int h) {
    component = new JPpl(w, h);
    component.setOpaque(true);
    component.setBackground(Color.WHITE);
    JFrame frame = new JFrame(s);
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.add(component);
    frame.setLocation(x, y);
    frame.pack();
    frame.setVisible(true);
  }

  /**
   * Adds a shape to the window.
   * @param shape the shape to be added.
   * @param fg the stroke color.
   * @param bg the fill color.
   * @return the index of the shape.
   */
  public static int paint(Shape shape, Color fg, Color bg) {
    return component.add(shape, fg, bg);
  }
  
  /**
   * Removes a shape from the window. The index must be one returned from 
   * {@link #paint(Shape, Color, Color) paint}
   * or {@link #erase(Shape) erase} and must not have already been removed.
   * @param index the index of the shape.
   * @throws NoSuchElementException if no shape of such index is present.
   */
  public static void remove(int index) {
    component.removeItem(index);
  }
  
  /**
   * Removes all shapes from the window.
   */
  public static void clear() {
    component.clear();
  }

  /**
   * Adds a white shape to the window. It is more efficient to call {@link #remove(int) remove}.
   * @param shape the white shape to draw.
   * @return the index of the added shape.
   */
  public static int erase(Shape shape) {
    return paint(shape,Color.WHITE,Color.WHITE);
  }

  public static int drawLine(double x1, double y1, double x2, double y2) {
    return drawLine(x1, y1, x2, y2, Color.BLACK);
  }

  public static int drawLine(double x1, double y1, double x2, double y2,
      Color color) {
    return paint(new Line2D.Double(x1, y1, x2, y2), color, null);
  }

  public static int drawCircle(double cx, double cy, double r) {
    return paintCircle(cx, cy, r, Color.BLACK, null);
  }

  public static int paintCircle(double cx, double cy, double r) {
    return paintCircle(cx, cy, r, Color.BLACK, Color.BLACK);
  }

  public static int paintCircle(double cx, double cy, double r, Color fg,
      Color bg) {
    return paint(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), fg, bg);
  }

  public static int eraseCircle(double cx, double cy, double r) {
    return erase(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
  }

  private static final Object readMonitor = new Object();
  private static volatile Scanner scanner = scanner(new InputStreamReader(
      System.in));

  private static Scanner scanner(Reader in) {
    Scanner scanner = new Scanner(in);
    scanner.useLocale(Locale.US);
    return scanner;
  }

  /**
   * Reads an int from the input.
   * @return the int read.
   */
  public static int readInt() {
    synchronized (readMonitor) {
      return scanner.nextInt();
    }
  }
  
  /**
   * Tells whether an int is available on input
   * @return true if an int is available on input.
   */
  public static boolean hasNextInt() {
    synchronized (readMonitor) {
      return scanner.hasNextInt();
    }
  }

  /**
   * Reads a word (delimitted by spaces) from the input as an array of {@code char}.
   * @return the read word.
   */
  public static char[] readWordAsCharArray() {
    return readWord().toCharArray();
  }

  /**
   * Reads a word (delimitted by spaces) from the input as a Java {@code String}.
   * @return the read word.
   */
  public static String readWord() {
    synchronized (readMonitor) {
      return scanner.next();
    }
  }

  /**
   * Reads a double from the input.
   * @return the double read.
   */
  public static double readDouble() {
    synchronized (readMonitor) {
      return scanner.nextDouble();
    }
  }
  
  /**
   * Tells whether a double is available on input
   * @return true if a double is available on input.
   */
  public static boolean hasNextDouble() {
    synchronized (readMonitor) {
      return scanner.hasNextDouble();
    }
  }

  private static final Pattern DOT = Pattern.compile(".", Pattern.DOTALL);
  private static Pattern BLANKS = Pattern.compile("\\p{javaWhitespace}*");

  /**
   * Reads a single character from the input. Blanks are first skipped.
   * @return the character read.
   */
  public static char readChar() {
    synchronized (readMonitor) {
      scanner.skip(BLANKS);
      String match = scanner.findWithinHorizon(DOT,1);
      if (match==null)
        throw new NoSuchElementException();
      return match.charAt(0);
    }
  }

  /**
   * Test for availability of more input.
   * @return {@code true} is no more input is available.
   */
  public static boolean endOfInput() {
    synchronized (readMonitor) {
      return !scanner.hasNext();
    }
  }

  /**
   * Saves the drawing of the window in a file.
   * @param fileName the file name.
   */
  public static void save(String fileName) {
    try {
      FileOutputStream out = new FileOutputStream(fileName);
      component.save(out);
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Wait from an amount of time.
   * @param milliseconds the time in milliseconds.
   */
  public static void sleep(int milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (InterruptedException e) {
      return;
    }
  }
  
  /**
   * reads the whole content of a file and returns it as a char array
   * @param fileName the file name
   * @return the content of the file
   * @throws IllegalArgumentException when file does not exist or is not readable
   */
  public static char[] charFromFile(String fileName) {
    return charFromFile(fileName,Charset.defaultCharset());
  }
  
  /**
   * reads the whole content of a file and returns it as a char array
   * @param fileName the file name
   * @param charsetName the name of the charset (UTF-8, US-ASCII, latin1, latin9, ...)
   * @return the content of the file
   * @throws IllegalArgumentException when file does not exist or is not readable
   * @throws  UnsupportedCharsetException if no support for the named charset is available
   */
  public static char[] charFromFile(String fileName, String charsetName) {
    return charFromFile(fileName,Charset.forName(charsetName));
  }
  
  private static char[] charFromFile(String fileName, Charset charset) {
    File file = new File(fileName);
    long size = file.length();
    ByteBuffer buffer;
    FileInputStream stream;
    try {
      stream = new FileInputStream(file);
      buffer = stream.getChannel().map(MapMode.READ_ONLY, 0, size);
      stream.close();
    } catch (IOException e) {
      throw new IllegalArgumentException(e.getMessage());
    }
    CharBuffer charBuffer = charset.decode(buffer);
    char[] charArray = new char[charBuffer.limit()];
    charBuffer.get(charArray);
    return charArray;
  }
  
  /**
   * Throws an new exception with the given message
   * @param message the exception message
   */
  public static void failWith(String message) {
    PplException.failWith(message);
  }

  private static class PplException extends RuntimeException {

    private static final long serialVersionUID = 2201577704304517117L;

    private PplException(String message) {
      super(message);
    }
  
    static void failWith(String message) {
      PplException e = new PplException(message);
      StackTraceElement[] stackTrace = e.getStackTrace();
      StackTraceElement[] newStackTrace = new StackTraceElement[stackTrace.length-2];
      System.arraycopy(stackTrace,2,newStackTrace,0,newStackTrace.length);
      e.setStackTrace(newStackTrace);
      throw e;
    }

    @Override public String toString() {
      String message = getLocalizedMessage();
      if (message==null)
        return "Failure";
      else
        return "Failure: "+message;
    }
  }
}


