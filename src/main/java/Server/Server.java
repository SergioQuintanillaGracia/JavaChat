package Server;

import Protocol.Protocol;
import Protocol.Protocol.AuthData;
import Utils.Utils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

class Server {
    private static final String VERSION = "7";
    private static int port = 49200;
    public boolean isAuthEnabled = true;

    private static final int MAX_SAVED_MESSAGES = 100;
    private static Queue<String> messageHistory = new ArrayDeque<>(MAX_SAVED_MESSAGES);

    private static String commandUsage = "Usage: 'java -jar server.jar (port)'";

    private LinkedList<User> users = new LinkedList<>();
    private HashMap<String, String> userData = new HashMap<>();
    public static String noticePref = "    * ";


    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        if (args.length > 1) {
            System.out.printf("Too many arguments. %s\n", commandUsage);
            return;
        }

        if (args.length == 1) {
            if (Utils.isValidPortString(args[0])) {
                port = Integer.parseInt(args[0]);
            } else {
                System.out.printf("Invalid port. Valid port number range: [%d, %d]\n%s\n",
                        Utils.MIN_PORT_NUM, Utils.MAX_PORT_NUM, commandUsage);
                return;
            }
        }

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
        // Get the next message sent by the user, which may contain authentication information or
        // a new user creation request
        String msg = user.getNextMessage();

        if (msg.equals(Protocol.Client.AUTH_CREATE_USER)) {
            // The user wants to create a new user, its next message should be an AuthData object containing the
            // new user information
            AuthData newUserAuthData = AuthData.fromString(user.getNextMessage());
            if (!handleValidAuthData(newUserAuthData, user)) {
                return false;
            }

            if (createUser(newUserAuthData)) {
                // The user was successfully created
                System.out.printf("User %s was created\n", newUserAuthData.getUsername());
                user.sendString(Protocol.Server.USER_CREATION_SUCCESSFUL);

            } else {
                // The user couldn't be created
                user.sendString(Protocol.Server.USER_CREATION_USER_ALREADY_EXISTS);
            }

            // Return false, as the user was created but the client didn't authenticate
            return false;
        }

        AuthData authData = AuthData.fromString(msg);
        if (!handleValidAuthData(authData, user)) {
            return false;
        }

        // We want users to be able to authenticate in parallel, but getting the authentication state of a user
        // (whether the username is registered, they entered the wrong password, or they authenticated successfully
        // and access to the chat should be granted) can't be done in parallel, as it may lead to race conditions
        // `handleAuthentication` will run instructions that can't be executed in parallel in a sequential way
        return handleAuthentication(user, authData);
    }

    public static boolean handleValidAuthData(AuthData authData, User user) {
        if (authData == null || authData.getUsername().isEmpty() || authData.getPassword().isEmpty()) {
            user.sendString(Protocol.Server.EMPTY_USER_OR_PASSWORD);
            return false;
        }

        if (authData.getUsername().length() < AuthData.MIN_USERNAME_LENGTH ||
                authData.getUsername().length() > AuthData.MAX_USERNAME_LENGTH) {
            user.sendString(Protocol.Server.USERNAME_OUT_OF_RANGE);
            return false;
        }

        return true;
    }

    private synchronized boolean handleAuthentication(User user, AuthData authData) {
        AuthState authState = getAuthState(authData);

        switch (authState) {
            case UNREGISTERED:
                // The user is not registered
                user.sendString(Protocol.Server.AUTH_USER_NOT_REGISTERED);
                return false;

            case REGISTERED_WRONG_PASSWORD:
                // The user is registered, but entered a wrong password
                // Inform the user and return false
                user.sendString(Protocol.Server.AUTH_WRONG_PASSWORD);
                return false;

            case REGISTERED_RIGHT_PASSWORD:
                // Break out of the switch, authentication was successful
                break;

            default:
                throw new IllegalStateException("Unexpected authState value: " + authState);
        }

        // If the execution gets to this part, the user has authenticated successfully by logging with an existing
        // user and the correct password

        // Authenticate the user only if a user with the same name is not already logged in
        if (!isUsernameLogged(authData.getUsername())) {
            user.setName(authData.getUsername());
            users.add(user);
            user.sendString(Protocol.Server.AUTH_SUCCESSFUL);
            return true;
        } else {
            user.sendString(Protocol.Server.AUTH_USER_ALREADY_LOGGED);
            return false;
        }
    }

    public boolean isUsernameLogged(String username) {
        for (User u : users) {
            if (u.getName().equals(username))
                return true;
        }

        return false;
    }

    public synchronized AuthState getAuthState(AuthData authData) {
        String password = userData.get(authData.getUsername());

        if (password == null) {
            return AuthState.UNREGISTERED;
        } else if (password.equals(authData.getPassword())) {
            return AuthState.REGISTERED_RIGHT_PASSWORD;
        } else {
            return AuthState.REGISTERED_WRONG_PASSWORD;
        }
    }

    public synchronized void addToHistory(String str) {
        if (messageHistory.size() >= MAX_SAVED_MESSAGES) {
            messageHistory.remove();
        }

        messageHistory.add(str);
    }

    public String createMessage(String msg, User fromUser) {
        return "[%s]: %s".formatted(fromUser.getName(), msg);
    }

    /* Sends a message to a specific user */
    public synchronized void sendMessage(String msg, User toUser) {
        toUser.sendString(Protocol.MESSAGE_PREFIX + msg);
    }

    /* Sends a message to all users connected to the server */
    public synchronized void broadcastMessage(String msg, User fromUser) {
        String formattedMsg = createMessage(msg, fromUser);

        // Send the message to every user except the sender
        for (User u : users) {
            if (u != fromUser) {
                sendMessage(formattedMsg, u);
            }
        }

        // Save the message to the message history queue
        addToHistory(formattedMsg);
    }

    public synchronized void broadcastString(String str) {
        // Send the notice to every user
        for (User u : users) {
            sendMessage(str, u);
        }

        // Save the string to the message history queue
        addToHistory(str);
    }

    public synchronized void broadcastString(String str, User exceptUser) {
        // Send the notice to every user except `exceptUser`
        for (User u : users) {
            if (u != exceptUser) {
                sendMessage(str, u);
            }
        }

        // Save the string to the message history queue
        addToHistory(str);
    }

    public synchronized void addUser(User user) {
        users.add(user);
    }

    public synchronized void removeUser(User user) {
        users.remove(user);
    }

    public synchronized boolean createUser(AuthData authData) {
        String prevPassword = userData.put(authData.getUsername(), authData.getPassword());
        // `prevPassword` will be null if the user wasn't associated with a password before adding it, which
        // means the user wasn't registered
        return prevPassword == null;
    }

    public synchronized void sendMessageHistory(User user) {
        for (String s : messageHistory) {
            sendMessage(s, user);
        }
    }

    public synchronized void handleProtocolMessage(String msg, User user) {
        switch (msg) {
            case Protocol.Client.LOAD_MESSAGE_HISTORY -> {
                sendMessageHistory(user);
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
    }

    @Override
    public void run() {
        if (server.isAuthEnabled) {
            // Keep asking the user to authenticate until authentication is successful
            // `authenticate()` will run `addUser()` internally to avoid 2 users with
            // the same name being authenticated successfully in exceptional cases
            boolean authenticated = false;

            while (!authenticated) {
                try {
                    authenticated = server.authenticate(user);
                } catch (NoSuchElementException e) {
                    System.out.printf("User %s disconnected during the authentication process\n", user.getName());
                    return;
                }
            }
            System.out.printf("User %s authenticated successfully\n", user.getName());
        } else {
            server.addUser(user);
        }

        System.out.printf("User %s connected\n", user.getName());
        server.broadcastString("%sUser %s joined the chat".formatted(Server.noticePref, user.getName()), user);

        // Send a welcome message to the user
        server.sendMessage(server.getWelcomeMessage(user), user);

        // Inform the user they can start typing messages
        user.sendString(Protocol.Server.CAN_TYPE);

        while (user.hasNextMessage()) {
            String str = user.getNextMessage();

            if (str.startsWith(Protocol.PROTOCOL_PREF)) {
                // If the message starts with the protocol prefix, it may be a client request to the server
                server.handleProtocolMessage(str, user);

            } else if (str.startsWith(Protocol.MESSAGE_PREFIX)) {
                String extractedMsg = str.substring(Protocol.MESSAGE_PREFIX.length());

                if (!extractedMsg.isEmpty()) {
                    server.broadcastMessage(extractedMsg, user);
                }

            } else {
                System.out.println("Couldn't interpret message");
            }
        }

        System.out.printf("User %s disconnected\n", user.getName());
        server.broadcastString("%sUser %s left the chat".formatted(Server.noticePref, user.getName()));

        server.removeUser(user);
    }
}