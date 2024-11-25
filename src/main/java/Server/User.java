package Server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

class User {
    static int userCount = 0;
    // UID, used to identify users
    int uid;
    String name;

    Socket socket;
    Scanner input;
    PrintWriter output;

    public User(Socket s) throws IOException {
        socket = s;

        // Create input and output streams
        try {
            input = new Scanner(s.getInputStream());
            output = new PrintWriter(s.getOutputStream(), true);
            uid = userCount;
            name = "UID_%d".formatted(uid);
            userCount++;

        } catch (IOException e) {
            // Throw an IOException with a custom message to inform there was an error creating the user
            throw new IOException("Couldn't get input / output stream of user socket");
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public String getName() {
        return name;
    }

    public int getUid() {
        return uid;
    }

    public void setName(String newName) {
        name = newName;
    }

    public boolean hasNextMessage() {
        return input.hasNextLine();
    }

    public String getNextMessage() {
        return input.nextLine();
    }

    public void sendMessage(String msg, User fromUser) {
        sendString("[%s]: %s".formatted(fromUser.getName(), msg));
    }

    public void sendString(String msg) {
        output.printf("%s\r\n", msg);
    }
}


enum AuthState {
    UNREGISTERED,
    REGISTERED_WRONG_PASSWORD,
    REGISTERED_RIGHT_PASSWORD
}