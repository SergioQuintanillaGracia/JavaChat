package Client;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import Protocol.Protocol;
import Protocol.Protocol.AuthData;
import Utils.Utils;
import org.jline.utils.InfoCmp;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static String address = "javachat.ddns.net";
    private static int port = 49200;
    private static String commandUsage = "Usage: 'java -jar client.jar (address) (port)'";
    private static final Object userInputLock = new Object();
    private static boolean userInputEnabled = false;
    private static boolean exit = false;
    private static String inputPrompt = "> ";
    private static String inputMsgPref = "[You]: ";
    private static String warningPref = "    [!] ";

    static PrintWriter output;

    public static void main(String[] args) {
        // Handle input arguments
        if (args.length > 2) {
            System.out.printf("Too many arguments. %s\n", commandUsage);
            return;
        }

        // If there is at least one argument, set `address` to the first one
        if (args.length >= 1) {
            address = args[0];

            // If a second argument is provided, set it as the port
            if (args.length == 2) {
                if (Utils.isValidPortString(args[1])) {
                    port = Integer.parseInt(args[1]);
                } else {
                    System.out.printf("Invalid port. Valid port number range: [%d, %d]\n%s\n",
                            Utils.MIN_PORT_NUM, Utils.MAX_PORT_NUM, commandUsage);
                    return;
                }
            }
        }

        Terminal terminal;
        LineReader lineReader;

        try {
            terminal = TerminalBuilder.builder().system(true).build();
            lineReader = LineReaderBuilder.builder().terminal(terminal).build();

        } catch (IOException e) {
            System.err.println("Error building terminal");
            System.err.println(e.getMessage());
            return;
        }

        try (Socket client = new Socket(address, port)) {
            System.out.printf("\n%sConnected to %s:%d\n", warningPref, address, port);

            try {
                output = new PrintWriter(client.getOutputStream(), true);

                // Create a thread to handle incoming messages while also being able to write
                ReadThread rt = new ReadThread(client, lineReader, output, terminal);
                rt.start();

                // Receive input messages from the user and send them to the server
                while (!exit) {
                    // Continue only if user input is enabled (which is disabled by default, and is only enabled
                    // when the server signals the user can type)
                    // This mechanism is used to allow the user to enter their username and password before they
                    // start typing normal messages in servers where authentication is enabled
                    synchronized (userInputLock) {
                        while (!userInputEnabled) {
                            try {
                                userInputLock.wait();
                            } catch (InterruptedException e) {
                                // The program was interrupted
                                return;
                            }
                        }
                    }

                    // Read a message from the user
                    String line = lineReader.readLine(inputPrompt).trim();
                    // Clear the terminal line above (that contains the message that was just inputted)
                    clearPrevLines(terminal, 1, 0);

                    if (line.isEmpty()) continue;

                    if (ClientCmds.hasCommandPref(line)) {
                        // The inputted message may be a command
                        handleCommand(line, terminal);
                    } else {
                        // The inputted message is a normal message, not a command
                        // Print the inputted message
                        terminalWrite(terminal, "%s%s\n".formatted(inputMsgPref, line));

                        // Send the message to the server
                        sendString(line);
                    }
                }

            } catch (IOException e) {
                System.err.println(e.getMessage());
            }

            System.out.printf("Disconnected from %s:%d\n", address, port);

        } catch (IOException e) {
            System.err.printf("I/O error occurred when trying to connect / when connected to %s:%d\n", address, port);
            System.err.println(e.getMessage());
        }
    }

    public static void handleCommand(String line, Terminal terminal) {
        String commandName = ClientCmds.removeCommandPref(line);

        // Handle the case where the message is just the command prefix
        if (commandName.isEmpty()) {
            terminalWrite(terminal, "%sType a command after %s to run it\n".formatted(warningPref,
                    ClientCmds.commandPrefix));
        }

        // Check if the inputted command exists, and run the corresponding code for it
        if (ClientCmds.isCommand(line, ClientCmds.EXIT_CMD)) {
            exit = true;

        } else if (ClientCmds.isCommand(line, ClientCmds.CLEAR_SCREEN_CMD)) {
            terminal.puts(InfoCmp.Capability.clear_screen);

        } else if (ClientCmds.isCommand(line, ClientCmds.LOAD_MESSAGE_HISTORY_CMD)) {
            sendString(Protocol.Client.LOAD_MESSAGE_HISTORY);

        } else {
            terminalWrite(terminal, "%sUnrecognized command: %s\n".formatted(warningPref, commandName));
        }
    }

    public static void sendString(String str) {
        output.printf("%s\r\n", str);
    }

    public static void enableUserInput() {
        synchronized (userInputLock) {
            userInputEnabled = true;
            userInputLock.notify();
        }
    }

    public static void disableUserInput() {
        synchronized (userInputLock) {
            userInputEnabled = false;
        }
    }

    public static void terminalWrite(Terminal terminal, String msg) {
        terminal.writer().print(msg);
        terminal.flush();
    }

    public static void clearPrevLines(Terminal terminal, int clearLineCount, int backDownLineCount) {
        for (int i = 0; i < clearLineCount; i++) {
            // Move the cursor up one line
            terminalWrite(terminal, "\033[1A");
            // Clear the line
            terminalWrite(terminal, "\033[2K");
        }

        // Move the cursor back down
        // 0 positions down is still interpreted as 1, so we only move the cursor if the value of `backDownLineCount`
        // is greater than 0
        if (backDownLineCount > 0) {
            terminalWrite(terminal, "\033[" + backDownLineCount + "B");
        }
    }

    public static void setInputMsgPref(String newPref) {
        inputMsgPref = newPref;
    }

    public static boolean askYesNo(String inputPrompt, Terminal terminal, LineReader lineReader) {
        String choice = "";
        boolean firstChoiceInput = true;

        while (!(choice.equals("y") || choice.equals("n"))) {
            if (!firstChoiceInput) {
                Client.clearPrevLines(terminal, 1, 0);
            }
            choice = lineReader.readLine(inputPrompt).trim().toLowerCase();
            firstChoiceInput = false;
        }

        return choice.equals("y");
    }
}


class ReadThread extends Thread {
    Socket client;
    Scanner input;
    LineReader lineReader;
    PrintWriter output;
    Terminal terminal;
    String username = "Unknown";
    String password = "";

    boolean showAuthInfo = true;
    boolean createNewUser = false;

    public ReadThread(Socket client, LineReader lineReader, PrintWriter output, Terminal terminal) throws IOException {
        this.client = client;

        try {
            this.input = new Scanner(client.getInputStream());

        } catch (IOException e) {
            // Throw an IOException with a custom message to inform there was an error getting the input stream
            throw new IOException("Couldn't get client input stream");
        }

        this.lineReader = lineReader;
        this.output = output;
        this.terminal = terminal;
    }

    @Override
    public void run() {
        while (input.hasNextLine()) {
            String line = input.nextLine();

            if (line.startsWith(Protocol.PROTOCOL_PREF)) {
                // The received string is a special request from the server
                handleProtocolRequest(line);
            } else {
                // The received string is a normal message
                lineReader.printAbove(line);
            }
        }
    }

    private void handleProtocolRequest(String req) {
        switch (req) {
            case Protocol.Server.AUTH_REQUEST -> {
                if (createNewUser) {
                    // The user decided to create a new user when they received the previous authentication request
                    // The server has sent a new authentication request which has to be handled differently for new user
                    // creation
                    Client.sendString(Protocol.Client.AUTH_CREATE_USER);
                    AuthData authData = new AuthData(username, password);
                    Client.sendString(authData.toString());

                    createNewUser = false;

                } else {
                    if (showAuthInfo) {
                        lineReader.printAbove("\nThis server has authentication enabled. Log in / register to " +
                                "enter the chat:\n");
                        showAuthInfo = false;
                    }

                    this.username = lineReader.readLine("$ Username: ");
                    this.password = lineReader.readLine("$ Password: ", '*');

                    Client.clearPrevLines(terminal, 2, 0);

                    // Create and send an `AuthData` object to the server
                    AuthData authData = new AuthData(username, password);
                    Client.sendString(authData.toString());
                }
            }

            case Protocol.Server.EMPTY_USER_OR_PASSWORD -> {
                System.out.println("This server doesn't allow empty usernames / passwords");
            }

            case Protocol.Server.USERNAME_OUT_OF_RANGE -> {
                System.out.printf("The username must be between %d and %d characters long.\n",
                        AuthData.MIN_USERNAME_LENGTH, AuthData.MAX_USERNAME_LENGTH);
            }

            case Protocol.Server.AUTH_WRONG_PASSWORD -> {
                System.out.printf("Wrong password for user %s\n", username);
            }

            case Protocol.Server.AUTH_USER_ALREADY_LOGGED -> {
                System.out.printf("User %s is already logged in\n", username);
            }

            case Protocol.Server.AUTH_USER_NOT_REGISTERED -> {
                System.out.printf("User %s is not registered\nDo you want to create it? (y/n)\n", username);
                createNewUser = Client.askYesNo("> ", terminal, lineReader);
                Client.clearPrevLines(terminal, 3, 0);
            }

            case Protocol.Server.USER_CREATION_SUCCESSFUL -> {
                System.out.printf("User %s was registered successfully\n", username);
            }

            case Protocol.Server.USER_CREATION_USER_ALREADY_EXISTS -> {
                System.out.printf("User %s already exists\n", username);
            }

            case Protocol.Server.AUTH_SUCCESSFUL -> {
                lineReader.printAbove("Successfully logged in as %s".formatted(username));
                Client.setInputMsgPref("[%s]: ".formatted(username));
            }

            case Protocol.Server.CAN_TYPE -> {
                Client.enableUserInput();
            }
        }
    }
}