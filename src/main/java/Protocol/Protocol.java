package Protocol;

import java.io.Serializable;

public class Protocol {
    public static final String PROTOCOL_PREF = "$";

    public static class Server {
        public static final String AUTH_REQUEST = PROTOCOL_PREF + "auth_request";
    }

    public static class Client {
        public static final String AUTH_CREATE_USER = PROTOCOL_PREF + "auth_create_user";
    }

    public class AuthData {
        private final String username;
        private final String password;

        public AuthData(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {

        }

        public AuthData fromString() {

        }
    }
}
