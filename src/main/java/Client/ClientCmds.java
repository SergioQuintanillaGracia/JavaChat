package Client;

class ClientCmds {
    static String commandPrefix = "/";
    static String exitCommand = "exit";

    static boolean isCommand(String msg, String cmd) {
        String trimMsg = msg.trim();

        if (!trimMsg.startsWith("/") || trimMsg.length() < 2) return false;

        return trimMsg.substring(1).equals(cmd);
    }
}
