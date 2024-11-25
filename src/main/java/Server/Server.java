package Server;

import Protocol.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import Protocol.Protocol.AuthData;

class Server {
    private static final String VERSION = "2";
    public boolean isAuthEnabled = true;
    private int port;
    private LinkedList<User> users = new LinkedList<>();
    public static String noticePref = "    * ";

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
                    // The user object will add itself to the `users` list when it authenticates (if authentication
                    // is required to join the chat)
                    User user = new User(client);

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

    public boolean authenticate(User user) {
        // Send a string to the client that is interpreted as an authentication request
        user.sendString(Protocol.Server.AUTH_REQUEST);
        // Get the authentication information sent by the user
        String authInfoStr = user.getNextMessage();
        AuthData authInfo = AuthData.fromString(authInfoStr);
    }

    /* Sends a message to a specific user */
    public void sendMessage(String msg, User fromUser, User toUser) {
        toUser.sendMessage(msg, fromUser);
    }

    /* Sends a message to all users connected to the server */
    public synchronized void broadcastMessage(String msg, User fromUser) {
        // Send the message to every user except the sender
        for (User u : users) {
            if (u != fromUser) {
                sendMessage(msg, fromUser, u);
            }
        }
    }

    public synchronized void broadcastNotice(String msg) {
        // Send the notice to every user
        for (User u : users) {
            u.sendString(msg);
        }
    }

    public synchronized void broadcastNotice(String msg, User exceptUser) {
        // Send the notice to every user except `exceptUser`
        for (User u : users) {
            if (u != exceptUser) {
                u.sendString(msg);
            }
        }
    }

    public synchronized void removeUser(User user) {
        users.remove(user);
    }
}


class ClientThread extends Thread {
    User user;
    Server server;

    public ClientThread(Server server, User user) {
        this.server = server;
        this.user = user;
    }

    @Override
    public void run() {
        if (server.isAuthEnabled) {
            if (server.authenticate(user)) {
                System.out.println("User %s authenticated successfully");
            }
        }

        System.out.printf("User %s connected\n", user.getName());
        server.broadcastNotice("%sUser %s joined the chat".formatted(Server.noticePref, user.getName()), user);

        // Send a welcome message to the user
        user.sendString(server.getWelcomeMessage(user));

        while (user.hasNextMessage()) {
            server.broadcastMessage(user.getNextMessage(), user);
        }

        System.out.printf("User %s disconnected\n", user.getName());
        server.broadcastNotice("%sUser %s left the chat".formatted(Server.noticePref, user.getName()));

        server.removeUser(user);
    }
}