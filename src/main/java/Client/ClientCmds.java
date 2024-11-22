package Client;

class ClientCmds {
    static final String commandPrefix = "/";
    static final String exitCommand = "exit";

    static boolean hasCommandPref(String msg) {
        String trimMsg = msg.trim();

        return trimMsg.startsWith(commandPrefix);
    }

    static String removeCommandPref(String msg) {
        return msg.substring(commandPrefix.length());
    }

    static boolean isCommand(String msg, String cmd) {
        String trimMsg = msg.trim();

        if (!hasCommandPref(msg) || trimMsg.length() < 2) return false;

        return trimMsg.substring(1).equals(cmd);
    }
}
