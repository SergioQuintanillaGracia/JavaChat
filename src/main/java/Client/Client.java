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
    private static String address = "localhost";
    private static int port = 49200;

    public static void main(String[] args) {
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
            System.out.printf("[!] Connected to %s:%d\n", address, port);

            try {
                // Create a thread to handle incoming messages while also being able to write
                ReadThread rt = new ReadThread(client, lineReader);
                rt.start();

                PrintWriter output = new PrintWriter(client.getOutputStream(), true);

                // Receive input messages from the user and send them to the server
                while (true) {
                    // Read a message from the user
                    String line = lineReader.readLine("> ");

                    // Exit if the user entered the exit command
                    if (ClientCmds.isCommand(line, ClientCmds.exitCommand)) break;

                    // Send the message to the server
                    output.printf("%s\r\n", line);
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