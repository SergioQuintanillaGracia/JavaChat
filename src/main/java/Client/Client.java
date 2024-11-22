package Client;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final int MIN_PORT_NUM = 2;
    private static final int MAX_PORT_NUM = 65535;
    private static String address = "javachat.ddns.net";
    private static int port = 49200;
    private static String inputPrompt = "> ";
    private static String inputMsgPref = "[You]: ";
    private static String warningPref = "    [!] ";
    private static String commandUsage = "Usage: 'java -jar client.jar (address) (port)'";

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
                if (isValidPortString(args[1])) {
                    port = Integer.parseInt(args[1]);
                } else {
                    System.out.printf("Invalid port. Valid port number range: [%d, %d]\n%s\n",
                            MIN_PORT_NUM, MAX_PORT_NUM, commandUsage);
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
            System.out.printf("\n%sConnected to %s:%d\n\n", warningPref, address, port);

            try {
                // Create a thread to handle incoming messages while also being able to write
                ReadThread rt = new ReadThread(client, lineReader);
                rt.start();

                PrintWriter output = new PrintWriter(client.getOutputStream(), true);

                // Receive input messages from the user and send them to the server
                while (true) {
                    // Read a message from the user
                    String line = lineReader.readLine(inputPrompt).trim();
                    // Clear the current terminal line (that contains the message that was just inputted)
                    terminalWrite(terminal, "\033[1A");  // Move cursor up one line
                    terminalWrite(terminal, "\033[2K");  // Clear line

                    if (line.isEmpty()) continue;

                    if (ClientCmds.hasCommandPref(line)) {
                        // The inputted message may be a command
                        // Exit if the user entered the exit command
                        String commandName = ClientCmds.removeCommandPref(line);

                        // Handle the case where the message is just the command prefix
                        if (commandName.isEmpty()) {
                            terminalWrite(terminal, "%sType a command after %s to run it\n".formatted(warningPref,
                                    ClientCmds.commandPrefix));
                            continue;
                        }

                        // Check if the inputted command exists, and run the corresponding code for it
                        if (ClientCmds.isCommand(line, ClientCmds.exitCommand)) break;
                        else {
                            terminalWrite(terminal, "%sUnrecognized command: %s\n".formatted(warningPref, commandName));
                        }
                    } else {
                        // The inputted message is a normal message, not a command
                        // Print the inputted message
                        terminalWrite(terminal, "%s%s\n".formatted(inputMsgPref, line));

                        // Send the message to the server
                        output.printf("%s\r\n", line);
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

    public static void terminalWrite(Terminal terminal, String msg) {
        terminal.writer().print(msg);
        terminal.flush();
    }

    public static boolean isValidPortString(String str) {
        try {
            int portNum = Integer.parseInt(str);
            return portNum >= MIN_PORT_NUM && portNum <= MAX_PORT_NUM;
        } catch (Exception e) {
            return false;
        }
    }
}


class ReadThread extends Thread {
    Socket client;
    Scanner input;
    LineReader lineReader;

    public ReadThread(Socket client, LineReader lineReader) throws IOException {
        this.client = client;

        try {
            this.input = new Scanner(client.getInputStream());

        } catch (IOException e) {
            // Throw an IOException with a custom message to inform there was an error getting the input stream
            throw new IOException("Couldn't get client input stream");
        }

        this.lineReader = lineReader;
    }

    @Override
    public void run() {
        while (input.hasNextLine()) {
            lineReader.printAbove(input.nextLine());
        }
    }
}