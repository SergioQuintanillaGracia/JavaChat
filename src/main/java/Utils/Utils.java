package Utils;

public class Utils {
    public static final int MIN_PORT_NUM = 2;
    public static final int MAX_PORT_NUM = 65535;

    public static boolean isValidPortString(String str) {
        try {
            int portNum = Integer.parseInt(str);
            return portNum >= MIN_PORT_NUM && portNum <= MAX_PORT_NUM;
        } catch (Exception e) {
            return false;
        }
    }
}
