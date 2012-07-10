import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created with IntelliJ IDEA.
 * User: Paul
 * Date: 7/9/12
 * Time: 9:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class HelloWorld {
    public static void main(String[] args) throws IOException {
        BufferedReader buff = new BufferedReader(
                new InputStreamReader(System.in));
        System.out.print("What's your name? ");
        System.out.flush();
        String s = buff.readLine();
        System.out.printf("Hello, %s!", s);
    }
}
