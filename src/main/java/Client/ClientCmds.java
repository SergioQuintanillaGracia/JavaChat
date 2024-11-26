package Client;

class ClientCmds {
    static final String commandPrefix = "/";
    static final String EXIT_CMD = "exit";
    static final String CLEAR_SCREEN_CMD = "clear";
    static final String LOAD_MESSAGE_HISTORY_CMD = "load";

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
