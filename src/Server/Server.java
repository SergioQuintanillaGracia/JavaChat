package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

class Server {
    private static final String VERSION = "1";
    private int port;
    private LinkedList<User> users = new LinkedList<>();

    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = 49200;
        Server server = new Server(port);
        server.start();
    }

    public void start() {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.printf("Server version %s is now listening on port %d\n", VERSION, port);

            while (true) {
                // Accept an incoming connection
                Socket client = server.accept();

                try {
                    // Create a user object for the connection
                    User user = new User(client);
                    users.add(user);

                    // Start a thread to handle the connection
                    ClientThread ct = new ClientThread(this, user);
                    ct.start();

                } catch (IOException e) {
                    // Handle IOExceptions that occur when creating a user
                    System.err.println(e.getMessage());
                    client.close();
                }
            }

        } catch (IOException e) {
            System.err.printf("I/O error occurred at server opened at port %d\n", port);
            System.err.println(e.getMessage());
        }
    }

    /* Returns a welcome message that can be sent to a user when they connect to the server */
    public String getWelcomeMessage(User u) {
        return """
                #  Welcome to the main JavaChat server!
                #  Server version: %s
                #  Logged as: %s
                """
                .formatted(VERSION, u.getName());
    }

    /* Sends a message to a specific user */
    public void sendMessage(String msg, User fromUser, User toUser) {
        toUser.sendMessage(msg, fromUser);
    }

    /* Sends a message to all users connected to the server */
    public void broadcastMessage(String msg, User fromUser) {
        for (User u : users) {
            // Send the message to every user except the sender
            if (u != fromUser) {
                sendMessage(msg, fromUser, u);
            }
        }
    }
}


class ClientThread extends Thread {
    User user;
    Server server;

    public ClientThread(Server server, User user) {
        this.server = server;
        this.user = user;

        // Send a welcome message to the user
        user.sendString(server.getWelcomeMessage(user));
    }

    @Override
    public void run() {
        while (user.hasNextMessage()) {
            server.broadcastMessage(user.getNextMessage(), user);
        }
    }
}