import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private int port;
    private Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Chat server started on port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler handler = new ClientHandler(socket, this);
            clients.add(handler);
            handler.start();
        }
    }

    public void broadcastText(String sender, String message) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendText(sender, message);
            }
        }
    }

    public void broadcastImage(String sender, String fileName, byte[] data) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendImage(sender, fileName, data);
            }
        }
    }

    public void removeClient(ClientHandler c) {
        clients.remove(c);
    }

    public static void main(String[] args) {
        int port = 5000; // 기본 포트
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        try {
            new ChatServer(port).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 클라이언트 하나 담당하는 스레드
    static class ClientHandler extends Thread {
        private Socket socket;
        private ChatServer server;
        private DataInputStream in;
        private DataOutputStream out;
        private String userName;

        ClientHandler(Socket socket, ChatServer server) {
            this.socket = socket;
            this.server = server;
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                // 첫 번째로 클라이언트가 보내는 건 사용자 이름
                userName = in.readUTF();
                System.out.println(userName + " connected from " + socket.getRemoteSocketAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                while (true) {
                    String type = in.readUTF(); // "TEXT" 또는 "IMAGE"
                    if ("TEXT".equals(type)) {
                        String msg = in.readUTF();
                        server.broadcastText(userName, msg);
                    } else if ("IMAGE".equals(type)) {
                        String fileName = in.readUTF();
                        int len = in.readInt();
                        byte[] data = new byte[len];
                        in.readFully(data);
                        server.broadcastImage(userName, fileName, data);
                    }
                }
            } catch (IOException e) {
                System.out.println(userName + " disconnected.");
            } finally {
                server.removeClient(this);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

        synchronized void sendText(String sender, String message) {
            try {
                out.writeUTF("TEXT");
                out.writeUTF(sender);
                out.writeUTF(message);
                out.flush();
            } catch (IOException e) {
                // 클라 죽었으면 그냥 무시
            }
        }

        synchronized void sendImage(String sender, String fileName, byte[] data) {
            try {
                out.writeUTF("IMAGE");
                out.writeUTF(sender);
                out.writeUTF(fileName);
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            } catch (IOException e) {
                // 마찬가지로 무시
            }
        }
    }
}
