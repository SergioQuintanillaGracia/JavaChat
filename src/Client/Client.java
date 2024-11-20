package Client;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

class Client {
    private static String address = "localhost";
    private static int port = 49200;

    public static void main(String[] args) {
        try (Socket client = new Socket(address, port)) {
            System.out.printf("[!] Connected to %s:%d\n", address, port);

            // Create a thread to handle incoming messages while also being able to write
            ReadThread rt = new ReadThread(client);
            rt.start();

            

            // Create a scanner to read messages from the standard input
            Scanner sc = new Scanner(System.in);

            // Receive input messages from the user and send them to the server
            while (true) {
                String line = sc.nextLine();

                // Exit if the user entered the exit command
                if (ClientCmds.isCommand(line, ClientCmds.exitCommand)) break;

                // Send the message to the server

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

    public ReadThread(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {

    }
}